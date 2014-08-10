package com.offbynull.peernetic.demos.chord.fsms;

import com.offbynull.peernetic.actor.Endpoint;
import com.offbynull.peernetic.actor.EndpointDirectory;
import com.offbynull.peernetic.actor.EndpointScheduler;
import com.offbynull.peernetic.common.DurationUtils;
import com.offbynull.peernetic.common.Id;
import com.offbynull.peernetic.common.IncomingRequestManager;
import com.offbynull.peernetic.common.NonceGenerator;
import com.offbynull.peernetic.common.NonceWrapper;
import com.offbynull.peernetic.common.OutgoingRequestManager;
import com.offbynull.peernetic.common.Response;
import com.offbynull.peernetic.demos.chord.core.ChordUtils;
import com.offbynull.peernetic.demos.chord.core.ExternalPointer;
import com.offbynull.peernetic.demos.chord.core.Pointer;
import com.offbynull.peernetic.demos.chord.messages.external.GetClosestPrecedingFingerRequest;
import com.offbynull.peernetic.demos.chord.messages.external.GetClosestPrecedingFingerResponse;
import com.offbynull.peernetic.demos.chord.messages.external.GetIdRequest;
import com.offbynull.peernetic.demos.chord.messages.external.GetIdResponse;
import com.offbynull.peernetic.demos.chord.messages.external.GetPredecessorRequest;
import com.offbynull.peernetic.demos.chord.messages.external.GetPredecessorResponse;
import com.offbynull.peernetic.demos.chord.messages.external.GetSuccessorRequest;
import com.offbynull.peernetic.demos.chord.messages.external.NotifyRequest;
import com.offbynull.peernetic.fsm.FilterHandler;
import com.offbynull.peernetic.fsm.FiniteStateMachine;
import com.offbynull.peernetic.fsm.StateHandler;
import java.time.Duration;
import java.time.Instant;
import org.apache.commons.lang3.Validate;

public final class Stabilize<A> {
    public static final String INITIAL_STATE = "start";
    public static final String AWAIT_PREDECESSOR_RESPONSE_STATE = "pred_await";
    public static final String DONE_STATE = "done";

    private static final Duration TIMER_DURATION = Duration.ofSeconds(3L);

    private final Id selfId;
    private final Pointer existingSuccessor;

    private final OutgoingRequestManager<A, byte[]> outgoingRequestManager;
    private final EndpointScheduler endpointScheduler;
    private final Endpoint selfEndpoint;
    
    private Pointer newSuccessor;

    public Stabilize(Id selfId, Pointer successor, EndpointDirectory<A> endpointDirectory, EndpointScheduler endpointScheduler,
            Endpoint selfEndpoint, NonceGenerator<byte[]> nonceGenerator, NonceWrapper<byte[]> nonceWrapper) {
        Validate.notNull(selfId);
        Validate.notNull(successor);
        Validate.notNull(endpointDirectory);
        Validate.notNull(endpointScheduler);
        Validate.notNull(selfEndpoint);
        Validate.notNull(nonceGenerator);
        Validate.notNull(nonceWrapper);
        
        this.selfId = selfId;
        this.existingSuccessor = successor;
        this.outgoingRequestManager = new OutgoingRequestManager<>(selfEndpoint, nonceGenerator, nonceWrapper, endpointDirectory);
        this.endpointScheduler = endpointScheduler;
        this.selfEndpoint = selfEndpoint;
    }
    
    @StateHandler(INITIAL_STATE)
    public void handleStart(String state, FiniteStateMachine fsm, Instant instant, Object unused, Endpoint srcEndpoint) throws Exception {
        if (existingSuccessor.getId().equals(selfId)) {
            newSuccessor = existingSuccessor;
            fsm.setState(DONE_STATE);
            return;
        }
        
        A successorAddress = ((ExternalPointer<A>) existingSuccessor).getAddress();
        outgoingRequestManager.sendRequestAndTrack(instant, new GetPredecessorRequest(), successorAddress);
        fsm.setState(AWAIT_PREDECESSOR_RESPONSE_STATE);
        
        endpointScheduler.scheduleMessage(TIMER_DURATION, selfEndpoint, selfEndpoint, new TimerTrigger());
    }

    @FilterHandler({AWAIT_PREDECESSOR_RESPONSE_STATE})
    public boolean filterResponses(String state, FiniteStateMachine fsm, Instant instant, Response response, Endpoint srcEndpoint)
            throws Exception {
        return outgoingRequestManager.testResponseMessage(instant, response);
    }

    @StateHandler(AWAIT_PREDECESSOR_RESPONSE_STATE)
    public void handleGetPredecessorResponse(String state, FiniteStateMachine fsm, Instant instant,
            GetPredecessorResponse<A> response, Endpoint srcEndpoint) throws Exception {
        A address = response.getAddress();
        byte[] idData = response.getId();
        
        Id potentiallyNewSuccessorId = new Id(idData, selfId.getLimitAsByteArray());
        Id existingSuccessorId = ((ExternalPointer<A>) existingSuccessor).getId();
        
        if (potentiallyNewSuccessorId.isWithin(selfId, false, existingSuccessorId, false)) {
            // set as new successor and fire-and-forget a notify msg to it
            newSuccessor = new ExternalPointer<>(potentiallyNewSuccessorId, address);
            outgoingRequestManager.sendRequestAndTrack(instant, new NotifyRequest(selfId.getValueAsByteArray()), address);
        } else {
            newSuccessor = existingSuccessor;
        }
        
        fsm.setState(DONE_STATE);
    }

    public Pointer getResult() {
        return newSuccessor;
    }

    public static final class TimerTrigger {
        private TimerTrigger() {
            // does nothing, prevents outside instantiation
        }
    }
}