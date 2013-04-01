package com.offbynull.peernetic.chord.processors;

import com.offbynull.peernetic.chord.Address;
import com.offbynull.peernetic.chord.Pointer;
import com.offbynull.peernetic.chord.messages.StatusRequest;
import com.offbynull.peernetic.chord.messages.StatusResponse;
import com.offbynull.peernetic.chord.messages.shared.NodePointer;
import com.offbynull.peernetic.chord.messages.util.MessageUtils;
import com.offbynull.peernetic.eventframework.impl.network.simpletcp.SendMessageProcessor;
import com.offbynull.peernetic.eventframework.impl.network.simpletcp.SendMessageProcessor.SendMessageException;
import com.offbynull.peernetic.eventframework.processor.Processor;
import com.offbynull.peernetic.eventframework.processor.ProcessorAdapter;
import com.offbynull.peernetic.eventframework.processor.ProcessorException;

public final class QueryForPredecessorProcessor extends
        ProcessorAdapter<StatusResponse, Pointer> {

    public QueryForPredecessorProcessor(Address address) {
        if (address == null) {
            throw new NullPointerException();
        }

        Processor proc = new SendMessageProcessor(address.getHost(),
                address.getPort(), new StatusRequest(), StatusResponse.class);

        setProcessor(proc);
    }

    @Override
    protected Pointer onResult(StatusResponse res) {
        // got response
        NodePointer predNp = res.getPredecessor();
        Pointer ret = MessageUtils.convertTo(predNp, false);

        return ret;
    }

    @Override
    protected Pointer onException(Exception e) throws Exception {
        if (e instanceof SendMessageException) {
            throw new QueryForPredecessorException();
        }
        
        throw e;
    }

    public static class QueryForPredecessorException
            extends ProcessorException {
    }
}