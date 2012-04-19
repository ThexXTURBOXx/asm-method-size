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

/**
 * Info attached to a label needed for splitting a large method. See {@link Label Label}.
 *
 * @author Mike Sperber
 */
class BasicBlock {
    public BasicBlock() {
        this.sccIndex = -1;
    }

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
     * Successors in flowgaph.
     *
     * We keep this separately, because {@link Label#successors} may
     * point to labels not in the labels list.
     */
    HashSet<Label> successors;

    /**
     * Predecessors, i.e. inverse to {@link #successors}.
     */
    HashSet<Label> predecessors;

    static BasicBlock get(Label label) {
        return (BasicBlock) label.info;
    }

    static void set(Label label, BasicBlock basicBlock) {
        label.info = basicBlock;
    }

    /**
     * Initialize the info field.
     */
    static void initializeBasicBlocks(Label l) {
        while (l != null) {
            set(l, new BasicBlock());
            l = l.successor;
        }
    }

    /**
     * Computes the predecessor graph.
     * Assumes that this is the first label.
     */
     static void computeSuccessorsPredecessors(Label labels) {
         {
             Label l = labels;
             while (l != null) {
                 BasicBlock si = get(l);
                 si.successors = new HashSet<Label>();
                 si.predecessors = new HashSet<Label>();
                 Edge s = l.successors;
                 while (s != null) {
                     BasicBlock ssi = get(s.successor);
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
                 for (Label s : get(l).successors) {
                     get(s).predecessors.add(l);
                 }
                 l = l.successor;
             }
         }
     }

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


}


