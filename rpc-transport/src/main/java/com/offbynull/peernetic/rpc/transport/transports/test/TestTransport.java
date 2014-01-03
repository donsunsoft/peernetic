/*
 * Copyright (c) 2013, Kasra Faghihi, All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */
package com.offbynull.peernetic.rpc.transport.transports.test;

import com.offbynull.peernetic.common.concurrent.actor.ActorQueue;
import com.offbynull.peernetic.common.concurrent.actor.ActorQueueWriter;
import com.offbynull.peernetic.common.concurrent.actor.Message;
import com.offbynull.peernetic.common.concurrent.actor.Message.MessageResponder;
import com.offbynull.peernetic.common.concurrent.actor.PushQueue;
import com.offbynull.peernetic.rpc.transport.Transport;
import com.offbynull.peernetic.rpc.transport.DropResponseCommand;
import com.offbynull.peernetic.rpc.transport.SendRequestCommand;
import com.offbynull.peernetic.rpc.transport.SendResponseCommand;
import com.offbynull.peernetic.rpc.transport.RequestArrivedEvent;
import com.offbynull.peernetic.rpc.transport.ResponseArrivedEvent;
import com.offbynull.peernetic.rpc.transport.ResponseErroredEvent;
import com.offbynull.peernetic.rpc.transport.common.IncomingMessageManager;
import com.offbynull.peernetic.rpc.transport.common.IncomingMessageManager.IncomingPacketManagerResult;
import com.offbynull.peernetic.rpc.transport.common.IncomingMessageManager.IncomingRequest;
import com.offbynull.peernetic.rpc.transport.common.IncomingMessageManager.IncomingResponse;
import com.offbynull.peernetic.rpc.transport.common.IncomingMessageManager.IncomingRequestInfo;
import com.offbynull.peernetic.rpc.transport.common.MessageId;
import com.offbynull.peernetic.rpc.transport.common.OutgoingMessageManager;
import com.offbynull.peernetic.rpc.transport.common.OutgoingMessageManager.OutgoingPacketManagerResult;
import com.offbynull.peernetic.rpc.transport.common.OutgoingMessageManager.Packet;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import org.apache.commons.lang3.Validate;

/**
 * A {@link Transport} used for testing. Backed by a {@link TestHub}.
 * @author Kasra Faghihi
 * @param <A> address type
 */
public final class TestTransport<A> extends Transport<A> {

    private A address;

    private long outgoingResponseTimeout;
    private long incomingResponseTimeout;
    
    private OutgoingMessageManager<A> outgoingPacketManager;
    private IncomingMessageManager<A> incomingPacketManager;
    private long nextId;
    
    private int cacheSize;
    
    private ActorQueueWriter dstWriter;
    private ActorQueueWriter hubWriter;

    /**
     * Constructs a {@link TestTransport} object.
     * @param address address to listen on
     * @param cacheSize number of packet ids to cache
     * @param outgoingResponseTimeout timeout duration for responses for outgoing requests to arrive
     * @param incomingResponseTimeout timeout duration for responses for incoming requests to be processed
     * @param hubWriter writer to {@link TestHub}
     * @throws IOException on error
     * @throws IllegalArgumentException if port is out of range, or if any of the other arguments are {@code <= 0};
     * @throws NullPointerException if any arguments are {@code null}
     */
    public TestTransport(A address, int cacheSize, long outgoingResponseTimeout,
            long incomingResponseTimeout, ActorQueueWriter hubWriter) throws IOException {
        super(true);
        
        Validate.notNull(address);
        Validate.inclusiveBetween(1, Integer.MAX_VALUE, cacheSize);
        Validate.inclusiveBetween(1L, Long.MAX_VALUE, outgoingResponseTimeout);
        Validate.inclusiveBetween(1L, Long.MAX_VALUE, incomingResponseTimeout);
        Validate.notNull(hubWriter);

        this.address = address;

        this.outgoingResponseTimeout = outgoingResponseTimeout;
        this.incomingResponseTimeout = incomingResponseTimeout;
        
        this.cacheSize = cacheSize;
        this.hubWriter = hubWriter;
    }

    @Override
    protected ActorQueue createQueue() {
        return new ActorQueue();
    }

    @Override
    protected void onStart() throws Exception {
        dstWriter = getDestinationWriter();
        Validate.validState(dstWriter != null);
        
        outgoingPacketManager = new OutgoingMessageManager<>(getOutgoingFilter()); 
        incomingPacketManager = new IncomingMessageManager<>(cacheSize, getIncomingFilter());

        // bind to testhub here
        Message msg = Message.createOneWayMessage(new ActivateEndpointCommand<>(address, getSelfWriter()));
        hubWriter.push(msg);
    }
    
    @Override
    protected long onStep(long timestamp, Iterator<Message> iterator, PushQueue pushQueue) throws Exception {
        // process messages
        while (iterator.hasNext()) {
            Message msg = iterator.next();
            Object content = msg.getContent();
            
            if (content instanceof SendRequestCommand) {
                SendRequestCommand<A> src = (SendRequestCommand) content;
                MessageResponder responder = msg.getResponder();
                if (responder == null) {
                    continue;
                }
                
                long id = nextId++;
                outgoingPacketManager.outgoingRequest(id, src.getTo(), src.getData(), timestamp + 1L,
                        timestamp + outgoingResponseTimeout, responder);
            } else if (content instanceof SendResponseCommand) {
                SendResponseCommand<A> src = (SendResponseCommand) content;
                Long id = msg.getResponseToId(Long.class);
                if (id == null) {
                    continue;
                }
                
                IncomingRequestInfo<A> pendingRequest = incomingPacketManager.responseFormed(id);
                if (pendingRequest == null) {
                    continue;
                }
                
                outgoingPacketManager.outgoingResponse(id, pendingRequest.getFrom(), src.getData(), pendingRequest.getMessageId(),
                        timestamp + 1L);
            } else if (content instanceof DropResponseCommand) {
                //DropResponseCommand trc = (DropResponseCommand) content;
                Long id = msg.getResponseToId(Long.class);
                if (id == null) {
                    continue;
                }
                
                incomingPacketManager.responseFormed(id);
            } else if (content instanceof ReceiveMessageEvent) {
                ReceiveMessageEvent<A> rme = (ReceiveMessageEvent) content;
                
                long id = nextId++;
                incomingPacketManager.incomingData(id, rme.getFrom(), rme.getData(), timestamp + incomingResponseTimeout);
            }
        }
        
        
        
        // process timeouts for outgoing requests
        OutgoingPacketManagerResult opmResult = outgoingPacketManager.process(timestamp);
        
        Collection<MessageResponder> respondersForFailures = opmResult.getMessageRespondersForFailures();
        for (MessageResponder responder : respondersForFailures) {
            responder.respondDeferred(pushQueue, new ResponseErroredEvent());
        }

        
        
        Packet<A> packet;
        while ((packet = outgoingPacketManager.getNextOutgoingPacket()) != null) {
            SendMessageCommand<A> smc = new SendMessageCommand(address, packet.getTo(), packet.getData());
            pushQueue.queueOneWayMessage(hubWriter, smc);
        }
        
        
        
        // process timeouts for incoming requests
        IncomingPacketManagerResult<A> ipmResult = incomingPacketManager.process(timestamp);
        
        for (IncomingRequest<A> incomingRequest : ipmResult.getNewIncomingRequests()) {
            RequestArrivedEvent<A> event = new RequestArrivedEvent<>(
                    incomingRequest.getFrom(),
                    incomingRequest.getData(),
                    timestamp);
            pushQueue.queueRespondableMessage(dstWriter, incomingRequest.getId(), event);
        }
        
        for (IncomingResponse<A> incomingResponse : ipmResult.getNewIncomingResponses()) {
            MessageId messageId = incomingResponse.getMessageId();
            MessageResponder responder = outgoingPacketManager.responseReturned(messageId);
            
            if (responder == null) {
                continue;
            }
            
            ResponseArrivedEvent<A> event = new ResponseArrivedEvent<>(
                    incomingResponse.getFrom(),
                    incomingResponse.getData(),
                    timestamp);
            responder.respondDeferred(pushQueue, event);
        }
        
        
        // calculate max time until next invoke
        long waitTime = Long.MAX_VALUE;
        waitTime = Math.min(waitTime, opmResult.getMaxTimestamp());
        waitTime = Math.min(waitTime, ipmResult.getMaxTimestamp());
        
        return waitTime;
    }

    @Override
    protected void onStop(PushQueue pushQueue) throws Exception {
        for (MessageResponder responder : outgoingPacketManager.process(Long.MAX_VALUE).getMessageRespondersForFailures()) {
            ResponseErroredEvent ree = new ResponseErroredEvent();
            responder.respondDeferred(pushQueue, ree);
        }
        
        Message msg = Message.createOneWayMessage(new DeactivateEndpointCommand<>(address));
        hubWriter.push(msg);
    }

}
