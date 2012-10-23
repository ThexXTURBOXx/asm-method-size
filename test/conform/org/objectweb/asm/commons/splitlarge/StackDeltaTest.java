/***
 * ASM tests
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

import java.util.HashMap;
import java.util.TreeSet;

import org.objectweb.asm.*;

import junit.framework.TestCase;

/**
 * Unit tests for the stack-delta computation.
 *
 * @author Mike Sperber
 */
public class StackDeltaTest extends TestCase {

    protected ClassWriter cw;

    protected MethodWriter mw;

    private void startMethod() {
        this.cw = new ClassWriter(0);
        this.cw.visit(Opcodes.V1_1,
                      Opcodes.ACC_PUBLIC,
                      "C",
                      null,
                      "java/lang/Object",
                      null);
        this.mw = (MethodWriter) cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        this.mw.visitCode();
        this.mw.visitVarInsn(Opcodes.ALOAD, 0);
        this.mw.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                "java/lang/Object",
                                "<init>",
                                "()V");
        this.mw.visitInsn(Opcodes.RETURN);
        this.mw.visitMaxs(1, 1);
        this.mw.visitEnd();
        this.mw = (MethodWriter) cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null);
        this.mw.visitCode();
    }

    private void endMethod(int maxStack, int expectedPopCount, int expectedPushCount) {
        int maxLocals = 0;
        this.mw.visitMaxs(maxStack, maxLocals);
        this.mw.visitEnd();
        this.cw.visitEnd();
        ByteVector code = mw.getCode();
        ConstantPool constantPool = new ConstantPool(cw.getConstantPool(), cw.getConstantPoolSize());
        BasicBlock block = new BasicBlock(0);
        block.computeStackDelta(code, constantPool);
        assertEquals(expectedPopCount, block.stackDelta.poppedCount);
        assertEquals(expectedPushCount, block.stackDelta.pushedCount);
    }

    public void testTrivial1() {
        startMethod();
        this.mw.visitInsn(Opcodes.ICONST_0);
        endMethod(1, 0, 1);
    }

    public void testTrivial2() {
        startMethod();
        this.mw.visitInsn(Opcodes.ICONST_0);
        this.mw.visitInsn(Opcodes.POP);
        this.mw.visitInsn(Opcodes.POP);
        this.mw.visitInsn(Opcodes.ICONST_0);
        this.mw.visitInsn(Opcodes.ICONST_0);
        this.mw.visitInsn(Opcodes.ICONST_0);
        endMethod(1, 1, 3);
    }

    public void testTrivial3() {
        startMethod();
        this.mw.visitInsn(Opcodes.ICONST_0);
        this.mw.visitInsn(Opcodes.POP);
        this.mw.visitInsn(Opcodes.POP);
        this.mw.visitInsn(Opcodes.I2L);
        endMethod(1, 2, 2);
    }

}


