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

    /**
     * Entry point of a split method.
     */
    BasicBlock entry;

    /**
     * Name of the main method.
     */
    String mainMethodName;

    /**
     * Name of this method.
     */
    String name;
    /**
     * Uid number among the split methods of a single main method.
     */
    int id;

    MethodWriter writer;

    public SplitMethod(String mainMethodName, int id, BasicBlock entry) {
        this.mainMethodName = mainMethodName;
        this.name = mainMethodName + "#split#" + id;
        this.id = id;
        this.entry = entry;
    }

    public void setSplitMethodWriter(final ClassWriter cw,
                                     final int access,
                                     final String mainDescriptor,
                                     final String signature,
                                     final int[] exceptions) {
        String descriptor = entry.frameData.getDescriptor(mainDescriptor, access);
        int desc = cw.newUTF8(descriptor);
        writer = new MethodWriter(cw, 
                                  access,
                                  cw.newUTF8(name),
                                  descriptor, desc,
                                  signature,  // #### this is all provisional
                                  exceptions, 
                                  true, // computeMaxs
                                  false,  // computeFrames
                                  null);
    }
}
