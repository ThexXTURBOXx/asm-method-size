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

class SplitMethod {

    /**
     * Entry point of a split method.
     */
    BasicBlock entry;

    /**
     * Name of the main method.
     */
    String mainMethodName;

    int access;

    /**
     * Name of this method.
     */
    String name;
    /**
     * Uid number among the split methods of a single main method.
     */
    int id;

    String descriptor;
    int descriptorIndex;

    MethodVisitor writer;

    public SplitMethod(String mainMethodName, int access, int id, BasicBlock entry) {
        this.mainMethodName = mainMethodName;
        this.access = access;
        this.name = mainMethodName + "#split#" + id;
        this.id = id;
        this.entry = entry;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public int getDescriptorIndex() {
        return descriptorIndex;
    }

    public void setSplitMethodWriter(final ClassWriter cw,
                                     final ClassVisitor cv,
                                     final String mainDescriptor,
                                     final String signature,
                                     final String[] exceptions) {
        descriptor = entry.frameData.getDescriptor(mainDescriptor);
        SplitMethodWriterFactory smwf = (SplitMethodWriterFactory) cw.getMethodWriterFactory();
        smwf.computeMaxsOverride = true;
        smwf.computeFramesOverride = false;
        smwf.split = false;
        writer = cv.visitMethod(access,
                                name,
                                descriptor,
                                signature,  // #### this is all provisional
                                exceptions);
        smwf.setDefaults();
    }

    public void visitJumpTo(ClassWriter cw, MethodVisitor mv) {
        entry.frameData.visitPushFrameArguments(cw, mv); // FIXME: intermediate method
        mv.visitMethodInsn(((access & Opcodes.ACC_STATIC) != 0) ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL,
                           cw.thisName,
                           name,
                           descriptor);
        mv.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN));
    }

    
            
}
