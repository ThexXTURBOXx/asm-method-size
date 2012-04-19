/***
 * ASM: a very small and fast Java bytecode manipulation framework
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

import java.util.HashSet;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.LinkedList;

/**
 * Info attached to a label needed for splitting a large method. See {@link Label Label}.
 *
 * @author Mike Sperber
 */
class BasicBlock implements Comparable<BasicBlock> {

    final int position;

    final int size;

    final HashSet<Label> labels;

    /**
     * The depth-first-search index for the SCC computation.
     * -1 if undefined.
     */
    int sccIndex;

    /**
     * The depth-first low-link for the SCC comparison - equal to the
     * smallest index of some node reachable from this, and always
     * less than this.sccIndex, or equal to this.sccIndex if no other
     * node is reachable from this.
     */
    int sccLowLink;

    /**
     * Root of this SCC component.
     */
    Scc sccRoot;

    /**
     * Successors in flowgraph.
     *
     * We keep this separately, because {@link Label#successors} may
     * point to labels not in the labels list.
     */
    final HashSet<BasicBlock> successors;

    /**
     * Predecessors, i.e. inverse to {@link #successors}.
     */
    final HashSet<BasicBlock> predecessors;

    public BasicBlock(Label l, int size) {
        this.sccIndex = -1;
        this.labels = new HashSet<Label>();
        addLabel(l);
        this.size = size;
        this.position = l.position;
        this.successors = new HashSet<BasicBlock>();
        this.predecessors = new HashSet<BasicBlock>();
    }

    public int compareTo(BasicBlock other) {
        Integer Position = position;
        return Position.compareTo(other.position);
    }

    public void addLabel(Label l) {
        assert position == l.position;
        labels.add(l);
        l.info = this;
    }

     static BasicBlock get(Label label) {
        return (BasicBlock) label.info;
    }

    public static TreeSet<BasicBlock> computeBasicBlocks(Label labels, int totalSize) {
        TreeSet<BasicBlock> blocks = collectBasicBlocks(labels, totalSize);
        initializeSuccessorsPredecessors(blocks);
        return blocks;
    }

    /**
     * Collect all basic blocks, initialize successors and
     * predecessors fields.
     */
    private static TreeSet<BasicBlock> collectBasicBlocks(Label labels, int totalSize) {
        // collect all basic blocks
        TreeMap<Integer, BasicBlock> map = new TreeMap<Integer, BasicBlock>();
        LinkedList<Label> work = new LinkedList<Label>();
        HashSet<Label> seen = new HashSet<Label>();
        {
            Label l = labels;
            while (l != null) {
                work.add(l);
                l = l.successor;
            }
        }
        while (!work.isEmpty()) {
            Label l = work.remove();
            seen.add(l);
            BasicBlock b = map.get(l.position);
            if (b == null) {
                int sp = (l.successor == null) ? totalSize : l.successor.position;
                b = new BasicBlock(l, sp - l.position);
                map.put(l.position, b);
            } else
                b.addLabel(l);
            Edge s = l.successors;
            while (s != null) {
                if (!seen.contains(s.successor)) {
                    work.add(s.successor);
                }
                s = s.next;
            }
        }
        return new TreeSet<BasicBlock>(map.values());
    }

    /**
     * Computes the predecessor graph.
     * Assumes that this is the first label.
     */
    private static void initializeSuccessorsPredecessors(TreeSet<BasicBlock> blocks) {
        for (BasicBlock b : blocks) {
            for (Label l : b.labels) {
                Edge s = l.successors;
                while (s != null) {
                    b.successors.add(get(s.successor));
                    s = s.next;
                }
            }
        }

        for (BasicBlock b : blocks) {
            for (BasicBlock s : b.successors) {
                s.predecessors.add(b);
            }
        }
    }

    @Override
    public String toString() {
        return "@" + position;
    }

}


