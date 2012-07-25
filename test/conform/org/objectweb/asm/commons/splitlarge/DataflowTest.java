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

import java.util.BitSet;
import java.util.HashMap;
import java.util.TreeSet;

import org.objectweb.asm.*;

import junit.framework.TestCase;

/**
 * Unit tests for the data-flow analyses in the splitting code.
 *
 * @author Mike Sperber
 */
public class DataflowTest extends TestCase {

    protected ClassWriter cw;

    protected MethodWriter mw;

    private void startMethod() {
        this.cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
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

    private static void assertBitSet(BitSet set, int... p) {
        BitSet s = new BitSet();
        for (int i : p) {
            s.set(i);
        }
        assertEquals(s, set);
    }

    private BasicBlock[] endMethod(int maxStack, int maxLocals) {
        this.mw.visitMaxs(0, 0);
        this.mw.visitEnd();
        this.cw.visitEnd();
        ByteVector code = mw.getCode();
        TreeSet<BasicBlock> blocks = new TreeSet<BasicBlock>();
        HashMap<Label, String> labelTypes = new HashMap<Label, String>();
        ConstantPool constantPool = new ConstantPool(cw.getConstantPool(), cw.getConstantPoolSize());
        BasicBlock.computeFlowgraph(code, mw.getFirstHandler(), new Label[code.length],
                                    constantPool, cw.thisName,
                                    maxStack, maxLocals,
                                    new FrameData[code.length + 1],
                                    65536,
                                    blocks,
                                    new BasicBlock[code.length + 1],
                                    new Label[code.length + 2],
                                    labelTypes);
        BasicBlock.computeLocalsReadWrittens(code, blocks);
        return blocks.toArray(new BasicBlock[0]);
    }

    private void LABEL(final Label l) {
        this.mw.visitLabel(l);
    }

    private void ILOAD(int index) {
        this.mw.visitVarInsn(Opcodes.ILOAD, index);
    }

    private void ISTORE(int index) {
        this.mw.visitVarInsn(Opcodes.ISTORE, index);
    }
    
    private void NOP() {
        this.mw.visitInsn(Opcodes.NOP);
    }

    private void PUSH() {
        this.mw.visitInsn(Opcodes.ICONST_0);
    }

    private void GOTO(final Label l) {
        this.mw.visitJumpInsn(Opcodes.GOTO, l);
    }

    private void IFNE(final Label l) {
        this.mw.visitJumpInsn(Opcodes.IFNE, l);
    }

 
    public void test1() {
        Label l1 = new Label();
        startMethod();
        PUSH(); // 0
        ISTORE(0);
        PUSH();
        ISTORE(1);
        LABEL(l1); // 1
        ILOAD(0);
        IFNE(l1);

        BasicBlock[] blocks = endMethod(1, 2);
        assertEquals(3, blocks.length);
        
        BasicBlock b0 = blocks[0];
        BasicBlock b1 = blocks[1];
        assertBitSet(b0.localsRead);
        assertBitSet(b0.localsReadTransitive);
        assertBitSet(b1.localsRead, 0);
        assertBitSet(b1.localsReadTransitive, 0);
    }

    public void test2() {
        Label l1 = new Label();
        startMethod();
        PUSH(); // 0
        ISTORE(0);
        PUSH();
        ISTORE(1);
        PUSH();
        ISTORE(2);
        PUSH();
        ISTORE(3);
        LABEL(l1); // 1
        PUSH();
        ISTORE(1);
        ILOAD(0);
        IFNE(l1);
        ILOAD(1); // 2
        ISTORE(2);
        ILOAD(2);
        ISTORE(0);
        ILOAD(3);

        BasicBlock[] blocks = endMethod(1, 4);
        assertEquals(3, blocks.length);
        
        BasicBlock b0 = blocks[0];
        BasicBlock b1 = blocks[1];
        BasicBlock b2 = blocks[2];
        assertBitSet(b0.localsRead);
        assertBitSet(b0.localsReadTransitive);
        assertBitSet(b1.localsRead, 0);
        assertBitSet(b1.localsReadTransitive, 0, 3);
        assertBitSet(b2.localsRead, 1, 3);
        assertBitSet(b2.localsReadTransitive, 1, 3);
    }




}