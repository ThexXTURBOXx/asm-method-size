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

import java.util.HashSet;
import java.util.TreeSet;
import java.util.Stack;

/**
 * Component of SCC graph.
 *
 * @author Mike Sperber
 */
class Scc {

    public Scc(BasicBlock first) {
        this.first = first;
        this.blocks = new HashSet<BasicBlock>();
        this.predecessors = new HashSet<Scc>();
    }

    /**
     * First basic block of component.
     */
    BasicBlock first;

    /**
     * Blocks of this component.
     */
    HashSet<BasicBlock> blocks;

    /**
     * Next component.
     */
    Scc next;

    /**
     * Successors in SCC graph.
     */
    HashSet<Scc> successors;

    /**
     * Predecessors in SCC graph.
     */
    HashSet<Scc> predecessors;

    /**
     * Transitive closure of the the successors of this node,
     * including this one.
     */
    HashSet<Scc> transitiveClosure;

    /**
     * Size of the basic blocks in the transitive closure of this component.
     */
    int transitiveClosureSize;

    /**
     * Combined size of all the basic blocks in this component.
     */
    int size = -1;

    /**
     * If this component is split out, this field references the
     * corresponding {@link SplitMethod} object.
     */
    SplitMethod splitMethod;


    // ------------------------------------------------------------------------
    // Strongly-connected components of control-flow graph
    // ------------------------------------------------------------------------

    /**
     * Computes strongly connected components of control-flow graph.
     *
     * @param l first label
     * @return first root
     */
    static Scc stronglyConnectedComponents(TreeSet<BasicBlock> blocks) {
        // Tarjan's algorithm
        int index = 0;
        BasicBlock first = blocks.first();
        Stack<BasicBlock> stack = new Stack<BasicBlock>();
        Scc dummyRoot = new Scc(first); // needed so we can mutate its next field
        for (BasicBlock b : blocks) {
            if (b.sccIndex == -1) {
                index = strongConnect(b, index, stack, dummyRoot);
            }
        }
        Scc realRoot = first.sccRoot;
        realRoot.computeSuccessors();
        return realRoot;
    }

    static private int strongConnect(BasicBlock b, int index, Stack<BasicBlock> stack, Scc root) {
        b.sccIndex = index;
        b.sccLowLink = index;
        ++index;
        stack.push(b);

        // Consider successors of b
        for (BasicBlock w : b.successors) {
            if (w.sccIndex == -1) {
                // Successor w has not yet been visited; recurse on it
                index = strongConnect(w, index, stack, root);
                b.sccLowLink = Math.min(b.sccLowLink, w.sccLowLink);
            } else if (stack.contains(w)) {
                // Successor w is in stack S and hence in the current SCC
                b.sccLowLink = Math.min(b.sccLowLink, w.sccIndex);
            }
        }

        // If l is a root node, pop the stack and generate an SCC
        if (b.sccLowLink == b.sccIndex) {
            // start a new strongly connected component
            Scc newRoot = new Scc(b);
            HashSet<BasicBlock> blocks = newRoot.blocks;
            BasicBlock w;
            do {
                w = stack.pop();
                w.sccRoot = newRoot;
                blocks.add(w);
            } while (w != b);

            newRoot.next = root.next;
            root.next = newRoot;
        };
        return index;
    }

    void initializeAll() {
        computeSuccessors();
        computeTransitiveClosure();
        computeSizeInfo();
        computePredecessors();
    }

    /**
     * Fills the {@link #successors} field of all roots.
     */
    void computeSuccessors() {
        Scc r = this;
        while (r != null) {
            r.computeSuccessors1();
            r = r.next;
        }
    }

    /**
     * Fills the {@link #successors} field of <code>this</code>.
     */
    private void computeSuccessors1() {
        successors = new HashSet<Scc>();
        for (BasicBlock b : blocks) {
            for (BasicBlock s : b.successors) {
                Scc c = s.sccRoot;
                if (c != this) {
                    successors.add(c);
                }
            }
        }
    }

    /**
     * Fills the {@link #predecessors} field of all roots.
     */
    void computePredecessors() {
        Scc r = this;
        while (r != null) {
            r.computePredecessors1();
            r = r.next;
        }
    }

    /**
     * Fills the {@link #predecessors} field of <code>this</code>.
     */
    private void computePredecessors1() {
        for (Scc root : successors) {
            root.predecessors.add(this);
        }
    }
     

    /**
     * Compute all size-relevant information, i.e. set the {@link
     * #size}, and {@link * #transitiveClosureSize} fields of all SCC
     * components.
     */
    void computeSizeInfo() {
        computeSizes();
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
        transitiveClosure = new HashSet<Scc>();
        transitiveClosure.add(this);
        for (Scc root : successors) {
            root.computeTransitiveClosure();
            transitiveClosure.addAll(root.transitiveClosure);
        }
    }

    /**
     * Compute the sizes of the transitive closures in all SCC components.
     */
    void computeTransitiveClosureSizes() {
        Scc root = this;
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
        for (Scc root : transitiveClosure) {
            transitiveClosureSize += root.size;
        }
    }

    /**
     * Compute the sizes of all SCC components, and set their
     * {@link #size} fields.
     */
    void computeSizes() {
        Scc root = this;
        while (root != null) {
            root.computeSize();
            root = root.next;
        }
    }

    /**
    * Compute size of all basic blocks in this component and set
    * the {@link #size} field to it.
    *
    * @param total size of code in this method.
    */
    private void computeSize() {
        size = 0;
        for (BasicBlock b : blocks) {
            size += b.size;
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
     * This assumes that {@link BasicBlock#predecessors} and {@link
     * #predecessors} is set.
     *
     * @return the entry basic block if it is, null if it isn't or if
     * it's the root block
     */
    public BasicBlock splitPoint() {
        BasicBlock entry = null;
        /*
         * First check that there's only one entry point to this
         * component.
         */
        for (BasicBlock b : blocks) {
            for (BasicBlock p : b.predecessors) {
                if (p.sccRoot != this) {
                    if (entry == null) {
                        entry = b;
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
            for (Scc root : transitiveClosure) {
                if (root != this) {
                    for (Scc p : root.predecessors) {
                        if (!transitiveClosure.contains(p)) {
                            return null;
                        }
                    }
                }
            }
            return entry;
        }
    }

    /**
     * Find an appropriate split point that will diminish the size of
     * a closure that's too big.
     *
     * @return split method with info about the closure
     */
    public SplitMethod findSplitPoint(int maxMethodLength) {
        for (Scc s : successors) {
            SplitMethod m = s.findSplitPoint(maxMethodLength);
            if (m != null)
                return m;
        }
        // none have been split
        if (transitiveClosureSize > maxMethodLength) {
            BasicBlock entry = lookMaxSizeSplitPointSuccessor();
            if (entry != null) {
                SplitMethod m = new SplitMethod(entry);
                for (Scc root : entry.sccRoot.transitiveClosure)
                    root.splitMethod = m;
                return m;
            }
        }
        return null;
    } 

    /**
     * Look for a successor of this component that we can split out.
     *
     * @return entry point of the component if found, null if not
     */
    public BasicBlock lookMaxSizeSplitPointSuccessor() {
        int maxSize = 0;
        BasicBlock maxEntry = null;
        for (Scc s : successors) {
            BasicBlock entry = s.lookForSplitPoint();
            Scc root = entry.sccRoot;
            if (root != null) {
                if (root.transitiveClosureSize > maxSize) {
                    maxSize = root.transitiveClosureSize;
                    maxEntry = entry;
                }
            }
        }
        return maxEntry;
    }

    /**
     * Look for a split point in this component or one of its successors.
     *
     * @return entry point of the component if found, null if not
     */
    public BasicBlock lookForSplitPoint() {
        BasicBlock b = splitPoint();
        if (b == null) {
            return lookMaxSizeSplitPointSuccessor();
        } else {
            return b;
        }
    }


    public HashSet<SplitMethod> split(final int maxMethodLength) {
        // #### very provisional
        HashSet<SplitMethod> set = new HashSet<SplitMethod>();
        SplitMethod m = findSplitPoint(maxMethodLength);
        if (m == null)
            throw new RuntimeException("no split point found");
        set.add(m);
        return set;
    }



    @Override
    public String toString() {
        return "*" + first.toString() + blocks.toString();
    }
        
}
