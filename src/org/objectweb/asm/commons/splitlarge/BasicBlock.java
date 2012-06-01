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

/**
 * Basic block in flowgraph.
 *
 * @author Mike Sperber
 */
class BasicBlock implements Comparable<BasicBlock> {

    final int position;

    int size = -1;

    /**
     * Label of start of basic block in target method.
     */
    Label startLabel;

    /**
     * Label of end of basic block in target method.
     */
    Label endLabel;

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
     * Block that follows this one in the code, or null if this is the last one.
     */
    BasicBlock subsequent;

    /**
     * Successors in flowgraph.
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

    public enum Kind {
        EXCEPTION_HANDLER, REGULAR
    };

    Kind kind;

    public BasicBlock(int position) {
        this.sccIndex = -1;
        this.position = position;
        this.successors = new HashSet<BasicBlock>();
        this.predecessors = new HashSet<BasicBlock>();
        this.startLabel = null;
        this.kind = Kind.REGULAR;
    }

    public int compareTo(BasicBlock other) {
        Integer Position = position;
        return Position.compareTo(other.position);
    }

    public Label getStartLabel() {
        if (startLabel != null)
            return startLabel;
        startLabel = new Label();
        return startLabel;
    }

    public Label getEndLabel() {
        if (endLabel != null)
            return endLabel;
        endLabel = new Label();
        return endLabel;
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
    public static TreeSet<BasicBlock> computeFlowgraph(ByteVector code, Handler firstHandler, Label[] largeBranchTargets,
                                                       int maxStack, int maxLocals, int maxBlockSize) {
        byte[] b = code.data;
        TreeSet<BasicBlock> blocks = new TreeSet<BasicBlock>();
        BasicBlock[] blockArray = new BasicBlock[code.length + 2];
        final int exitOverhead = visitInvocationMaxSize(maxStack, maxLocals);
        final int stackOverhead = reconstructStackMaxSize(maxStack, maxLocals);
        // first, collect all the blocks
        {
            getBasicBlock(0, blockArray, blocks);
            int v = 0;
            int previousV = v;
            int s = 0; // block size
            int previousS = s;
            while (v < code.length) {
                int exitCount = 1;
                int opcode = b[v] & 0xFF;
                switch (ClassWriter.TYPE[opcode]) {
                case ClassWriter.NOARG_INSN:
                case ClassWriter.IMPLVAR_INSN:
                    v += 1;
                    s += 1;
                    break;
                case ClassWriter.LABEL_INSN: {
                    if (opcode == Opcodes.JSR)
                        throw new UnsupportedOperationException("JSR instruction not supported yet");
                    int label;
                    /*
                     * converts temporary opcodes 202 to 217, 218 and
                     * 219 to IFEQ ... JSR (inclusive), IFNULL and
                     * IFNONNULL
                     */
                    if (opcode > 201) {
                        opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                        Label l = largeBranchTargets[v + 1];
                        if (l != null) {
                            label = l.position;
                        } else {
                            label = v + ByteArray.readUnsignedShort(b, v + 1);
                        }
                    } else {
                        label = v + ByteArray.readShort(b, v + 1);
                    }
                    getBasicBlock(label, blockArray, blocks);
                    v += 3;
                    s += 8;
                    if (opcode != Opcodes.GOTO) {  // the rest are conditional branches
                        getBasicBlock(v, blockArray, blocks);
                        ++exitCount;
                    }
                    break;
                }
                case ClassWriter.LABELW_INSN: {
                    if (opcode == 201) // JSR_W
                        throw new UnsupportedOperationException("JSR_W instruction not supported yet");
                    getBasicBlock(v + ByteArray.readInt(b, v + 1), blockArray, blocks);
                    v += 5;
                    s += 8;
                    if (opcode != 200) { // GOTO_W; the rest are conditional branches
                        getBasicBlock(v, blockArray, blocks);
                        ++exitCount;
                    }
                    break;
                }
                case ClassWriter.WIDE_INSN:
                    opcode = b[v + 1] & 0xFF;
                    if (opcode == Opcodes.IINC) {
                        v += 6;
                        s += 6;
                    } else {
                        v += 4;
                        s += 4;
                    }
                    break;
                case ClassWriter.TABL_INSN: {
                    // skips 0 to 3 padding bytes*
                    v = v + 4 - (v & 3);
                    s += 3;
                    getBasicBlock(v + ByteArray.readInt(b, v), blockArray, blocks);
                    int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                    exitCount = j;
                    v += 12;
                    s += 12;
                    for (; j > 0; --j) {
                        getBasicBlock(v + ByteArray.readInt(b, v), blockArray, blocks);
                        v += 4;
                        s += 4;
                    }
                    getBasicBlock(v, blockArray, blocks);
                    break;
                }
                case ClassWriter.LOOK_INSN: {
                    // skips 0 to 3 padding bytes*
                    v = v + 4 - (v & 3);
                    s += 3;
                    getBasicBlock(v + ByteArray.readInt(b, v), blockArray, blocks);
                    int j = ByteArray.readInt(b, v + 4);
                    exitCount = j;
                    v += 8;
                    s += 8;
                    for (; j > 0; --j) {
                        getBasicBlock(v + ByteArray.readInt(b, v + 4), blockArray, blocks);
                        v += 8;
                        s += 8;
                    }
                    getBasicBlock(v, blockArray, blocks);
                    break;
                }
                case ClassWriter.VAR_INSN:
                    if (opcode == Opcodes.RET)
                        throw new UnsupportedOperationException("RET instruction not supported yet");
                case ClassWriter.SBYTE_INSN:
                case ClassWriter.LDC_INSN:
                    v += 2;
                    s += 2;
                    break;
                case ClassWriter.SHORT_INSN:
                case ClassWriter.LDCW_INSN:
                case ClassWriter.FIELDORMETH_INSN:
                case ClassWriter.TYPE_INSN:
                case ClassWriter.IINC_INSN:
                    v += 3;
                    s += 3;
                    break;
                case ClassWriter.ITFMETH_INSN:
                case ClassWriter.INDYMETH_INSN:
                    v += 5;
                    s += 5;
                    break;
                    // case MANA_INSN:
                default:
                    v += 4;
                    s += 4;
                    break;
                }
                if (s + (exitCount * exitOverhead) + stackOverhead > maxBlockSize) {
                    getBasicBlock(previousV, blockArray, blocks);
                    s = (s - previousS);
                }
                if (blockArray[v] != null) {
                    s = exitOverhead;
                }
                previousV = v;
                previousS = s;
            }
        }

        {
            /*
             * The label positions should be OK; if
             * MethodWriter.resizeInstructions() has run, it has
             * relocated.
             */
            Handler h = firstHandler;
            while (h != null) {
                getBasicBlock(h.start.position, blockArray, blocks); // start
                getBasicBlock(h.end.position, blockArray, blocks); // end
                BasicBlock handler = getBasicBlock(h.handler.position, blockArray, blocks);
                handler.kind = Kind.EXCEPTION_HANDLER;
                h = h.next;
            }
        }

        // now insert edges
        int v = 0;
        BasicBlock currentBlock = null;
        while (v < code.length) {
            if (blockArray[v] != null) {
                currentBlock = blockArray[v];
            }
            int opcode = b[v] & 0xFF;
            switch (ClassWriter.TYPE[opcode]) {
            case ClassWriter.NOARG_INSN:
            case ClassWriter.IMPLVAR_INSN:
                v += 1;
                break;
            case ClassWriter.LABEL_INSN: {
                int label;
                if (opcode > 201) {
                    opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                    Label l = largeBranchTargets[v + 1];
                    if (l != null) {
                        label = l.position;
                    } else {
                        label = v + ByteArray.readUnsignedShort(b, v + 1);
                    }
                } else {
                    label = v + ByteArray.readShort(b, v + 1);
                }
                currentBlock.addEdge(blockArray[label]);
                v += 3;
                break;
            }
            case ClassWriter.LABELW_INSN:
                currentBlock.addEdge(blockArray[v + ByteArray.readInt(b, v + 1)]);
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
                v = v + 4 - (v & 3);
                // reads instruction
                currentBlock.addEdge(blockArray[v + ByteArray.readInt(b, v)]);
                int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                v += 12;
                for (; j > 0; --j) {
                    currentBlock.addEdge(blockArray[v + ByteArray.readInt(b, v)]);
                    v += 4;
                }
                break;
            }
            case ClassWriter.LOOK_INSN: {
                // skips 0 to 3 padding bytes*
                v = v + 4 - (v & 3);
                // reads instruction
                int j = ByteArray.readInt(b, v + 4);
                v += 8;
                for (; j > 0; --j) {
                    currentBlock.addEdge(blockArray[v + ByteArray.readInt(b, v + 4)]);
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
                && (opcode != Opcodes.LOOKUPSWITCH)
                && !((opcode >= Opcodes.IRETURN) && (opcode <= Opcodes.RETURN))) {
                BasicBlock next = blockArray[v];
                if (next != null) {
                    currentBlock.addEdge(next);
                }
            }
        }

        /*
         * Add edges for the exception handlers.
         */
        {
            Handler h = firstHandler;
            while (h != null) {
                BasicBlock start = blockArray[h.start.position];
                BasicBlock end = blockArray[h.end.position];
                BasicBlock handler = blockArray[h.handler.position];
                for (BasicBlock src : blocks.subSet(start, end)) {
                    src.addEdge(handler);
                }
                h = h.next;
            }
        }
        
        BasicBlock previous = null;
        for (BasicBlock block : blocks) {
            if (previous != null) {
                previous.subsequent = block;
            }
            previous = block;
        }
        blocks.last().subsequent = null;

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
     * This needs, for all basic blocks, the {@link #sccRoot} field to
     * be set, and the {@link Scc#splitPoint} fields of that to be
     * set.  Also, we need the {@link #frameData} to be set.
     */

    public void computeSize(ByteVector code) {
        int end = (subsequent != null) ? subsequent.position : code.length;
        int size = end - position;
        int u = position;
        byte[] b = code.data;
        while (u < end) {
            int opcode = b[u] & 0xFF; // opcode of current instruction

            switch (ClassWriter.TYPE[opcode]) {
            case ClassWriter.NOARG_INSN:
            case ClassWriter.IMPLVAR_INSN:
                u += 1;
                break;
            case ClassWriter.LABEL_INSN:
                u += 3;
                // five additional bytes will be required to
                // replace this IFxxx <l> instruction with
                // IFNOTxxx <l'> GOTO_W <l>, where IFNOTxxx
                // is the "opposite" opcode of IFxxx (i.e.,
                // IFNE for IFEQ) and where <l'> designates
                // the instruction just after the GOTO_W.
                size += 5;
                break;
            case ClassWriter.LABELW_INSN:
                u += 5;
                break;
            case ClassWriter.TABL_INSN:
                // skips instruction
                size += 3; // very coarse
                u = u & ~3;
                u += 12 + 4 * (ByteArray.readInt(b, u + 8) - ByteArray.readInt(b, u + 4) + 1);
                break;
            case ClassWriter.LOOK_INSN:
                size += 3;
                u = u & ~3;
                u += 8 + u + (ByteArray.readInt(b, u + 4) * 8);
                break;
            case ClassWriter.WIDE_INSN:
                opcode = b[u + 1] & 0xFF;
                if (opcode == Opcodes.IINC) {
                    u += 6;
                } else {
                    u += 4;
                }
                break;
            case ClassWriter.VAR_INSN:
            case ClassWriter.SBYTE_INSN:
            case ClassWriter.LDC_INSN:
                u += 2;
                break;
            case ClassWriter.SHORT_INSN:
            case ClassWriter.LDCW_INSN:
            case ClassWriter.FIELDORMETH_INSN:
            case ClassWriter.TYPE_INSN:
            case ClassWriter.IINC_INSN:
                u += 3;
                break;
            case ClassWriter.ITFMETH_INSN:
            case ClassWriter.INDYMETH_INSN:
                u += 5;
                break;
                // case ClassWriter.MANA_INSN:
            default:
                u += 4;
                break;
            }
        }

        if (sccRoot.splitPoint == this) {
            // compute what it takes to restore this frame
            size += reconstructStackSize();
        }
        for (BasicBlock s : successors) {
            if (s.sccRoot.splitPoint == s) {
                size += s.visitInvocationSize();
            }
        }
        this.size = size;
    }

    public static void computeSizes(ByteVector code, TreeSet<BasicBlock> blocks) {
        for (BasicBlock b : blocks) {
            b.computeSize(code);
        }
    }

    /**
     * Calculcate the size of the code needed to invoke this basic
     * block as the entry point of a split method.
     */
    public int visitInvocationSize() {
        return frameData.visitPushFrameArgumentsSize() 
            + 3 // INVOKESTATIC or INVOKEVIRTUAL
            + 1; // RETURN
    }

    public static int visitInvocationMaxSize(int maxStack, int maxLocals) {
        return FrameData.visitPushFrameArgumentsMaxSize(maxStack, maxLocals)
            + 3 // INVOKESTATIC or INVOKEVIRTUAL
            + 1; // RETURN
    }

    /**
     * Calculate code size needed to reconstruct the stack from the parameters.
     */
    public int reconstructStackSize() {
        return frameData.reconstructStackSize();
    }

    /**
     * Calculate maximum code size needed to reconstruct the stack from the parameters.
     */
    public static int reconstructStackMaxSize(int maxStack, int maxLocals) {
        return FrameData.reconstructStackMaxSize(maxStack, maxLocals);
    }

    @Override
    public String toString() {
        return "@" + position;
    }

}


