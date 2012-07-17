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

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.Collection;

/**
 * Computing cycle equivalence according to
 * Johnson, Pearson, and Pingali,
 * The program structure tree: Computing control regions in linear time,
 * PLDI 1994
 * http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.31.5126
 * http://dl.acm.org/citation.cfm?doid=178243.178258
 */
public class CycleEquivalence {

    public static class EquivClass {
        /**
         * Topmost bracket; identifies the class.
         * May be <code>null</code> for edges with no brackets.
         */
        Edge bracket;

        /**
         * Size of the the bracket list.
         */
        int size;

        Collection<Edge> edges;
        Collection<Node> nodes; // FIXME: probably not needed

        public EquivClass(Edge bracket, int size) {
            this.bracket = bracket;
            this.size = size;
            this.edges = new ArrayList<Edge>();
            this.nodes = new ArrayList<Node>();
        }

        public EquivClass() {
            this(null, 0);
        }

        public EquivClass(DList<Edge> bracketList) {
            this(bracketList.getFirst(), bracketList.size());
        }

        /**
         * Does not check if the edge is already included.
         */
        public void addEdge(Edge edge) {
            this.edges.add(edge);
            if (edge.represented != null) {
                this.nodes.add(edge.represented);
            }
        }
        
        @Override public String toString() {
            if (this.bracket == null) {
                return "<>";
            } else {
                return "<" + this.bracket.toStringBase() + ", " + this.size + ">";
            }
        }
    }

    public static class Edge {
        EquivClass equivClass;
        int recentSize;
        EquivClass recentEquivClass;
        DList<Edge>.Node node;
        /**
         * In node represented by this edge, if any.
         */
        Node represented;
        /**
         * Says whether this edge was seen in a traversal.
         */
        boolean seen;

        final Node node1, node2;

        public Edge(Node node1, Node node2) {
            this.node1 = node1;
            this.node2 = node2;
            this.seen = false;
            this.represented = null;
        }

        public Node getOtherNode(Node node) {
            if (node == node1) {
                return this.node2;
            } else if (node == node2) {
                return this.node1;
            } else {
                throw new AssertionError("This node isn't at this edge.");
            }
        }

        public String toStringBase() {
            return "(" + this.node1.toString() + " <-> " + this.node2.toString() + ")";
        }

        @Override public String toString() {
            String base = toStringBase();
            if (this.equivClass == null) {
                return base;
            } else {
                return base + this.equivClass.toString();
            }
        }
    }

    public static class Node {
        /**
         * Depth-first search number of this block, i.e. the index in
         * an ordering of the nodes according to a depth-first
         * traversal.
         *
         * The value is -1 if uninitialized.
         */
        int dfsNum = -1;
        
        /**
         * Representative edge for this node if we're computing
         * node-cycle equivalence.  It's set identically in both the
         * in and the out node.
         */
        Edge representativeEdge;

        /**
         * List of brackets of this block.
         */
        final DList<Edge> bracketList;
        
        /**
         * Destination block closest to root of any edge originating
         * from a descendant of node n.
         */
        Node hi;
        
        /**
         * Depth-fist spanning tree.
         */
        Edge parent;
        /**
         * Tree edges from this node to descendants.
         */
        final List<Edge> treeEdges;
        /**
         * Backedge from this node to another.
         */
        final List<Edge> backEdgesFrom;
        /**
         * Backedge from another node to this one.
         */
        final List<Edge> backEdgesTo;

        /**
         * Capping edges to this one.
         */
        final List<Edge> cappingEdges;

        final List<Edge> allEdges;

        /**
         * <code>null</code> if this is an artifical node
         */
        BasicBlock block;

        /**
         * Note that the list of edges isn't copied.
         */
         
        public Node(BasicBlock block) {
            this.block = block;
            this.representativeEdge = null;
            this.allEdges = new ArrayList<Edge>();
            this.treeEdges = new ArrayList<Edge>();
            this.backEdgesFrom = new ArrayList<Edge>();
            this.backEdgesTo = new ArrayList<Edge>();
            this.cappingEdges = new ArrayList<Edge>();
            this.bracketList = new DList<Edge>();
        }

        public Node() {
            this(null);
        }

        public Edge addEdge(Node other) {
            Edge edge = new Edge(this, other);
            allEdges.add(edge);
            other.allEdges.add(edge);
            return edge;
        }

        public void addEdge(Edge edge) {
            allEdges.add(edge);
        }

        @Override public String toString() {
            String b = "[" + this.dfsNum + "]";
            if (block == null) {
                return b;
            } else {
                return b + block.toString();
            }
        }

        public void computeSpanningTree(List<Node> nodes) {
            assert this.dfsNum == -1;
            this.dfsNum = nodes.size();
            nodes.add(this);
            // the dfsNum field serves as a "seen mark"
            for (Edge edge : this.allEdges) {
                if (edge != this.parent) {
                    // not the parent edge
                    Node other = edge.getOtherNode(this);
                    if (other.dfsNum == -1) {
                        // not seen other
                        this.treeEdges.add(edge);
                        other.parent = edge;
                        other.computeSpanningTree(nodes);
                    } else if (other.dfsNum < this.dfsNum) {
                        this.backEdgesFrom.add(edge);
                        other.backEdgesTo.add(edge);
                    }
                }
            }
        }

        public void computeCycleEquivalence(EquivClass boring) {
            // hi0 := min { t.dfsnum | (n, t) is a backedge } ;
            Node hi0 = null;
            for (Edge edge : this.backEdgesFrom) {
                Node other = edge.getOtherNode(this);
                if ((hi0 == null) || (hi0.dfsNum > other.dfsNum)) {
                    hi0 = other;
                }
            }
            // hi1 := min { c.hi | c is a child of n } ;
            // hichild := any child c of n having c.hi = hi1 ;
            Node hi1 = null;
            Node hiChild = null;
            for (Edge edge : this.treeEdges) {
                Node other = edge.getOtherNode(this);
                if ((hi1 == null) || (hi1.dfsNum > other.hi.dfsNum)) {
                    hiChild = other;
                    hi1 = other.hi;
                }
            }
            // n.hi := min { hi0, hi1 } ;
            if (hi0 == null) {
                this.hi = hi1;
            } else if (hi1 == null) {
                this.hi = hi0;
            } else {
                this.hi = (hi0.dfsNum < hi1.dfsNum) ? hi0 : hi1;
            }

            // hi2 := min { c.hi | c is a child of n other than hichild }
            Node hi2 = null;
            for (Edge edge : this.treeEdges) {
                Node other = edge.getOtherNode(this);
                if (other != hiChild) {
                    if ((hi2 == null) || (hi2.dfsNum > other.hi.dfsNum)) {
                        hi2 = other.hi;
                    }
                }
            }
        
            /* compute bracketlist */
            DList<Edge> blist = this.bracketList;
            // n.blist := create ()
            // for each child c of n do
            for (Edge edge : this.treeEdges) {
                // n.blist := concat (c.blist, n.blist)
                blist.appendDestroying(edge.getOtherNode(this).bracketList);
            }
            // for each capping backedge d from a descendant of n to n do
            for (Edge edge : this.cappingEdges) {
                // delete (n.blist, d) ;
                blist.delete(edge.node);
            }
            // for each backedge b from a descendant of n to n do
            for (Edge edge : this.backEdgesTo) {
                // delete (n.blist, b) ;
                blist.delete(edge.node);
                // if b.class undefined then
                if (edge.equivClass == null) {
                    // b.class := new-class () ;
                    edge.equivClass = new  EquivClass(blist);
                }
            }
            // for each backedge e from n to an ancestor of n do
            for (Edge edge : this.backEdgesFrom) {
                // push (n.blist, e) ;
                DList<Edge>.Node dnode = blist.prepend(edge);
                edge.node = dnode;
            }
            // if hi2 < hi0 then
            if ((hi2 != null) &&
                ((hi0 == null) || hi0.dfsNum > hi2.dfsNum)) {
                /* create capping backedge */
                // d := (n, node[hi2]) ;
                Edge edge = new Edge(this, hi2);
                hi2.cappingEdges.add(edge);
                // push (n.blist, d) ;
                DList<Edge>.Node dnode = blist.prepend(edge);
                edge.node = dnode;
            }

            /* determine class for edge from parent(n) to n */
            Edge parent = this.parent;
            // if n is not the root of dfstree then
            if (parent != null) {
                if (blist.size() == 0) {
                    parent.equivClass = boring;
                } else {
                    // let e be the tree edge from parent(n) to n :
                    // b := top(n.blist) ;
                    Edge edge = blist.getFirst();
                    // if b.recentSize != size(n.blist) then
                    int bsize = blist.size();
                    if (edge.recentSize != bsize) {
                        // b.recentSize := size(n.bracketList) ;
                        edge.recentSize = bsize;
                        // b.recentEquivClass := new-class() ;
                        edge.recentEquivClass = new EquivClass(blist);
                    }
                    // e.class := b.recentEquivClass ;
                    parent.equivClass = edge.recentEquivClass;
                    
                    /* check for e, b equivalence */
                    // if b.recentSize = 1 then
                    if (edge.recentSize == 1) {
                        // b.class := e.class ;
                        edge.equivClass = parent.equivClass;
                    }
                }
            }
        }
    }

    /**
     * Compute the undirected, expanded flowgraph from a set of basic
     * blocks.
     * 
     * @return starting node
     */
    public static Node computeExpandedUndigraph(SortedSet<BasicBlock> blocks, Collection<Edge> terminalEdges) {
        // FIXME: stick the nodes in a field of BasicBlock
        HashMap<BasicBlock, Node> blockNodesIn = new HashMap<BasicBlock, Node>(blocks.size());
        ArrayList<Node> blockNodesOut = new ArrayList<Node>(blocks.size());
        // add nodes
        for (BasicBlock block : blocks) {
            Node in = new Node(block);
            Node out = new Node(block);
            blockNodesIn.put(block, in);
            blockNodesOut.add(out);
            Edge rep = in.addEdge(out);
            rep.represented = in;
            in.representativeEdge = rep;
            out.representativeEdge = rep;
        }

        // create artifical nodes
        BasicBlock first = blocks.first();
        Node start = new Node();
        Node firstNode = blockNodesIn.get(first);
        start.addEdge(firstNode);
        
        Node end = new Node();
        end.addEdge(start);

        // add edges
        for (Node out : blockNodesOut) {
            BasicBlock block = out.block;
            for (BasicBlock b : block.successors) {
                Node other = blockNodesIn.get(b);
                out.addEdge(other);
            }
            if (block.successors.isEmpty()) { // leaf node
                out.addEdge(end);
                if (terminalEdges != null) {
                    terminalEdges.add(out.representativeEdge);
                }
            }
        }
        return start;
    }

    public static Node computeExpandedUndigraph(SortedSet<BasicBlock> blocks) {
        return computeExpandedUndigraph(blocks, null);
    }

    public static Node computeSimpleUndigraph(SortedSet<BasicBlock> blocks, Collection<Edge> terminalEdges) {
        // FIXME: stick the nodes in a field of BasicBlock
        HashMap<BasicBlock, Node> blockNodes = new HashMap<BasicBlock, Node>(blocks.size());
        // add nodes
        for (BasicBlock block : blocks) {
            Node n = new Node(block);
            blockNodes.put(block, n);
        }

        // create artifical nodes
        BasicBlock first = blocks.first();
        Node start = new Node();
        Node firstNode = blockNodes.get(first);
        start.addEdge(firstNode);
        
        Node end = new Node();
        // FIXME? end.addEdge(start);

        // add edges
        for (Map.Entry<BasicBlock, Node> entry : blockNodes.entrySet()) {
            BasicBlock block = entry.getKey();
            Node n = entry.getValue();
            for (BasicBlock b : block.successors) {
                Node other = blockNodes.get(b);
                n.addEdge(other);
            }
            if (block.successors.isEmpty()) { // leaf node
                Edge terminal = n.addEdge(end);
                if (terminalEdges != null) {
                    terminalEdges.add(terminal);
                }
            }
        }
        return start;
    }


    public static Node computeSimpleUndigraph(SortedSet<BasicBlock> blocks) {
        return computeSimpleUndigraph(blocks, null);
    }

    public static void computeCycleEquivalence(ArrayList<Node> nodes) {
        int i = nodes.size() - 1;
        EquivClass boring = new EquivClass();
        while (i >= 0) {
            nodes.get(i).computeCycleEquivalence(boring);
            --i;
        }
        for (Node node : nodes) {
            for (Edge edge : node.treeEdges) {
                if (!edge.seen) {
                    edge.equivClass.addEdge(edge);
                    edge.seen = true;
                }
            }
            for (Edge edge : node.backEdgesFrom) {
                if (!edge.seen) {
                    edge.equivClass.addEdge(edge);
                    edge.seen = true;
                }
            }
        }
    }

    public static ArrayList<Node> compute(Node start) {
        ArrayList<Node> nodes = new ArrayList<Node>();
        start.computeSpanningTree(nodes);
        computeCycleEquivalence(nodes);
        return nodes;
    }

    public static void addEquivalentBlocks(EquivClass equiv, Collection<BasicBlock> blocks) {
        for (Edge e : equiv.edges) {
            Node n = e.represented;
            if ((n != null) && (n.block != null)) {
                blocks.add(n.block);
            }
        }
    }

    
}