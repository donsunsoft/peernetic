package com.offbynull.p2prpc.transport;

import java.nio.ByteBuffer;
import org.apache.commons.lang3.Validate;

public final class IncomingMessage<A> {
    private A from;
    private ByteBuffer data;
    private long arriveTime;

    public IncomingMessage(A from, ByteBuffer data, long arriveTime) {
        Validate.notNull(from);
        Validate.notNull(data);
        
        this.from = from;
        this.data = ByteBuffer.allocate(data.remaining()).put(data);
        this.arriveTime = arriveTime;
        this.data.flip();
    }

    public IncomingMessage(A from, byte[] data, long arriveTime) {
        Validate.notNull(from);
        Validate.notNull(data);
        
        this.from = from;
        this.data = ByteBuffer.allocate(data.length).put(data);
        this.arriveTime = arriveTime;
        this.data.flip();
    }

    public A getFrom() {
        return from;
    }

    public ByteBuffer getData() {
        return data.asReadOnlyBuffer();
    }

    public long getArriveTime() {
        return arriveTime;
    }
    
}
