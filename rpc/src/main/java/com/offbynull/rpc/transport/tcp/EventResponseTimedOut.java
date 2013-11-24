package com.offbynull.rpc.transport.tcp;

import com.offbynull.rpc.transport.OutgoingMessageResponseListener;
import java.net.InetSocketAddress;
import org.apache.commons.lang3.Validate;

final class EventResponseTimedOut implements Event {
    private OutgoingMessageResponseListener<InetSocketAddress> receiver;

    EventResponseTimedOut(OutgoingMessageResponseListener<InetSocketAddress> receiver) {
        Validate.notNull(receiver);
        
        this.receiver = receiver;
    }

    public OutgoingMessageResponseListener<InetSocketAddress> getReceiver() {
        return receiver;
    }
    
    
}