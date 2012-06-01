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

import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

import junit.framework.TestCase;

public class CycleEquivalenceTest extends TestCase {

    public void testTechreportFigure1() {
        // Figure 1 from the tech report
        BasicBlock a = new BasicBlock(0);
        BasicBlock b = new BasicBlock(1);
        BasicBlock c = new BasicBlock(2);
        BasicBlock d = new BasicBlock(3);
        BasicBlock e = new BasicBlock(4);
        BasicBlock f = new BasicBlock(5);
        BasicBlock t = new BasicBlock(6);
        a.addEdge(b);
        a.addEdge(c);
        b.addEdge(c);
        c.addEdge(d);
        c.addEdge(e);
        d.addEdge(f);
        e.addEdge(f);
        f.addEdge(b);
        f.addEdge(t);
        SortedSet<BasicBlock> blocks = new TreeSet<BasicBlock>();
        blocks.add(a);
        blocks.add(b);
        blocks.add(c);
        blocks.add(d);
        blocks.add(e);
        blocks.add(f);
        blocks.add(t);
        CycleEquivalence.Node start = CycleEquivalence.computeUndigraph(blocks);
        CycleEquivalence.Node aIn = findEdgeTo(start, a);
        assertNotNull(aIn);
        CycleEquivalence.Node aOut = findOutNode(aIn);
        assertNotNull(aOut);

        CycleEquivalence.Node bIn = findEdgeTo(aOut, b);
        assertNotNull(bIn);
        CycleEquivalence.Node bOut = findOutNode(bIn);
        assertNotNull(bOut);

        CycleEquivalence.Node cIn = findEdgeTo(bOut, c);
        assertNotNull(cIn);
        CycleEquivalence.Node cOut = findOutNode(cIn);
        assertNotNull(cOut);

        CycleEquivalence.Node dIn = findEdgeTo(cOut, d);
        assertNotNull(dIn);
        CycleEquivalence.Node dOut = findOutNode(dIn);
        assertNotNull(dOut);

        CycleEquivalence.Node eIn = findEdgeTo(cOut, e);
        assertNotNull(eIn);
        CycleEquivalence.Node eOut = findOutNode(eIn);
        assertNotNull(eOut);

        CycleEquivalence.Node fIn = findEdgeTo(dOut, f);
        assertNotNull(fIn);
        CycleEquivalence.Node fOut = findOutNode(fIn);
        assertNotNull(fOut);

        assertSame(fIn, findEdgeTo(eOut, f));

        ArrayList<CycleEquivalence.Node> nodes = new ArrayList<CycleEquivalence.Node>();
        start.computeSpanningTree(nodes);
        CycleEquivalence.computeCycleEquivalence(nodes);
        
        CycleEquivalence.EquivClass ca = aIn.representativeEdge.equivClass;
        CycleEquivalence.EquivClass cb = bIn.representativeEdge.equivClass;
        CycleEquivalence.EquivClass cc = cIn.representativeEdge.equivClass;
        CycleEquivalence.EquivClass cd = dIn.representativeEdge.equivClass;
        CycleEquivalence.EquivClass ce = eIn.representativeEdge.equivClass;
        CycleEquivalence.EquivClass cf = fIn.representativeEdge.equivClass;

        assertNotSame(ca, cb);
        assertNotSame(ca, cc);
        assertNotSame(ca, cd);
        assertNotSame(ca, ce);
        assertNotSame(ca, cf);

        assertNotSame(cb, cc);
        assertNotSame(cb, cd);
        assertNotSame(cb, ce);
        assertNotSame(cb, cf);

        assertNotSame(cc, cd);
        assertNotSame(cc, ce);
        assertSame(cc, cf);

        assertNotSame(cd, ce);
        assertNotSame(cd, cf);

        assertNotSame(ce, cf);
    }

    /**
     * @returns in node
     */
    private CycleEquivalence.Node findEdgeTo(CycleEquivalence.Node from, BasicBlock block) {
        for (CycleEquivalence.Edge edge : from.allEdges) {
            CycleEquivalence.Node other = edge.getOtherNode(from);
            if (other.block == block) {
                return other;
            }
        }
        return null;
    }

    private CycleEquivalence.Node findOutNode(CycleEquivalence.Node in) {
        return in.representativeEdge.getOtherNode(in);
    }

    public void testSpanningTree1() {
        CycleEquivalence.Node n0 = new CycleEquivalence.Node();
        CycleEquivalence.Node n1 = new CycleEquivalence.Node();
        CycleEquivalence.Node n2 = new CycleEquivalence.Node();
        CycleEquivalence.Node n3 = new CycleEquivalence.Node();
        n0.addEdge(n1);
        n1.addEdge(n2);
        n2.addEdge(n3);
        n3.addEdge(n0);
        ArrayList<CycleEquivalence.Node> nodes = new ArrayList<CycleEquivalence.Node>();
        n0.computeSpanningTree(nodes);
        assertSame(nodes.get(0), n0);
        assertSame(nodes.get(1), n1);
        assertSame(nodes.get(2), n2);
        assertSame(nodes.get(3), n3);
        assertEquals(n0.treeEdges.size(), 1);
        assertSame(n0.treeEdges.get(0).getOtherNode(n0), n1);
        assertEquals(n0.backEdgesFrom.size(), 0);
        assertEquals(n0.backEdgesTo.size(), 1);
        assertEquals(n0.backEdgesTo.get(0).getOtherNode(n0), n3);
        assertEquals(n1.treeEdges.size(), 1);
        assertSame(n1.treeEdges.get(0).getOtherNode(n1), n2);
        assertEquals(n1.backEdgesFrom.size(), 0);
        assertEquals(n1.backEdgesTo.size(), 0);
        assertEquals(n2.treeEdges.size(), 1);
        assertSame(n2.treeEdges.get(0).getOtherNode(n2), n3);
        assertEquals(n2.backEdgesFrom.size(), 0);
        assertEquals(n2.backEdgesTo.size(), 0);
        assertEquals(n3.treeEdges.size(), 0);
        assertEquals(n3.backEdgesFrom.size(), 1);
        assertSame(n3.backEdgesFrom.get(0).getOtherNode(n3), n0);
        assertEquals(n3.backEdgesTo.size(), 0);
    }

    
    public void testCycleEquivalence3a() {
        // Figure 3(a) from paper
        CycleEquivalence.Node n0 = new CycleEquivalence.Node();
        CycleEquivalence.Node n1 = new CycleEquivalence.Node();
        CycleEquivalence.Node n2 = new CycleEquivalence.Node();
        CycleEquivalence.Node n3 = new CycleEquivalence.Node();
        CycleEquivalence.Node n4 = new CycleEquivalence.Node();
        CycleEquivalence.Node n5 = new CycleEquivalence.Node();
        CycleEquivalence.Node n6 = new CycleEquivalence.Node();
        CycleEquivalence.Node n7 = new CycleEquivalence.Node();
        n0.addEdge(n1);
        n0.addEdge(n7);
        n1.addEdge(n2);
        n1.addEdge(n4);
        n2.addEdge(n3);
        n2.addEdge(n3);
        n3.addEdge(n4);
        n4.addEdge(n5);
        n5.addEdge(n6);
        n5.addEdge(n6); // back edge
        n6.addEdge(n7);
        ArrayList<CycleEquivalence.Node> nodes = new ArrayList<CycleEquivalence.Node>();
        n0.computeSpanningTree(nodes);
        assertSame(nodes.get(0), n0);
        assertSame(nodes.get(1), n1);
        assertSame(nodes.get(2), n2);
        assertSame(nodes.get(3), n3);
        assertSame(nodes.get(4), n4);
        assertSame(nodes.get(5), n5);
        assertSame(nodes.get(6), n6);
        assertSame(nodes.get(7), n7);
        assertEquals(1, n0.treeEdges.size());
        assertSame(n1, n0.treeEdges.get(0).getOtherNode(n0));
        assertEquals(0, n0.backEdgesFrom.size());
        assertEquals(1, n0.backEdgesTo.size());
        assertSame(n7, n0.backEdgesTo.get(0).getOtherNode(n0));
        assertEquals(1, n1.treeEdges.size());
        assertSame(n2, n1.treeEdges.get(0).getOtherNode(n1));
        assertEquals(0, n1.backEdgesFrom.size());
        assertEquals(1, n1.backEdgesTo.size());
        assertSame(n4, n1.backEdgesTo.get(0).getOtherNode(n1));
        assertEquals(1, n2.treeEdges.size());
        assertSame(n3, n2.treeEdges.get(0).getOtherNode(n2));
        assertEquals(0, n2.backEdgesFrom.size());
        assertEquals(1, n2.backEdgesTo.size());
        assertSame(n3, n2.backEdgesTo.get(0).getOtherNode(n2));
        assertEquals(1, n3.treeEdges.size());
        assertSame(n4, n3.treeEdges.get(0).getOtherNode(n3));
        assertEquals(1, n3.backEdgesFrom.size());
        assertSame(n2, n3.backEdgesFrom.get(0).getOtherNode(n3));
        assertEquals(0, n3.backEdgesTo.size());
        assertEquals(1, n4.treeEdges.size());
        assertSame(n5, n4.treeEdges.get(0).getOtherNode(n4));
        assertEquals(1, n4.backEdgesFrom.size());
        assertSame(n1, n4.backEdgesFrom.get(0).getOtherNode(n4));
        assertEquals(0, n4.backEdgesTo.size());
        assertEquals(1, n5.treeEdges.size());
        assertSame(n6, n5.treeEdges.get(0).getOtherNode(n5));
        assertEquals(0, n5.backEdgesFrom.size());
        assertEquals(1, n5.backEdgesTo.size());
        assertSame(n6, n5.backEdgesTo.get(0).getOtherNode(n5));
        assertEquals(1, n6.treeEdges.size());
        assertSame(n7, n6.treeEdges.get(0).getOtherNode(n6));
        assertEquals(1, n6.backEdgesFrom.size());
        assertSame(n5, n6.backEdgesFrom.get(0).getOtherNode(n6));
        assertEquals(0, n6.backEdgesTo.size());
        assertEquals(0, n7.treeEdges.size());
        assertEquals(1, n7.backEdgesFrom.size());
        assertSame(n0, n7.backEdgesFrom.get(0).getOtherNode(n7));
        assertEquals(0, n7.backEdgesTo.size());

        CycleEquivalence.computeCycleEquivalence(nodes);
        CycleEquivalence.EquivClass c0 = n0.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c1 = n1.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c2 = n2.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c3 = n3.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c4 = n4.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c5 = n5.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c6 = n6.treeEdges.get(0).equivClass;
 
        assertNotSame(c0, c1);
        assertNotSame(c0, c2);
        assertNotSame(c0, c3);
        assertSame(c0, c4);
        assertNotSame(c0, c5);
        assertSame(c0, c6);

        assertNotSame(c1, c2);
        assertSame(c1, c3);
        assertNotSame(c1, c4);
        assertNotSame(c1, c5);
        assertNotSame(c1, c6);

        assertNotSame(c2, c3);
        assertNotSame(c2, c4);
        assertNotSame(c2, c5);
        assertNotSame(c2, c6);

        assertNotSame(c3, c4);
        assertNotSame(c3, c5);
        assertNotSame(c3, c6);

        assertNotSame(c4, c5);
        assertSame(c4, c6);

        assertNotSame(c5, c6);
    }

    public void testCycleEquivalence3b() {
        // Figure 3(b) from paper
        CycleEquivalence.Node n0 = new CycleEquivalence.Node();
        CycleEquivalence.Node n1 = new CycleEquivalence.Node();
        CycleEquivalence.Node n2 = new CycleEquivalence.Node();
        CycleEquivalence.Node n3 = new CycleEquivalence.Node();
        CycleEquivalence.Node n4 = new CycleEquivalence.Node();
        CycleEquivalence.Node n5 = new CycleEquivalence.Node();
        CycleEquivalence.Node n6 = new CycleEquivalence.Node();
        CycleEquivalence.Node n7 = new CycleEquivalence.Node();
        n0.addEdge(n1);
        n0.addEdge(n7);
        n1.addEdge(n2);
        n1.addEdge(n5);
        n2.addEdge(n3);
        n2.addEdge(n6);
        n3.addEdge(n4);
        n3.addEdge(n4); // back edge
        n4.addEdge(n5);
        n5.addEdge(n6);
        n6.addEdge(n7);
        ArrayList<CycleEquivalence.Node> nodes = new ArrayList<CycleEquivalence.Node>();
        n0.computeSpanningTree(nodes);
        assertSame(nodes.get(0), n0);
        assertSame(nodes.get(1), n1);
        assertSame(nodes.get(2), n2);
        assertSame(nodes.get(3), n3);
        assertSame(nodes.get(4), n4);
        assertSame(nodes.get(5), n5);
        assertSame(nodes.get(6), n6);
        assertSame(nodes.get(7), n7);
        assertEquals(1, n0.treeEdges.size());
        assertSame(n1, n0.treeEdges.get(0).getOtherNode(n0));
        assertEquals(0, n0.backEdgesFrom.size());
        assertEquals(1, n0.backEdgesTo.size());
        assertSame(n7, n0.backEdgesTo.get(0).getOtherNode(n0));
        assertEquals(1, n1.treeEdges.size());
        assertSame(n2, n1.treeEdges.get(0).getOtherNode(n1));
        assertEquals(0, n1.backEdgesFrom.size());
        assertEquals(1, n1.backEdgesTo.size());
        assertSame(n5, n1.backEdgesTo.get(0).getOtherNode(n1));
        assertEquals(1, n2.treeEdges.size());
        assertSame(n3, n2.treeEdges.get(0).getOtherNode(n2));
        assertEquals(0, n2.backEdgesFrom.size());
        assertEquals(1, n2.backEdgesTo.size());
        assertSame(n6, n2.backEdgesTo.get(0).getOtherNode(n2));
        assertEquals(1, n3.treeEdges.size());
        assertSame(n4, n3.treeEdges.get(0).getOtherNode(n3));
        assertEquals(0, n3.backEdgesFrom.size());
        assertEquals(1, n3.backEdgesTo.size());
        assertSame(n4, n3.backEdgesTo.get(0).getOtherNode(n3));
        assertEquals(1, n4.treeEdges.size());
        assertSame(n5, n4.treeEdges.get(0).getOtherNode(n4));
        assertEquals(1, n4.backEdgesFrom.size());
        assertSame(n3, n4.backEdgesFrom.get(0).getOtherNode(n4));
        assertEquals(0, n4.backEdgesTo.size());
        assertEquals(1, n5.treeEdges.size());
        assertSame(n6, n5.treeEdges.get(0).getOtherNode(n5));
        assertEquals(1, n5.backEdgesFrom.size());
        assertSame(n1, n5.backEdgesFrom.get(0).getOtherNode(n5));
        assertEquals(0, n5.backEdgesTo.size());
        assertEquals(1, n6.treeEdges.size());
        assertSame(n7, n6.treeEdges.get(0).getOtherNode(n6));
        assertEquals(1, n6.backEdgesFrom.size());
        assertSame(n2, n6.backEdgesFrom.get(0).getOtherNode(n6));
        assertEquals(0, n6.backEdgesTo.size());
        assertEquals(0, n7.treeEdges.size());
        assertEquals(1, n7.backEdgesFrom.size());
        assertSame(n0, n7.backEdgesFrom.get(0).getOtherNode(n7));
        assertEquals(0, n7.backEdgesTo.size());

        CycleEquivalence.computeCycleEquivalence(nodes);
        CycleEquivalence.EquivClass c0 = n0.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c1 = n1.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c2 = n2.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c3 = n3.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c4 = n4.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c5 = n5.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c6 = n6.treeEdges.get(0).equivClass;
 
        assertNotSame(c0, c1);
        assertNotSame(c0, c2);
        assertNotSame(c0, c3);
        assertNotSame(c0, c4);
        assertNotSame(c0, c5);
        assertSame(c0, c6);

        assertNotSame(c1, c2);
        assertNotSame(c1, c3);
        assertNotSame(c1, c4);
        assertNotSame(c1, c5);
        assertNotSame(c1, c6);

        assertNotSame(c2, c3);
        assertSame(c2, c4);
        assertNotSame(c2, c5);
        assertNotSame(c2, c6);

        assertNotSame(c3, c4);
        assertNotSame(c3, c5);
        assertNotSame(c3, c6);

        assertNotSame(c4, c5);
        assertNotSame(c4, c6);

        assertNotSame(c5, c6);
    }

    public void testCycleEquivalence3c() {
        // Figure 3(c) from paper
        CycleEquivalence.Node n0 = new CycleEquivalence.Node();
        CycleEquivalence.Node n1 = new CycleEquivalence.Node();
        CycleEquivalence.Node n2 = new CycleEquivalence.Node();
        CycleEquivalence.Node n3 = new CycleEquivalence.Node();
        CycleEquivalence.Node n4 = new CycleEquivalence.Node();
        CycleEquivalence.Node n5 = new CycleEquivalence.Node();
        CycleEquivalence.Node n6 = new CycleEquivalence.Node();
        CycleEquivalence.Node n7 = new CycleEquivalence.Node();
        CycleEquivalence.Node n8 = new CycleEquivalence.Node();
        n0.addEdge(n1);
        n0.addEdge(n8);
        n1.addEdge(n2);
        n1.addEdge(n6);
        n2.addEdge(n3);
        n2.addEdge(n7);
        n3.addEdge(n4);
        n3.addEdge(n5);
        n4.addEdge(n5);
        n4.addEdge(n7);
        n5.addEdge(n6);
        n7.addEdge(n8);
        ArrayList<CycleEquivalence.Node> nodes = new ArrayList<CycleEquivalence.Node>();
        n0.computeSpanningTree(nodes);
        assertSame(nodes.get(0), n0);
        assertSame(nodes.get(1), n1);
        assertSame(nodes.get(2), n2);
        assertSame(nodes.get(3), n3);
        assertSame(nodes.get(4), n4);
        assertSame(nodes.get(5), n5);
        assertSame(nodes.get(6), n6);
        assertSame(nodes.get(7), n7);
        assertSame(nodes.get(8), n8);
        assertEquals(1, n0.treeEdges.size());
        assertSame(n1, n0.treeEdges.get(0).getOtherNode(n0));
        assertEquals(0, n0.backEdgesFrom.size());
        assertEquals(1, n0.backEdgesTo.size());
        assertSame(n8, n0.backEdgesTo.get(0).getOtherNode(n0));

        assertEquals(1, n1.treeEdges.size());
        assertSame(n2, n1.treeEdges.get(0).getOtherNode(n1));
        assertEquals(0, n1.backEdgesFrom.size());
        assertEquals(1, n1.backEdgesTo.size());
        assertSame(n6, n1.backEdgesTo.get(0).getOtherNode(n1));

        assertEquals(1, n2.treeEdges.size());
        assertSame(n3, n2.treeEdges.get(0).getOtherNode(n2));
        assertEquals(0, n2.backEdgesFrom.size());
        assertEquals(1, n2.backEdgesTo.size());
        assertSame(n7, n2.backEdgesTo.get(0).getOtherNode(n2));

        assertEquals(1, n3.treeEdges.size());
        assertSame(n4, n3.treeEdges.get(0).getOtherNode(n3));
        assertEquals(0, n3.backEdgesFrom.size());
        assertEquals(1, n3.backEdgesTo.size());
        assertSame(n5, n3.backEdgesTo.get(0).getOtherNode(n3));

        assertEquals(2, n4.treeEdges.size());
        assertSame(n5, n4.treeEdges.get(0).getOtherNode(n4));
        assertSame(n7, n4.treeEdges.get(1).getOtherNode(n4));
        assertEquals(0, n4.backEdgesFrom.size());
        assertEquals(0, n4.backEdgesTo.size());

        assertEquals(1, n5.treeEdges.size());
        assertSame(n6, n5.treeEdges.get(0).getOtherNode(n5));
        assertEquals(1, n5.backEdgesFrom.size());
        assertSame(n3, n5.backEdgesFrom.get(0).getOtherNode(n5));
        assertEquals(0, n5.backEdgesTo.size());

        assertEquals(0, n6.treeEdges.size());
        assertEquals(1, n6.backEdgesFrom.size());
        assertSame(n1, n6.backEdgesFrom.get(0).getOtherNode(n6));
        assertEquals(0, n6.backEdgesTo.size());

        assertEquals(1, n7.treeEdges.size());
        assertSame(n8, n7.treeEdges.get(0).getOtherNode(n7));
        assertEquals(1, n7.backEdgesFrom.size());
        assertSame(n2, n7.backEdgesFrom.get(0).getOtherNode(n7));
        assertEquals(0, n7.backEdgesTo.size());

        assertEquals(0, n8.treeEdges.size());
        assertEquals(1, n8.backEdgesFrom.size());
        assertSame(n0, n8.backEdgesFrom.get(0).getOtherNode(n8));
        assertEquals(0, n8.backEdgesTo.size());

        CycleEquivalence.computeCycleEquivalence(nodes);
        CycleEquivalence.EquivClass c0 = n0.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c1 = n1.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c2 = n2.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c3 = n3.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c4 = n4.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c6 = n4.treeEdges.get(1).equivClass;
        CycleEquivalence.EquivClass c5 = n5.treeEdges.get(0).equivClass;
        CycleEquivalence.EquivClass c7 = n7.treeEdges.get(0).equivClass;
 
        assertNotSame(c0, c1);
        assertNotSame(c0, c2);
        assertNotSame(c0, c3);
        assertNotSame(c0, c4);
        assertNotSame(c0, c5);
        assertNotSame(c0, c6);
        assertSame(c0, c7);

        assertNotSame(c1, c2);
        assertNotSame(c1, c3);
        assertNotSame(c1, c4);
        assertNotSame(c1, c5);
        assertNotSame(c1, c6);
        assertNotSame(c1, c7);

        assertNotSame(c2, c3);
        assertNotSame(c2, c4);
        assertNotSame(c2, c5);
        assertNotSame(c2, c6);
        assertNotSame(c2, c7);

        assertNotSame(c3, c4);
        assertNotSame(c3, c5);
        assertNotSame(c3, c6);
        assertNotSame(c3, c7);

        assertNotSame(c4, c5);
        assertNotSame(c4, c6);
        assertNotSame(c4, c7);

        assertNotSame(c5, c6);
        assertNotSame(c5, c7);

        assertNotSame(c6, c7);
    }
    
}