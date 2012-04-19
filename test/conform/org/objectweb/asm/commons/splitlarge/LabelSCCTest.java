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
import java.util.TreeSet;

import junit.framework.TestCase;

/**
 * Unit tests for the SCC computation for the flow graph.
 *
 * @author Mike Sperber
 */
public class LabelSCCTest extends TestCase {

    private <A> void assertSet(Set<A> labels, A... p) {
        Set<A> s = new HashSet<A>();
        for (A l : p) {
            s.add(l);
        }
        assertEquals(labels, s);
    }

    private Scc findRoot(Scc roots, BasicBlock b) {
        Scc root = roots;
        while (root != null) {
            if (isInRoot(root, b))
                return root;
            root = root.next;
        }
        fail("root not found at all");
        return null;
    }

    private boolean isInRoot(Scc root, BasicBlock b) {
        return root.blocks.contains(b);
    }

    private void assertSCC1(final Set<BasicBlock> desired, Scc roots) {
        // the labels of this desired root must all be in the same actual root
        Scc desiredRoot = findRoot(roots, desired.iterator().next());
        for (BasicBlock b: desired)
            assertTrue(isInRoot(desiredRoot, b));
        // ... and they must be in no other root
        Scc root = roots;
        while (root != null) {
            if (root != desiredRoot) {
                for (BasicBlock b: desired)
                    assertFalse(isInRoot(root, b));
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

    private void assertSCC(final Set<Set<BasicBlock>> desired, Scc roots) {
        for (Set<BasicBlock> s: desired)
            assertSCC1(s, roots);
    }

    private Scc initialize(Label labels, int size) {
        return Split.initializeAll(labels, size).first().sccRoot;
    }

    private static Edge makeEdge(Label... label) {
        int i = label.length  - 1;
        Edge e = null;
        while (i >= 0) {
            Edge o = e;
            e = new Edge();
            e.next = o;
            e.successor = label[i];
            --i;
        }
        return e;
    }

    public Label makeLabel(int position) {
        Label l = new Label();
        l.position = position;
        return l;
    }

    
    /**
     * Method with zero real SCCs
     */
    public void testZero() {
        Label l1 = makeLabel(0);
        Label l2 = makeLabel(2);

        l1.successor = l2;

        l1.successors = makeEdge(l2);

        Scc scc = initialize(l1, 10);

        BasicBlock b1 = BasicBlock.get(l1);
        BasicBlock b2 = BasicBlock.get(l2);

        Set<BasicBlock> s1 = new HashSet<BasicBlock>();
        s1.add(b1);
        Set<BasicBlock> s2 = new HashSet<BasicBlock>();
        s2.add(b2);
        Set<Set<BasicBlock>> s = new HashSet<Set<BasicBlock>>();
        s.add(s1);
        s.add(s2);

        assertSCC(s, scc);
        assertSet(b1.sccRoot.successors, b2.sccRoot);
        assertSet(b2.sccRoot.successors);
        assertNull(b1.sccRoot.splitPoint());
        assertSame(b2, b2.sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(8);
        assertNotNull(m);
        assertSame(b2, m.entry);
    }

   /**
     * Method with one SCC.
     */
    public void testOne1() {
        Label l1 = makeLabel(0);

        l1.successors = makeEdge(l1);

        Scc scc = initialize(l1, 10);

        BasicBlock b1 = BasicBlock.get(l1);

        Set<BasicBlock> s1 = new HashSet<BasicBlock>();
        s1.add(b1);
        Set<Set<BasicBlock>> s = new HashSet<Set<BasicBlock>>();
        s.add(s1);

        assertSCC(s, scc);
        assertSet(b1.sccRoot.successors);
        assertNull(b1.sccRoot.splitPoint());

        assertNull(scc.findSplitPoint(8));
    }

    /**
     * Method with one SCC.
     */
    public void testOne2() {
        Label l1 = makeLabel(0);
        Label l2 = makeLabel(5);
        Label l3 = makeLabel(10);
        Label l4 = makeLabel(15);
        
        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l1, l4);

        Scc scc = initialize(l1, 20);

        BasicBlock b1 = BasicBlock.get(l1);
        BasicBlock b2 = BasicBlock.get(l2);
        BasicBlock b3 = BasicBlock.get(l3);
        BasicBlock b4 = BasicBlock.get(l4);

        Set<BasicBlock> s1 = new HashSet<BasicBlock>();
        s1.add(b1);
        s1.add(b2);
        s1.add(b3);
        Set<BasicBlock> s2 = new HashSet<BasicBlock>();
        s2.add(b4);
        Set<Set<BasicBlock>> s = new HashSet<Set<BasicBlock>>();
        s.add(s1);
        s.add(s2);

        assertSCC(s, scc);

        assertSet(b1.sccRoot.successors, b4.sccRoot);
        assertSet(b4.sccRoot.successors);

        assertNull(b1.sccRoot.splitPoint());
        assertSame(b4, b4.sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(8);
        assertNotNull(m);
        assertSame(b4, m.entry);
    }

    /**
     * Method with one SCC.
     */
    public void testOne3() {
        Label l1 = makeLabel(0);
        Label l2 = makeLabel(5);
        Label l3 = makeLabel(10);
        Label l4 = makeLabel(15);
        Label l5 = makeLabel(20);

        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;
        l4.successor = l5;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l2, l4);
        l4.successors = makeEdge(l1, l5);
        l5.successors = makeEdge();

        Scc scc = initialize(l1, 25);

        BasicBlock b1 = BasicBlock.get(l1);
        BasicBlock b2 = BasicBlock.get(l2);
        BasicBlock b3 = BasicBlock.get(l3);
        BasicBlock b4 = BasicBlock.get(l4);
        BasicBlock b5 = BasicBlock.get(l5);

        Set<BasicBlock> s1 = new HashSet<BasicBlock>();
        s1.add(b1);
        s1.add(b2);
        s1.add(b3);
        s1.add(b4);
        Set<BasicBlock> s2 = new HashSet<BasicBlock>();
        s2.add(b5);
        Set<Set<BasicBlock>> s = new HashSet<Set<BasicBlock>>();
        s.add(s1);
        s.add(s2);

        assertSCC(s, scc);

        assertSet(b1.sccRoot.successors, b5.sccRoot);
        assertSet(b5.sccRoot.successors);

        assertNull(b1.sccRoot.splitPoint());
        assertSame(b5, b5.sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(8);
        assertNotNull(m);
        assertSame(b5, m.entry);
    }
    
    /**
     * Method with two SCCs.
     */
    public void testTwo1() {
        Label l1 = makeLabel(0);
        Label l2 = makeLabel(5);
        Label l3 = makeLabel(10);
        Label l4 = makeLabel(15);
        Label l5 = makeLabel(20);
        Label l6 = makeLabel(25);

        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;
        l4.successor = l5;
        l5.successor = l6;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l1, l4);
        l4.successors = makeEdge(l5);
        l5.successors = makeEdge(l6);
        l6.successors = makeEdge(l4);

        Scc scc = initialize(l1, 30);

        BasicBlock b1 = BasicBlock.get(l1);
        BasicBlock b2 = BasicBlock.get(l2);
        BasicBlock b3 = BasicBlock.get(l3);
        BasicBlock b4 = BasicBlock.get(l4);
        BasicBlock b5 = BasicBlock.get(l5);
        BasicBlock b6 = BasicBlock.get(l6);

        Set<BasicBlock> s1 = new HashSet<BasicBlock>();
        s1.add(b1);
        s1.add(b2);
        s1.add(b3);
        Set<BasicBlock> s2 = new HashSet<BasicBlock>();
        s2.add(b4);
        s2.add(b5);
        s2.add(b6);
        Set<Set<BasicBlock>> s = new HashSet<Set<BasicBlock>>();
        s.add(s1);
        s.add(s2);

        assertSCC(s, scc);

        assertSet(b1.sccRoot.successors, b4.sccRoot);
        assertSet(b4.sccRoot.successors);

        assertNull(b1.sccRoot.splitPoint());
        assertSame(b4, b4.sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(20);
        assertNotNull(m);
        assertSame(b4, m.entry);
    }
    
    /**
     * Method with two SCCs.
     */
    public void testTwo2() {
        Label l1 = makeLabel(0);
        Label l2 = makeLabel(5);
        Label l3 = makeLabel(10);
        Label l4 = makeLabel(15);

        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l2, l4);
        l4.successors = makeEdge(l3);

        Scc scc = initialize(l1, 20);

        BasicBlock b1 = BasicBlock.get(l1);
        BasicBlock b2 = BasicBlock.get(l2);
        BasicBlock b3 = BasicBlock.get(l3);
        BasicBlock b4 = BasicBlock.get(l4);

        Set<BasicBlock> s1 = new HashSet<BasicBlock>();
        s1.add(b1);
        Set<BasicBlock> s2 = new HashSet<BasicBlock>();
        s2.add(b2);
        s2.add(b3);
        s2.add(b4);
        Set<Set<BasicBlock>> s = new HashSet<Set<BasicBlock>>();
        s.add(s1);
        s.add(s2);

        assertSCC(s, scc);

        assertSet(b1.sccRoot.successors, b2.sccRoot);
        assertSet(b2.sccRoot.successors);

        assertNull(b1.sccRoot.splitPoint());
        assertSame(b2, b2.sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(12);
        assertNotNull(m);
        assertSame(b2, m.entry);
    }

}