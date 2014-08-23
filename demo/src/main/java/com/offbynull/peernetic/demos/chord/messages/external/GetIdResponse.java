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
package com.offbynull.peernetic.demos.chord.messages.external;

import com.offbynull.peernetic.common.message.Response;
import java.util.Arrays;
import org.apache.commons.lang3.Validate;

public final class GetIdResponse extends Response {
    private byte[] id;

    public GetIdResponse(byte[] id) {
        this.id = Arrays.copyOf(id, id.length);
        validate();
    }

    public byte[] getId() {
        return Arrays.copyOf(id, id.length);
    }
    
    @Override
    protected void innerValidate() {
        Validate.notNull(id);
        Validate.isTrue(id.length > 0);
    }
}
