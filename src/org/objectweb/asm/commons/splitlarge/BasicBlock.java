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

    int size = -1;

    /**
     * Label associated with this block upon writing.
     */
    Label outputLabel;

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

    /**
     * Frame data needed to call {@link MethodWriter#visitFrame} on this block.
     */
    FrameData frameData;

    public BasicBlock(Label l, int size) {
        this.sccIndex = -1;
        this.labels = new HashSet<Label>();
        addLabel(l);
        this.size = size;
        this.position = l.position;
        this.successors = new HashSet<BasicBlock>();
        this.predecessors = new HashSet<BasicBlock>();
        this.outputLabel = null;
    }
    

    public BasicBlock(int position) {
        this.sccIndex = -1;
        this.labels = null;
        this.position = position;
        this.successors = new HashSet<BasicBlock>();
        this.predecessors = new HashSet<BasicBlock>();
        this.outputLabel = null;
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

    public Label getOutputLabel() {
        if (outputLabel != null)
            return outputLabel;
        outputLabel = new Label();
        return outputLabel;
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
     * Add an edge from <code>this</code> to another basic block.
     */
    public void addEdge(BasicBlock other) {
        successors.add(other);
        other.predecessors.add(this);
    }

    /**
     * Compute flowgraph from code.
     */
    public static TreeSet<BasicBlock> computeFlowgraph(byte[] b, int codeStart, int codeSize) {
        TreeSet<BasicBlock> blocks = new TreeSet<BasicBlock>();
        BasicBlock[] blockArray = new BasicBlock[codeSize + 2];
        int codeEnd = codeStart + codeSize;
        // first, collect all the blocks
        {
            getBasicBlock(0, blockArray, blocks);
            int v = codeStart;
            while (v < codeEnd) {
                int w = v - codeStart;
                int opcode = b[v] & 0xFF;
                switch (ClassWriter.TYPE[opcode]) {
                case ClassWriter.NOARG_INSN:
                case ClassWriter.IMPLVAR_INSN:
                    v += 1;
                    break;
                case ClassWriter.LABEL_INSN: {
                    if (opcode == Opcodes.JSR)
                        throw new UnsupportedOperationException("JSR instruction not supported yet");
                    getBasicBlock(w + ByteArray.readShort(b, v + 1), blockArray, blocks);
                    v += 3;
                    if (opcode != Opcodes.GOTO) // the rest are conditional branches
                        getBasicBlock(v - codeStart, blockArray, blocks);
                    break;
                }
                case ClassWriter.LABELW_INSN: {
                    if (opcode == 201) // JSR_W
                        throw new UnsupportedOperationException("JSR_W instruction not supported yet");
                    getBasicBlock(w + ByteArray.readInt(b, v + 1), blockArray, blocks);
                    v += 5;
                    if (opcode != 200) // GOTO_W; the rest are conditional branches
                        getBasicBlock(v - codeStart, blockArray, blocks);
                    break;
                }
                case ClassWriter.WIDE_INSN:
                    opcode = b[v + 1] & 0xFF;
                    if (opcode == Opcodes.IINC) {
                        v += 6;
                    } else {
                        v += 4;
                    }
                    break;
                case ClassWriter.TABL_INSN: {
                    // skips 0 to 3 padding bytes*
                    v = v + 4 - (w & 3);
                    // reads instruction
                    getBasicBlock(w + ByteArray.readInt(b, v), blockArray, blocks);
                    int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                    v += 12;
                    for (; j > 0; --j) {
                        getBasicBlock(w + ByteArray.readInt(b, v), blockArray, blocks);
                        v += 4;
                    }
                    break;
                }
                case ClassWriter.LOOK_INSN: {
                    // skips 0 to 3 padding bytes*
                    v = v + 4 - (w & 3);
                    // reads instruction
                    getBasicBlock(w + ByteArray.readInt(b, v), blockArray, blocks);
                    int j = ByteArray.readInt(b, v + 4);
                    v += 8;
                    for (; j > 0; --j) {
                        getBasicBlock(w + ByteArray.readInt(b, v + 4), blockArray, blocks);
                        v += 8;
                    }
                    break;
                }
                case ClassWriter.VAR_INSN:
                    if (opcode == Opcodes.RET)
                        throw new UnsupportedOperationException("RET instruction not supported yet");
                case ClassWriter.SBYTE_INSN:
                case ClassWriter.LDC_INSN:
                    v += 2;
                    break;
                case ClassWriter.SHORT_INSN:
                case ClassWriter.LDCW_INSN:
                case ClassWriter.FIELDORMETH_INSN:
                case ClassWriter.TYPE_INSN:
                case ClassWriter.IINC_INSN:
                    v += 3;
                    break;
                case ClassWriter.ITFMETH_INSN:
                case ClassWriter.INDYMETH_INSN:
                    v += 5;
                    break;
                    // case MANA_INSN:
                default:
                    v += 4;
                    break;
                }
            }

// FIXME: This does not work: The exception handlers have not been copied into the code array yet.
//             // parse the try catch entries
//             int j = ByteArray.readUnsignedShort(b, v);
//             v += 2;
//             for (; j > 0; --j) {
//                 getBasicBlock(ByteArray.readUnsignedShort(b, v), blockArray, blocks);
//                 getBasicBlock(ByteArray.readUnsignedShort(b, v + 2), blockArray, blocks);
//                 getBasicBlock(ByteArray.readUnsignedShort(b, v + 4), blockArray, blocks);
//                 v += 8;
//             }
        }

        // now insert edges
        int v = codeStart;
        BasicBlock currentBlock = null;
        while (v < codeEnd) {
            int w = v - codeStart;
            if (blockArray[w] != null) {
                currentBlock = blockArray[w];
            }
            int opcode = b[v] & 0xFF;
            switch (ClassWriter.TYPE[opcode]) {
            case ClassWriter.NOARG_INSN:
            case ClassWriter.IMPLVAR_INSN:
                v += 1;
                break;
            case ClassWriter.LABEL_INSN:
                currentBlock.addEdge(blockArray[w + ByteArray.readShort(b, v + 1)]);
                v += 3;
                break;
            case ClassWriter.LABELW_INSN:
                currentBlock.addEdge(blockArray[w + ByteArray.readInt(b, v + 1)]);
                v += 5;
                break;
            case ClassWriter.WIDE_INSN:
                opcode = b[v + 1] & 0xFF;
                if (opcode == Opcodes.IINC) {
                    v += 6;
                } else {
                    v += 4;
                }
                break;
            case ClassWriter.TABL_INSN: {
                // skips 0 to 3 padding bytes*
                v = v + 4 - (w & 3);
                // reads instruction
                currentBlock.addEdge(blockArray[w + ByteArray.readInt(b, v)]);
                int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                v += 12;
                for (; j > 0; --j) {
                    currentBlock.addEdge(blockArray[w + ByteArray.readInt(b, v)]);
                    v += 4;
                }
                break;
            }
            case ClassWriter.LOOK_INSN: {
                // skips 0 to 3 padding bytes*
                v = v + 4 - (w & 3);
                // reads instruction
                int j = ByteArray.readInt(b, v + 4);
                v += 8;
                for (; j > 0; --j) {
                    currentBlock.addEdge(blockArray[w + ByteArray.readInt(b, v + 4)]);
                    v += 8;
                }
                break;
            }
            case ClassWriter.VAR_INSN:
            case ClassWriter.SBYTE_INSN:
            case ClassWriter.LDC_INSN:
                v += 2;
                break;
            case ClassWriter.SHORT_INSN:
            case ClassWriter.LDCW_INSN:
            case ClassWriter.FIELDORMETH_INSN:
            case ClassWriter.TYPE_INSN:
            case ClassWriter.IINC_INSN:
                v += 3;
                break;
            case ClassWriter.ITFMETH_INSN:
            case ClassWriter.INDYMETH_INSN:
                v += 5;
                break;
                // case MANA_INSN:
            default:
                v += 4;
                break;
            }
            if ((opcode != Opcodes.GOTO) && (opcode != 200) // GOTO_W
                && (opcode != Opcodes.TABLESWITCH)
                && (opcode != Opcodes.LOOKUPSWITCH)) {
                BasicBlock next = blockArray[v - codeStart];
                if (next != null) {
                    currentBlock.addEdge(next);
                }
            }
        }
// FIXME: This does not work: The exception handlers have not been copied into the code array yet.
//         // parses the try catch entries
//         int j = ByteArray.readUnsignedShort(b, v);
//         v += 2;
//         for (; j > 0; --j) {
//             BasicBlock start = blockArray[ByteArray.readUnsignedShort(b, v)];
//             BasicBlock end = blockArray[ByteArray.readUnsignedShort(b, v + 2)];
//             BasicBlock handler = blockArray[ByteArray.readUnsignedShort(b, v + 4)];
//             for (BasicBlock src : blocks.subSet(start, end))
//                 src.addEdge(handler);
//             v += 8;
//         }
        
        BasicBlock previous = null;
        for (BasicBlock block : blocks) {
            if (previous != null) {
                previous.size = block.position - previous.position;
            }
            previous = block;
        }
        BasicBlock last = blocks.last();
        last.size = codeEnd - last.position;
        
        return blocks;
    }
    
    private static BasicBlock getBasicBlock(int offset, BasicBlock[] array, TreeSet<BasicBlock> blocks) {
        BasicBlock block = array[offset];
        if (block == null) {
            block = new BasicBlock(offset);
            array[offset] = block;
            blocks.add(block);
        }
        return block;
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
                    b.addEdge(get(s.successor));
                    s = s.next;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "@" + position;
    }

}


