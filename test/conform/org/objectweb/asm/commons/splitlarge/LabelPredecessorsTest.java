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
 * Unit tests for the predecessor computation for the flow graph.
 *
 * @author Mike Sperber
 */
public class LabelPredecessorsTest extends TestCase {

    private void assertLabels(Set<Label> labels, Label... p) {
        Set<Label> s = new HashSet<Label>();
        for (Label l : p) {
            s.add(l);
        }
        assertEquals(labels, s);
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

    public void testDag1() {
        Label l1 = new Label();
        Label l2 = new Label();
        Label l3 = new Label();
        Label l4 = new Label();
        Label l5 = new Label();
        Label l6 = new Label();
        Label l7 = new Label();
        Label l8 = new Label();
        
        l1.successor = l2;
        l2.successor = l3;
        l3.successor = l4;
        l4.successor = l5;
        l5.successor = l6;
        l6.successor = l7;
        l7.successor = l8;

        l1.successors = makeEdge(l2);
        l2.successors = makeEdge(l3);
        l3.successors = makeEdge(l4, l5);
        l4.successors = makeEdge(l6);
        l5.successors = makeEdge(l6, l7);
        l6.successors = makeEdge();
        l7.successors = makeEdge(l8);

        BasicBlock.initializeBasicBlocks(l1);
        BasicBlock.computeSuccessorsPredecessors(l1);

        assertLabels(BasicBlock.get(l1).predecessors);
        assertLabels(BasicBlock.get(l2).predecessors, l1);
        assertLabels(BasicBlock.get(l3).predecessors, l2);
        assertLabels(BasicBlock.get(l4).predecessors, l3);
        assertLabels(BasicBlock.get(l5).predecessors, l3);
        assertLabels(BasicBlock.get(l6).predecessors, l4, l5);
        assertLabels(BasicBlock.get(l7).predecessors, l5);
        assertLabels(BasicBlock.get(l8).predecessors, l7);
    }
}