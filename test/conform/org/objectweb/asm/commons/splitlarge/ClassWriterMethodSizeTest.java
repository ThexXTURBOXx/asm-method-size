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
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.tree.ClassNode;

import junit.framework.TestCase;

import java.io.PrintWriter;
import java.io.FileNotFoundException;

/**
 * ClassWriter unit tests for method size restriction
 *
 * @author Mike Sperber
 */
public class ClassWriterMethodSizeTest extends TestCase {

    protected ClassVisitor cv;
    protected ClassWriter cw;

    protected MethodVisitor mv;

    protected String className;

    int oldMaxCodeLength;
    int oldSparseThreshold;
    
    PrintWriter out;

    private void startMethod(String className, int access, int maxCodeLength, int sparseThreshold) {
        this.className = className;
        oldMaxCodeLength = ClassWriter.MAX_CODE_LENGTH;
        oldSparseThreshold = BasicBlock.SPARSE_FRAME_TRANSFER_THRESHOLD;
        ClassWriter.MAX_CODE_LENGTH = maxCodeLength;
        BasicBlock.SPARSE_FRAME_TRANSFER_THRESHOLD = sparseThreshold;
        /*
        try {
            this.out = new PrintWriter(className + ".dot");
        }
        catch (FileNotFoundException e) {
        }
        */
        this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES, new SplitMethodWriterDelegate(/* this.out */));
        TraceClassVisitor tcv = new TraceClassVisitor(cw, new java.io.PrintWriter(System.out));
        this.cv = tcv;
        this.cv.visit(Opcodes.V1_6,
                      Opcodes.ACC_PUBLIC,
                      className,
                      null,
                      "java/lang/Object",
                      null);
        this.mv = cv.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        this.mv.visitCode();
        this.mv.visitVarInsn(Opcodes.ALOAD, 0);
        this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                "java/lang/Object",
                                "<init>",
                                "()V");
        this.mv.visitInsn(Opcodes.RETURN);
        this.mv.visitMaxs(1, 1);
        this.mv.visitEnd();
        this.mv = cv.visitMethod(access, "m", "()V", null, null);
        this.mv.visitCode();
    }

    private void startMethod(String className, int access, int maxCodeLength) {
        startMethod(className, access, maxCodeLength, BasicBlock.SPARSE_FRAME_TRANSFER_THRESHOLD);
    }

    class MyClassLoader extends ClassLoader {
        public Class defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

    private void endMethod() {
        try {
            this.mv.visitMaxs(0, 0);
            this.mv.visitEnd();
            this.cv.visitEnd();
            /* this.out.close(); */
            byte[] b = cw.toByteArray();
            (new ClassReader(b)).accept(new CheckClassAdapter(new ClassNode(), true), ClassReader.SKIP_DEBUG);
            // make sure this code may actually work
            MyClassLoader myClassLoader = new MyClassLoader();
            myClassLoader.defineClass(className, b);
        }
        finally {
            ClassWriter.MAX_CODE_LENGTH = oldMaxCodeLength;
            BasicBlock.SPARSE_FRAME_TRANSFER_THRESHOLD = oldSparseThreshold;
        }
    }

    private void LABEL(final Label l) {
        this.mv.visitLabel(l);
    }

    private void POP() {
        this.mv.visitInsn(Opcodes.POP);
    }

    private void POP2() {
        this.mv.visitInsn(Opcodes.POP2);
    }

    private void NOP() {
        this.mv.visitInsn(Opcodes.NOP);
    }

    private void PUSH() {
        this.mv.visitInsn(Opcodes.ICONST_0);
    }

    private void LPUSH() {
        this.mv.visitInsn(Opcodes.LCONST_0);
    }
    
    private void DPUSH() {
        this.mv.visitInsn(Opcodes.DCONST_0);
    }

    private void GOTO(final Label l) {
        this.mv.visitJumpInsn(Opcodes.GOTO, l);
    }

   private void IFNE(final Label l) {
        this.mv.visitJumpInsn(Opcodes.IFNE, l);
    }

   private void L2I() {
        this.mv.visitInsn(Opcodes.L2I);
    }

    private void RETURN() {
        this.mv.visitInsn(Opcodes.RETURN);
    }

    private void ILOAD(int var) {
        this.mv.visitVarInsn(Opcodes.ILOAD, var);
    }

    private void LLOAD(int var) {
        this.mv.visitVarInsn(Opcodes.LLOAD, var);
    }

    private void DLOAD(int var) {
        this.mv.visitVarInsn(Opcodes.DLOAD, var);
    }

    private void ISTORE(int var) {
        this.mv.visitVarInsn(Opcodes.ISTORE, var);
    }

    private void LSTORE(int var) {
        this.mv.visitVarInsn(Opcodes.LSTORE, var);
    }

    private void DSTORE(int var) {
        this.mv.visitVarInsn(Opcodes.DSTORE, var);
    }

    private void TABLESWITCH(int min, int max, 
                             Label dflt, Label... labels) {
        this.mv.visitTableSwitchInsn(min, max, dflt, labels);
    }

    private void LOOKUPSWITCH(Label dflt, int[] keys, Label[] labels) {
        this.mv.visitLookupSwitchInsn(dflt, keys, labels);
    }
    
    /**
     * Method with one huge basic block of NOPs
     */
    public void testBasic() {
        startMethod("Basic", Opcodes.ACC_PUBLIC, 50);
        int i = 0;
        while (i < 70) {
            NOP();
            ++i;
        }
        RETURN();
        endMethod();
    }

    public void testBasicSparse1() {
        startMethod("BasicSparse1", Opcodes.ACC_PUBLIC, 50, 0);
        {
            int i = 1;
            while (i < 10) {
                PUSH();
                ISTORE(i);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < 70) {
                NOP();
                ++i;
            }
            ILOAD(2);
            POP();
            ILOAD(3);
            POP();
            ILOAD(5);
            POP();
        }
        RETURN();
        endMethod();
    }


    public void testBasicSparse2() {
        startMethod("BasicSparse2", Opcodes.ACC_PUBLIC, 50, 0);
        {
            int i = 0;
            while (i < 10) {
                PUSH();
                ISTORE(i * 5 + 1);
                LPUSH();
                LSTORE(i * 5 + 2);
                DPUSH();
                DSTORE(i * 5 + 4);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < 70) {
                NOP();
                ++i;
            }
            LLOAD(2);
            POP2();
            ILOAD(6);
            POP();
            LLOAD(7);
            POP2();
            DLOAD(9);
            POP2();
        }
        RETURN();
        endMethod();
    }

    
    /**
     * Method with essentially two large basic blocks.
     */
    public void testTwo1() {
        Label l1 = new Label();
        startMethod("Two1", Opcodes.ACC_PUBLIC, 100);
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        endMethod();
    }

    /**
     * Method with essentially two large basic blocks.
     */
    public void testTwo1Static() {
        Label l1 = new Label();
        startMethod("Two1Static", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, 100);
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        endMethod();
    }

    /**
     * Method with essentially two large basic blocks & constructor call.
     *
     * But we can't split at points where parts of the frame are
     * uninitialized.
     */
    public void testTwo1New() {
        Label l1 = new Label();
        startMethod("Two1New", Opcodes.ACC_PUBLIC, 100);
        this.mv.visitTypeInsn(Opcodes.NEW, "Two1");
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                    "Two1",
                                    "<init>",
                                    "()V");
            RETURN();
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            this.mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                                    "Two1",
                                    "<init>",
                                    "()V");
            RETURN();
        }
        try {
            endMethod();
        }
        catch (RuntimeException e) {
            assertEquals("no split point was found", e.getMessage());
        }
    }

    /**
     * Method with essentially two large basic blocks, with stuff in
     * the frame.
     */
    public void testTwo2() {
        Label l1 = new Label();
        startMethod("Two2", Opcodes.ACC_PUBLIC, 100);
        PUSH();
        ISTORE(1);
        PUSH();
        ISTORE(2);
        PUSH();
        ILOAD(1);
        // at this point we have this, two variables, and one operand
        IFNE(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        endMethod();
    }

    /**
     * Method with essentially two large basic blocks.
     */
    public void testTwo3() {
        Label l1 = new Label();
        startMethod("Two3", Opcodes.ACC_PUBLIC, 100);
        {
            int i = 0;
            while (i < 40) {
                NOP();
                ++i;
            }
        }
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 20) {
                NOP();
                ++i;
            }
            RETURN();
        }
        endMethod();
    }

    /**
     * Method with two basic blocks & a loop.
     */
    public void testTwo4() {
        Label l1 = new Label();
        startMethod("Two4", Opcodes.ACC_PUBLIC, 100);
        {
            int i = 0;
            while (i < 40) {
                NOP();
                ++i;
            }
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        PUSH();
        IFNE(l1);
        RETURN();
        endMethod();
    }

    /**
     * Method with essentially two large basic blocks, with stuff in
     * the frame.
     */
    public void testTwo5() {
        Label l1 = new Label();
        startMethod("Two5", Opcodes.ACC_PUBLIC, 100);
        LPUSH();
        LSTORE(1);
        LPUSH();
        LSTORE(3);
        LPUSH();
        LLOAD(1);
        // at this point we have this, two variables, and one operand
        L2I();
        IFNE(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        LABEL(l1);
        {
            int i = 0;
            while (i < 60) {
                NOP();
                ++i;
            }
            RETURN();
        }
        endMethod();
    }

    /**
     * Method with three basic blocks
     */
    public void testThree1() {
        Label l1 = new Label();
        startMethod("Three1", Opcodes.ACC_PUBLIC, 100);
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        RETURN();

        LABEL(l1);

        Label l2 = new Label();
        PUSH();
        IFNE(l2);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        RETURN();

        LABEL(l2);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        RETURN();

        endMethod();
    }

    /**
     * Method with three basic blocks, with large branch.
     */
    public void testThree2() {
        Label l1 = new Label();
        startMethod("Three2", Opcodes.ACC_PUBLIC, 65536);
        PUSH();
        IFNE(l1);
        {
            int i = 0;
            while (i < 50000) {
                NOP();
                ++i;
            }
        }
        RETURN();

        Label l2 = new Label();
        LABEL(l2);
        {
            int i = 0;
            while (i < 50000) {
                NOP();
                ++i;
            }
        }
        RETURN();

        LABEL(l1);

        PUSH();
        IFNE(l2);
        {
            int i = 0;
            while (i < 50000) {
                NOP();
                ++i;
            }
        }
        RETURN();


        endMethod();
    }

    /**
     * Method with three basic blocks
     */
    public void testThree1LocalVariables() {
        startMethod("Three1LocalVariables", Opcodes.ACC_PUBLIC, 100);
        PUSH();
        ISTORE(1);
        PUSH();
        ISTORE(2);
        PUSH();
        Label l1 = new Label();
        IFNE(l1);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        RETURN();

        LABEL(l1);

        Label l2 = new Label();
        PUSH();
        IFNE(l2);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        RETURN();

        LABEL(l2);
        {
            int i = 0;
            while (i < 80) {
                NOP();
                ++i;
            }
        }
        RETURN();

        Label l3 = new Label();
        LABEL(l3);

        this.mv.visitLocalVariable("one", "I", null, l1, l3, 1);
        this.mv.visitLocalVariable("two", "I", null, l1, l3, 2);
        endMethod();
    }


    /**
     * Method split at a tableswitch.
     */
    public void testTableSwitch1() {
        startMethod("tableSwitch1", Opcodes.ACC_PUBLIC, 100);
        PUSH();
        {
            int i = 0;
            while (i < 50) {
                NOP();
                ++i;
            }
        }
        Label dflt = new Label();
        Label l0 = new Label();
        Label l1 = new Label();
        TABLESWITCH(0, 1, dflt, l0, l1);
        LABEL(dflt);
        {
            int i = 0;
            while (i < 70) {
                NOP();
                ++i;
            }
        }
        RETURN();
        LABEL(l0);
        RETURN();
        LABEL(l1);
        RETURN();
        endMethod();
    }

    /**
     * Method split at a lookupswitch.
     */
    public void testLookupSwitch1() {
        startMethod("LookupSwitch1", Opcodes.ACC_PUBLIC, 100);
        PUSH();
        {
            int i = 0;
            while (i < 50) {
                NOP();
                ++i;
            }
        }
        Label dflt = new Label();
        Label l0 = new Label();
        Label l1 = new Label();
        int[] keys = { 0, 1 };
        Label[] labels = new Label[2];
        labels[0] = l0;
        labels[1] = l1;
        LOOKUPSWITCH(dflt, keys, labels);
        LABEL(dflt);
        {
            int i = 0;
            while (i < 70) {
                NOP();
                ++i;
            }
        }
        RETURN();
        LABEL(l0);
        RETURN();
        LABEL(l1);
        RETURN();
        endMethod();
    }

}
