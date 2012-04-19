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

final class Split {

    /**
     * JVM limit for maximum method size.
     */
    static final int MAX_METHOD_LENGTH = 65536;
 
    /**
     * Initialize everything needed for performing the splitting.
     *
     * @param l first label
     * @param totalLength total length of the method
     */
    static Scc initializeAll(Label l, int totalLength) {
        initializeBasicBlocks(l);
        computeSuccessorsPredecessors(l);
        Scc root = stronglyConnectedComponents(l);
        root.initializeAll(totalLength);
        return root;
    }

    static BasicBlock getBasicBlock(Label label) {
        return (BasicBlock) label.info;
    }

    static void setBasicBlock(Label label, BasicBlock basicBlock) {
        label.info = basicBlock;
    }

    /**
     * Initialize the info field.
     */
    static void initializeBasicBlocks(Label l) {
        while (l != null) {
            setBasicBlock(l, new BasicBlock());
            l = l.successor;
        }
    }

    // ------------------------------------------------------------------------
    // Utilities for splitting large methods
    // ------------------------------------------------------------------------
    /**
     * Compute size of basic block
     *
     * @param total total size of code in this method
     * @return size of basic block
     */
    static int labelSize(Label l, int total) {
        if (l.successor != null) {
            return l.successor.position - l.position;
        } else {
            return total - l.position;
        }
    }

    // ------------------------------------------------------------------------
    // Strongly-connected components of control-flow graph
    // ------------------------------------------------------------------------

    /**
     * Computes strongly connected components of control-flow graph.
     *
     * @param l first label
     * @return first root
     */
    static Scc stronglyConnectedComponents(Label labels) {
        // Tarjan's algorithm
        int index = 0;
        java.util.Stack<Label> stack = new java.util.Stack<Label>();
        Scc dummyRoot = new Scc(labels); // needed so we can mutate its next field
        Label l = labels;
        while (l != null) {
            if (getBasicBlock(l).sccIndex == -1) {
                index = strongConnect(l, index, stack, dummyRoot);
            }
            l = l.successor;
        }
        Scc realRoot = getBasicBlock(labels).sccRoot;
        realRoot.computeSuccessors();
        return realRoot;
    }

    static private int strongConnect(Label l, int index, java.util.Stack<Label> stack, Scc root) {
        BasicBlock basicBlock = getBasicBlock(l);
        basicBlock.sccIndex = index;
        basicBlock.sccLowLink = index;
        ++index;
        stack.push(l);

        // Consider successors of l
        for (Label w : getBasicBlock(l).successors) {
            if (getBasicBlock(w).sccIndex == -1) {
                // Successor w has not yet been visited; recurse on it
                index = strongConnect(w, index, stack, root);
                basicBlock.sccLowLink = Math.min(basicBlock.sccLowLink, getBasicBlock(w).sccLowLink);
            } else if (stack.contains(w)) {
                // Successor w is in stack S and hence in the current SCC
                basicBlock.sccLowLink = Math.min(basicBlock.sccLowLink, getBasicBlock(w).sccIndex);
            }
        }

        // If l is a root node, pop the stack and generate an SCC
        if (basicBlock.sccLowLink == basicBlock.sccIndex) {
            // start a new strongly connected component
            Scc newRoot = new Scc(l);
            HashSet<Label> labels = newRoot.labels;
            Label w;
            do {
                w = stack.pop();
                getBasicBlock(w).sccRoot = newRoot;
                labels.add(w);
            } while (w != l);

            newRoot.next = root.next;
            root.next = newRoot;
        };
        return index;
    }

    /**
     * Computes the predecessor graph.
     * Assumes that this is the first label.
     */
     static void computeSuccessorsPredecessors(Label labels) {
         {
             Label l = labels;
             while (l != null) {
                 BasicBlock si = getBasicBlock(l);
                 si.successors = new HashSet<Label>();
                 si.predecessors = new HashSet<Label>();
                 Edge s = l.successors;
                 while (s != null) {
                     BasicBlock ssi = getBasicBlock(s.successor);
                     if (ssi != null) {
                         si.successors.add(s.successor);
                     }
                     s = s.next;
                 }
                 l = l.successor;
             }
         }
             

         {
             Label l = labels;
             while (l != null) {
                 for (Label s : getBasicBlock(l).successors) {
                     getBasicBlock(s).predecessors.add(l);
                 }
                 l = l.successor;
             }
         }
     }

}