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

final class Split {
    static SplitInfo getSplitInfo(Label label) {
        return (SplitInfo) label.info;
    }

    static void setSplitInfo(Label label, SplitInfo splitInfo) {
        label.info = splitInfo;
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
     * Initializes the sccIndex field.
     */
    static void initializeSplitInfos(Label l) {
        while (l != null) {
            setSplitInfo(l, new SplitInfo());
            l = l.successor;
        }
    }

    /**
     * Computes strongly connected components of control-flow graph.
     * Assumes that this is the first label.
     */
    static SccRoot stronglyConnectedComponents(Label l) {
        // Tarjan's algorithm
        int index = 0;
        java.util.Stack<Label> stack = new java.util.Stack<Label>();
        SccRoot dummyRoot = new SccRoot(l); // needed so we can mutate its next field
        while (l != null) {
            if (getSplitInfo(l).sccIndex == -1) {
                index = strongConnect(l, index, stack, dummyRoot);
            }
            l = l.successor;
        }
        dummyRoot.next.computeSuccessors();
        return dummyRoot.next;
    }

    static private int strongConnect(Label l, int index, java.util.Stack<Label> stack, SccRoot root) {
        SplitInfo splitInfo = getSplitInfo(l);
        splitInfo.sccIndex = index;
        splitInfo.sccLowLink = index;
        ++index;
        stack.push(l);

        // Consider successors of l
        Edge e = l.successors;
        while (e != null) {
            Label w = e.successor;
            if (getSplitInfo(w).sccIndex == -1) {
                // Successor w has not yet been visited; recurse on it
                index = strongConnect(w, index, stack, root);
                splitInfo.sccLowLink = Math.min(splitInfo.sccLowLink, getSplitInfo(w).sccLowLink);
            } else if (stack.contains(w)) {
                // Successor w is in stack S and hence in the current SCC
                splitInfo.sccLowLink = Math.min(splitInfo.sccLowLink, getSplitInfo(w).sccIndex);
            }
            e = e.next;
        }

        // If l is a root node, pop the stack and generate an SCC
        if (splitInfo.sccLowLink == splitInfo.sccIndex) {
            // start a new strongly connected component
            Label w;
            Label previous = null;
            SccRoot newRoot = new SccRoot(l);
            do {
                w = stack.pop();
                SplitInfo wSplitInfo = getSplitInfo(w);
                wSplitInfo.sccRoot = newRoot;
                wSplitInfo.sccNext = previous;
                previous = w;
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
     static void computePredecessors(Label l) {
        while (l != null) {
            Edge s = l.successors;
            while (s != null) {
                getSplitInfo(s.successor).predecessors.add(l);
                s = s.next;
            }
            l = l.successor;
        }
    }



}