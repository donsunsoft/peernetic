package com.offbynull.peernetic.common.transmission;

import com.offbynull.peernetic.actor.Endpoint;
import com.offbynull.peernetic.actor.EndpointDirectory;
import com.offbynull.peernetic.common.Processable;
import com.offbynull.peernetic.common.message.Nonce;
import com.offbynull.peernetic.common.message.NonceAccessor;
import com.offbynull.peernetic.common.message.NonceGenerator;
import com.offbynull.peernetic.common.message.Validator;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.Validate;

public final class OutgoingRequestManager<A, N> implements Processable {

    private static final Duration DEFAULT_RESEND_DURATION = Duration.ofSeconds(5L);
    private static final int DEFAULT_MAXIMUM_RESEND_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETAIN_DURATION = Duration.ofSeconds(30L);
    
    private final Endpoint selfEndpoint;
    private final NonceGenerator<N> nonceGenerator;
    private final NonceAccessor<N> nonceAccessor;
    private final EndpointDirectory<A> endpointDirectory;

    private final PriorityQueue<Event> queue;
    private final Map<Nonce<N>, Request> requests;
    
    private final Duration defaultResendDuration;
    private final int defaultMaxResendCount;
    private final Duration defaultRetainDuration;
    
    private final Set<Nonce<N>> removedNonces;


    public OutgoingRequestManager(Endpoint selfEndpoint, NonceGenerator<N> nonceGenerator, NonceAccessor<N> nonceAccessor,
            EndpointDirectory<A> endpointDirectory) {
        this(selfEndpoint, nonceGenerator, nonceAccessor, endpointDirectory, DEFAULT_RESEND_DURATION, DEFAULT_MAXIMUM_RESEND_ATTEMPTS,
                DEFAULT_RETAIN_DURATION);
    }
    
    public OutgoingRequestManager(Endpoint selfEndpoint, NonceGenerator<N> nonceGenerator, NonceAccessor<N> nonceAccessor,
            EndpointDirectory<A> endpointDirectory, Duration defaultResendDuration, int defaultMaxResendCount,
            Duration defaultRetainDuration) {
        Validate.notNull(selfEndpoint);
        Validate.notNull(nonceAccessor);
        Validate.notNull(nonceGenerator);
        Validate.notNull(endpointDirectory);
        Validate.notNull(defaultResendDuration);
        Validate.notNull(defaultRetainDuration);
        
        Validate.isTrue(!defaultResendDuration.isNegative() && !defaultRetainDuration.isNegative() && defaultMaxResendCount >= 0);
        Validate.isTrue(defaultResendDuration.multipliedBy(defaultMaxResendCount).compareTo(defaultRetainDuration) <= 0);
        
        this.selfEndpoint = selfEndpoint;
        this.nonceAccessor = nonceAccessor;
        this.nonceGenerator = nonceGenerator;
        this.endpointDirectory = endpointDirectory;

        this.queue = new PriorityQueue<>(new EventTriggerTimeComparator());
        this.requests = new HashMap<>();
        
        this.defaultResendDuration = defaultResendDuration;
        this.defaultMaxResendCount = defaultMaxResendCount;
        this.defaultRetainDuration = defaultRetainDuration;
        
        this.removedNonces = new HashSet<>();
    }

    public Nonce<N> sendRequestAndTrack(Instant time, Object request, A dstAddress) {
        return sendRequestAndTrack(time, request, dstAddress, defaultResendDuration, defaultMaxResendCount, defaultRetainDuration);
    }
    
    public Nonce<N> sendRequestAndTrack(Instant time, Object request, A dstAddress, Duration resendDuration, int maxResendCount,
            Duration retainDuration) {
        Validate.notNull(time);
        Validate.notNull(request);
        Validate.notNull(dstAddress);
        Validate.notNull(resendDuration);
        Validate.notNull(retainDuration);
        //Validate.notNull(responseHandler); // can be null

        Validate.isTrue(!resendDuration.isNegative() && !retainDuration.isNegative() && maxResendCount >= 0);
        Validate.isTrue(resendDuration.multipliedBy(maxResendCount).compareTo(retainDuration) <= 0);

        // generate nonce and validate
        Nonce<N> nonce;
        int attempt = 0;
        do {
            Validate.isTrue(attempt < 10, "Unable to generate unique nonce -- likely due to poor nonce generation algorithm");
            attempt++;

            // generate and set a nonce
            nonce = nonceGenerator.generate();
            nonceAccessor.set(request, nonce);
        } while (requests.containsKey(nonce));

        // validate before adding to queue and sending
        Validate.isTrue(Validator.validate(request));

        // sendRequestAndTrack and queue resends/discards
        Endpoint dstEndpoint = endpointDirectory.lookup(dstAddress);
        OutgoingRequestManager.Event[] newEvents = new OutgoingRequestManager.Event[maxResendCount + 1];
        for (int i = 0; i < maxResendCount; i++) {
            SendEvent event = new SendEvent(nonce, time.plus(resendDuration.multipliedBy(i + 1)));
            queue.add(event);
            newEvents[i] = event;
        }
        DiscardEvent discardEvent = new DiscardEvent(nonce, time.plus(retainDuration));
        queue.add(discardEvent);
        newEvents[maxResendCount] = discardEvent;
        
        requests.put(nonce, new Request(dstEndpoint, request, newEvents));
        
        dstEndpoint.send(selfEndpoint, request);
        
        return nonce;
    }

    @Override
    public Duration process(Instant time) {
        Validate.notNull(time);
        
        removedNonces.clear();
        
        while (!queue.isEmpty()) {
            Event event = queue.peek();

            if (event.isCancelled()) {
                queue.poll(); // if cancelled, remove and continue to next item in queue
                continue;
            }

            Instant hitTime = event.getTriggerTime();
            if (hitTime.isAfter(time)) {
                break;
            }

            if (event instanceof OutgoingRequestManager.DiscardEvent) {
                DiscardEvent discardEvent = (DiscardEvent) event;
                Nonce<N> nonce = discardEvent.getRequestNonce();
                requests.remove(nonce);
                removedNonces.add(nonce);
            } else if (event instanceof OutgoingRequestManager.SendEvent) {
                SendEvent sendEvent = (SendEvent) event;
                Nonce<N> nonce = sendEvent.getRequestNonce();
                Request request = requests.get(nonce);
                
                request.getDestination().send(selfEndpoint, request.getRequest()); // request should never be null
            } else {
                throw new IllegalStateException();
            }

            queue.poll(); // remove
        }
        
        return queue.isEmpty() ? null : Duration.between(time, queue.peek().getTriggerTime());
    }
    
    // check to see if the nonce in the message is currently being tracked by this manager, can be request or response
    public boolean isMessageTracked(Instant time, Object request) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        Validate.notNull(time);
        Validate.notNull(request);

        // validate response
        if (!Validator.validate(request)) {
            return false; // message failed to validate, treat it as if it isn't tracked
        }
        
        // get nonce from response
        Nonce<N> nonce = nonceAccessor.get(request);
        return requests.containsKey(nonce);
    }
    
    public boolean testResponseMessage(Instant time, Object response) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        Validate.notNull(time);
        Validate.notNull(response);

        // validate response
        if (!Validator.validate(response)) {
            return false; // message failed to validate
        }
        
        // get nonce from response
        Nonce<N> nonce = nonceAccessor.get(response);
        
        // check to see if we're expecting a response, if we are remove record + queued events and return true ...
        //   since the record is being removed, any subsequent response with same nonce will be dropped
        Request request;
        if ((request = requests.remove(nonce)) == null) {
            return false; // tell to ignore if nonce not being tracked
        }
        request.cancelQueuedEvents();

        return true; // tell to handle
    }

    public int getPending() {
        return requests.size();
    }

    public Duration getDefaultResendDuration() {
        return defaultResendDuration;
    }

    public int getDefaultMaxResendCount() {
        return defaultMaxResendCount;
    }

    public Duration getDefaultRetainDuration() {
        return defaultRetainDuration;
    }

    private static final class Request {

        private Endpoint destination;
        private Object request;
        private UnmodifiableList<OutgoingRequestManager.Event> queuedEvents;

        public Request(Endpoint destination, Object request, OutgoingRequestManager.Event ... queuedEvents) {
            this.destination = destination;
            this.request = request;
            this.queuedEvents = (UnmodifiableList<OutgoingRequestManager.Event>)
                    UnmodifiableList.unmodifiableList(new ArrayList(Arrays.asList(queuedEvents)));
        }

        public Endpoint getDestination() {
            return destination;
        }

        public Object getRequest() {
            return request;
        }

        public UnmodifiableList<OutgoingRequestManager.Event> getQueuedEvents() {
            return queuedEvents;
        }

        public void cancelQueuedEvents() {
            queuedEvents.forEach(x -> x.cancel());
        }
    }

    private abstract class Event {

        private final Instant triggerTime;
        private boolean cancel;

        public Event(Instant triggerTime) {
            this.triggerTime = triggerTime;
        }

        public Instant getTriggerTime() {
            return triggerTime;
        }

        public boolean isCancelled() {
            return cancel;
        }

        public void cancel() {
            this.cancel = true;
        }

    }

    private final class EventTriggerTimeComparator implements Comparator<Event> {

        @Override
        public int compare(Event o1, Event o2) {
            return o1.getTriggerTime().compareTo(o2.getTriggerTime());
        }

    }

    private final class SendEvent extends Event {

        private final Nonce<N> requestNonce;

        public SendEvent(Nonce<N> requestNonce, Instant triggerTime) {
            super(triggerTime);
            this.requestNonce = requestNonce;
        }

        public Nonce<N> getRequestNonce() {
            return requestNonce;
        }
    }

    private final class DiscardEvent extends Event {

        private final Nonce<N> requestNonce;

        public DiscardEvent(Nonce<N> requestNonce, Instant triggerTime) {
            super(triggerTime);
            this.requestNonce = requestNonce;
        }

        public Nonce<N> getRequestNonce() {
            return requestNonce;
        }
    }
}
