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
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

final public class SplitMethodWriterDelegate extends MethodWriterDelegate {

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
    String thisName;

    INameGenerator nameGenerator;

    ConstantPool constantPool;

    class Branch {
        public Label label;
        public int reference;
        public Branch(Label label, int reference) {
            this.label = label;
            this.reference = reference;
        }
    }
        
    ArrayList<Branch> largeBranches;

    Label[] largeBranchTargets;

    public SplitMethodWriterDelegate(INameGenerator nameGenerator) {
        this.maxMethodLength = ClassWriter.MAX_CODE_LENGTH;
        this.nameGenerator = nameGenerator;
    }

    public SplitMethodWriterDelegate() {
        this(new HashNameGenerator());
    }


    @Override
    public void newMethod() {
        this.largeBranches = new ArrayList<Branch>();
    }


    @Override
    public void noteTooLargeOffset(Label label, int reference) {
        largeBranches.add(new Branch(label, reference));
    }

    @Override
    public void visitEnd() {
        if ((version & 0xFFFF) < Opcodes.V1_6) {
            throw new RuntimeException("JVM version < 1.6 not supported");
        }
        boolean isStatic = (access & Opcodes.ACC_STATIC) == 0;
        constantPool = new ConstantPool(pool, poolSize, cw.bootstrapMethods, cw.bootstrapMethodsCount);
        thisName = constantPool.readUTF8Item(name);
        cv = cw.getFirstVisitor();

        Object[] frameLocal = new Object[maxLocals];
        int frameLocalCount = computeMethodDescriptorFrame(cw.thisName, thisName, isStatic, this.descriptor, frameLocal);
        FrameData[] frameDataByOffset = new FrameData[code.length + 1];
        this.labelsByOffset = new Label[code.length];
        BasicBlock.parseStackMap(stackMap, constantPool, frameCount, maxLocals, frameLocalCount, frameLocal, maxStack, labelsByOffset, frameDataByOffset);
        this.largeBranchTargets = computeLargeBranchTargets(largeBranches);
        this.blocksByOffset = new BasicBlock[code.length + 2];
        TreeSet<BasicBlock> blocks = new TreeSet<BasicBlock>();
        HashMap<Label, String> labelTypes = new HashMap<Label, String>();
        BasicBlock.computeFlowgraph(code, firstHandler, largeBranchTargets,
                                    constantPool, cw.thisName,
                                    maxStack, maxLocals,
                                    frameDataByOffset,
                                    maxMethodLength,
                                    blocks,
                                    blocksByOffset, labelsByOffset,
                                    labelTypes);
        CycleEquivalence.compute(blocks);
        BasicBlock.computeLocalsReads(code, blocks);
        BasicBlock.computeInvocationSizes(blocks);
        this.scc = Scc.stronglyConnectedComponents(blocks);
        this.scc.initializeAll();
        this.upwardLabelsByOffset = new Label[code.length + 1 ]; // the + 1 is for a label beyond the end
        this.scc.computeSplitPoints();
        BasicBlock.computeSizes(code, blocks);
        this.scc.computeSizes();
        this.splitMethods = scc.split(thisName, access, maxMethodLength, nameGenerator);
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

   
    /**
     * Creates the very first (implicit) frame from the method
     * descriptor.
     */
    private static int computeMethodDescriptorFrame(String className, String methodName, boolean isStatic, String desc, Object[] frameLocal) {
        int local = 0;
        if (isStatic) {
            if ("<init>".equals(methodName)) {
                frameLocal[local++] = Opcodes.UNINITIALIZED_THIS;
            } else {
                frameLocal[local++] = className;
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
        return local;
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
            case ClassWriter.LABEL_INSN: {
                int label;
                /*
                 * ASM's artificial, temporary branch opcodes with unsigned offsets.
                 */
                if (opcode > 201) {
                    opcode = opcode < 218 ? opcode - 49 : opcode - 20;
                    Label l = largeBranchTargets[v + 1];
                    if (l != null) {
                        label = l.position;
                    } else {
                        label = v + readUnsignedShort(v + 1);
                    }
                } else {
                    label = v + readShort(v + 1);
                }
                handleJump(mv, opcode, currentBlock, blocksByOffset[label]);
                v += 3;
                break;
            }
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
                int start = v;
                v = v + 4 - (v & 3);
                int label = start + readInt(v);
                BasicBlock defaultBlock = blocksByOffset[label];
                int min = readInt(v + 4);
                int max = readInt(v + 8);
                v += 12;
                int size = max - min + 1;
                BasicBlock[] targetBlocks = new BasicBlock[size];
                for (int j = 0; j < size; ++j) {
                    targetBlocks[j] = blocksByOffset[start + readInt(v)];
                    v += 4;
                }
                Label[] targetLabels = new Label[size];
                Label defaultLabel = generateSwitchLabels(currentBlock,
                                                          defaultBlock, targetBlocks,
                                                          targetLabels);
                mv.visitTableSwitchInsn(min, max, defaultLabel, targetLabels);
                generateSwitchPostlude(mv, currentBlock,
                                       defaultBlock, defaultLabel, targetBlocks, targetLabels);
                break;
            }
            case ClassWriter.LOOK_INSN: {
                int start = v;
                v = v + 4 - (v & 3);
                int label = start + readInt(v);
                BasicBlock defaultBlock = blocksByOffset[label];
                int size = readInt(v + 4);
                v += 8;
                int[] keys = new int[size];
                BasicBlock[] targetBlocks = new BasicBlock[size];
                for (int j = 0; j < size; ++j) {
                    keys[j] = readInt(v);
                    targetBlocks[j] = blocksByOffset[start + readInt(v + 4)];
                    v += 8;
                }
                Label[] targetLabels = new Label[size];
                Label defaultLabel = generateSwitchLabels(currentBlock,
                                                          defaultBlock, targetBlocks,
                                                          targetLabels);
                mv.visitLookupSwitchInsn(defaultLabel, keys, targetLabels);
                generateSwitchPostlude(mv, currentBlock,
                                       defaultBlock, defaultLabel, targetBlocks, targetLabels);
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
                mv.visitLdcInsn(constantPool.readConst(b[v + 1] & 0xFF));
                v += 2;
                break;
            case ClassWriter.LDCW_INSN:
                mv.visitLdcInsn(constantPool.readConst(readUnsignedShort(v + 1)));
                v += 3;
                break;
            case ClassWriter.FIELDORMETH_INSN:
            case ClassWriter.ITFMETH_INSN: {
                ConstantPool.MemberSymRef sr = constantPool.parseMemberSymRef(readUnsignedShort(v + 1));
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
                ConstantPool.DynamicSymRef sr = constantPool.parseDynamicSymRef(readUnsignedShort(v + 1));

                byte[] bm = cw.bootstrapMethods.data;
                
                int bsmIndex = sr.bsmIndex;
                int mhIndex = ByteArray.readUnsignedShort(bm, bsmIndex);
                Handle bsm = (Handle) constantPool.readConst(mhIndex);
                int bsmArgCount = ByteArray.readUnsignedShort(bm, bsmIndex + 2);
                Object[] bsmArgs = new Object[bsmArgCount];
                bsmIndex += 4;
                for(int a = 0; a < bsmArgCount; a++) {
                    int argIndex = ByteArray.readUnsignedShort(bm, bsmIndex);
                    bsmArgs[a] = constantPool.readConst(argIndex);
                    bsmIndex += 2;
                }
                mv.visitInvokeDynamicInsn(sr.name, sr.desc, bsm, bsmArgs);
                
                v += 5;
                break;
            }
            case ClassWriter.TYPE_INSN:
                mv.visitTypeInsn(opcode, constantPool.readClass(readUnsignedShort(v + 1)));
                v += 3;
                break;
            case ClassWriter.IINC_INSN:
                mv.visitIincInsn(b[v + 1] & 0xFF, b[v + 2]);
                v += 3;
                break;
                // case MANA_INSN:
            default:
                mv.visitMultiANewArrayInsn(constantPool.readClass(readUnsignedShort(v + 1)), b[v + 3] & 0xFF);
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

    private Label generateSwitchLabels(BasicBlock currentBlock,
                                       BasicBlock defaultBlock, BasicBlock[] targetBlocks,
                                       Label[] targetLabels) {
        
        Label dflt;
        if (defaultBlock.sccRoot.splitMethod != currentBlock.sccRoot.splitMethod) {
            dflt = new Label();
        } else {
            dflt = defaultBlock.getStartLabel();
        }
        int size = targetBlocks.length;
        for (int j = 0; j < size; ++j) {
            BasicBlock target = targetBlocks[j];
            if (target.sccRoot.splitMethod != currentBlock.sccRoot.splitMethod) {
                targetLabels[j] = new Label();
            } else {
                targetLabels[j] = target.getStartLabel();
            }
        }
        return dflt;
    }

    private void generateSwitchPostlude(MethodVisitor mv, BasicBlock currentBlock,
                                        BasicBlock defaultBlock, Label defaultLabel,
                                        BasicBlock[] targetBlocks,
                                        Label[] targetLabels) {
        if (defaultBlock.sccRoot.splitMethod != currentBlock.sccRoot.splitMethod) {
            mv.visitLabel(defaultLabel);
            jumpToMethod(mv, defaultBlock);
        }
        int size = targetBlocks.length;
        for (int j = 0; j < size; ++j) {
            BasicBlock target = targetBlocks[j];
            if (target.sccRoot.splitMethod != currentBlock.sccRoot.splitMethod) {
                mv.visitLabel(targetLabels[j]);
                jumpToMethod(mv, target);
            }
        }
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
            m.reconstructFrame();
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
        

    private Label[] computeLargeBranchTargets(ArrayList<Branch> largeBranches) {
        Label[] array = new Label[code.length];
        for (Branch lb : largeBranches) {
            array[lb.reference] = lb.label;
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
                exceptionNames[i] = constantPool.readUTF8Item(name);
                ++i;
            }
        }
        for (SplitMethod m : splitMethods) {
            m.setSplitMethodWriter(cw, cv,
                                   descriptor,
                                   exceptionNames,
                                   labelTypes);
        }
        boolean computeMaxs = cw.computeMaxs;
        boolean computeFrames = cw.computeFrames;
        MethodWriterDelegate tooLargeDelegate = cw.tooLargeDelegate;
        cw.computeMaxs = true;
        cw.computeFrames = false;
        cw.tooLargeDelegate = null;
        cw.registerMethodWriter = false;
        mainMethodVisitor =
            cv.visitMethod(access,
                           thisName,
                           descriptor,
                           signature,
                           exceptionNames);
        mainMethodWriter = (MethodWriter) mainMethodVisitor.getFirstVisitor();
        cw.computeMaxs = computeMaxs;
        cw.computeFrames = computeFrames;
        cw.tooLargeDelegate = tooLargeDelegate;
        cw.registerMethodWriter = true;
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
            }        
        }
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
                            vsignature = constantPool.readUTF8Item(typeTable[a + 2]);
                            break;
                        }
                    }
                }
                visitLocalVariable(constantPool.readUTF8Item(ByteArray.readUnsignedShort(b, w + 4)),
                                   constantPool.readUTF8Item(ByteArray.readUnsignedShort(b, w + 6)),
                                   vsignature,
                                   start, length, index);
                w += 10;
            }
        }
    }
    
    private void visitLocalVariable(String name, String desc, String signature,
                                    int start, int length, int index) {
        // null means it's the main method
        HashMap<SplitMethod, Label> startLabels = new HashMap<SplitMethod, Label>();
        HashMap<SplitMethod, Label> endLabels = new HashMap<SplitMethod, Label>();

        // first search backwards for the basic block we're in
        SplitMethod method = null;
        BasicBlock currentBlock = null;
        {
            int i = start;
            while (i >= 0) {
                BasicBlock b = blocksByOffset[i];
                if (b != null) {
                    method = b.sccRoot.splitMethod;
                    currentBlock = b;
                    break;
                }
                --i;
            }
        }
        startLabels.put(method, labelsByOffset[start]);
        SplitMethod firstMethod = method;

        // ... then move forward
        int v = start;
        int end = start + length;
        while (v < end) {
            BasicBlock b = blocksByOffset[v];
            if (b != null) {
                // push the end forward
                if (currentBlock != null) {
                    endLabels.put(method, currentBlock.getEndLabel());
                }
                method = b.sccRoot.splitMethod;
                Label startLabel = startLabels.get(method);
                if (startLabel == null) {
                    startLabels.put(method, b.getStartLabel());
                }
                currentBlock = b;
            }
            ++v;
        }
        // final end
        endLabels.put(method, upwardLabelsByOffset[end]);
                
        for (Map.Entry<SplitMethod, Label> entry : startLabels.entrySet()) {
            SplitMethod m = entry.getKey();
            MethodVisitor mv;
            if (m == null) {
                mv = mainMethodVisitor;
            } else {
                mv = m.writer;
            }
            Label startLabel = entry.getValue();
            Label endLabel = endLabels.get(m);
            assert endLabel != null;
            assert endLabel.position >= startLabel.position;
            if ((m == null) || (m == firstMethod)
                || !m.entry.sparseInvocation || m.entry.localsReadTransitive.get(index)) {
                mv.visitLocalVariable(name, desc, signature, startLabel, endLabel, index);
            }
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

    

    private static Label getLabelAt(Label[] labelsByOffset, int offset) {
        Label l = labelsByOffset[offset];
        if (l == null) {
            l = new Label();
            labelsByOffset[offset] = l;
        }
        return l;
    }

    private Label getLabelAt(int offset) {
        return getLabelAt(labelsByOffset, offset);
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


    // ------------------------------------------------------------------------
    // Utility methods: low level parsing
    // ------------------------------------------------------------------------

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

}