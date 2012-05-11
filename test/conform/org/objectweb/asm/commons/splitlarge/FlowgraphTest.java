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

import java.util.Set;
import java.util.HashSet;

import junit.framework.TestCase;

/**
 * Unit tests for the computation of the flow graph.
 *
 * @author Mike Sperber
 */
public class FlowgraphTest extends TestCase {

    protected ClassWriter cw;

    protected MethodWriter mw;

    private void startMethod() {
        this.cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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

    private <A> void assertSet(Set<A> set, A... p) {
        Set<A> s = new HashSet<A>();
        for (A l : p) {
            s.add(l);
        }
        assertEquals(s, set);
    }

    private BasicBlock[] endMethod() {
        this.mw.visitMaxs(0, 0);
        this.mw.visitEnd();
        this.cw.visitEnd();
        ByteVector code = mw.getCode();
        return BasicBlock.computeFlowgraph(code.data, 0, code.length, mw.getFirstHandler()).toArray(new BasicBlock[0]);
    }

    private void LABEL(final Label l) {
        this.mw.visitLabel(l);
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

 
    /**
     * Very simple method.
     */
    public void testZero() {
        startMethod();
        NOP();
        BasicBlock[] blocks = endMethod();
        assertEquals(1, blocks.length);
    }

   /**
     * Method with one SCC.
     */
    public void testOne1() {
        Label l1 = new Label();
        startMethod();
        LABEL(l1);
        GOTO(l1);
        BasicBlock[] blocks = endMethod();
        assertEquals(1, blocks.length);
    }

    /**
     * Method with one SCC.
     */
    public void testOne2() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        startMethod();
        LABEL(l1); // 0
        PUSH();
        LABEL(l2);
        NOP();
        LABEL(l3);
        IFNE(l1);
        LABEL(l4); // 1

        BasicBlock[] blocks = endMethod();
        assertEquals(2, blocks.length);
        
        BasicBlock b0 = blocks[0];
        BasicBlock b1 = blocks[1];
        assertSet(b0.successors, b0, b1);
        assertSet(b1.successors);
    }


    /**
     * Method with one SCC.
     */
    public void testOne3() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        Label l5 = new Label();
        startMethod();
        LABEL(l1); // 0
        PUSH();
        LABEL(l2); // 1
        NOP();
        LABEL(l3);
        IFNE(l2);
        NOP(); // 2
        LABEL(l4);
        PUSH();
        IFNE(l1);
        LABEL(l5); // 3

        BasicBlock[] blocks = endMethod();
        assertEquals(4, blocks.length);
        
        BasicBlock b0 = blocks[0];
        BasicBlock b1 = blocks[1];
        BasicBlock b2 = blocks[2];
        BasicBlock b3 = blocks[3];
        assertSet(b0.successors, b1);
        assertSet(b1.successors, b1, b2);
        assertSet(b2.successors, b0, b3);
        assertSet(b3.successors);
    }

    /**
     * Method with two SCCs.
     */
    public void testTwo1() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        Label l5 = new Label();
        Label l6 = new Label();
        startMethod();
        LABEL(l1); // 0
        PUSH();
        LABEL(l2);
        NOP();
        LABEL(l3);
        IFNE(l1);

        LABEL(l4); // 1
        PUSH();
        LABEL(l5);
        NOP();
        LABEL(l6);
        IFNE(l4);
        // 2

        BasicBlock[] blocks = endMethod();
        assertEquals(3, blocks.length);
        
        BasicBlock b0 = blocks[0];
        BasicBlock b1 = blocks[1];
        BasicBlock b2 = blocks[2];
        assertSet(b0.successors, b0, b1);
        assertSet(b1.successors, b1, b2);
        assertSet(b2.successors);
    }


    /**
     * Method with two SCCs.
     */
    public void testTwo2() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        startMethod();
        LABEL(l1); // 0
        NOP();
        LABEL(l2); // 1
        PUSH();
        LABEL(l3); // 2
        IFNE(l2);
        PUSH(); // 3
        LABEL(l4);
        IFNE(l3);
        // 4

        BasicBlock[] blocks = endMethod();
        assertEquals(5, blocks.length);
        
        BasicBlock b0 = blocks[0];
        BasicBlock b1 = blocks[1];
        BasicBlock b2 = blocks[2];
        BasicBlock b3 = blocks[3];
        BasicBlock b4 = blocks[4];
        assertSet(b0.successors, b1);
        assertSet(b1.successors, b2);
        assertSet(b2.successors, b1, b3);
        assertSet(b3.successors, b2, b4);
        assertSet(b4.successors);
   }


}
