package com.offbynull.peernetic.common.concurrent.actor.endpoints.local;

import com.offbynull.peernetic.common.concurrent.actor.Endpoint;
import com.offbynull.peernetic.common.concurrent.actor.EndpointHandler;
import com.offbynull.peernetic.common.concurrent.actor.Incoming;
import com.offbynull.peernetic.common.concurrent.actor.MessageUtils;
import com.offbynull.peernetic.common.concurrent.actor.Outgoing;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.lang3.Validate;

public final class LocalEndpointHandler implements EndpointHandler<LocalEndpoint> {

    @Override
    public void push(Endpoint source, LocalEndpoint destination, Collection<Outgoing> outgoing) {
        Validate.notNull(source);
        Validate.notNull(destination);
        Validate.noNullElements(outgoing);
        
        Collection<Incoming> incoming = new ArrayList<>(outgoing.size());
        
        for (Outgoing outgoingMsg : outgoing) {
            Incoming incomingMsg = MessageUtils.flip(source, outgoingMsg);
            incoming.add(incomingMsg);
        }
        
        destination.getActorQueueWriter().push(incoming);
    }
    
}
