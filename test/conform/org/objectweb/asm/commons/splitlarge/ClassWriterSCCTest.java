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
        assertEquals(labels, s);
    }

    private Scc findRoot(Scc roots, Label l) {
        Scc root = roots;
        while (root != null) {
            if (isInRoot(root, l))
                return root;
            root = root.next;
        }
        fail("root not found at all");
        return null;
    }

    private boolean isInRoot(Scc root, Label l) {
        return root.blocks.contains(BasicBlock.get(l));
    }

    private void assertSCC1(final Set<Label> desired, Scc roots) {
        // the labels of this desired root must all be in the same actual root
        Scc desiredRoot = findRoot(roots, desired.iterator().next());
        for (Label l: desired)
            assertTrue(isInRoot(desiredRoot, l));
        // ... and they must be in no other root
        Scc root = roots;
        while (root != null) {
            if (root != desiredRoot) {
                for (Label l: desired)
                    assertFalse(isInRoot(root, l));
            }
            root = root.next;
        }

        // check that the sccRoot fields match up
        root = roots;
        while (root != null) {
            for (BasicBlock b : root.blocks) {
                assertSame(root, b.sccRoot);
            }
            root = root.next;
        }
    }

    private void assertSCC(final Set<Set<Label>> desired, Scc roots) {
        for (Set<Label> s: desired)
            assertSCC1(s, roots);
    }

    private Scc endMethod() {
        this.mw.visitMaxs(0, 0);
        this.mw.visitEnd();
        this.cw.visitEnd();
        return Split.initializeAll(mw.labels, mw.getCodeSize()).first().sccRoot;
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
        Label l1 = new Label();
        Label l2 = new Label();
        startMethod();
        LABEL(l1);
        NOP();
        LABEL(l2);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l2);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc root = endMethod();

        assertSCC(s, root);
        assertSet(BasicBlock.get(l1).sccRoot.successors, BasicBlock.get(l2).sccRoot);
        assertSet(BasicBlock.get(l2).sccRoot.successors);
    }

   /**
     * Method with one SCC.
     */
    public void testOne1() {
        Label l1 = new Label();
        startMethod();
        LABEL(l1);
        GOTO(l1);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        Scc root = endMethod();

        assertSCC(s, root);
        assertSet(BasicBlock.get(l1).sccRoot.successors);
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

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        s1.add(l2);
        s1.add(l3);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l4);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc root = endMethod();

        assertSCC(s, root);
        assertSet(BasicBlock.get(l1).sccRoot.successors, BasicBlock.get(l4).sccRoot);
        assertSet(BasicBlock.get(l4).sccRoot.successors);
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

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        s1.add(l2);
        s1.add(l3);
        s1.add(l4);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l5);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc root = endMethod();

        assertSCC(s, root);
        assertSet(BasicBlock.get(l1).sccRoot.successors, BasicBlock.get(l5).sccRoot);
        assertSet(BasicBlock.get(l5).sccRoot.successors);
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

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        s1.add(l2);
        s1.add(l3);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l4);
        s2.add(l5);
        s2.add(l6);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);

        Scc root = endMethod();

        assertSCC(s, root);
        assertSet(BasicBlock.get(l1).sccRoot.successors, BasicBlock.get(l4).sccRoot);
        assertSet(BasicBlock.get(l4).sccRoot.successors);
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

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l2);
        s2.add(l3);
        s2.add(l4);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc root = endMethod();

        assertSCC(s, root);
        assertSet(BasicBlock.get(l1).sccRoot.successors, BasicBlock.get(l2).sccRoot);
        assertSet(BasicBlock.get(l2).sccRoot.successors);
    }


}
