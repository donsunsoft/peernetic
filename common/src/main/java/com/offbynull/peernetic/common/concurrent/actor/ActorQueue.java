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
package com.offbynull.peernetic.common.concurrent.actor;

import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.Validate;

/**
 * Message pump.
 * @author Kasra Faghihi
 */
public final class ActorQueue {
    private LinkedList<Iterator<Outgoing>> internalQueue;
    private Lock internalQueueLock;
    private ActorQueueReader reader;
    private ActorQueueWriter writer;

    /**
     * Constructs an {@link ActorQueue} object.
     */
    public ActorQueue() {
        this.internalQueue = new LinkedList<>();
        this.internalQueueLock = new ReentrantLock();
        Condition internalQueueLockReadyCondition = internalQueueLock.newCondition();
        this.reader = new ActorQueueReader(internalQueue, internalQueueLock, internalQueueLockReadyCondition);
        this.writer = new ActorQueueWriter(internalQueue, internalQueueLock, internalQueueLockReadyCondition);
    }

    /**
     * Constructs an {@link ActorQueue} object with a third-party notification mechanism for handling when the reader wakes up. Useful for
     * when you want to be notified if some other event happened in addition to incoming events (e.g. {@link Selector} events).
     * @param notifier third-party notification mechanism
     * @throws NullPointerException if any arguments are {@code null}
     */
    public ActorQueue(ActorQueueNotifier notifier) {
        Validate.notNull(notifier);
        this.internalQueue = new LinkedList<>();
        this.internalQueueLock = new ReentrantLock();
        this.reader = new ActorQueueReader(internalQueue, internalQueueLock, notifier);
        this.writer = new ActorQueueWriter(internalQueue, internalQueueLock, notifier);
    }
    
    /**
     * Get the reader. Reads messages from the queue.
     * @return reader
     */
    public ActorQueueReader getReader() {
        return reader;
    }
    
    /**
     * Get the writer. Writes messages to the queue.
     * @return writer
     */
    public ActorQueueWriter getWriter() {
        return writer;
    }

    void close() {
        internalQueueLock.lock();
        try {
            reader.close();
            writer.close();
        } finally {
            internalQueueLock.unlock();
        }
    }
}
