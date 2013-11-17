package com.offbynull.p2prpc.transport.udp;

import com.offbynull.p2prpc.transport.IncomingMessage;
import com.offbynull.p2prpc.transport.NonSessionedTransport;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.commons.lang3.Validate;

final class UdpReceiveNotifier implements NonSessionedTransport.ReceiveNotifier<InetSocketAddress> {
    private LinkedBlockingQueue<NonSessionedTransport.MessageReceiver> handlers;

    UdpReceiveNotifier() {
        handlers = new LinkedBlockingQueue<>();
    }

    @Override
    public void add(NonSessionedTransport.MessageReceiver<InetSocketAddress> e) {
        Validate.notNull(e);
        handlers.add(e);
    }

    @Override
    public void remove(NonSessionedTransport.MessageReceiver<InetSocketAddress> e) {
        Validate.notNull(e);
        handlers.remove(e);
    }

    void notify(Collection<IncomingMessage<InetSocketAddress>> packets) {
        Validate.noNullElements(packets);
        
        NonSessionedTransport.MessageReceiver[] handlersArray = handlers.toArray(new NonSessionedTransport.MessageReceiver[0]);
        for (IncomingMessage<InetSocketAddress> packet : packets) {
            for (NonSessionedTransport.MessageReceiver<InetSocketAddress> handler : handlersArray) {
                // to array to avoid locks
                try {
                    if (handler.messageArrived(packet)) {
                        break;
                    }
                } catch (RuntimeException re) {
                    // do nothing
                }
            }
        }
    }
    
}
