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

final class SplitMethodWriterDelegate extends MethodWriterDelegate {

    final int maxMethodLength;

    Scc scc;

    HashSet<SplitMethod> splitMethods;
    
    BasicBlock[] blocksByOffset;

    /**
     * Maximum length of the strings contained in the constant pool of the
     * class.
     */
    private int maxStringLength;

    private char[] buffer;

    int[] items;

    String thisName;

    public SplitMethodWriterDelegate(final int maxMethodLength) {
        this.maxMethodLength = maxMethodLength;
    }

    void split() {
        TreeSet<BasicBlock> blocks = Split.initializeAll(code);
        this.scc = blocks.first().sccRoot;
        this.splitMethods = scc.split(maxMethodLength);
        this.blocksByOffset = computeBlocksByOffset(blocks);
        parseConstantPool();
        thisName = readUTF8Item(name, buffer);
        parseStackMap();
        makeSplitMethodWriters();
        writeCode();
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
        buffer = new char[maxStringLength];
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
                v = readFrameType(frameStack, 0, v, buffer);
                frameStackCount = 1;

            } else {
                delta = ByteArray.readUnsignedShort(b, v);
                v += 2;
                if (tag == MethodWriter.SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
                    v = readFrameType(frameStack, 0, v, buffer);
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
                        v = readFrameType(frameLocal, j++, v, buffer);
                    }
                    frameLocalCount += tag - MethodWriter.SAME_FRAME_EXTENDED;
                    frameStackCount = 0;
                } else { // if (tag == FULL_FRAME) {
                    {
                        int n = frameLocalCount = ByteArray.readUnsignedShort(b, v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(frameLocal, j++, v, buffer);
                        }
                    }
                    {
                        int n = frameStackCount = readUnsignedShort(v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(frameStack, j++, v, buffer);
                        }
                    }
                }
            }
            frameOffset += delta + 1;

            // the first frame can't be a reasonable split point
            BasicBlock block = blocksByOffset[frameOffset];
            if (block != null) {
                SplitMethod m = block.sccRoot.splitMethod;
                if ((m != null) && (m.entry == block)) {
                    m.frameLocal = Arrays.copyOf(frameLocal, frameLocalCount);
                    m.frameStack = Arrays.copyOf(frameStack, frameStackCount);
                }
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
            frame[index] = readClass(v, buf);
            v += 2;
            break;
        default: // Uninitialized
            frame[index] = blocksByOffset[ByteArray.readUnsignedShort(b, v)];
            v += 2;
        }
        return v;
    }


    private void writeCode() {
        byte[] b = code.data; // bytecode of the method
        int j;
        int v = 0;
        MethodWriter mw = null;
        while (v < b.length) {
            BasicBlock block = blocksByOffset[v];
            if (block != null) {
                SplitMethod m = block.sccRoot.splitMethod;
                if (m == null)
                    mw = null;
                else
                    mw = m.writer;
            }

            int opcode = b[v] & 0xFF;
            switch (ClassWriter.TYPE[opcode]) {
            case ClassWriter.NOARG_INSN:
                //                mv.visitInsn(opcode);
                v += 1;
                break;
            case ClassWriter.IMPLVAR_INSN:
                if (opcode > Opcodes.ISTORE) {
                    opcode -= 59; // ISTORE_0
                    //mv.visitVarInsn(Opcodes.ISTORE + (opcode >> 2),
                    //                opcode & 0x3);
                } else {
                    opcode -= 26; // ILOAD_0
                    //                    mv.visitVarInsn(Opcodes.ILOAD + (opcode >> 2),
                    //                                    opcode & 0x3);
                }
                v += 1;
                break;
            case ClassWriter.LABEL_INSN:
                //                mv.visitJumpInsn(opcode, labels[w
                //                                                + readShort(v + 1)]);
                v += 3;
                break;
            case ClassWriter.LABELW_INSN:
                //                mv.visitJumpInsn(opcode - 33, labels[w
                //                                                     + readInt(v + 1)]);
                v += 5;
                break;
            case ClassWriter.WIDE_INSN:
                opcode = b[v + 1] & 0xFF;
                if (opcode == Opcodes.IINC) {
                    //                    mv.visitIincInsn(readUnsignedShort(v + 2),
                    //                                     readShort(v + 4));
                    v += 6;
                } else {
                    //                    mv.visitVarInsn(opcode,
                    //                                    readUnsignedShort(v + 2));
                    v += 4;
                }
                break;
            case ClassWriter.TABL_INSN:
                // skips 0 to 3 padding bytes
                v = v & ~3;
                // reads instruction
//                 label = v + readInt(v);
                int min = readInt(v + 4);
                int max = readInt(v + 8);
                v += 12;
                int size = max - min + 1;
//                 Label[] table = new Label[size];
                for (j = 0; j < size; ++j) {
//                     table[j] = labels[v + readInt(v)];
                    v += 4;
                }
//                 mv.visitTableSwitchInsn(min,
//                                         max,
//                                         labels[label],
//                                         table);
                break;
            case ClassWriter.LOOK_INSN:
                // skips 0 to 3 padding bytes
                v = v & ~3;
                // reads instruction
//                 label = v + readInt(v);
                j = readInt(v + 4);
                v += 8;
//                 int[] keys = new int[j];
//                 Label[] values = new Label[j];
                for (j = 0; j < j; ++j) { // ##################FIXME HARD
//                     keys[j] = readInt(v);
//                     values[j] = labels[v + readInt(v + 4)];
                    v += 8;
                }
//                 mv.visitLookupSwitchInsn(labels[label],
//                                          keys,
//                                          values);
                break;
            case ClassWriter.VAR_INSN:
//                 mv.visitVarInsn(opcode, b[v + 1] & 0xFF);
                v += 2;
                break;
            case ClassWriter.SBYTE_INSN:
//                 mv.visitIntInsn(opcode, b[v + 1]);
                v += 2;
                break;
            case ClassWriter.SHORT_INSN:
//                 mv.visitIntInsn(opcode, readShort(v + 1));
                v += 3;
                break;
            case ClassWriter.LDC_INSN:
//                 mv.visitLdcInsn(readConst(b[v + 1] & 0xFF, c));
                v += 2;
                break;
            case ClassWriter.LDCW_INSN:
//                 mv.visitLdcInsn(readConst(readUnsignedShort(v + 1),
//                                           c));
                v += 3;
                break;
            case ClassWriter.FIELDORMETH_INSN:
            case ClassWriter.ITFMETH_INSN: {
//                 int cpIndex = items[readUnsignedShort(v + 1)];
//                 String iowner = readClass(cpIndex, c);
//                 cpIndex = items[readUnsignedShort(cpIndex + 2)];
//                 String iname = readUTF8(cpIndex, c);
//                 String idesc = readUTF8(cpIndex + 2, c);
                if (opcode < Opcodes.INVOKEVIRTUAL) {
//                     mv.visitFieldInsn(opcode, iowner, iname, idesc);
                } else {
//                     mv.visitMethodInsn(opcode, iowner, iname, idesc);
                }
                if (opcode == Opcodes.INVOKEINTERFACE) {
                    v += 5;
                } else {
                    v += 3;
                }
                break;
            }
            case ClassWriter.INDYMETH_INSN: {
//                 int cpIndex = items[readUnsignedShort(v + 1)];
//                 int bsmIndex = bootstrapMethods[readUnsignedShort(cpIndex)];
//                 cpIndex = items[readUnsignedShort(cpIndex + 2)];
//                 String iname = readUTF8(cpIndex, c);
//                 String idesc = readUTF8(cpIndex + 2, c);

//                 int mhIndex = readUnsignedShort(bsmIndex);
//                 Handle bsm = (Handle) readConst(mhIndex, c);
//                 int bsmArgCount = readUnsignedShort(bsmIndex + 2);
//                 Object[] bsmArgs = new Object[bsmArgCount];
//                 bsmIndex += 4;
//                 for(int a = 0; a < bsmArgCount; a++) {
//                     int argIndex = readUnsignedShort(bsmIndex);
//                     bsmArgs[a] = readConst(argIndex, c);
//                     bsmIndex += 2;
//                 }
//                 mv.visitInvokeDynamicInsn(iname, idesc, bsm, bsmArgs);

                v += 5;
                break;
            }
            case ClassWriter.TYPE_INSN:
//                 mv.visitTypeInsn(opcode, readClass(v + 1, c));
                v += 3;
                break;
            case ClassWriter.IINC_INSN:
//                 mv.visitIincInsn(b[v + 1] & 0xFF, b[v + 2]);
                v += 3;
                break;
                // case MANA_INSN:
            default:
//                 mv.visitMultiANewArrayInsn(readClass(v + 1, c),
//                                            b[v + 3] & 0xFF);
                v += 4;
                break;
            }
        }
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
     * Get array, indexed by code pointer, of all method writers.
     */
    private void makeSplitMethodWriters() {
        int id = 0;
        for (SplitMethod m : splitMethods) {
            m.setMethodWriter(cw,
                              access,
                              thisName, id,
                              descriptor,
                              signature,
                              exceptions);
            ++id;
        }
    }

    /**
     * Returns the size of the bytecode of this method.
     *
     * @return the size of the bytecode of this method.
     */
    @Override
    public int getSize() {
        split();
        return 0; // ####
    }
    

    

   
    /**
     * Puts the bytecode of this method in the given byte vector.
     *
     * @param out the byte vector into which the bytecode of this method must be
     *        copied.
     */
    @Override
    public void put(ByteVector out) {
    }

    //
    // Constant pool
    //
    public String readClass(final int index, final char[] buf) {
        // computes the start index of the CONSTANT_Class item in b
        // and reads the CONSTANT_Utf8 item designated by
        // the first two bytes of this CONSTANT_Class item
        return readUTF8(items[ByteArray.readUnsignedShort(stackMap.data, index)], buf);
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

}