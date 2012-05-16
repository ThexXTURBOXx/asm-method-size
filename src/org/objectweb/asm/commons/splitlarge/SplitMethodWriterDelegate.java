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

import java.util.Arrays;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;

final class SplitMethodWriterDelegate extends MethodWriterDelegate {

    /**
     * We'll want to call the same {@link ClassVisitor} methods
     * (specifically {@link ClassVisitor#visitMethod} as the original
     * caller.  So we unravel the delegation chain to that point.
     */
    ClassVisitor cv;

    final int maxMethodLength;

    Scc scc;

    HashSet<SplitMethod> splitMethods;

    MethodWriter mainMethodWriter;
    MethodVisitor mainMethodVisitor;

    BasicBlock[] blocksByOffset;
    /**
     * Labels not associated with a basic block - NEW instructions,
     * line numbers, region start for local variables.  These labels
     * point <em>downward</em>, i.e. they really refer to something
     * that comes directly after.
     */
    Label[] labelsByOffset;
    /**
     * Labels pointing <em>upward</em> not asssociated with a basic
     * block: Region ends for local variables.
     *
     * These need to be distinguished from {@link #labelsByOffset}
     * because if a labels is just at a split point, the downward
     * label goes into the split method, while the upward label stays
     * in the calling method.
     */
    Label[] upwardLabelsByOffset;

    /**
     * Maximum length of the strings contained in the constant pool of the
     * class.
     */
    private int maxStringLength;

    private char[] utfDecodeBuffer;

    /**
     * Offsets of the CONSTANT_Class_info structures in the pools;
     * more precisely the offsets of the meat of those structures
     * after the tag.
     */
    int[] items;

    String thisName;

    int[] bootstrapMethods;

    public SplitMethodWriterDelegate(final int maxMethodLength) {
        this.maxMethodLength = maxMethodLength;
    }

    @Override
    public void visitEnd() {
        parseConstantPool();
        thisName = readUTF8Item(name, utfDecodeBuffer);
        cv = cw.getFirstVisitor();

        TreeSet<BasicBlock> blocks = BasicBlock.computeFlowgraph(code.data, 0, code.length, firstHandler);
        this.scc = Scc.stronglyConnectedComponents(blocks);
        this.scc.initializeAll();
        this.blocksByOffset = computeBlocksByOffset(blocks);
        this.labelsByOffset = new Label[code.length];
        this.upwardLabelsByOffset = new Label[code.length + 1 ]; // the + 1 is for a label beyond the end
        parseStackMap();
        HashMap<Label, String> labelTypes = computeFrames();
        BasicBlock.computeSizes(code, blocks);
        this.scc.computeSizes();
        this.splitMethods = scc.split(thisName, access, maxMethodLength);
        parseBootstrapMethods();
        makeMethodWriters(labelTypes);
        if (lineNumber != null) {
            visitLineNumberLabels();
        }
        if (localVar != null) {
            visitLocalVarLabels();
        }
        writeMethods();
        if (localVar != null) {
            visitLocalVars();
        }
        transferAnnotations();
        transferNonstandardAttributes();
    }

    void parseConstantPool() {
        int n = poolSize;
        items = new int[n];
        byte[] b = pool.data;
        int max = 0;
        int index = 0;
        for (int i = 1; i < n; ++i) {
            items[i] = index + 1;
            int size;
            switch (b[index]) {
            case ClassWriter.FIELD:
            case ClassWriter.METH:
            case ClassWriter.IMETH:
            case ClassWriter.INT:
            case ClassWriter.FLOAT:
            case ClassWriter.NAME_TYPE:
            case ClassWriter.INDY:
                size = 5;
                break;
            case ClassWriter.LONG:
            case ClassWriter.DOUBLE:
                size = 9;
                ++i;
                break;
            case ClassWriter.UTF8:
                size = 3 + ByteArray.readUnsignedShort(b, index + 1);
                if (size > max) {
                    max = size;
                }
                break;
            case ClassWriter.HANDLE:
                size = 4;
                break;
                // case ClassWriter.CLASS:
                // case ClassWriter.STR:
                // case ClassWriter.MTYPE
            default:
                size = 3;
                break;
            }
            index += size;
        }
        maxStringLength = max;
        utfDecodeBuffer = new char[maxStringLength];
    }

    void parseBootstrapMethods() {
        if (cw.bootstrapMethods == null)
            return;
        int boostrapMethodCount = cw.bootstrapMethodsCount;
        bootstrapMethods = new int[boostrapMethodCount];
        int x = 0;
        for (int j = 0; j < boostrapMethodCount; j++) {
            bootstrapMethods[j] = x;
            x += 2 + readUnsignedShort(x + 2) << 1;
        }
    }

    private void parseStackMap() {
        if ((version & 0xFFFF) < Opcodes.V1_6) {
            throw new RuntimeException("JVM version < 1.6 not supported");
        }
        int frameLocalCount = 0;
        int frameStackCount = 0;
        Object[] frameLocal = new Object[maxLocals];
        Object[] frameStack = new Object[maxStack];
        String desc = this.descriptor;

        // creates the very first (implicit) frame from the method
        // descriptor
        {
            int local = 0;
            if ((access & Opcodes.ACC_STATIC) == 0) {
                if ("<init>".equals(thisName)) {
                    frameLocal[local++] = Opcodes.UNINITIALIZED_THIS;
                } else {
                    frameLocal[local++] = cw.thisName;
                }
            }
            int j = 1;
            loop: while (true) {
                int k = j;
                switch (desc.charAt(j++)) {
                case 'Z':
                case 'C':
                case 'B':
                case 'S':
                case 'I':
                    frameLocal[local++] = Opcodes.INTEGER;
                    break;
                case 'F':
                    frameLocal[local++] = Opcodes.FLOAT;
                    break;
                case 'J':
                    frameLocal[local++] = Opcodes.LONG;
                    break;
                case 'D':
                    frameLocal[local++] = Opcodes.DOUBLE;
                    break;
                case '[':
                    while (desc.charAt(j) == '[') {
                        ++j;
                    }
                    if (desc.charAt(j) == 'L') {
                        ++j;
                        while (desc.charAt(j) != ';') {
                            ++j;
                        }
                    }
                    frameLocal[local++] = desc.substring(k, ++j);
                    break;
                case 'L':
                    while (desc.charAt(j) != ';') {
                        ++j;
                    }
                    frameLocal[local++] = desc.substring(k + 1,
                                                         j++);
                    break;
                default:
                    break loop;
                }
            }
            frameLocalCount = local;
        }

        blocksByOffset[0].frameData =
            new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);

        /*
         * for the first explicit frame the offset is not
         * offset_delta + 1 but only offset_delta; setting the
         * implicit frame offset to -1 allow the use of the
         * "offset_delta + 1" rule in all cases
         */
        int frameOffset = -1;
        int v = 0;
        byte[] b = stackMap.data;
        int count = 0;
        while (count < frameCount) {
            int tag = b[v++] & 0xFF;
            int delta;
            if (tag < MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME) {
                delta = tag;
            } else if (tag < MethodWriter.RESERVED) {
                delta = tag - MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME;
                v = readFrameType(frameStack, 0, v, utfDecodeBuffer);
                frameStackCount = 1;

            } else {
                delta = ByteArray.readUnsignedShort(b, v);
                v += 2;
                if (tag == MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
                    v = readFrameType(frameStack, 0, v, utfDecodeBuffer);
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
                        v = readFrameType(frameLocal, j++, v, utfDecodeBuffer);
                    }
                    frameLocalCount += tag - MethodWriter.SAME_FRAME_EXTENDED;
                    frameStackCount = 0;
                } else { // if (tag == FULL_FRAME) {
                    {
                        int n = frameLocalCount = ByteArray.readUnsignedShort(b, v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(frameLocal, j++, v, utfDecodeBuffer);
                        }
                    }
                    {
                        int n = frameStackCount = ByteArray.readUnsignedShort(b, v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(frameStack, j++, v, utfDecodeBuffer);
                        }
                    }
                }
            }
            frameOffset += delta + 1;

            BasicBlock block = blocksByOffset[frameOffset];
            if (block != null) {
                block.frameData = new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);
            }

            ++count;
        }
    }

    private int readFrameType(final Object[] frame,
                              final int index,
                              int v,
                              final char[] buf) {
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
            frame[index] = readClass(ByteArray.readUnsignedShort(b, v), buf);
            v += 2;
            break;
        default: { // Uninitialized
            int offset = ByteArray.readUnsignedShort(b, v);
            Label label = getLabelAt(offset);
            frame[index] = label;
            v += 2;
        }
        }
        return v;
    }

    /**
     * Symbolic reference to method or field.
     */
    class MemberSymRef {
        final String owner;
        final String name;
        final String desc;
        public MemberSymRef(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }
    }

    /**
     * Parse a symbolic reference to a member.
     *
     * @param v address of symbolic reference
     * @returns symbolic reference
     */
    private MemberSymRef parseMemberSymRef(int v) {
        // offset of the {Fieldref, MethodRef, InterfaceMethodRef}_Info structure
        int cpIndex = items[readUnsignedShort(v)];
        String iowner = readClass(ByteArray.readUnsignedShort(pool.data, cpIndex), utfDecodeBuffer);
        cpIndex = items[ByteArray.readUnsignedShort(pool.data, cpIndex + 2)];
        String iname = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex), utfDecodeBuffer);
        String idesc = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex + 2), utfDecodeBuffer);
        return new MemberSymRef(iowner, iname, idesc);
    }

    /**
     * Symbolic reference to dynamic method.
     */
    class DynamicSymRef {
        final String name;
        final String desc;
        final int bsmIndex;
        public DynamicSymRef(String name, String desc, int bsmIndex) {
            this.name = name;
            this.desc = desc;
            this.bsmIndex = bsmIndex;
        }
    }

    /**
     * Parse a symbolic reference to a dynamic method.
     *
     * @param v address of symbolic reference
     * @returns symbolic reference
     */
    private DynamicSymRef parseDynamicSymRef(int v) {
        int cpIndex = items[readUnsignedShort(v + 1)];
        int bsmIndex = bootstrapMethods[ByteArray.readUnsignedShort(pool.data, cpIndex)];
        cpIndex = items[readUnsignedShort(cpIndex + 2)];
        String iname = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex), utfDecodeBuffer);
        String idesc = readUTF8Item(ByteArray.readUnsignedShort(pool.data, cpIndex + 2), utfDecodeBuffer);
        return new DynamicSymRef(iname, idesc, bsmIndex);
    }

    /**
     * Compute frames for all basic blocks.
     */
    private HashMap<Label, String> computeFrames() {
        int v = 0;
        byte[] b = code.data;
        int frameLocalCount = 0;
        int frameStackCount = 0;
        Object[] frameLocal = new Object[maxLocals];
        Object[] frameStack = new Object[maxStack];
        // map labels of NEW instructions to their types
        HashMap<Label, String> labelTypes = new HashMap<Label, String>();
        while (v < code.length) {
            BasicBlock block = blocksByOffset[v];
            if (block != null) {
                FrameData fd = block.frameData;
                if (fd == null) {
                    block.frameData = new FrameData(frameLocalCount, frameLocal, frameStackCount, frameStack);
                } else {
                    frameLocalCount = fd.frameLocal.length;
                    System.arraycopy(fd.frameLocal, 0, frameLocal, 0, frameLocalCount);
                    frameStackCount = fd.frameStack.length;
                    System.arraycopy(fd.frameStack, 0, frameStack, 0, frameStackCount);
                }
            }
            int opcode = b[v] & 0xFF;
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
            case Opcodes.SIPUSH:
                frameStack[frameStackCount++] = Opcodes.INTEGER;
                v += 2;
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
                frameStack[frameStackCount++] = frameLocal[readUnsignedShort(v + 1)];
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
                frameStack[frameStackCount++] = frameLocal[readUnsignedShort(v + 1)];
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
                    frameStackCount = pushDesc(frameStack, frameStackCount, ((String) t).substring(1));
                } else {
                    frameStack[frameStackCount++] =  "java/lang/Object";
                }
                v += 1;
                break;
            }
            case Opcodes.ISTORE:
            case Opcodes.FSTORE:
            case Opcodes.ASTORE: {
                int n = b[v + 1];
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
                int n = b[v + 1];
                frameLocal[n] = frameStack[--frameStackCount];
                frameLocal[n + 1] = Opcodes.TOP;
                frameLocalCount = Math.max(frameLocalCount, n + 2);
                invalidateTwoWordLocal(frameLocal, n - 1);
                v += 1;
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
            case Opcodes.TABLESWITCH:
                frameStackCount = frameLocalCount = 0;
                // skips 0 to 3 padding bytes
                v = v & ~3;
                v += 12 + 4 * (readInt(v + 8) - readInt(v + 4) + 1);
                break;
            case Opcodes.LOOKUPSWITCH:
                frameStackCount = frameLocalCount = 0;
                // skips 0 to 3 padding bytes
                v = v & ~3;
                v += 8 + v + (readInt(v + 4) * 8);
                break;
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
                int n = b[v + 1];
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
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                v += 3;
                break;
            }
            case Opcodes.PUTSTATIC: {
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                v += 3;
                break;
            }
            case Opcodes.GETFIELD: {
                --frameStackCount;
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                v += 3;
                break;
            }
            case Opcodes.PUTFIELD: {
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                --frameStackCount;
                v += 3;
                break;
            }
            case Opcodes.INVOKEVIRTUAL: {
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                --frameStackCount;
                frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                v += 3;
                break;
            }
            case Opcodes.INVOKESPECIAL: {
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                Object t = frameStack[--frameStackCount];
                if (sr.name.charAt(0) == '<') {
                    Object u;
                    if (t == Opcodes.UNINITIALIZED_THIS) {
                        u = thisName;
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
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                v += 3;
                break;
            }
            case Opcodes.INVOKEINTERFACE: {
                MemberSymRef sr = parseMemberSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                --frameStackCount;
                frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                v += 5;
                break;
            }
            case Opcodes.INVOKEDYNAMIC: {
                DynamicSymRef sr = parseDynamicSymRef(v + 1);
                frameStackCount = popDesc(frameStackCount, sr.desc);
                frameStackCount = pushDesc(frameStack, frameStackCount, sr.desc);
                v += 5;
                break;
            }
            case Opcodes.LDC:
            case 19: // LDC_W
            case 20: { // LDC2_W
                int itemIndex = (opcode == Opcodes.LDC) ? (b[v + 1] & 0xFF) : readUnsignedShort(v + 1);
                Object cst = readConst(itemIndex, utfDecodeBuffer);
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
                
                v += 2;
                break;
            }
            case Opcodes.NEW: {
                Label l = getLabelAt(v);
                frameStack[frameStackCount++] = l;
                String clazz = readClass(readUnsignedShort(v + 1), utfDecodeBuffer);
                labelTypes.put(l, clazz);
                v += 3;
                break;
            }
            case Opcodes.NEWARRAY:
                --frameStackCount;
                switch (b[v + 1]) {
                case Opcodes.T_BOOLEAN:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[Z");
                    break;
                case Opcodes.T_CHAR:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[C");
                    break;
                case Opcodes.T_BYTE:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[B");
                    break;
                case Opcodes.T_SHORT:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[S");
                    break;
                case Opcodes.T_INT:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[I");
                    break;
                case Opcodes.T_FLOAT:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[F");
                    break;
                case Opcodes.T_DOUBLE:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[D");
                    break;
                    // case Opcodes.T_LONG:
                default:
                    frameStackCount = pushDesc(frameStack, frameStackCount, "[J");
                    break;
                }
                v += 2;
                break;
            case Opcodes.ANEWARRAY: {
                --frameStackCount;
                frameStackCount = pushDesc(frameStack, frameStackCount,
                                           "[" + Type.getObjectType(readClass(readUnsignedShort(v + 1), utfDecodeBuffer)).getDescriptor());
                v += 3;
                break;
            }
            case Opcodes.CHECKCAST: {
                --frameStackCount;
                frameStackCount = pushDesc(frameStack, frameStackCount,
                                           Type.getObjectType(readClass(readUnsignedShort(v + 1), utfDecodeBuffer)).getDescriptor());
                v += 3;
                break;
            }
                
            case Opcodes.MULTIANEWARRAY: {
                frameStackCount -= b[v + 3];
                frameStackCount = pushDesc(frameStack, frameStackCount,
                                           Type.getObjectType(readClass(readUnsignedShort(v + 1), utfDecodeBuffer)).getDescriptor());
                v += 4;
                break;
            }
            default: {
                throw new RuntimeException("unhandled opcode " + opcode);
            }
            }
        }
        // FIXME: WIDE
        return labelTypes;
    }
    
    private int pushDesc(final Object[] frame, int frameCount, final String desc) {
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
            // case 'L':
        default:
            if (index == 0) {
                frame[frameCount++] = desc.substring(1, desc.length() - 1);
            } else {
                frame[frameCount++] = desc.substring(index + 1, desc.length() - 1);
            }
        }
        return frameCount;
    }

    private int popDesc(int frameCount, final String desc) {
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
    private void invalidateTwoWordLocal(Object frameLocal[], int n) {
        if (n >= 0) {
            Object t = frameLocal[n];
            if (t == Opcodes.LONG || t == Opcodes.DOUBLE) {
                frameLocal[n] = Opcodes.TOP;
            }
        }
    }

    private void writeMethods() {
        startSplitMethods();
        writeBodyCode();
        visitExceptionHandlers();
        endSplitMethods();
    }


    private void writeBodyCode() {
        byte[] b = code.data; // bytecode of the method
        int v = 0;
        MethodVisitor mv = mainMethodVisitor;
        BasicBlock currentBlock = null;
        // whether the previous block may end by just falling through
        boolean fallThrough = false;
        while (v < code.length) {
            {
                Label l = upwardLabelsByOffset[v];
                if (l != null) {
                    mv.visitLabel(l);
                }
            }
            {
                BasicBlock block = blocksByOffset[v];
                if (block != null) {
                    SplitMethod m = block.sccRoot.splitMethod;
                    if (fallThrough && (m != currentBlock.sccRoot.splitMethod)) {
                        jumpToMethod(mv, block);
                    }
                    if (currentBlock != null) {
                        // needed for local variables
                        mv.visitLabel(currentBlock.getEndLabel());
                    }
                    if (m != null) {
                        mv = m.writer;
                        block.frameData.visitFrame(mv);
                    } else {
                        mv = mainMethodVisitor;
                    }
                    mv.visitLabel(block.getStartLabel());
                    currentBlock = block;
                }
            }
            {
                Label l = labelsByOffset[v];
                if (l != null) {
                    mv.visitLabel(l);
                    if (l.line > 0) {
                        mv.visitLineNumber(l.line, l);
                    }
                }
            }

            int opcode = b[v] & 0xFF;
            switch (ClassWriter.TYPE[opcode]) {
            case ClassWriter.NOARG_INSN:
                mv.visitInsn(opcode);
                v += 1;
                break;
            case ClassWriter.IMPLVAR_INSN:
                if (opcode > Opcodes.ISTORE) {
                    opcode -= 59; // ISTORE_0
                    mv.visitVarInsn(Opcodes.ISTORE + (opcode >> 2),
                                    opcode & 0x3);
                } else {
                    opcode -= 26; // ILOAD_0
                    mv.visitVarInsn(Opcodes.ILOAD + (opcode >> 2),
                                    opcode & 0x3);
                }
                v += 1;
                break;
            case ClassWriter.LABEL_INSN:
                handleJump(mv, opcode, currentBlock, blocksByOffset[v + readShort(v + 1)]);
                v += 3;
                break;
            case ClassWriter.LABELW_INSN:
                handleJump(mv, opcode - 33, currentBlock, blocksByOffset[v + readInt(v + 1)]);
                v += 5;
                break;
            case ClassWriter.WIDE_INSN:
                opcode = b[v + 1] & 0xFF;
                if (opcode == Opcodes.IINC) {
                    mv.visitIincInsn(readUnsignedShort(v + 2), readShort(v + 4));
                    v += 6;
                } else {
                    mv.visitVarInsn(opcode, readUnsignedShort(v + 2));
                    v += 4;
                }
                break;
            case ClassWriter.TABL_INSN: {
                // skips 0 to 3 padding bytes
                v = v & ~3;
                // reads instruction
                int label = v + readInt(v);
                int min = readInt(v + 4);
                int max = readInt(v + 8);
                v += 12;
                int size = max - min + 1;
                Label[] table = new Label[size];
                for (int j = 0; j < size; ++j) {
                    table[j] = blocksByOffset[v + readInt(v)].getStartLabel();
                    v += 4;
                }
                mv.visitTableSwitchInsn(min,
                                        max,
                                        blocksByOffset[label].getStartLabel(),
                                        table);
                break;
            }
            case ClassWriter.LOOK_INSN: {
                // skips 0 to 3 padding bytes
                v = v & ~3;
                // reads instruction
                int label = v + readInt(v);
                int size = readInt(v + 4);
                v += 8;
                int[] keys = new int[size];
                Label[] values = new Label[size];
                for (int j = 0; j < size; ++j) {
                    keys[j] = readInt(v);
                    values[j] = blocksByOffset[v + readInt(v + 4)].getStartLabel();
                    v += 8;
                }
                mv.visitLookupSwitchInsn(blocksByOffset[label].getStartLabel(),
                                         keys,
                                         values);
                break;
            }
            case ClassWriter.VAR_INSN:
                mv.visitVarInsn(opcode, b[v + 1] & 0xFF);
                v += 2;
                break;
            case ClassWriter.SBYTE_INSN:
                mv.visitIntInsn(opcode, b[v + 1]);
                v += 2;
                break;
            case ClassWriter.SHORT_INSN:
                mv.visitIntInsn(opcode, readShort(v + 1));
                v += 3;
                break;
            case ClassWriter.LDC_INSN:
                mv.visitLdcInsn(readConst(b[v + 1] & 0xFF, utfDecodeBuffer));
                v += 2;
                break;
            case ClassWriter.LDCW_INSN:
                mv.visitLdcInsn(readConst(readUnsignedShort(v + 1), utfDecodeBuffer));
                v += 3;
                break;
            case ClassWriter.FIELDORMETH_INSN:
            case ClassWriter.ITFMETH_INSN: {
                MemberSymRef sr = parseMemberSymRef(v + 1);
                if (opcode < Opcodes.INVOKEVIRTUAL) {
                    mv.visitFieldInsn(opcode, sr.owner, sr.name, sr.desc);
                } else {
                    mv.visitMethodInsn(opcode, sr.owner, sr.name, sr.desc);
                }
                if (opcode == Opcodes.INVOKEINTERFACE) {
                    v += 5;
                } else {
                    v += 3;
                }
                break;
            }
            case ClassWriter.INDYMETH_INSN: {
                DynamicSymRef sr = parseDynamicSymRef(v + 1);

                byte[] bm = cw.bootstrapMethods.data;
                
                int bsmIndex = sr.bsmIndex;
                int mhIndex = ByteArray.readUnsignedShort(bm, bsmIndex);
                Handle bsm = (Handle) readConst(mhIndex, utfDecodeBuffer);
                int bsmArgCount = ByteArray.readUnsignedShort(bm, bsmIndex + 2);
                Object[] bsmArgs = new Object[bsmArgCount];
                bsmIndex += 4;
                for(int a = 0; a < bsmArgCount; a++) {
                    int argIndex = ByteArray.readUnsignedShort(bm, bsmIndex);
                    bsmArgs[a] = readConst(argIndex, utfDecodeBuffer);
                    bsmIndex += 2;
                }
                mv.visitInvokeDynamicInsn(sr.name, sr.desc, bsm, bsmArgs);
                
                v += 5;
                break;
            }
            case ClassWriter.TYPE_INSN:
                mv.visitTypeInsn(opcode, readClass(readUnsignedShort(v + 1), utfDecodeBuffer));
                v += 3;
                break;
            case ClassWriter.IINC_INSN:
                mv.visitIincInsn(b[v + 1] & 0xFF, b[v + 2]);
                v += 3;
                break;
                // case MANA_INSN:
            default:
                mv.visitMultiANewArrayInsn(readClass(readUnsignedShort(v + 1), utfDecodeBuffer), b[v + 3] & 0xFF);
                v += 4;
                break;
            }

            switch (opcode) {
            case Opcodes.GOTO:
            case 200: // GOTO_W
            case Opcodes.RET:
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
            case Opcodes.RETURN:
            case Opcodes.ATHROW:
                fallThrough = false;
                break;
            default:
                fallThrough = true;
                break;
            }
        }

        // finish off the final block
        if (currentBlock != null) {
            mv.visitLabel(currentBlock.getEndLabel());
        }
        if (upwardLabelsByOffset[v] != null) {
            mv.visitLabel(upwardLabelsByOffset[v]);
        }
    }

    private void handleJump(MethodVisitor mv, int opcode, BasicBlock currentBlock, BasicBlock target) {
        SplitMethod m = target.sccRoot.splitMethod;
        if (m != currentBlock.sccRoot.splitMethod) {
            int reverse = reverseBranch(opcode);
            if (reverse != -1) {
                // ##### JSR
                Label over = new Label();
                mv.visitJumpInsn(reverse, over);
                jumpToMethod(mv, target);
                mv.visitLabel(over);
            } else {
                jumpToMethod(mv, target);
            }
        } else {
            mv.visitJumpInsn(opcode, target.getStartLabel());
        }
    }

    private void jumpToMethod(MethodVisitor mv, BasicBlock target) {
        target.sccRoot.splitMethod.visitJumpTo(cw, mv);
    }
                                           


    private int reverseBranch(int opcode) {
        int reverse = -1;
        switch (opcode) {
        case Opcodes.IFEQ: 
            reverse = Opcodes.IFNE;
            break;
        case Opcodes.IFNE:
            reverse = Opcodes.IFEQ;
            break;
        case Opcodes.IFLT:
            reverse = Opcodes.IFGE;
            break;
        case Opcodes.IFGE:
            reverse = Opcodes.IFLT;
            break;
        case Opcodes.IFGT:
            reverse = Opcodes.IFLE;
            break;
        case Opcodes. IFLE:
            reverse = Opcodes.IFGT;
            break;
        case Opcodes.IF_ICMPEQ:
            reverse = Opcodes.IF_ICMPNE;
            break;
        case Opcodes.IF_ICMPNE:
            reverse = Opcodes.IF_ICMPEQ;
            break;
        case Opcodes.IF_ICMPLT:
            reverse = Opcodes.IF_ICMPGE;
            break;
        case Opcodes.IF_ICMPGE:
            reverse = Opcodes.IF_ICMPLT;
            break;
        case Opcodes.IF_ICMPGT:
            reverse = Opcodes.IF_ICMPLE;
            break;
        case Opcodes.IF_ICMPLE:
            reverse = Opcodes.IF_ICMPGT;
            break;
        case Opcodes.IF_ACMPEQ:
            reverse = Opcodes.IF_ACMPNE;
            break;
        case Opcodes.IF_ACMPNE:
            reverse = Opcodes.IF_ACMPEQ;
            break;
        }
        return reverse;
    }


    void visitExceptionHandlers() {
        Handler h = firstHandler;
        while (h != null) {
            BasicBlock start = blocksByOffset[h.start.position];
            BasicBlock end = blocksByOffset[h.end.position];
            BasicBlock handler = blocksByOffset[h.handler.position];
            SplitMethod m = handler.sccRoot.splitMethod;
            assert m == start.sccRoot.splitMethod;
            assert m == end.sccRoot.splitMethod;
            MethodVisitor mv;
            if (m == null) {
                mv = mainMethodVisitor;
            } else {
                mv = m.writer;
            }
            mv.visitTryCatchBlock(start.getStartLabel(), end.getStartLabel(), handler.getStartLabel(), h.desc);
            h = h.next;
        }
    }

    /**
     * @return main split method
     */
    private void startSplitMethods() {
        for (SplitMethod m : splitMethods) {
            m.writer.visitCode();
            m.entry.frameData.reconstructStack(m.writer); // FIXME: intermediate methods, maybe 2
        }
        mainMethodVisitor.visitCode();
    }
    
    private void endSplitMethods() {
        for (SplitMethod m : splitMethods) {
            m.writer.visitMaxs(0, 0);
            m.writer.visitEnd();
        }
        mainMethodVisitor.visitMaxs(0, 0);
        mainMethodVisitor.visitEnd();
    }
        

    /**
     * Get array, indexed by code pointer, of all labels.
     */
    private BasicBlock[] computeBlocksByOffset(TreeSet<BasicBlock> blocks) {
        BasicBlock[] array = new BasicBlock[code.length];
        for (BasicBlock b : blocks) {
            array[b.position] = b;
        }
        return array;
    }

    /**
     * Create all method writers.
     */
    private void  makeMethodWriters(HashMap<Label, String> labelTypes) {
        String[] exceptionNames = null;
        if (exceptions != null) {
            exceptionNames = new String[exceptions.length];
            int i = 0;
            while (i < exceptions.length) {
                exceptionNames[i] = readUTF8Item(name, utfDecodeBuffer);
                ++i;
            }
        }
        for (SplitMethod m : splitMethods) {
            m.setSplitMethodWriter(cw, cv,
                                   descriptor,
                                   exceptionNames,
                                   labelTypes);
        }
        SplitMethodWriterFactory smwf = (SplitMethodWriterFactory) cw.getMethodWriterFactory();
        smwf.computeMaxsOverride = true;
        smwf.computeFramesOverride = false;
        smwf.split = false;
        smwf.register = false;
        mainMethodVisitor =
            cv.visitMethod(access,
                           thisName,
                           descriptor,
                           signature,
                           exceptionNames);
        /*
         * Major kludge:
         * We really need the MethodWriter, as we're calling its
         * getSize and put methods.  But visitMethod may give us a
         * visitor wrapped around a MethodWriter.  Instead,
         * SplitMethodWriterFactory records the instance that must be
         * sitting insite the visitor.
         */
        mainMethodWriter = SplitMethodWriterFactory.lastInstance;
        smwf.setDefaults();
    }

    private void visitLineNumberLabels() {
        int v = 0;
        byte[] b = lineNumber.data;
        while (v < lineNumber.length) {
            int offset = ByteArray.readUnsignedShort(b, v);
            Label l = getLabelAt(offset);
            l.line = ByteArray.readUnsignedShort(b, v + 2);
            v += 4;
        }
    }

    private void visitLocalVarLabels() {
        int v = 0;
        byte[] b = localVar.data;
        while (v < localVar.length) {
            int from = ByteArray.readUnsignedShort(b, v);
            getLabelAt(from);
            int to = from + ByteArray.readUnsignedShort(b, v + 2);
            if (upwardLabelsByOffset[to] == null) {
                upwardLabelsByOffset[to] = new Label();
            }
            v += 10;
        }
    }

    private void visitLocalVars() {
        int[] typeTable = null;
        if (localVarType != null) {
            byte[] b = localVarType.data;
            int k = localVarTypeCount * 3;
            int w = 0;
            typeTable = new int[k];
            while (k > 0) {
                typeTable[--k] = ByteArray.readUnsignedShort(b, w + 6); // item index of signature
                typeTable[--k] = ByteArray.readUnsignedShort(b, w + 8); // index
                typeTable[--k] = ByteArray.readUnsignedShort(b, w); // start
                w += 10;
            }        }
        {
            byte[] b = localVar.data;
            int k = localVarCount;
            int w = 0;
            for (; k > 0; --k) {
                int start = ByteArray.readUnsignedShort(b, w);
                int length = ByteArray.readUnsignedShort(b, w + 2);
                int index = ByteArray.readUnsignedShort(b, w + 8);
                String vsignature = null;
                if (typeTable != null) {
                    for (int a = 0; a < typeTable.length; a += 3) {
                        if ((typeTable[a] == start) && (typeTable[a + 1] == index)) {
                            vsignature = readUTF8Item(typeTable[a + 2], utfDecodeBuffer);
                            break;
                        }
                    }
                }
                visitLocalVariable(readUTF8Item(ByteArray.readUnsignedShort(b, w + 4), utfDecodeBuffer),
                                   readUTF8Item(ByteArray.readUnsignedShort(b, w + 6), utfDecodeBuffer),
                                   vsignature,
                                   start, length, index);
                w += 10;
            }
        }
    }
    
    private void visitLocalVariable(String name, String desc, String signature,
                                    int start, int length, int index) {
        HashMap<MethodVisitor, Label> startLabels = new HashMap<MethodVisitor, Label>();
        HashMap<MethodVisitor, Label> endLabels = new HashMap<MethodVisitor, Label>();

        // first search backwards for the basic block we're in
        MethodVisitor mv = null;
        {
            int i = start;
            while (i >= 0) {
                BasicBlock b = blocksByOffset[i];
                if (b != null) {
                    SplitMethod m = b.sccRoot.splitMethod;
                    if (m != null) {
                        mv = m.writer;
                    } else {
                        mv = mainMethodVisitor;
                    }
                    break;
                }
                --i;
            }
        }
        if (mv == null) {
            mv = mainMethodVisitor;
        }

        startLabels.put(mv, labelsByOffset[start]);

        // ... then move forward
        int v = start;
        int end = start + length;
        BasicBlock currentBlock = null;
        while (v < end) {
            BasicBlock b = blocksByOffset[v];
            if (b != null) {
                // push the end forward
                if (currentBlock != null) {
                    endLabels.put(mv, currentBlock.getEndLabel());
                }
                SplitMethod m = b.sccRoot.splitMethod;
                if (m != null) {
                    mv = m.writer;
                } else {
                    mv = mainMethodVisitor;
                }
                Label startLabel = startLabels.get(mv);
                if (startLabel == null) {
                    startLabels.put(mv, b.getStartLabel());
                }
                currentBlock = b;
            }
            ++v;
        }
        // final end
        endLabels.put(mv, upwardLabelsByOffset[end]);
                
        for (Map.Entry<MethodVisitor, Label> entry : startLabels.entrySet()) {
            mv = entry.getKey();
            Label startLabel = entry.getValue();
            Label endLabel = endLabels.get(mv);
            assert endLabel != null;
            mv.visitLocalVariable(name, desc, signature, startLabel, endLabel, index);
        }
        
    }

    

    private void transferAnnotations() {
        /*
         * Parsing the annotations would be a huge pain; we just copy
         * the bytes directly.
         */
        mainMethodWriter.setAnnotations(annd, anns, ianns, panns, ipanns, synthetics);
    }

    private void transferNonstandardAttributes() {
        /*
         * We don't know what these look like, so we copy them directly.
         */
        if (cattrs != null) {
            throw new RuntimeException("don't know how to transfer code attributes when splitting a method.");
        }
        mainMethodWriter.setNonstandardAttributes(attrs, cattrs);
    }

    

    private Label getLabelAt(int offset) {
        Label l = labelsByOffset[offset];
        if (l == null) {
            l = new Label();
            labelsByOffset[offset] = l;
        }
        return l;
    }

    /**
     * Returns the size of the bytecode of this method.
     *
     * @return the size of the bytecode of this method.
     */
    @Override
    public int getSize() {
        return mainMethodWriter.getSize();
    }
    
    /**
     * Puts the bytecode of this method in the given byte vector.
     *
     * @param out the byte vector into which the bytecode of this method must be
     *        copied.
     */
    @Override
    public void put(ByteVector out) {
        mainMethodWriter.put(out);
    }

    //
    // Constant pool
    //
    /**
     * Reads a class constant pool item in {@link #b b}. <i>This method is
     * intended for {@link Attribute} sub classes, and is normally not needed by
     * class generators or adapters.</i>
     *
     * @param index the index of a constant pool class item.
     * @param buf buffer to be used to read the item. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified class item.
     */
    public String readClass(final int itemIndex, final char[] buf) {
        // computes the start index of the CONSTANT_Class item in b
        // and reads the CONSTANT_Utf8 item designated by
        // the first two bytes of this CONSTANT_Class item
        int classOffset = items[itemIndex];
        return readUTF8Item(ByteArray.readUnsignedShort(pool.data, classOffset), buf);
    }


    // ------------------------------------------------------------------------
    // Utility methods: low level parsing
    // ------------------------------------------------------------------------


    /**
     * Reads a byte value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readByte(final int index) {
        return ByteArray.readByte(code.data, index);
    }

    /**
     * Reads an unsigned short value in {@link #b b}. <i>This method is
     * intended for {@link Attribute} sub classes, and is normally not needed by
     * class generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readUnsignedShort(final int index) {
        return ByteArray.readUnsignedShort(code.data, index);
    }

    /**
     * Reads a signed short value in {@link #b b}. <i>This method is intended
     * for {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public short readShort(final int index) {
        return ByteArray.readShort(code.data, index);
    }

    /**
     * Reads a signed int value in {@link #b b}. <i>This method is intended for
     * {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public int readInt(final int index) {
        return ByteArray.readInt(code.data, index);
    }

    /**
     * Reads a signed long value in {@link #b b}. <i>This method is intended
     * for {@link Attribute} sub classes, and is normally not needed by class
     * generators or adapters.</i>
     *
     * @param index the start index of the value to be read in {@link #b b}.
     * @return the read value.
     */
    public long readLong(final int index) {
        return ByteArray.readLong(code.data, index);
    }


    /**
     * Reads an UTF8 string constant pool item in {@link #b b}. <i>This method
     * is intended for {@link Attribute} sub classes, and is normally not needed
     * by class generators or adapters.</i>
     *
     * @param index the start index of an unsigned short value in {@link #b b},
     *        whose value is the index of an UTF8 constant pool item.
     * @param buf buffer to be used to read the item. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the String corresponding to the specified UTF8 item.
     */
    public String readUTF8(int index, final char[] buf) {
        return readUTF8Item(readUnsignedShort(index), buf);
    }

    public String readUTF8Item(int item, final char[] buf) {
        int offset = items[item];
        return ByteArray.readUTF8(pool.data, offset + 2, ByteArray.readUnsignedShort(pool.data, offset), buf);
    }


    /**
     * Reads a numeric or string constant pool item in {@link #b b}. <i>This
     * method is intended for {@link Attribute} sub classes, and is normally not
     * needed by class generators or adapters.</i>
     *
     * @param item the index of a constant pool item.
     * @param buf buffer to be used to read the item. This buffer must be
     *        sufficiently large. It is not automatically resized.
     * @return the {@link Integer}, {@link Float}, {@link Long}, {@link Double},
     *         {@link String}, {@link Type} or {@link Handle} corresponding to
     *         the given constant pool item.
     */
    public Object readConst(final int item, final char[] buf) {
        int index = items[item];
        byte[] b = pool.data;
        switch (b[index - 1]) {
            case ClassWriter.INT:
                return new Integer(readInt(index));
            case ClassWriter.FLOAT:
                return new Float(Float.intBitsToFloat(readInt(index)));
            case ClassWriter.LONG:
                return new Long(readLong(index));
            case ClassWriter.DOUBLE:
                return new Double(Double.longBitsToDouble(readLong(index)));
            case ClassWriter.CLASS:
                return Type.getObjectType(readUTF8(index, buf));
            case ClassWriter.STR:
                return readUTF8(index, buf);
            case ClassWriter.MTYPE:
                return Type.getMethodType(readUTF8(index, buf));

            //case ClassWriter.HANDLE_BASE + [1..9]:
            default: {
                int tag = readByte(index);
                int[] items = this.items;
                int cpIndex = items[readUnsignedShort(index + 1)];
                String owner = readClass(cpIndex, buf);
                cpIndex = items[readUnsignedShort(cpIndex + 2)];
                String name = readUTF8(cpIndex, buf);
                String desc = readUTF8(cpIndex + 2, buf);
                return new Handle(tag, owner, name, desc);
            }
        }
    }
}