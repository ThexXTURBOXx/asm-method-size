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
package org.objectweb.asm;

/**
 * Root of SCC graph.
 *
 * @author Mike Sperber
 */
class SccRoot {

    public SccRoot(Label first) {
        this.first = first;
    }

    /**
     * First label of root.
     */
    Label first;

    /**
     * Next root of an SCC component.
     */
    SccRoot next;

    /**
     * Successors in SCC graph, i.e. their first labels.
     */
    Edge successors;

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
        Label l = first;
        successors = null;
        while (l != null) {
            Edge e = l.successors;
            while (e != null) {
                SccRoot root = e.successor.splitInfo.sccRoot;
                Label rootFirst = root.first;
                if ((root != this) && !hasLabel(successors, rootFirst)) {
                    Edge re = new Edge();
                    re.successor = rootFirst;
                    re.next = successors;
                    successors = re;
                }
                e = e.next;
            }
            l = l.splitInfo.sccNext;
        }
        
    }

    /**
     * Does an edge contain a certain label?
     *
     * @return <code>true</code> if yes, <code>false</code> if no
     */
    private static boolean hasLabel(Edge e, Label l) {
        while (e != null) {
            if (e.successor == l)
                return true;
            e = e.next;
        }
        return false;
    }
}