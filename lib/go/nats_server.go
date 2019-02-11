/*
 * Copyright 2017 Workiva
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package frugal

import (
	"bytes"
	"time"

	"git.apache.org/thrift.git/lib/go/thrift"
	"github.com/nats-io/go-nats"
)

const (
	defaultWorkQueueLen = 64
	defaultWatermark    = 5 * time.Second
)

type frameWrapper struct {
	frameBytes []byte
	timestamp  time.Time
	reply      string
	correlationID string
}

// FNatsServerBuilder configures and builds NATS server instances.
type FNatsServerBuilder struct {
	conn          *nats.Conn
	processor     FProcessor
	protoFactory  *FProtocolFactory
	subjects      []string
	queue         string
	workerCount   uint
	queueLen      uint
	highWatermark time.Duration

	// event handlers
	onHighWatermark func(string, time.Duration)
	onNewRequest func(string)
	onFinishedRequest func(string)
}

// NewFNatsServerBuilder creates a builder which configures and builds NATS
// server instances.
func NewFNatsServerBuilder(conn *nats.Conn, processor FProcessor,
	protoFactory *FProtocolFactory, subjects []string) *FNatsServerBuilder {
	return &FNatsServerBuilder{
		conn:          conn,
		processor:     processor,
		protoFactory:  protoFactory,
		subjects:      subjects,
		workerCount:   1,
		queueLen:      defaultWorkQueueLen,
		highWatermark: defaultWatermark,
	}
}

// WithQueueGroup adds a NATS queue group to receive requests on.
func (f *FNatsServerBuilder) WithQueueGroup(queue string) *FNatsServerBuilder {
	f.queue = queue
	return f
}

// WithWorkerCount controls the number of goroutines used to process requests.
func (f *FNatsServerBuilder) WithWorkerCount(workerCount uint) *FNatsServerBuilder {
	f.workerCount = workerCount
	return f
}

// WithQueueLength controls the length of the work queue used to buffer
// requests.
func (f *FNatsServerBuilder) WithQueueLength(queueLength uint) *FNatsServerBuilder {
	f.queueLen = queueLength
	return f
}

// WithHighWatermark controls the time duration requests wait in queue before
// triggering slow consumer logic.
func (f *FNatsServerBuilder) WithHighWatermark(highWatermark time.Duration) *FNatsServerBuilder {
	f.highWatermark = highWatermark
	return f
}

// WithHighWatermarkHandler sets a function to be called when the FNatsServer
// encounters a request that took an excess amount of time to begin processing.
// Takes a correlation id and the duration the message was waiting to be processed.
func (f *FNatsServerBuilder) WithHighWatermakeHandler(handler func(string, time.Duration)) *FNatsServerBuilder {
	f.onHighWatermark = handler
	return f
}

// WithNewRequestHandler sets a function to be called when a new request is received,
// before being put into any work queue.
func (f *FNatsServerBuilder) WithNewRequestHandler(handler func(string)) *FNatsServerBuilder {
	f.onNewRequest = handler
	return f
}

// WithFinishedRequestHandler sets a function to be called when a request has
// been successfully processed.
func (f *FNatsServerBuilder) WithFinishedRequestHandler(handler func(string)) *FNatsServerBuilder {
	f.onFinishedRequest = handler
	return f
}

func defaultNatsHighWatermarkHandler(correlationID string, dur time.Duration) {
	logger().
		WithField("correlation_id", correlationID).
		Warnf("frugal: request spent %+v in the transport buffer, your consumer might be backed up", dur)
}

func defaultNatsNewRequestHandler(correlationID string) {}

func defaultNatsFinishedRequestHandler(correlationID string) {}

func defaultNatsInvalidRequestHandler(err error) {
	logger().
		WithError(err).
		Error("frugal: received invalid request")
}

func defaultNatsRespondErrorHandler(correlationID string, err error) {
	logger().
		WithField("correlation_id", correlationID).
		WithError(err).
		Error("frugal: failed to send response")
}

// Build a new configured NATS FServer.
func (f *FNatsServerBuilder) Build() FServer {
	if f.onHighWatermark == nil {
		f.onHighWatermark = defaultNatsHighWatermarkHandler
	}
	if f.onNewRequest == nil {
		f.onNewRequest = defaultNatsNewRequestHandler
	}
	if f.onFinishedRequest == nil {
		f.onFinishedRequest = defaultNatsFinishedRequestHandler
	}

	return &fNatsServer{
		conn:          f.conn,
		processor:     f.processor,
		protoFactory:  f.protoFactory,
		subjects:      f.subjects,
		queue:         f.queue,
		workerCount:   f.workerCount,
		workC:         make(chan *frameWrapper, f.queueLen),
		quit:          make(chan struct{}),
		highWatermark: f.highWatermark,

		onHighWatermark: f.onHighWatermark,
		onNewRequest: f.onNewRequest,
		onFinishedRequest: f.onFinishedRequest,
	}
}

// fNatsServer implements FServer by using NATS as the underlying transport.
// Clients must connect with the transport created by NewNatsFTransport.
type fNatsServer struct {
	conn          *nats.Conn
	processor     FProcessor
	protoFactory  *FProtocolFactory
	subjects      []string
	queue         string
	workerCount   uint
	workC         chan *frameWrapper
	quit          chan struct{}
	highWatermark time.Duration

	onHighWatermark func(string, time.Duration)
	onNewRequest func(string)
	onFinishedRequest func(string)
}

// Serve starts the server.
func (f *fNatsServer) Serve() error {
	subscriptions := []*nats.Subscription{}
	for _, subject := range f.subjects {
		sub, err := f.conn.QueueSubscribe(subject, f.queue, f.handler)
		if err != nil {
			return err
		}
		subscriptions = append(subscriptions, sub)
	}

	for i := uint(0); i < f.workerCount; i++ {
		go f.worker()
	}

	logger().Info("frugal: server running...")
	<-f.quit
	logger().Info("frugal: server stopping...")

	for _, sub := range subscriptions {
		sub.Unsubscribe()
	}

	return nil
}

// Stop the server.
func (f *fNatsServer) Stop() error {
	close(f.quit)
	return nil
}

// handler is invoked when a request is received. The request is placed on the
// work channel which is processed by a worker goroutine.
func (f *fNatsServer) handler(msg *nats.Msg) {
	if msg.Reply == "" {
		logger().Warn("frugal: discarding invalid NATS request (no reply)")
		//logger().Warn("frugal: discarding invalid NATS request (no reply)")
		return
	}
	headers, err := getHeadersFromFrame(msg.Data)
	if err != nil {
		logger().WithError(err).Warn("frugal: discarding invalid NATS request, could not get FContext headers")
		return
	}
	cid := headers[cidHeader]
	f.onNewRequest(cid)

	select {
	case f.workC <- &frameWrapper{frameBytes: msg.Data, timestamp: time.Now(), reply: msg.Reply, correlationID: cid}:
	case <-f.quit:
		return
	}
}

// worker should be called as a goroutine. It reads requests off the work
// channel and processes them.
func (f *fNatsServer) worker() {
	for {
		select {
		case <-f.quit:
			return
		case frame := <-f.workC:
			dur := time.Since(frame.timestamp)
			if dur > f.highWatermark {
				f.onHighWatermark(frame.correlationID, dur)
				//logger().Warnf("frugal: request spent %+v in the transport buffer, your consumer might be backed up", dur)
			}
			if err := f.processFrame(frame); err != nil {
				logger().Errorf("frugal: error processing request: %s", err.Error())
				continue
			}
			f.onFinishedRequest(frame.correlationID)
		}
	}
}

// processFrame invokes the FProcessor and sends the response on the given
// subject.
func (f *fNatsServer) processFrame(frame *frameWrapper) error {
	// Read and process frame.
	input := &thrift.TMemoryBuffer{Buffer: bytes.NewBuffer(frame.frameBytes[4:])} // Discard frame size
	// Only allow 1MB to be buffered.
	output := NewTMemoryOutputBuffer(natsMaxMessageSize)
	iprot := f.protoFactory.GetProtocol(input)
	oprot := f.protoFactory.GetProtocol(output)
	if err := f.processor.Process(iprot, oprot); err != nil {
		return err
	}

	if !output.HasWriteData() {
		return nil
	}

	// Send response.
	return f.conn.Publish(frame.reply, output.Bytes())
}
