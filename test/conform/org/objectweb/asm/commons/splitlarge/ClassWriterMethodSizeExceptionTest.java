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
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * ClassWriter unit tests for method size restriction, involving exceptions
 *
 * @author Mike Sperber
 */
public class ClassWriterMethodSizeExceptionTest extends TestCase {

    ClassWriter cw;
    ClassVisitor cv;

    private MethodVisitor startMethod(String className) {
        ClassWriter.MAX_CODE_LENGTH = 100;

        MethodWriterFactory cwf = new SplitMethodWriterFactory();
        this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES, cwf);

        TraceClassVisitor tcv = new TraceClassVisitor(cw, new java.io.PrintWriter(System.out));
        this.cv = tcv;

        cv.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        {
            MethodVisitor mv = cv.visitMethod(0, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "m", "()I", null, null);
            mv.visitCode();
            return mv;
        }
    }

    private void endMethod() {
        cv.visitEnd();
        CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, new java.io.PrintWriter(System.out));
    }


    public void testSimple() {
        MethodVisitor mv = startMethod("C1");

        {
            int i = 0;
            while (i < 80) {
                mv.visitInsn(Opcodes.NOP);
                ++i;
            }
        }

        Label l0 = new Label();
        Label l1 = new Label();
        Label l2 = new Label();
        mv.visitTryCatchBlock(l0, l1, l2, "java/lang/RuntimeException");
        Label l3 = new Label();
        Label l4 = new Label();
        mv.visitTryCatchBlock(l0, l3, l4, null);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitVarInsn(Opcodes.ISTORE, 1);
        mv.visitLabel(l0);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IDIV);
        mv.visitVarInsn(Opcodes.ISTORE, 1);
        mv.visitLabel(l1);
        Label l6 = new Label();
        mv.visitJumpInsn(Opcodes.GOTO, l6);
        mv.visitLabel(l2);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitVarInsn(Opcodes.ISTORE, 1);
        mv.visitLabel(l3);
        mv.visitIincInsn(1, 1);
        Label l8 = new Label();
        mv.visitJumpInsn(Opcodes.GOTO, l8);
        mv.visitLabel(l4);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitIincInsn(1, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(l6);
        mv.visitIincInsn(1, 1);
        mv.visitLabel(l8);
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(2, 4);
        mv.visitEnd();


        endMethod();
    }


}
