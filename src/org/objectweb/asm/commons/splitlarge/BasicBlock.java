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
import java.util.HashMap;
import java.util.TreeSet;
import java.util.Arrays;

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

    public static void parseStackMap(ByteVector stackMap,
                                     ConstantPool constantPool,
                                     int frameCount,
                                     int maxLocals, int frameLocalCount, Object[] frameLocal, int maxStack,
                                     Label[] labelsByOffset,
                                     FrameData[] frameDataByOffset) {
        int frameStackCount = 0;
        Object[] frameStack = new Object[maxStack];

        frameDataByOffset[0] = new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);

        /*
         * for the first explicit frame the offset is not
         * offset_delta + 1 but only offset_delta; setting the
         * implicit frame offset to -1 allow the use of the
         * "offset_delta + 1" rule in all cases
         */
        int frameOffset = -1;
        int v = 0;
        byte[] b = (stackMap != null) ? stackMap.data : new byte[0];
        int count = 0;
        while (count < frameCount) {
            int tag = b[v++] & 0xFF;
            int delta;
            if (tag < MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME) {
                delta = tag;
            } else if (tag < MethodWriter.RESERVED) {
                delta = tag - MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME;
                v = readFrameType(stackMap, constantPool, labelsByOffset, frameStack, 0, v);
                frameStackCount = 1;

            } else {
                delta = ByteArray.readUnsignedShort(b, v);
                v += 2;
                if (tag == MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
                    v = readFrameType(stackMap, constantPool, labelsByOffset, frameStack, 0, v);
                    frameStackCount = 1;
                } else if (tag >= MethodWriter.CHOP_FRAME
                           && tag < MethodWriter.SAME_FRAME_EXTENDED) {
                    frameLocalCount -= MethodWriter.SAME_FRAME_EXTENDED - tag;
                    frameStackCount = 0;
                } else if (tag == MethodWriter.SAME_FRAME_EXTENDED) {
                    frameStackCount = 0;
                } else if (tag < MethodWriter.FULL_FRAME) {
                    int j = frameLocalCount;
                    for (int k = tag - MethodWriter.SAME_FRAME_EXTENDED; k > 0; k--) {
                        v = readFrameType(stackMap, constantPool, labelsByOffset, frameLocal, j++, v);
                    }
                    frameLocalCount += tag - MethodWriter.SAME_FRAME_EXTENDED;
                    frameStackCount = 0;
                } else { // if (tag == FULL_FRAME) {
                    {
                        int n = frameLocalCount = ByteArray.readUnsignedShort(b, v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(stackMap, constantPool, labelsByOffset, frameLocal, j++, v);
                        }
                    }
                    {
                        int n = frameStackCount = ByteArray.readUnsignedShort(b, v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(stackMap, constantPool, labelsByOffset, frameStack, j++, v);
                        }
                    }
                }
            }
            frameOffset += delta + 1;

            frameDataByOffset[frameOffset] = new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);

            ++count;
        }
    }

    private static int readFrameType(ByteVector stackMap, 
                                     ConstantPool constantPool,
                                     Label[] labelsByOffset,
                                     final Object[] frame,
                                     final int index,
                                     int v) {
        byte[] b = stackMap.data;
        int type = b[v++] & 0xFF;
        switch (type) {
        case 0:
            frame[index] = Opcodes.TOP;
            break;
        case 1:
            frame[index] = Opcodes.INTEGER;
            break;
        case 2:
            frame[index] = Opcodes.FLOAT;
            break;
        case 3:
            frame[index] = Opcodes.DOUBLE;
            break;
        case 4:
            frame[index] = Opcodes.LONG;
            break;
        case 5:
            frame[index] = Opcodes.NULL;
            break;
        case 6:
            frame[index] = Opcodes.UNINITIALIZED_THIS;
            break;
        case 7: // Object
            frame[index] = constantPool.readClass(ByteArray.readUnsignedShort(b, v));
            v += 2;
            break;
        default: { // Uninitialized
            int offset = ByteArray.readUnsignedShort(b, v);
            Label label = getLabelAt(labelsByOffset, offset);
            frame[index] = label;
            v += 2;
        }
        }
        return v;
    }

    private static Label getLabelAt(Label[] labelsByOffset, int offset) {
        Label l = labelsByOffset[offset];
        if (l == null) {
            l = new Label();
            labelsByOffset[offset] = l;
        }
        return l;
    }

    private static int pushDesc(final Object[] frame, int frameCount, final String desc) {
        // FIXME: too much overlap with parseStackMap
        int index = desc.charAt(0) == '(' ? desc.indexOf(')') + 1 : 0;
        switch (desc.charAt(index)) {
        case 'V':
            break;
        case 'Z':
        case 'C':
        case 'B':
        case 'S':
        case 'I':
            frame[frameCount++] = Opcodes.INTEGER;
            break;
        case 'F':
            frame[frameCount++] = Opcodes.FLOAT;
            break;
        case 'J':
            frame[frameCount++] = Opcodes.LONG;
            frame[frameCount++] = Opcodes.TOP;
            break;
        case 'D':
            frame[frameCount++] = Opcodes.DOUBLE;
            frame[frameCount++] = Opcodes.TOP;
            break;
        case '[':
            if (index == 0) {
                frame[frameCount++] = desc;
            } else {
                frame[frameCount++] = desc.substring(index, desc.length());
            }
            break;
        case 'L':
            if (index == 0) {
                frame[frameCount++] = desc.substring(1, desc.length() - 1);
            } else {
                frame[frameCount++] = desc.substring(index + 1, desc.length() - 1);
            }
            break;
        default:
            throw new RuntimeException("unexpected descriptor");
        }
        return frameCount;
    }

    private static int popDesc(int frameCount, final String desc) {
        char c = desc.charAt(0);
        if (c == '(') {
            int n = 0;
            Type[] types = Type.getArgumentTypes(desc);
            for (int i = 0; i < types.length; ++i) {
                n += types[i].getSize();
            }
            return frameCount - n;
        } else if (c == 'J' || c == 'D') {
            return frameCount - 2;
        } else {
            return frameCount - 1;
        }
    }


    /**
     * If there's a two-word value at an index, invalidate it, as we
     * just overwrote the second word.
     */
    private static void invalidateTwoWordLocal(Object frameLocal[], int n) {
        if (n >= 0) {
            Object t = frameLocal[n];
            if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
                frameLocal[n] = Opcodes.TOP;
            }
        }
    }

    /**
     * Compute flowgraph from code.
     */
    public static void computeFlowgraph(ByteVector code, Handler firstHandler, Label[] largeBranchTargets,
                                        ConstantPool constantPool, String className,
                                        int maxStack, int maxLocals, 
                                        FrameData[] frameDataByOffset,
                                        int maxBlockSize,
                                        TreeSet<BasicBlock> blocks,                     
                                        BasicBlock[] blocksByOffset,
                                        Label[] labelsByOffset,
                                        // map labels of NEW instructions to their types
                                        HashMap<Label, String> labelTypes) {
        byte[] b = code.data;
        // upper bound for size of each instruction in generated code
        int[] sizes = new int[code.length];
        // first, collect all the blocks
        {
            getBasicBlock(0, blocksByOffset, blocks);
            int v = 0;
            while (v < code.length) {
                int start = v;
                int opcode = b[v] & 0xFF;
                if (opcode > 201) {
                    opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                }
                switch (ClassWriter.TYPE[opcode]) {
                case ClassWriter.NOARG_INSN:
                case ClassWriter.IMPLVAR_INSN:
                    v += 1;
                    sizes[start] = 1;
                    break;
                case ClassWriter.LABEL_INSN: {
                    if (opcode == Opcodes.JSR)
                        throw new UnsupportedOperationException("JSR instruction not supported yet");
                    int label;
                    Label l = largeBranchTargets[v + 1];
                    if (l != null) {
                        label = l.position;
                    } else {
                        label = v + ByteArray.readShort(b, v + 1);
                    }
                    getBasicBlock(label, blocksByOffset, blocks);
                    v += 3;
                    sizes[start] = 8;
                    if (opcode != Opcodes.GOTO) {  // the rest are conditional branches
                        getBasicBlock(v, blocksByOffset, blocks);
                    }
                    break;
                }
                case ClassWriter.LABELW_INSN: {
                    if (opcode == 201) // JSR_W
                        throw new UnsupportedOperationException("JSR_W instruction not supported yet");
                    getBasicBlock(v + ByteArray.readInt(b, v + 1), blocksByOffset, blocks);
                    v += 5;
                    sizes[start] = 5;
                    if (opcode != 200) { // GOTO_W; the rest are conditional branches
                        getBasicBlock(v, blocksByOffset, blocks);
                    }
                    break;
                }
                case ClassWriter.WIDE_INSN:
                    opcode = b[v + 1] & 0xFF;
                    if (opcode == Opcodes.IINC) {
                        v += 6;
                        sizes[start] = 6;
                    } else {
                        v += 4;
                        sizes[start] = 4;
                    }
                    break;
                case ClassWriter.TABL_INSN: {
                    v = v + 4 - (v & 3);
                    int s = 3;
                    getBasicBlock(start + ByteArray.readInt(b, v), blocksByOffset, blocks);
                    int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                    v += 12;
                    s += 12;
                    for (; j > 0; --j) {
                        getBasicBlock(start + ByteArray.readInt(b, v), blocksByOffset, blocks);
                        v += 4;
                        s += 4;
                    }
                    sizes[start] = s;
                    getBasicBlock(v, blocksByOffset, blocks);
                    break;
                }
                case ClassWriter.LOOK_INSN: {
                    // skips 0 to 3 padding bytes
                    v = v + 4 - (v & 3);
                    int s = 3;
                    getBasicBlock(start + ByteArray.readInt(b, v), blocksByOffset, blocks);
                    int j = ByteArray.readInt(b, v + 4);
                    v += 8;
                    s += 8;
                    for (; j > 0; --j) {
                        getBasicBlock(start + ByteArray.readInt(b, v + 4), blocksByOffset, blocks);
                        v += 8;
                        s += 8;
                    }
                    sizes[start] = s;
                    getBasicBlock(v, blocksByOffset, blocks);
                    break;
                }
                case ClassWriter.VAR_INSN:
                    if (opcode == Opcodes.RET)
                        throw new UnsupportedOperationException("RET instruction not supported yet");
                case ClassWriter.SBYTE_INSN:
                case ClassWriter.LDC_INSN:
                    v += 2;
                    sizes[start] = 2;
                    break;
                case ClassWriter.SHORT_INSN:
                case ClassWriter.LDCW_INSN:
                case ClassWriter.FIELDORMETH_INSN:
                case ClassWriter.TYPE_INSN:
                case ClassWriter.IINC_INSN:
                    v += 3;
                    sizes[start] = 3;
                    break;
                case ClassWriter.ITFMETH_INSN:
                case ClassWriter.INDYMETH_INSN:
                    v += 5;
                    sizes[start] = 5;
                    break;
                    // case MANA_INSN:
                default:
                    v += 4;
                    sizes[start] = 4;
                    break;
                }
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
                getBasicBlock(h.start.position, blocksByOffset, blocks); // start
                getBasicBlock(h.end.position, blocksByOffset, blocks); // end
                BasicBlock handler = getBasicBlock(h.handler.position, blocksByOffset, blocks);
                handler.kind = Kind.EXCEPTION_HANDLER;
                h = h.next;
            }
        }

        // Next, add frames and split too-large blocks
        {
            final int invocationOverhead = visitInvocationMaxSize(maxStack, maxLocals);
            final int stackOverhead = reconstructStackMaxSize(maxStack, maxLocals);
            int frameLocalCount = 0;
            int frameStackCount = 0;
            Object[] frameLocal = new Object[maxLocals];
            Arrays.fill(frameLocal, Opcodes.TOP);
            Object[] frameStack = new Object[maxStack];
            Arrays.fill(frameStack, Opcodes.TOP);
            // These hold copies of frameLocal and frameStack from
            // when it was last fully defined, just before it went
            // partially undefined.
            Object[] lastDefinedFrameLocal = null;
            Object[] lastDefinedFrameStack = null;
            int lastDefinedV = 0;
            int lastDefinedS = 0;
            int v = 0;
            int s = 0; // block size
            while (v < code.length) {
                {
                    FrameData fd = frameDataByOffset[v];
                    if (fd != null) {
                        // transitioning from possibly-defined to undefined
                        if (!fd.isFullyDefined()) {
                            lastDefinedFrameLocal = Arrays.copyOf(frameLocal, frameLocalCount);
                            lastDefinedFrameStack = Arrays.copyOf(frameStack, frameStackCount);
                            lastDefinedV = v;
                            lastDefinedS = s;
                        }
                        frameLocalCount = fd.frameLocal.length;
                        System.arraycopy(fd.frameLocal, 0, frameLocal, 0, frameLocalCount);
                        frameStackCount = fd.frameStack.length;
                        System.arraycopy(fd.frameStack, 0, frameStack, 0, frameStackCount);
                    }
                }
                {
                    BasicBlock block = blocksByOffset[v];
                    if (block != null) {
                        FrameData fd = block.frameData;
                        if (fd == null) {
                            block.frameData = new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);
                        }
                        lastDefinedFrameLocal = lastDefinedFrameStack = null;
                        s = stackOverhead;
                    } else {
                        // the next instruction would put it over the top, so put in a potential split point
                        if (s + sizes[v] + invocationOverhead > maxBlockSize) {
                            if (FrameData.isFrameFullyDefined(frameLocal, frameLocalCount)
                                && FrameData.isFrameFullyDefined(frameStack, frameStackCount)) {
                                // current frame is fully defined, so it's OK to split here
                                BasicBlock split = getBasicBlock(v, blocksByOffset, blocks);
                                split.frameData = new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);
                                s = stackOverhead;
                            } else if (lastDefinedFrameLocal != null) {
                                // current frame is not fully defined, so split just before it became undefined
                                BasicBlock split = getBasicBlock(lastDefinedV, blocksByOffset, blocks);
                                split.frameData = new FrameData(lastDefinedFrameLocal, lastDefinedFrameStack);
                                lastDefinedFrameLocal = lastDefinedFrameStack = null;
                                // code between the split point and here is the new size
                                s = s - lastDefinedS + stackOverhead;
                            }
                            // else we'll die later ...
                        }
                    }
                }
                int start = v;
                int opcode = b[v] & 0xFF;
                if (opcode > 201) {
                    /*
                     * Convert temporary opcodes 202 to 217, 218 and 219
                     * to IFEQ ... JSR (inclusive), IFNULL and
                     * IFNONNULL. (The label targets themselves aren't
                     * relevant here.)
                     */
                    opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                }
                switch (opcode) {
                case Opcodes.NOP:
                case Opcodes.INEG:
                case Opcodes.LNEG:
                case Opcodes.FNEG:
                case Opcodes.DNEG:
                case Opcodes.I2B:
                case Opcodes.I2C:
                case Opcodes.I2S:
                    v += 1;
                    break;
                case Opcodes.GOTO:
                    frameLocalCount = frameStackCount = 0;
                    v += 3;
                    break;
                case 200: // GOTO_W
                    frameLocalCount = frameStackCount = 0;
                    v += 5;
                    break;
                case Opcodes.IRETURN:
                case Opcodes.FRETURN:
                case Opcodes.ARETURN:
                case Opcodes.ATHROW:
                case Opcodes.RETURN:
                    frameLocalCount = frameStackCount = 0;
                    v += 1;
                    break;
                case Opcodes.ACONST_NULL:
                    frameStack[frameStackCount++] = Opcodes.NULL;
                    v += 1;
                    break;
                case Opcodes.ICONST_M1:
                case Opcodes.ICONST_0:
                case Opcodes.ICONST_1:
                case Opcodes.ICONST_2:
                case Opcodes.ICONST_3:
                case Opcodes.ICONST_4:
                case Opcodes.ICONST_5:
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 1;
                    break;
                case Opcodes.BIPUSH:
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 2;
                    break;
                case Opcodes.SIPUSH:
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 3;
                    break;
                case Opcodes.LCONST_0:
                case Opcodes.LCONST_1:
                    frameStack[frameStackCount++] = Opcodes.LONG;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.FCONST_0:
                case Opcodes.FCONST_1:
                case Opcodes.FCONST_2:
                    frameStack[frameStackCount++] = Opcodes.FLOAT;
                    v += 1;
                    break;
                case Opcodes.DCONST_0:
                case Opcodes.DCONST_1:
                    frameStack[frameStackCount++] = Opcodes.DOUBLE;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.ILOAD:
                case Opcodes.FLOAD:
                case Opcodes.ALOAD:
                    frameStack[frameStackCount++] = frameLocal[b[v + 1] & 0xFF];
                    v += 2;
                    break;

                    // ILOAD_n
                case 26:
                case 27:
                case 28:
                case 29:
                    frameStack[frameStackCount++] = frameLocal[opcode - 26];
                    v += 1;
                    break;
                
                    // LLOAD_n
                case 30:
                case 31:
                case 32:
                case 33:
                    frameStack[frameStackCount++] = frameLocal[opcode - 30];
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;

                    // FLOAD_n
                case 34:
                case 35:
                case 36:
                case 37:
                    frameStack[frameStackCount++] = frameLocal[opcode - 34];
                    v += 1;
                    break;

                    // DLOAD_n
                case 38:
                case 39:
                case 40:
                case 41:
                    frameStack[frameStackCount++] = frameLocal[opcode - 38];
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;

                    // ALOAD_n
                case 42:
                case 43:
                case 44:
                case 45:
                    frameStack[frameStackCount++] = frameLocal[opcode - 42];
                    v += 1;
                    break;

                case Opcodes.LLOAD:
                case Opcodes.DLOAD:
                    frameStack[frameStackCount++] = frameLocal[b[v + 1] & 0xFF];
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 2;
                    break;
                case Opcodes.IALOAD:
                case Opcodes.BALOAD:
                case Opcodes.CALOAD:
                case Opcodes.SALOAD:
                    frameStackCount -= 2;
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 1;
                    break;
                case Opcodes.LALOAD:
                case Opcodes.D2L:
                    frameStackCount -= 2;
                    frameStack[frameStackCount++] = Opcodes.LONG;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.FALOAD:
                    frameStackCount -= 2;
                    frameStack[frameStackCount++] = Opcodes.FLOAT;
                    v += 1;
                    break;
                case Opcodes.DALOAD:
                case Opcodes.L2D:
                    frameStackCount -= 2;
                    frameStack[frameStackCount++] = Opcodes.DOUBLE;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.AALOAD: {
                    frameStackCount -= 2;
                    Object t = frameStack[frameStackCount];
                    if (t instanceof String) {
                        frameStack[frameStackCount++] = ((String) t).substring(1);
                    } else {
                        frameStack[frameStackCount++] =  "java/lang/Object";
                    }
                    v += 1;
                    break;
                }
                case Opcodes.ISTORE:
                case Opcodes.FSTORE:
                case Opcodes.ASTORE: {
                    int n = b[v + 1] & 0xFF;
                    frameLocal[n] = frameStack[--frameStackCount];
                    frameLocalCount = Math.max(frameLocalCount, n + 1);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 2;
                    break;
                }
                    // ISTORE_n
                case 59:
                case 60:
                case 61:
                case 62: {
                    int n = opcode - 59;
                    frameLocal[n] = frameStack[--frameStackCount];
                    frameLocalCount = Math.max(frameLocalCount, n + 1);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 1;
                    break;
                }
                    // LSTORE_n
                case 63:
                case 64:
                case 65:
                case 66: {
                    int n = opcode - 63;
                    frameLocal[n] = frameStack[--frameStackCount];
                    --frameStackCount;
                    frameLocal[n + 1] = Opcodes.TOP;
                    frameLocalCount = Math.max(frameLocalCount, n + 2);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 1;
                    break;
                }

                    // FSTORE_n
                case 67:
                case 68:
                case 69:
                case 70: {
                    int n = opcode - 67;
                    frameLocal[n] = frameStack[--frameStackCount];
                    frameLocalCount = Math.max(frameLocalCount, n + 1);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 1;
                    break;
                }

                    // DSTORE_n
                case 71:
                case 72:
                case 73:
                case 74: {
                    int n = opcode - 71;
                    frameLocal[n] = frameStack[--frameStackCount];
                    frameLocal[n + 1] = Opcodes.TOP;
                    --frameStackCount;
                    frameLocalCount = Math.max(frameLocalCount, n + 2);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 1;
                    break;
                }
                    // ASTORE_n
                case 75:
                case 76:
                case 77:
                case 78: {
                    int n = opcode - 75;
                    frameLocal[n] = frameStack[--frameStackCount];
                    frameLocalCount = Math.max(frameLocalCount, n + 1);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 1;
                    break;
                }

                case Opcodes.LSTORE:
                case Opcodes.DSTORE: {
                    int n = b[v + 1] & 0xFF;
                    frameLocal[n] = frameStack[--frameStackCount];
                    frameLocal[n + 1] = Opcodes.TOP;
                    --frameStackCount;
                    frameLocalCount = Math.max(frameLocalCount, n + 2);
                    invalidateTwoWordLocal(frameLocal, n - 1);
                    v += 2;
                    break;
                }
                case Opcodes.IASTORE:
                case Opcodes.BASTORE:
                case Opcodes.CASTORE:
                case Opcodes.SASTORE:
                case Opcodes.FASTORE:
                case Opcodes.AASTORE:
                    frameStackCount -= 3;
                    v += 1;
                    break;
                case Opcodes.LASTORE:
                case Opcodes.DASTORE:
                    frameStackCount -= 4;
                    v += 1;
                    break;
                case Opcodes.POP:
                    --frameStackCount;
                    v += 1;
                    break;
                case Opcodes.IFEQ:
                case Opcodes.IFNE:
                case Opcodes.IFLT:
                case Opcodes.IFGE:
                case Opcodes.IFGT:
                case Opcodes.IFLE:
                case Opcodes.IFNULL:
                case Opcodes.IFNONNULL:
                    --frameStackCount;
                    v += 3;
                    break;
                case Opcodes.MONITORENTER:
                case Opcodes.MONITOREXIT:
                    --frameStackCount;
                    v += 1;
                    break;
                case Opcodes.TABLESWITCH: {
                    frameStackCount = frameLocalCount = 0;
                    // skips 0 to 3 padding bytes
                    v = v + 4 - (v & 3);
                    int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                    v += 12 + 4 * j;
                    break;
                }
                case Opcodes.LOOKUPSWITCH: {
                    frameStackCount = frameLocalCount = 0;
                    // skips 0 to 3 padding bytes
                    v = v + 4 - (v & 3);
                    int j = ByteArray.readInt(b, v + 4);
                    v += 8 + (j * 8);
                    break;
                }
                case Opcodes.POP2:
                    frameStackCount -= 2;
                    v += 1;
                    break;
                case Opcodes.IF_ICMPEQ:
                case Opcodes.IF_ICMPNE:
                case Opcodes.IF_ICMPLT:
                case Opcodes.IF_ICMPGE:
                case Opcodes.IF_ICMPGT:
                case Opcodes.IF_ICMPLE:
                case Opcodes.IF_ACMPEQ:
                case Opcodes.IF_ACMPNE:
                    frameStackCount -= 2;
                    v += 3;
                    break;
                case Opcodes.LRETURN:
                case Opcodes.DRETURN:
                    frameStackCount -= 2;
                    v += 1;
                    break;
                case Opcodes.DUP: {
                    Object t = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t;
                    frameStack[frameStackCount++] = t;
                    v += 1;
                    break;
                }
                case Opcodes.DUP_X1: {
                    Object t1 = frameStack[--frameStackCount];
                    Object t2 = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t1;
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    v += 1;
                    break;
                }
                case Opcodes.DUP_X2: {
                    Object t1 = frameStack[--frameStackCount];
                    Object t2 = frameStack[--frameStackCount];
                    Object t3 = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t1;
                    frameStack[frameStackCount++] = t3;
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    v += 1;
                    break;
                }
                case Opcodes.DUP2: {
                    Object t1 = frameStack[--frameStackCount];
                    Object t2 = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    v += 1;
                    break;
                }
                case Opcodes.DUP2_X1: {
                    Object t1 = frameStack[--frameStackCount];
                    Object t2 = frameStack[--frameStackCount];
                    Object t3 = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    frameStack[frameStackCount++] = t3;
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    v += 1;
                    break;
                }
                case Opcodes.DUP2_X2: {
                    Object t1 = frameStack[--frameStackCount];
                    Object t2 = frameStack[--frameStackCount];
                    Object t3 = frameStack[--frameStackCount];
                    Object t4 = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    frameStack[frameStackCount++] = t4;
                    frameStack[frameStackCount++] = t3;
                    frameStack[frameStackCount++] = t2;
                    frameStack[frameStackCount++] = t1;
                    v += 1;
                    break;
                }
                case Opcodes.SWAP: {
                    Object t1 = frameStack[--frameStackCount];
                    Object t2 = frameStack[--frameStackCount];
                    frameStack[frameStackCount++] = t1;
                    frameStack[frameStackCount++] = t2;
                    v += 1;
                    break;
                }
                case Opcodes.IADD:
                case Opcodes.ISUB:
                case Opcodes.IMUL:
                case Opcodes.IDIV:
                case Opcodes.IREM:
                case Opcodes.IAND:
                case Opcodes.IOR:
                case Opcodes.IXOR:
                case Opcodes.ISHL:
                case Opcodes.ISHR:
                case Opcodes.IUSHR:
                case Opcodes.L2I:
                case Opcodes.D2I:
                case Opcodes.FCMPL:
                case Opcodes.FCMPG:
                    frameStackCount -= 2;
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 1;
                    break;
                case Opcodes.LADD:
                case Opcodes.LSUB:
                case Opcodes.LMUL:
                case Opcodes.LDIV:
                case Opcodes.LREM:
                case Opcodes.LAND:
                case Opcodes.LOR:
                case Opcodes.LXOR:
                    frameStackCount -= 4;
                    frameStack[frameStackCount++] = Opcodes.LONG;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.FADD:
                case Opcodes.FSUB:
                case Opcodes.FMUL:
                case Opcodes.FDIV:
                case Opcodes.FREM:
                case Opcodes.L2F:
                case Opcodes.D2F:
                    frameStackCount -= 2;
                    frameStack[frameStackCount++] = Opcodes.FLOAT;
                    v += 1;
                    break;
                case Opcodes.DADD:
                case Opcodes.DSUB:
                case Opcodes.DMUL:
                case Opcodes.DDIV:
                case Opcodes.DREM:
                    frameStackCount -= 4;
                    frameStack[frameStackCount++] = Opcodes.DOUBLE;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.LSHL:
                case Opcodes.LSHR:
                case Opcodes.LUSHR:
                    frameStackCount -= 3;
                    frameStack[frameStackCount++] = Opcodes.LONG;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.IINC: {
                    int n = b[v + 1] & 0xFF;
                    frameLocal[n] = Opcodes.INTEGER;
                    frameLocalCount = Math.max(frameLocalCount, n + 1);
                    v += 3;
                    break;
                }
                case Opcodes.I2L:
                case Opcodes.F2L:
                    --frameStackCount;
                    frameStack[frameStackCount++] = Opcodes.LONG;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.I2F:
                    --frameStackCount;
                    frameStack[frameStackCount++] = Opcodes.FLOAT;
                    v += 1;
                    break;
                case Opcodes.I2D:
                case Opcodes.F2D:
                    --frameStackCount;
                    frameStack[frameStackCount++] = Opcodes.DOUBLE;
                    frameStack[frameStackCount++] = Opcodes.TOP;
                    v += 1;
                    break;
                case Opcodes.F2I:
                case Opcodes.ARRAYLENGTH:
                    --frameStackCount;
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 1;
                    break;
                case Opcodes.INSTANCEOF:
                    --frameStackCount;
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 3;
                    break;
                case Opcodes.LCMP:
                case Opcodes.DCMPL:
                case Opcodes.DCMPG:
                    frameStackCount -= 4;
                    frameStack[frameStackCount++] = Opcodes.INTEGER;
                    v += 1;
                    break;
                case Opcodes.JSR:
                case 201: // JSR_W
                case Opcodes.RET:
                    throw new RuntimeException("JSR/RET are not supported");
                case Opcodes.GETSTATIC: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 3;
                    break;
                }
                case Opcodes.PUTSTATIC: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    v += 3;
                    break;
                }
                case Opcodes.GETFIELD: {
                    --frameStackCount;
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 3;
                    break;
                }
                case Opcodes.PUTFIELD: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    --frameStackCount;
                    v += 3;
                    break;
                }
                case Opcodes.INVOKEVIRTUAL: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    --frameStackCount;
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 3;
                    break;
                }
                case Opcodes.INVOKESPECIAL: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    Object t = frameStack[--frameStackCount];
                    if (sr.name.charAt(0) == '<') {
                        Object u;
                        if (t == Opcodes.UNINITIALIZED_THIS) {
                            u = className;
                        } else {
                            u = labelTypes.get(t);
                        }
                        for (int i = 0; i < frameLocalCount; ++i) {
                            if (frameLocal[i] == t) {
                                frameLocal[i] = u;
                            }
                        }
                        for (int i = 0; i < frameStackCount; ++i) {
                            if (frameStack[i] == t) {
                                frameStack[i] = u;
                            }
                        }
                    }
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 3;
                    break;
                }
                case Opcodes.INVOKESTATIC: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 3;
                    break;
                }
                case Opcodes.INVOKEINTERFACE: {
                    ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    --frameStackCount;
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 5;
                    break;
                }
                case Opcodes.INVOKEDYNAMIC: {
                    ConstantPool.DynamicSymRef sr = constantPool.parseDynamicSymRef(ByteArray.readUnsignedShort(b, v + 1));
                    frameStackCount = popDesc(frameStackCount, sr.desc);
                    frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                    v += 5;
                    break;
                }
                case Opcodes.LDC:
                case 19: // LDC_W
                case 20: { // LDC2_W
                    int itemIndex = (opcode == Opcodes.LDC) ? (b[v + 1] & 0xFF) : ByteArray.readUnsignedShort(b, v + 1);
                    Object cst = constantPool.readConst(itemIndex);
                    if (cst instanceof Integer) {
                        frameStack[frameStackCount++] = Opcodes.INTEGER;
                    } else if (cst instanceof Long) {
                        frameStack[frameStackCount++] = Opcodes.LONG;
                        frameStack[frameStackCount++] = Opcodes.TOP;
                    } else if (cst instanceof Float) {
                        frameStack[frameStackCount++] = Opcodes.FLOAT;
                    } else if (cst instanceof Double) {
                        frameStack[frameStackCount++] = Opcodes.DOUBLE;
                        frameStack[frameStackCount++] = Opcodes.TOP;
                    } else if (cst instanceof String) {
                        frameStack[frameStackCount++] = "java/lang/String";
                    } else if (cst instanceof Type) {
                        int sort = ((Type) cst).getSort();
                        if (sort == Type.OBJECT || sort == Type.ARRAY) {
                            frameStack[frameStackCount++] = "java/lang/Class";
                        } else if (sort == Type.METHOD) {
                            frameStack[frameStackCount++] = "java/lang/invoke/MethodType";
                        } else {
                            throw new IllegalArgumentException();
                        }
                    } else if (cst instanceof Handle) {
                        frameStack[frameStackCount++] = "java/lang/invoke/MethodHandle";
                    } else {
                        throw new IllegalArgumentException();
                    }
                
                    if (opcode == Opcodes.LDC) {
                        v += 2;
                    } else {
                        v += 3;
                    }
                    break;
                }
                case Opcodes.NEW: {
                    // transitioning from possibly-defined to undefined
                    if (FrameData.isFrameFullyDefined(frameLocal, frameLocalCount)
                        && FrameData.isFrameFullyDefined(frameStack, frameStackCount)) {
                        lastDefinedFrameLocal = Arrays.copyOf(frameLocal, frameLocalCount);
                        lastDefinedFrameStack = Arrays.copyOf(frameStack, frameStackCount);
                        lastDefinedV = v;
                        lastDefinedS = s;
                    }
                    Label l = getLabelAt(labelsByOffset, v);
                    frameStack[frameStackCount++] = l;
                    String clazz = constantPool.readClass(ByteArray.readUnsignedShort(b, v + 1));
                    labelTypes.put(l, clazz);
                    v += 3;
                    break;
                }
                case Opcodes.NEWARRAY:
                    --frameStackCount;
                    switch (b[v + 1]) {
                    case Opcodes.T_BOOLEAN:
                        frameStack[frameStackCount++] = "[Z";
                        break;
                    case Opcodes.T_CHAR:
                        frameStack[frameStackCount++] = "[C";
                        break;
                    case Opcodes.T_BYTE:
                        frameStack[frameStackCount++] = "[B";
                        break;
                    case Opcodes.T_SHORT:
                        frameStack[frameStackCount++] = "[S";
                        break;
                    case Opcodes.T_INT:
                        frameStack[frameStackCount++] = "[I";
                        break;
                    case Opcodes.T_FLOAT:
                        frameStack[frameStackCount++] = "[F";
                        break;
                    case Opcodes.T_DOUBLE:
                        frameStack[frameStackCount++] = "[D";
                        break;
                        // case Opcodes.T_LONG:
                    default:
                        frameStack[frameStackCount++] = "[J";
                        break;
                    }
                    v += 2;
                    break;
                case Opcodes.ANEWARRAY: {
                    --frameStackCount;
                    frameStack[frameStackCount++] = "[" + constantPool.readClass(ByteArray.readUnsignedShort(b, v + 1));
                    v += 3;
                    break;
                }
                case Opcodes.CHECKCAST: {
                    --frameStackCount;
                    frameStack[frameStackCount++] = constantPool.readClass(ByteArray.readUnsignedShort(b, v + 1));
                    v += 3;
                    break;
                }
                
                case Opcodes.MULTIANEWARRAY: {
                    frameStackCount -= b[v + 3] & 0xFF;
                    frameStack[frameStackCount++] = constantPool.readClass(ByteArray.readUnsignedShort(b, v + 1));
                    v += 4;
                    break;
                }
                case 196: // WIDE
                    opcode = b[v + 1] & 0xFF;
                    switch (opcode) {
                    case Opcodes.ILOAD:
                    case Opcodes.FLOAD:
                    case Opcodes.ALOAD:
                        frameStack[frameStackCount++] = frameLocal[ByteArray.readUnsignedShort(b, v + 2)];
                        v += 4;
                        break;
                    case Opcodes.LLOAD:
                    case Opcodes.DLOAD:
                        frameStack[frameStackCount++] = frameLocal[ByteArray.readUnsignedShort(b, v + 2)];
                        frameStack[frameStackCount++] = Opcodes.TOP;
                        v += 4;
                        break;
                    case Opcodes.ISTORE:
                    case Opcodes.FSTORE:
                    case Opcodes.ASTORE: {
                        int n = ByteArray.readUnsignedShort(b, v + 2);
                        frameLocal[n] = frameStack[--frameStackCount];
                        frameLocalCount = Math.max(frameLocalCount, n + 1);
                        invalidateTwoWordLocal(frameLocal, n - 1);
                        v += 4;
                        break;
                    }
                    case Opcodes.LSTORE:
                    case Opcodes.DSTORE: {
                        int n = ByteArray.readUnsignedShort(b, v + 2);
                        frameLocal[n] = frameStack[--frameStackCount];
                        frameLocal[n + 1] = Opcodes.TOP;
                        --frameStackCount;
                        frameLocalCount = Math.max(frameLocalCount, n + 2);
                        invalidateTwoWordLocal(frameLocal, n - 1);
                        v += 4;
                        break;
                    }
                    case Opcodes.IINC: {
                        int n = ByteArray.readUnsignedShort(b, v + 2);
                        frameLocal[n] = Opcodes.INTEGER;
                        frameLocalCount = Math.max(frameLocalCount, n + 1);
                        v += 6;
                        break;
                    }
                    default: {
                        throw new RuntimeException("unhandled wide opcode " + opcode);
                    }
                    }
                    break;
                default: {
                    throw new RuntimeException("unhandled opcode " + opcode);
                }
                }
                s += sizes[start];
             }
        }

        // now insert edges
        int v = 0;
        BasicBlock currentBlock = null;
        while (v < code.length) {
            if (blocksByOffset[v] != null) {
                currentBlock = blocksByOffset[v];
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
                currentBlock.addEdge(blocksByOffset[label]);
                v += 3;
                break;
            }
            case ClassWriter.LABELW_INSN:
                currentBlock.addEdge(blocksByOffset[v + ByteArray.readInt(b, v + 1)]);
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
                int start = v;
                v = v + 4 - (v & 3);
                currentBlock.addEdge(blocksByOffset[start + ByteArray.readInt(b, v)]);
                int j = ByteArray.readInt(b, v + 8) - ByteArray.readInt(b, v + 4) + 1;
                v += 12;
                for (; j > 0; --j) {
                    currentBlock.addEdge(blocksByOffset[start + ByteArray.readInt(b, v)]);
                    v += 4;
                }
                break;
            }
            case ClassWriter.LOOK_INSN: {
                int start = v;
                v = v + 4 - (v & 3);
                currentBlock.addEdge(blocksByOffset[start + ByteArray.readInt(b, v)]);
                int j = ByteArray.readInt(b, v + 4);
                v += 8;
                for (; j > 0; --j) {
                    currentBlock.addEdge(blocksByOffset[start + ByteArray.readInt(b, v + 4)]);
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
                BasicBlock next = blocksByOffset[v];
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
                BasicBlock start = blocksByOffset[h.start.position];
                BasicBlock end = blocksByOffset[h.end.position];
                BasicBlock handler = blocksByOffset[h.handler.position];
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
                size += 3; // very coarse
                u = u + 4 - (u & 3);
                u += 12 + 4 * (ByteArray.readInt(b, u + 8) - ByteArray.readInt(b, u + 4) + 1);
                break;
            case ClassWriter.LOOK_INSN:
                size += 3;
                u = u + 4 - (u & 3);
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

    public boolean hasFullyDefinedFrame() {
        return frameData.isFullyDefined();
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


