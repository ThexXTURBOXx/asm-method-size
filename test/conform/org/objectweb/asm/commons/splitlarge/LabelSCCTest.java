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

import static org.objectweb.asm.commons.splitlarge.Split.*;
import org.objectweb.asm.*;

import java.util.Set;
import java.util.HashSet;

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
        return root.labels.contains(l);
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
            for (Label l : root.labels) {
                assertSame(root, getBasicBlock(l).sccRoot);
            }
            root = root.next;
        }
    }

    private void assertSCC(final Set<Set<Label>> desired, Scc roots) {
        for (Set<Label> s: desired)
            assertSCC1(s, roots);
    }

    private Scc initialize(Label labels, int size) {
        return initializeAll(labels, size);
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
    
    /**
     * Method with zero real SCCs
     */
    public void testZero() {
        Label l1 = new Label();
        l1.position = 0;
        Label l2 = new Label();
        l2.position = 5;

        l1.successor = l2;

        l1.successors = makeEdge(l2);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l2);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc scc = initialize(l1, 10);

        assertSCC(s, scc);
        assertSet(getBasicBlock(l1).sccRoot.successors, getBasicBlock(l2).sccRoot);
        assertSet(getBasicBlock(l2).sccRoot.successors);
        assertNull(getBasicBlock(l1).sccRoot.splitPoint());
        assertSame(l2, getBasicBlock(l2).sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(8);
        assertNotNull(m);
        assertSame(l2, m.entry);
    }

   /**
     * Method with one SCC.
     */
    public void testOne1() {
        Label l1 = new Label();
        l1.position = 0;

        l1.successors = makeEdge(l1);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        Scc scc = initialize(l1, 10);

        assertSCC(s, scc);
        assertSet(getBasicBlock(l1).sccRoot.successors);
        assertNull(getBasicBlock(l1).sccRoot.splitPoint());

        assertNull(scc.findSplitPoint(8));
    }

    /**
     * Method with one SCC.
     */
    public void testOne2() {
        Label l1 = new Label();
        l1.position = 0;
        Label l2 = new Label();
        l2.position = 5;
        Label l3 = new Label();
        l3.position = 10;
        Label l4 = new Label();
        l4.position = 15;
        
        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l1, l4);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        s1.add(l2);
        s1.add(l3);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l4);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc scc = initialize(l1, 20);

        assertSCC(s, scc);

        assertSet(getBasicBlock(l1).sccRoot.successors, getBasicBlock(l4).sccRoot);
        assertSet(getBasicBlock(l4).sccRoot.successors);

        assertNull(getBasicBlock(l1).sccRoot.splitPoint());
        assertSame(l4, getBasicBlock(l4).sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(8);
        assertNotNull(m);
        assertSame(l4, m.entry);
    }

    /**
     * Method with one SCC.
     */
    public void testOne3() {
        Label l1 = new Label();
        l1.position = 0;
        Label l2 = new Label();
        l2.position = 5;
        Label l3 = new Label();
        l3.position = 10;
        Label l4 = new Label();
        l4.position = 15;
        Label l5 = new Label();
        l5.position = 20;

        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;
        l4.successor = l5;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l2, l4);
        l4.successors = makeEdge(l1, l5);
        l5.successors = makeEdge();

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
        Scc scc = initialize(l1, 25);

        assertSCC(s, scc);

        assertSet(getBasicBlock(l1).sccRoot.successors, getBasicBlock(l5).sccRoot);
        assertSet(getBasicBlock(l5).sccRoot.successors);

        assertNull(getBasicBlock(l1).sccRoot.splitPoint());
        assertSame(l5, getBasicBlock(l5).sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(8);
        assertNotNull(m);
        assertSame(l5, m.entry);
    }
    
    /**
     * Method with two SCCs.
     */
    public void testTwo1() {
        Label l1 = new Label();
        l1.position = 0;
        Label l2 = new Label();
        l2.position = 5;
        Label l3 = new Label();
        l3.position = 10;
        Label l4 = new Label();
        l4.position = 15;
        Label l5 = new Label();
        l5.position = 20;
        Label l6 = new Label();
        l5.position = 25;

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
        Scc scc = initialize(l1, 30);

        assertSCC(s, scc);

        assertSet(getBasicBlock(l1).sccRoot.successors, getBasicBlock(l4).sccRoot);
        assertSet(getBasicBlock(l4).sccRoot.successors);

        assertNull(getBasicBlock(l1).sccRoot.splitPoint());
        assertSame(l4, getBasicBlock(l4).sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(20);
        assertNotNull(m);
        assertSame(l4, m.entry);
    }
    
    /**
     * Method with two SCCs.
     */
    public void testTwo2() {
        Label l1 = new Label();
        l1.position = 0;
        Label l2 = new Label();
        l2.position = 5;
        Label l3 = new Label();
        l3.position = 10;
        Label l4 = new Label();
        l4.position = 15;

        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l2, l4);
        l4.successors = makeEdge(l3);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l2);
        s2.add(l3);
        s2.add(l4);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        Scc scc = initialize(l1, 20);

        assertSCC(s, scc);

        assertSet(getBasicBlock(l1).sccRoot.successors, getBasicBlock(l2).sccRoot);
        assertSet(getBasicBlock(l2).sccRoot.successors);

        assertNull(getBasicBlock(l1).sccRoot.splitPoint());
        assertSame(l2, getBasicBlock(l2).sccRoot.splitPoint());

        SplitMethod m = scc.findSplitPoint(12);
        assertNotNull(m);
        assertSame(l2, m.entry);
    }

}