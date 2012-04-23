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

    MethodWriter mainMethodWriter;

    BasicBlock[] blocksByOffset;
    /* labels not associated with a basic block - NEW instructions */
    Label[] labelsByOffset;

    /**
     * Maximum length of the strings contained in the constant pool of the
     * class.
     */
    private int maxStringLength;

    private char[] utfDecodeBuffer;

    int[] items;

    String thisName;

    int[] bootstrapMethods;

    public SplitMethodWriterDelegate(final int maxMethodLength) {
        this.maxMethodLength = maxMethodLength;
    }

    void split() {
        TreeSet<BasicBlock> blocks = Split.initializeAll(code);
        this.scc = blocks.first().sccRoot;
        this.splitMethods = scc.split(maxMethodLength);
        this.blocksByOffset = computeBlocksByOffset(blocks);
        this.labelsByOffset = new Label[code.length];
        parseConstantPool();
        thisName = readUTF8Item(name, utfDecodeBuffer);
        parseStackMap();
        parseBootstrapMethods();
        makeMethodWriters();
        writeMethods();
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
                        int n = frameStackCount = readUnsignedShort(v);
                        v += 2;
                        for (int j = 0; n > 0; n--) {
                            v = readFrameType(frameStack, j++, v, utfDecodeBuffer);
                        }
                    }
                }
            }
            frameOffset += delta + 1;

            // the first frame can't be a reasonable split point
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
            frame[index] = readClass(v, buf);
            v += 2;
            break;
        default: { // Uninitialized
            int offset = ByteArray.readUnsignedShort(b, v);
            Label label = labelsByOffset[offset];
            if (label == null) {
                label = new Label();
                labelsByOffset[offset] = label;
            }
            frame[index] = label;
            v += 2;
        }
        }
        return v;
    }

    private void writeMethods() {
        // FIXME: calls to split methods

        // FIXME: visitTryCatchBlock
        // FIXME: visitLocalVariable
        // FIXME: visitLineNumber
        startSplitMethods();
        writeBodyCode();
        endSplitMethods();
    }

    private void writeBodyCode() {
        byte[] b = code.data; // bytecode of the method
        int v = 0;
        MethodWriter mw = null;
        while (v < code.length) {
            BasicBlock block = blocksByOffset[v];
            if (block != null) {
                SplitMethod m = block.sccRoot.splitMethod;
                if (m != null) {
                    mw = m.writer;
                    mw.visitLabel(block.getOutputLabel());
                    block.frameData.visitFrame(mw);
                } else {
                    mw = mainMethodWriter;
                }
            }
            {
                Label l = labelsByOffset[v];
                if (l != null) {
                    mw.visitLabel(l);
                }
            }

            int opcode = b[v] & 0xFF;
            switch (ClassWriter.TYPE[opcode]) {
            case ClassWriter.NOARG_INSN:
                mw.visitInsn(opcode);
                v += 1;
                break;
            case ClassWriter.IMPLVAR_INSN:
                if (opcode > Opcodes.ISTORE) {
                    opcode -= 59; // ISTORE_0
                    mw.visitVarInsn(Opcodes.ISTORE + (opcode >> 2),
                                    opcode & 0x3);
                } else {
                    opcode -= 26; // ILOAD_0
                    mw.visitVarInsn(Opcodes.ILOAD + (opcode >> 2),
                                    opcode & 0x3);
                }
                v += 1;
                break;
            case ClassWriter.LABEL_INSN:
                mw.visitJumpInsn(opcode, blocksByOffset[v + readShort(v + 1)].getOutputLabel());
                v += 3;
                break;
            case ClassWriter.LABELW_INSN:
                mw.visitJumpInsn(opcode - 33, blocksByOffset[v + readInt(v + 1)].getOutputLabel());
                v += 5;
                break;
            case ClassWriter.WIDE_INSN:
                opcode = b[v + 1] & 0xFF;
                if (opcode == Opcodes.IINC) {
                    mw.visitIincInsn(readUnsignedShort(v + 2), readShort(v + 4));
                    v += 6;
                } else {
                    mw.visitVarInsn(opcode, readUnsignedShort(v + 2));
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
                    table[j] = blocksByOffset[v + readInt(v)].getOutputLabel();
                    v += 4;
                }
                mw.visitTableSwitchInsn(min,
                                        max,
                                        blocksByOffset[label].getOutputLabel(),
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
                     values[j] = blocksByOffset[v + readInt(v + 4)].getOutputLabel();
                    v += 8;
                }
                mw.visitLookupSwitchInsn(blocksByOffset[label].getOutputLabel(),
                                         keys,
                                         values);
                break;
            }
            case ClassWriter.VAR_INSN:
                mw.visitVarInsn(opcode, b[v + 1] & 0xFF);
                v += 2;
                break;
            case ClassWriter.SBYTE_INSN:
                mw.visitIntInsn(opcode, b[v + 1]);
                v += 2;
                break;
            case ClassWriter.SHORT_INSN:
                mw.visitIntInsn(opcode, readShort(v + 1));
                v += 3;
                break;
            case ClassWriter.LDC_INSN:
                mw.visitLdcInsn(readConst(b[v + 1] & 0xFF, utfDecodeBuffer));
                v += 2;
                break;
            case ClassWriter.LDCW_INSN:
                mw.visitLdcInsn(readConst(readUnsignedShort(v + 1), utfDecodeBuffer));
                v += 3;
                break;
            case ClassWriter.FIELDORMETH_INSN:
            case ClassWriter.ITFMETH_INSN: {
                 int cpIndex = items[readUnsignedShort(v + 1)];
                 String iowner = readClass(cpIndex, utfDecodeBuffer);
                 cpIndex = items[readUnsignedShort(cpIndex + 2)];
                 String iname = readUTF8(cpIndex, utfDecodeBuffer);
                 String idesc = readUTF8(cpIndex + 2, utfDecodeBuffer);
                if (opcode < Opcodes.INVOKEVIRTUAL) {
                    mw.visitFieldInsn(opcode, iowner, iname, idesc);
                } else {
                    mw.visitMethodInsn(opcode, iowner, iname, idesc);
                }
                if (opcode == Opcodes.INVOKEINTERFACE) {
                    v += 5;
                } else {
                    v += 3;
                }
                break;
            }
            case ClassWriter.INDYMETH_INSN: {
                int cpIndex = items[readUnsignedShort(v + 1)];
                int bsmIndex = bootstrapMethods[readUnsignedShort(cpIndex)];
                cpIndex = items[readUnsignedShort(cpIndex + 2)];
                String iname = readUTF8(cpIndex, utfDecodeBuffer);
                String idesc = readUTF8(cpIndex + 2, utfDecodeBuffer);

                byte[] bm = cw.bootstrapMethods.data;
                
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
                mw.visitInvokeDynamicInsn(iname, idesc, bsm, bsmArgs);
                
                v += 5;
                break;
            }
            case ClassWriter.TYPE_INSN:
                mw.visitTypeInsn(opcode, readClass(v + 1, utfDecodeBuffer));
                v += 3;
                break;
            case ClassWriter.IINC_INSN:
                    mw.visitIincInsn(b[v + 1] & 0xFF, b[v + 2]);
                v += 3;
                break;
                // case MANA_INSN:
            default:
                mw.visitMultiANewArrayInsn(readClass(v + 1, utfDecodeBuffer), b[v + 3] & 0xFF);
                v += 4;
                break;
            }
        }
    }

    /**
     * @return main split method
     */
    private void startSplitMethods() {
        for (SplitMethod m : splitMethods) {
            m.writer.visitCode();
            m.entry.frameData.reconstructStack(m.writer);
        }
        mainMethodWriter.visitCode();
    }
    
    private void endSplitMethods() {
        for (SplitMethod m : splitMethods) {
            m.writer.visitMaxs(0, 0);
            m.writer.visitEnd();
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
     * Create all method writers.
     */
    private void  makeMethodWriters() {
        int id = 0;
        for (SplitMethod m : splitMethods) {
            m.setSplitMethodWriter(cw,
                                   access,
                                   cw.newUTF8(thisName + "#split#" + id),
                                   descriptor,
                                   signature,
                                   exceptions);
            ++id;
        }
        mainMethodWriter = new MethodWriter(cw, 
                                            access,
                                            name,
                                            descriptor, desc,
                                            signature,  // #### this is all provisional
                                            exceptions, 
                                            true, // computeMaxs
                                            false,  // computeFrames
                                            false, // register
                                            null);
    }

    /**
     * Returns the size of the bytecode of this method.
     *
     * @return the size of the bytecode of this method.
     */
    @Override
    public int getSize() {
        split();
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