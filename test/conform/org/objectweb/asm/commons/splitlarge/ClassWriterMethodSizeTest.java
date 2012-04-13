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

import org.objectweb.asm.*;

import junit.framework.TestCase;

/**
 * ClassWriter unit tests for method size restriction
 *
 * @author Mike Sperber
 */
public class ClassWriterMethodSizeTest extends TestCase {

    protected ClassWriter cw;

    protected MethodVisitor mv;

    private void startMethod() {
        this.cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        this.cw.visit(Opcodes.V1_1,
                      Opcodes.ACC_PUBLIC,
                      "C",
                      null,
                      "java/lang/Object",
                      null);
        this.mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        this.mv.visitCode();
        this.mv.visitVarInsn(Opcodes.ALOAD, 0);
        this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                "java/lang/Object",
                                "<init>",
                                "()V");
        this.mv.visitInsn(Opcodes.RETURN);
        this.mv.visitMaxs(1, 1);
        this.mv.visitEnd();
        this.mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "m", "()V", null, null);
        this.mv.visitCode();
    }

    private void endMethod() {
        this.mv.visitMaxs(0, 0);
        this.mv.visitEnd();
        this.cw.visitEnd();
        byte[] b = cw.toByteArray();
    }

    private void LABEL(final Label l) {
        this.mv.visitLabel(l);
    }
    
    private void NOP() {
        this.mv.visitInsn(Opcodes.NOP);
    }

    private void PUSH() {
        this.mv.visitInsn(Opcodes.ICONST_0);
    }

    private void ICONST_0() {
        this.mv.visitInsn(Opcodes.ICONST_0);
    }

    private void GOTO(final Label l) {
        this.mv.visitJumpInsn(Opcodes.GOTO, l);
    }

    private void IFNE(final Label l) {
        this.mv.visitJumpInsn(Opcodes.IFNE, l);
    }

    /**
     * Method with one huge basic block of NOPs
     */
    public void testBasic() {
        startMethod();
        int i = 0;
        while (i < 100000) {
            NOP();
            ++i;
        }
        endMethod();
    }

    /**
     * Method with essentially two large basic blocks.
     */
    public void testTwo() {
        Label l1 = new Label();
        Label l2 = new Label();
        startMethod();
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 40000) {
                NOP();
                ++i;
            }
        }
        GOTO(l2);
        LABEL(l1);
        {
            int i = 0;
            while (i < 40000) {
                NOP();
                ++i;
            }
        }
        LABEL(l2);
        endMethod();
    }

}