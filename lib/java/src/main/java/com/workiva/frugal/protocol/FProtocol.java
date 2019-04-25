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

package com.workiva.frugal.protocol;

import com.workiva.frugal.FContext;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TSet;
import org.apache.thrift.protocol.TStruct;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static com.workiva.frugal.FContext.CID_HEADER;
import static com.workiva.frugal.FContext.OPID_HEADER;

/**
 * FProtocol is Frugal's equivalent of Thrift's TProtocol. It defines the
 * serialization protocol used for messages, such as JSON, binary, etc. FProtocol
 * actually extends TProtocol and adds support for serializing FContext. In
 * practice, FProtocol simply wraps a TProtocol and uses Thrift's built-in
 * serialization. FContext is encoded before the TProtocol serialization of the
 * message using a simple binary protocol. See the protocol documentation for more
 * details.
 */
public class FProtocol extends TProtocol {

    private TProtocol wrapped;
    private Map<Object, Object> ephemeralProperties;

    protected FProtocol(TProtocol proto) {
        super(proto.getTransport());
        wrapped = proto;
        ephemeralProperties = new HashMap<>();
    }

    public void setEphemeralProperties(Map<Object, Object> ephemeralProperties) {
        this.ephemeralProperties = ephemeralProperties;
    }

    /**
     * Writes the request headers set on the given FContext into the protocol.
     *
     * @param context context with headers to write
     * @throws TException an error occurred while writing the headers
     */
    public void writeRequestHeader(FContext context) throws TException {
        wrapped.getTransport().write(HeaderUtils.encode(context.getRequestHeaders()));
    }

    /**
     * Reads the request headers on the protocol into a returned FContext.
     *
     * @return FContext with read headers
     * @throws TException an error occurred while reading the headers
     */
    public FContext readRequestHeader() throws TException {
        Map<String, String> headers = HeaderUtils.read(wrapped.getTransport());
        // Store the opId so it can be added to the response headers
        // as the opId will be overridden when creating the FContext
        String opId = headers.get(OPID_HEADER);

        FContext ctx = FContext.withRequestHeaders(headers);
        // Put the opId in response headers for the response.
        ctx.addResponseHeader(OPID_HEADER, opId);

        String cid = ctx.getCorrelationId();
        if (cid != null && !cid.isEmpty()) {
            ctx.addResponseHeader(CID_HEADER, cid);
        }

        // setting the ephemeral properties allows this map in FContext and the
        // map in the server to be the same.
        ctx.setEphemeralProperties(ephemeralProperties);
        return ctx;
    }

    /**
     * Writes the response headers set on the given FContext into the protocol.
     *
     * @param context context with headers to write
     * @throws TException an error occurred while writing the headers
     */
    public void writeResponseHeader(FContext context) throws TException {
        wrapped.getTransport().write(HeaderUtils.encode(context.getResponseHeaders()));
    }

    /**
     * Reads the response headers on the protocol into the given FContext.
     *
     * @param context context to read headers into
     * @throws TException an error occurred while reading the headers
     */
    public void readResponseHeader(FContext context) throws TException {
        Map<String, String> headers = HeaderUtils.read(wrapped.getTransport());
        // Don't want to overwrite the opid header we set for a propagated
        // response.
        headers.remove(FContext.OPID_HEADER);
        context.addResponseHeaders(headers);
    }

    @Override
    public void writeMessageBegin(TMessage tMessage) throws TException {
        wrapped.writeMessageBegin(tMessage);
    }

    @Override
    public void writeMessageEnd() throws TException {
        wrapped.writeMessageEnd();
    }

    @Override
    public void writeStructBegin(TStruct tStruct) throws TException {
        wrapped.writeStructBegin(tStruct);
    }

    @Override
    public void writeStructEnd() throws TException {
        wrapped.writeStructEnd();
    }

    @Override
    public void writeFieldBegin(TField tField) throws TException {
        wrapped.writeFieldBegin(tField);
    }

    @Override
    public void writeFieldEnd() throws TException {
        wrapped.writeFieldEnd();
    }

    @Override
    public void writeFieldStop() throws TException {
        wrapped.writeFieldStop();
    }

    @Override
    public void writeMapBegin(TMap tMap) throws TException {
        wrapped.writeMapBegin(tMap);
    }

    @Override
    public void writeMapEnd() throws TException {
        wrapped.writeMapEnd();
    }

    @Override
    public void writeListBegin(TList tList) throws TException {
        wrapped.writeListBegin(tList);
    }

    @Override
    public void writeListEnd() throws TException {
        wrapped.writeListEnd();
    }

    @Override
    public void writeSetBegin(TSet tSet) throws TException {
        wrapped.writeSetBegin(tSet);
    }

    @Override
    public void writeSetEnd() throws TException {
        wrapped.writeSetEnd();
    }

    @Override
    public void writeBool(boolean b) throws TException {
        wrapped.writeBool(b);
    }

    @Override
    public void writeByte(byte b) throws TException {
        wrapped.writeByte(b);
    }

    @Override
    public void writeI16(short i) throws TException {
        wrapped.writeI16(i);
    }

    @Override
    public void writeI32(int i) throws TException {
        wrapped.writeI32(i);
    }

    @Override
    public void writeI64(long l) throws TException {
        wrapped.writeI64(l);
    }

    @Override
    public void writeDouble(double v) throws TException {
        wrapped.writeDouble(v);
    }

    @Override
    public void writeString(String s) throws TException {
        wrapped.writeString(s);
    }

    @Override
    public void writeBinary(ByteBuffer byteBuffer) throws TException {
        wrapped.writeBinary(byteBuffer);
    }

    @Override
    public TMessage readMessageBegin() throws TException {
        return wrapped.readMessageBegin();
    }

    @Override
    public void readMessageEnd() throws TException {
        wrapped.readMessageEnd();
    }

    @Override
    public TStruct readStructBegin() throws TException {
        return wrapped.readStructBegin();
    }

    @Override
    public void readStructEnd() throws TException {
        wrapped.readStructEnd();
    }

    @Override
    public TField readFieldBegin() throws TException {
        return wrapped.readFieldBegin();
    }

    @Override
    public void readFieldEnd() throws TException {
        wrapped.readFieldEnd();
    }

    @Override
    public TMap readMapBegin() throws TException {
        return wrapped.readMapBegin();
    }

    @Override
    public void readMapEnd() throws TException {
        wrapped.readMapEnd();
    }

    @Override
    public TList readListBegin() throws TException {
        return wrapped.readListBegin();
    }

    @Override
    public void readListEnd() throws TException {
        wrapped.readListEnd();
    }

    @Override
    public TSet readSetBegin() throws TException {
        return wrapped.readSetBegin();
    }

    @Override
    public void readSetEnd() throws TException {
        wrapped.readSetEnd();
    }

    @Override
    public boolean readBool() throws TException {
        return wrapped.readBool();
    }

    @Override
    public byte readByte() throws TException {
        return wrapped.readByte();
    }

    @Override
    public short readI16() throws TException {
        return wrapped.readI16();
    }

    @Override
    public int readI32() throws TException {
        return wrapped.readI32();
    }

    @Override
    public long readI64() throws TException {
        return wrapped.readI64();
    }

    @Override
    public double readDouble() throws TException {
        return wrapped.readDouble();
    }

    @Override
    public String readString() throws TException {
        return wrapped.readString();
    }

    @Override
    public ByteBuffer readBinary() throws TException {
        return wrapped.readBinary();
    }

    @Override
    public void reset() {
        wrapped.reset();
    }
}
