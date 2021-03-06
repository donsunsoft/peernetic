/*
 * Copyright (c) 2013-2014, Kasra Faghihi, All rights reserved.
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
package com.offbynull.peernetic.debug.visualizer;

import org.apache.commons.lang3.Validate;

/**
 * Command to add a node.
 * @author Kasra Faghihi
 * @param <A> address type
 */
public final class AddNodeCommand<A> implements Command<A> {
    private A node;

    /**
     * Constructs a {@link AddNodeCommand} object.
     * @param node node
     * @throws NullPointerException if any arguments are {@code null}
     */
    public AddNodeCommand(A node) {
        Validate.notNull(node);
        
        this.node = node;
    }

    /**
     * Get node.
     * @return node.
     */
    public A getNode() {
        return node;
    }
}
