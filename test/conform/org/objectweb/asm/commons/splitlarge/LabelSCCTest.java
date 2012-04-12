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

    private SccRoot findRoot(SccRoot roots, Label l) {
        SccRoot root = roots;
        while (root != null) {
            if (isInRoot(root, l))
                return root;
            root = root.next;
        }
        fail("root not found at all");
        return null;
    }

    private boolean isInRoot(SccRoot root, Label l) {
        return root.labels.contains(l);
    }

    private void assertSCC1(final Set<Label> desired, SccRoot roots) {
        // the labels of this desired root must all be in the same actual root
        SccRoot desiredRoot = findRoot(roots, desired.iterator().next());
        for (Label l: desired)
            assertTrue(isInRoot(desiredRoot, l));
        // ... and they must be in no other root
        SccRoot root = roots;
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
                assertSame(root, getSplitInfo(l).sccRoot);
            }
            root = root.next;
        }
    }

    private void assertSCC(final Set<Set<Label>> desired, SccRoot roots) {
        for (Set<Label> s: desired)
            assertSCC1(s, roots);
    }

    private void assertSCC(Label labels, final Set<Set<Label>> desired) {
        initializeSplitInfos(labels);
        SccRoot scc = stronglyConnectedComponents(labels);
        scc.computeTransitiveClosure();
        assertSCC(desired, scc);
        computePredecessors(labels);
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
        Label l2 = new Label();

        l1.successor = l2;

        l1.successors = makeEdge(l2);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Label> s2 = new HashSet<Label>();
        s2.add(l2);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);
        s.add(s2);
        assertSCC(l1, s);

        assertSet(getSplitInfo(l1).sccRoot.successors, getSplitInfo(l2).sccRoot);
        assertSet(getSplitInfo(l2).sccRoot.successors);
        assertNull(getSplitInfo(l1).sccRoot.splitPoint());
        assertSame(l2, getSplitInfo(l2).sccRoot.splitPoint());
    }

   /**
     * Method with one SCC.
     */
    public void testOne1() {
        Label l1 = new Label();

        l1.successors = makeEdge(l1);

        Set<Label> s1 = new HashSet<Label>();
        s1.add(l1);
        Set<Set<Label>> s = new HashSet<Set<Label>>();
        s.add(s1);

        assertSCC(l1, s);

        assertSet(getSplitInfo(l1).sccRoot.successors);
        assertNull(getSplitInfo(l1).sccRoot.splitPoint());
    }

    /**
     * Method with one SCC.
     */
    public void testOne2() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        
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

        assertSCC(l1, s);

        assertSet(getSplitInfo(l1).sccRoot.successors, getSplitInfo(l4).sccRoot);
        assertSet(getSplitInfo(l4).sccRoot.successors);

        assertNull(getSplitInfo(l1).sccRoot.splitPoint());
        assertSame(l4, getSplitInfo(l4).sccRoot.splitPoint());
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

        assertSCC(l1, s);

        assertSet(getSplitInfo(l1).sccRoot.successors, getSplitInfo(l5).sccRoot);
        assertSet(getSplitInfo(l5).sccRoot.successors);

        assertNull(getSplitInfo(l1).sccRoot.splitPoint());
        assertSame(l5, getSplitInfo(l5).sccRoot.splitPoint());
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

        assertSCC(l1, s);

        assertSet(getSplitInfo(l1).sccRoot.successors, getSplitInfo(l4).sccRoot);
        assertSet(getSplitInfo(l4).sccRoot.successors);

        assertNull(getSplitInfo(l1).sccRoot.splitPoint());
        assertSame(l4, getSplitInfo(l4).sccRoot.splitPoint());
    }
    
    /**
     * Method with two SCCs.
     */
    public void testTwo2() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();

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

        assertSCC(l1, s);

        assertSet(getSplitInfo(l1).sccRoot.successors, getSplitInfo(l2).sccRoot);
        assertSet(getSplitInfo(l2).sccRoot.successors);

        assertNull(getSplitInfo(l1).sccRoot.splitPoint());
        assertSame(l2, getSplitInfo(l2).sccRoot.splitPoint());
    }

}