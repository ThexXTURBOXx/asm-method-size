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
import static org.objectweb.asm.commons.splitlarge.Split.*;

import java.util.HashSet;

/**
 * Root of SCC graph.
 *
 * @author Mike Sperber
 */
class SccRoot {

    public SccRoot(Label first) {
        this.first = first;
        this.labels = new HashSet<Label>();
        this.predecessors = new HashSet<SccRoot>();
    }

    /**
     * First label of root.
     */
    Label first;

    /**
     * Labels of this component.
     */
    HashSet<Label> labels;

    /**
     * Next root of an SCC component.
     */
    SccRoot next;

    /**
     * Successors in SCC graph.
     */
    HashSet<SccRoot> successors;

    /**
     * Predecessors in SCC graph.
     */
    HashSet<SccRoot> predecessors;

    /**
     * Transitive closure of the the successors of this node,
     * including this one.
     */
    HashSet<SccRoot> transitiveClosure;

    /**
     * Size of the basic blocks in the transitive closure of this component.
     */
    int transitiveClosureSize;

    /**
     * Combined size of all the basic blocks in this component.
     */
    int size = -1;

    /**
     * Fills the {@link #successors} field of all roots.
     */
    void computeSuccessors() {
        SccRoot r = this;
        while (r != null) {
            r.computeSuccessors1();
            r = r.next;
        }
    }

    /**
     * Fills the {@link #successors} field of <code>this</code>.
     */
    private void computeSuccessors1() {
        successors = new HashSet<SccRoot>();
        for (Label l : labels) {
            Edge e = l.successors;
            while (e != null) {
                SccRoot root = getSplitInfo(e.successor).sccRoot;
                if (root != this) {
                    successors.add(root);
                }
                e = e.next;
            }
        }
    }

    /**
     * Fills the {@link #predecessors} field of all roots.
     */
    void computePredecessors() {
        SccRoot r = this;
        while (r != null) {
            r.computePredecessors1();
            r = r.next;
        }
    }

    /**
     * Fills the {@link #predecessors} field of <code>this</code>.
     */
    private void computePredecessors1() {
        for (SccRoot root : successors) {
            root.predecessors.add(this);
        }
    }
     

    /**
     * Compute all size-relevant information, i.e. set the 
     * {@link #transitiveClosure}, {@link #size}, and
     * {@link * #transitiveClosureSize} fields of all SCC components.
     */
    void computeSizeInfo(int total) {
        computeSizes(total);
        computeTransitiveClosure();
        computeTransitiveClosureSizes();
    }

    /**
     * Compute transitive closure of successors relation,
     * and store it in the {@link #transitiveClosure} field.
     * Assumes that this is the root component.
     */
     void computeTransitiveClosure() {
        if (transitiveClosure != null)
            return;
        transitiveClosure = new HashSet<SccRoot>();
        transitiveClosure.add(this);
        for (SccRoot root : successors) {
            root.computeTransitiveClosure();
            transitiveClosure.addAll(root.transitiveClosure);
        }
    }

    /**
     * Compute the sizes of the transitive closures in all SCC components.
     */
    void computeTransitiveClosureSizes() {
        SccRoot root = this;
        while (root != null) {
            root.computeTransitiveClosureSize();
            root = root.next;
        }
    }

    /**
     * Compute code size of all basic blocks in the transitive closure
     * of this component.
     */
    private void computeTransitiveClosureSize() {
        transitiveClosureSize = 0;
        for (SccRoot root : transitiveClosure) {
            transitiveClosureSize += root.size;
        }
    }

    /**
     * Compute the sizes of all SCC components, and set their
     * {@link #size} fields.
     */
    void computeSizes(int total) {
        SccRoot root = this;
        while (root != null) {
            root.computeSize(total);
            root = root.next;
        }
    }

    /**
    * Compute size of all basic blocks in this component and set
    * the {@link #size} field to it.
    *
    * @param total size of code in this method.
    */
    private void computeSize(int total) {
        size = 0;
        for (Label l : labels) {
            size += labelSize(l, total);
        }
   }

    /**
     * Tell us if this SCC component is a possible split point.
     *
     * This is the case if the component only has a single entry
     * point, i.e. every basic block within the component only has
     * predecessors within the component except for one, and that
     * a corresponding criterion for the transitive closure holds.
     *
     * This assumes that {@link SplitInfo#predecessors} and {@link
     * #predecessors} is set.
     *
     * @return the entry basic block if it is, null if it isn't or if
     * it's the root block
     */
    public Label splitPoint() {
        Label entry = null;
        /*
         * First check that there's only one entry point to this
         * component.
         */
        for (Label l : labels) {
            for (Label p : getSplitInfo(l).predecessors) {
                if (getSplitInfo(p).sccRoot != this) {
                    if (entry == null) {
                        entry = l;
                        break;
                    } else {
                        return null;
                    }
                }
            }
        }
        if (entry == null) {
            return null;
        } else {
            /*
             * Now check that all SCC components in the transitive
             * closure have only predecessors within the transitive
             * closure.
             */
            for (SccRoot root : transitiveClosure) {
                if (root != this) {
                    for (SccRoot p : root.predecessors) {
                        if (!transitiveClosure.contains(p)) {
                            return null;
                        }
                    }
                }
            }
            return entry;
        }
    }
}
