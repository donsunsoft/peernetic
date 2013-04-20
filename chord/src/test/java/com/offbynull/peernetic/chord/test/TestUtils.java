package com.offbynull.peernetic.chord.test;

import com.offbynull.peernetic.chord.messages.SetPredecessorResponse;
import com.offbynull.peernetic.chord.messages.StatusResponse;
import com.offbynull.peernetic.chord.messages.util.MessageUtils;
import com.offbynull.peernetic.eventframework.event.OutgoingEvent;
import com.offbynull.peernetic.eventframework.processor.FinishedProcessResult;
import com.offbynull.peernetic.eventframework.processor.ProcessResult;
import com.offbynull.peernetic.p2ptools.identification.Address;
import com.offbynull.peernetic.p2ptools.identification.BitLimitedId;
import com.offbynull.peernetic.p2ptools.identification.BitLimitedPointer;
import com.offbynull.peernetic.p2ptools.overlay.structured.chord.FingerTable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class TestUtils {
    
    private TestUtils() {
        
    }

    public static void assertOutgoingEventTypes(
            ProcessResult procRes, Class<? extends OutgoingEvent> ... classes) {
        List<OutgoingEvent> outEvents = procRes.viewOutgoingEvents();
        assertEquals(classes.length, outEvents.size());
        
        for (int i = 0; i < classes.length; i++) {
            assertEquals(classes[i], outEvents.get(i).getClass());
        }
    }

    public static <T extends OutgoingEvent> T extractProcessResultEvent(
            ProcessResult procRes, int index) {
        List<OutgoingEvent> outEvents = procRes.viewOutgoingEvents();
        return (T) outEvents.get(index);
    }

    public static <T extends Object> T extractProcessResultResult(
            ProcessResult procRes) {
        FinishedProcessResult fpr = (FinishedProcessResult) procRes;
        return (T) fpr.getResult();
    }
    
    public static BitLimitedId generateId(int bitCount, long idData) {
        byte[] bytes = ByteBuffer.allocate(8).putLong(idData).array();
        return new BitLimitedId(bitCount, bytes);
    }
    
    public static Address generateAddressFromId(BitLimitedId id) {
        byte[] data = Arrays.copyOf(id.asByteArray(), 16);
        
        Address selfAddress = new Address(data, 1);
        return selfAddress;
    }
    
    public static BitLimitedPointer generatePointer(int bitCount, long idData) {
        BitLimitedId id = generateId(bitCount, idData);
        Address address = generateAddressFromId(id);
        return new BitLimitedPointer(id, address);
    }
    
    private static long convertIdToLong(BitLimitedId id) {
        byte[] bytes = id.asByteArray();
        long value = 0;
        for (int i = 0; i < bytes.length; i++) {
            value += ((long) bytes[i] & 0xffL) << (8 * i);
        }
        return value;
    }
    
    public static SetPredecessorResponse generateSetPredecessorResponse(
            BitLimitedId id, Long predecessorUndershoot) {
        
        if (id.getBitCount() >= 64) {
            throw new IllegalArgumentException();
        }
        
        SetPredecessorResponse resp = new SetPredecessorResponse();
        
        if (predecessorUndershoot == null) {
            resp.setAssignedPredecessor(null);
        } else {
            long idData = convertIdToLong(id);
            
            int bitCount = id.getBitCount();
            long limit = 1L << bitCount;
            
            if (predecessorUndershoot >= limit) {
                throw new IllegalArgumentException();
            }
            
            long val = idData - 1L - predecessorUndershoot;
            if (val < 0L) {
                val += limit;
            }

            byte[] data = ByteBuffer.allocate(8).putLong(val).array();
            BitLimitedId predId = new BitLimitedId(bitCount, data);

            Address predAddress = generateAddressFromId(predId);
            
            BitLimitedPointer pred = new BitLimitedPointer(predId, predAddress);
            
            resp.setAssignedPredecessor(MessageUtils.createFrom(pred, false));
        }
        
        return resp;
    }
    
    public static StatusResponse generateStatusResponse(BitLimitedId id,
            Long predecessorUndershoot, Long ... entryOvershoot) {
        if (id.getBitCount() != entryOvershoot.length) {
            throw new IllegalArgumentException();
        }
        long value = convertIdToLong(id);
        
        return generateStatusResponse(value, predecessorUndershoot,
                entryOvershoot);
    }
    
    public static StatusResponse generateStatusResponse(long idData,
            Long predecessorUndershoot, Long ... entryOvershoot) {
        FingerTable ft = generateFingerTable(idData, entryOvershoot);
        
        BitLimitedPointer pred;
        
        if (predecessorUndershoot == null) {
            pred = null;
        } else {
            int bitCount = entryOvershoot.length;
            long limit = 1L << bitCount;
            
            if (predecessorUndershoot >= limit) {
                throw new IllegalArgumentException();
            }
            
            long val = idData - 1L - predecessorUndershoot;
            if (val < 0L) {
                val += limit;
            }

            byte[] data = ByteBuffer.allocate(8).putLong(val).array();
            BitLimitedId predId = new BitLimitedId(bitCount, data);

            Address predAddress = generateAddressFromId(predId);
            
            pred = new BitLimitedPointer(predId, predAddress);
        }
        
        return MessageUtils.createFrom(ft.getBaseId(), pred, ft.dump(), false);
    }
    
    public static FingerTable generateFingerTable(long idData,
            Long ... entryOvershoot) {
        byte[] bytes = ByteBuffer.allocate(8).putLong(idData).array();
        byte[][] convertEntryOvershoot = new byte[entryOvershoot.length][];
        
        for (int i = 0; i < entryOvershoot.length; i++) {
            Long val = entryOvershoot[i];
            byte[] data = val == null ? null
                    : ByteBuffer.allocate(8).putLong(val).array();
            convertEntryOvershoot[i] = data;
        }
        
        return generateFingerTable(bytes, convertEntryOvershoot);
    }
    
    public static FingerTable generateFingerTable(byte[] idData,
            byte[] ... entryOvershoot) {
        int bitCount = entryOvershoot.length;
        
        BitLimitedId selfId = new BitLimitedId(bitCount, idData);
        Address selfAddress = generateAddressFromId(selfId);
        FingerTable ft = new FingerTable(new BitLimitedPointer(selfId, selfAddress));
        
        for (int i = 0; i < entryOvershoot.length; i++) {
            if (entryOvershoot[i] != null) {
                BitLimitedId overshootAmountId = new BitLimitedId(bitCount, entryOvershoot[i]);
                BitLimitedId fingId = ft.getExpectedId(i).add(overshootAmountId);
                Address fingAddress = generateAddressFromId(fingId);
                
                // must be greater than or equal to expected id
                BitLimitedId expId = ft.getExpectedId(i);
                if (fingId.comparePosition(selfId, expId) < 0) {
                    throw new IllegalArgumentException();
                }
                
                // must be less than or equal to next expected id
                if (i < entryOvershoot.length - 1) {
                    BitLimitedId nextId = ft.getExpectedId(i + 1);
                    if (fingId.comparePosition(selfId, nextId) > 0) {
                        throw new IllegalArgumentException();
                    }
                }
                
                BitLimitedPointer fingPtr = new BitLimitedPointer(fingId, fingAddress);
                ft.put(fingPtr);
            }
        }
        
        return ft;
    }
}
