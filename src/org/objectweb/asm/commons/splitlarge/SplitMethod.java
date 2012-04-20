/***
 * ASM: a very small and fast Java bytecode manipulation framework
 * Copyright (c) 2000-2011 INRIA, France Telecom
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holders nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.objectweb.asm.commons.splitlarge;

import org.objectweb.asm.*;

import java.util.HashSet;

class SplitMethod {

    public SplitMethod(BasicBlock entry, HashSet<Scc> components) {
        this.entry = entry;
        this.components = components;
    }

    /**
     * Entry point
     */
    BasicBlock entry;

    public Object[] frameLocal;
    public Object[] frameStack;

    /**
     * SCC components that are in the function.
     */
    HashSet<Scc> components;

    MethodWriter writer;

    /*
     * @param methodDescriptor method descriptor of host method
     * @param access access flags of host method
     */
    public String getDescriptor(final String methodDescriptor, final int access) {
        StringBuilder b = new StringBuilder();

        b.append("(");
        {
            // for non-static methods, this is the first local, and implicit
            int i = ((access & Opcodes.ACC_STATIC) != 0) ? 0 : 1;
            while (i < frameLocal.length) {
                appendFrameTypeDescriptor(b, frameLocal[i]);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameStack.length) {
                appendFrameTypeDescriptor(b, frameStack[i]);
                ++i;
            }
        }
        b.append(")");
        b.append(Type.getReturnType(methodDescriptor));
        return b.toString();
    }

    private void appendFrameTypeDescriptor(StringBuilder b, Object d) {
        if (d == Opcodes.INTEGER)
            b.append("I");
        else if (d == Opcodes.FLOAT)
            b.append("F");
        else if (d == Opcodes.LONG)
            b.append("J");
        else if (d == Opcodes.DOUBLE)
            b.append("D");
        else if (d instanceof String) {
            b.append((String) d);
            b.append(";");
        } else
            // missing are TOP, and uninitialized / Labels
            throw new RuntimeException("can't handle this frame element yet"); // ####
    }

    public void setMethodWriter(final ClassWriter cw,
                                final int access,
                                final String hostName, final int id,
                                final String hostDescriptor,
                                final String signature,
                                final int[] exceptions) {
        String descriptor = getDescriptor(hostDescriptor, access);
        int desc = cw.newUTF8(descriptor);
        writer = new MethodWriter(cw, 
                                  access,
                                  cw.newUTF8(hostName + "#split#" + id),
                                  descriptor, desc,
                                  signature,  // #### this is all provisional
                                  exceptions, 
                                  false, false, null);
                                      
    }

}
