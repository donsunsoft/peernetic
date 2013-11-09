package com.offbynull.p2prpc.transport.tcp;

import com.offbynull.p2prpc.transport.OutgoingData;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

final class SendQueuedResponse implements OutgoingResponse {
    private SocketChannel channel;
    private OutgoingData<InetSocketAddress> data;

    SendQueuedResponse(SocketChannel channel, OutgoingData<InetSocketAddress> data) {
        this.channel = channel;
        this.data = data;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public OutgoingData<InetSocketAddress> getData() {
        return data;
    }
    
}