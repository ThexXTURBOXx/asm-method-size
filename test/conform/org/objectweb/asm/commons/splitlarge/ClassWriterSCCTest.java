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
import java.util.HashMap;
import java.util.TreeSet;
import java.util.SortedSet;

import junit.framework.TestCase;

/**
 * ClassWriter unit tests for the SCC computation for the flow graph.
 *
 * @author Mike Sperber
 */
public class ClassWriterSCCTest extends TestCase {

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

    private <A> void assertSet(Set<A> labels, A... p) {
        Set<A> s = new HashSet<A>();
        for (A l : p) {
            s.add(l);
        }
        assertEquals(s, labels);
    }

    private SortedSet<StrongComponent> endMethod(int maxStack, int maxLocals) {
        this.mw.visitMaxs(0, 0);
        this.mw.visitEnd();
        this.cw.visitEnd();
        ByteVector code = mw.getCode();
        TreeSet<BasicBlock> blocks = new TreeSet<BasicBlock>();
        HashMap<Label, String> labelTypes = new HashMap<Label, String>();
        ConstantPool constantPool = new ConstantPool(cw.getConstantPool(), cw.getConstantPoolSize());
        BasicBlock.computeFlowgraph(code, null, new Label[code.length + 1], 
                                    constantPool, cw.thisName,
                                    maxStack, maxLocals, 
                                    new FrameData[code.length + 1],
                                    65536,
                                    blocks,
                                    new BasicBlock[code.length + 2],
                                    new Label[code.length + 2],
                                    labelTypes);
        return BasicBlock.computeTransitiveClosures(blocks);
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
     * Method with zero real SCCs
     */
    public void testZero() {
        startMethod();
        NOP();

        SortedSet<StrongComponent> scs = endMethod(0, 0);

        assertEquals(1, scs.size());
        assertEquals(1, scs.first().members.size());
    }
    
    // for calling toArray
    private BasicBlock[] bbTag = new BasicBlock[0];
    private StrongComponent[] scTag = new StrongComponent[0];

    /**
     * Method with one SCC.
     */
    public void testOne1() {
        Label l1 = new Label();
        startMethod();
        LABEL(l1);
        GOTO(l1);

        SortedSet<StrongComponent> scs = endMethod(0, 0);

        assertEquals(1, scs.size());
        BasicBlock[] blocks = scs.first().members.toArray(bbTag);
        assertEquals(1, blocks.length);
        BasicBlock[] successors = blocks[0].successors.toArray(bbTag);
        assertEquals(1, successors.length);
        assertEquals(blocks[0], successors[0]);

        assertSet(scs.first().transitiveClosure, scs.first());
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
        LABEL(l1);
        PUSH();
        LABEL(l2);
        NOP();
        LABEL(l3);
        IFNE(l1);
        LABEL(l4);

        SortedSet<StrongComponent> scs = endMethod(1, 0);

        assertEquals(2, scs.size());

        StrongComponent[] scsa = scs.toArray(scTag);

        BasicBlock[] blocks0 = scsa[0].members.toArray(bbTag);
        BasicBlock[] blocks1 = scsa[1].members.toArray(bbTag);

        assertEquals(1, blocks0.length);
        assertEquals(1, blocks1.length);

        BasicBlock[] successors0 = blocks0[0].successors.toArray(bbTag);
        BasicBlock[] successors1 = blocks1[0].successors.toArray(bbTag);

        assertEquals(2, successors0.length);
        assertEquals(0, successors1.length);

        assertSet(scsa[0].transitiveClosure, scsa[0], scsa[1]);
        assertSet(scsa[1].transitiveClosure, scsa[1]);
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
        LABEL(l1);
        PUSH();
        LABEL(l2);
        NOP();
        LABEL(l3);
        IFNE(l2);
        NOP();
        LABEL(l4);
        PUSH();
        IFNE(l1);
        LABEL(l5);

        SortedSet<StrongComponent> scs = endMethod(1, 0);

        assertEquals(2, scs.size());

        StrongComponent[] scsa = scs.toArray(scTag);

        BasicBlock[] blocks0 = scsa[0].members.toArray(bbTag);
        BasicBlock[] blocks1 = scsa[1].members.toArray(bbTag);

        assertEquals(3, blocks0.length);
        assertEquals(1, blocks1.length);

        assertSet(scsa[0].transitiveClosure, scsa[0], scsa[1]);
        assertSet(scsa[1].transitiveClosure, scsa[1]);
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
        LABEL(l1);
        PUSH();
        LABEL(l2);
        NOP();
        LABEL(l3);
        IFNE(l1);

        LABEL(l4);
        PUSH();
        LABEL(l5);
        NOP();
        LABEL(l6);
        IFNE(l4);

        SortedSet<StrongComponent> scs = endMethod(1, 0);

        assertEquals(3, scs.size());
        
        StrongComponent[] scsa = scs.toArray(scTag);

        BasicBlock[] blocks0 = scsa[0].members.toArray(bbTag);
        BasicBlock[] blocks1 = scsa[1].members.toArray(bbTag);
        BasicBlock[] blocks2 = scsa[2].members.toArray(bbTag);

        assertEquals(1, blocks0.length);
        assertEquals(1, blocks1.length);
        assertEquals(1, blocks2.length);

        assertSet(scsa[0].transitiveClosure, scsa[0], scsa[1], scsa[2]);
        assertSet(scsa[1].transitiveClosure, scsa[1], scsa[2]);
        assertSet(scsa[2].transitiveClosure, scsa[2]);
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
        LABEL(l1);
        NOP();
        LABEL(l2);
        PUSH();
        LABEL(l3);
        IFNE(l2);
        PUSH();
        LABEL(l4);
        IFNE(l3);

        SortedSet<StrongComponent> scs = endMethod(1, 0);

        assertEquals(3, scs.size());

        StrongComponent[] scsa = scs.toArray(scTag);
        
        BasicBlock[] blocks0 = scsa[0].members.toArray(bbTag);
        BasicBlock[] blocks1 = scsa[1].members.toArray(bbTag);
        BasicBlock[] blocks2 = scsa[2].members.toArray(bbTag);

        assertEquals(1, blocks0.length);
        assertEquals(3, blocks1.length);
        assertEquals(1, blocks2.length);

        assertSet(scsa[0].transitiveClosure, scsa[0], scsa[1], scsa[2]);
        assertSet(scsa[1].transitiveClosure, scsa[1], scsa[2]);
        assertSet(scsa[2].transitiveClosure, scsa[2]);
        
    }


}
