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

import java.util.*;

public final class FrameData {
    Object[] frameLocal;
    Object[] frameStack;

    public FrameData(int nLocal, Object[] frameLocal, int nStack, Object[] frameStack) {
        this.frameLocal = Arrays.copyOf(frameLocal, nLocal);
        this.frameStack = Arrays.copyOf(frameStack, nStack);
    }
    
    public FrameData(Object[] frameLocal, Object[] frameStack) {
        this(frameLocal.length, frameLocal, frameStack.length, frameStack);
    }

    public void visitFrame(MethodVisitor mv) {
        mv.visitFrame(Opcodes.F_NEW, frameLocal.length, frameLocal, frameStack.length, frameStack);
    }

    /**
     * Return <code>true</code> if this frame has no uninitialized
     * elements, <code>false</code> otherwise,
     */
    public boolean isFullyDefined() {
        return isFrameFullyDefined(frameLocal, frameLocal.length)
            && isFrameFullyDefined(frameStack, frameStack.length);
    }

    public static boolean isFrameFullyDefined(Object[] elements, int size) {
        int i = 0;
        while (i < size) {
            if (!isElementFullyDefined(elements[i])) {
                return false;
            }
            ++i;
        }
        return true;
    }

    private static boolean isElementFullyDefined(Object el) {
        return (el != Opcodes.UNINITIALIZED_THIS)
            && !(el instanceof Label);
    }

    /**
     * @param methodDescriptor method descriptor of host method
     * @param isStatic says whether host method is static
     */
    public String getDescriptor(final String methodDescriptor, final boolean isStatic, HashMap<Label, String> labelTypes) {
        StringBuilder b = new StringBuilder();

        b.append("(");
        {
            // for non-static methods, this is the first local, and implicit
            int i = isStatic ? 0 : 1;
            while (i < frameLocal.length) {
                appendFrameTypeDescriptor(b, frameLocal[i], labelTypes);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameStack.length) {
                appendFrameTypeDescriptor(b, frameStack[i], labelTypes);
                ++i;
            }
        }
        b.append(")");
        b.append(Type.getReturnType(methodDescriptor));
        return b.toString();
    }

    /**
     * Generate a jump to this method.
     */
    public void visitPushFrameArguments(ClassWriter cw, MethodVisitor mv) {
        /*
         * We'll want the locals first, so they get the same indices
         * in the target block.  Then we'll want the operands.
         * Unfortunately, there's no way to push the locals below the
         * operands (or whatever), so we'll need to copy the operands
         * into new local variables, and from there push them on top.
         */
        // FIXME: need to adjust frameLocal
        {
            int i = 0;
            while (i < frameStack.length) {
                storeStackElement(mv, frameLocal.length + i, frameStack[frameStack.length - i - 1]);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameLocal.length) {
                loadFrameElement(mv, i, frameLocal[i]);
                ++i;
            }
        }
        {
            int i = 0;
            // now the relative frame indices correspond to the original stack indices
            while (i < frameStack.length) {
                loadFrameElement(mv, frameLocal.length + frameStack.length - i - 1, frameStack[i]);
                ++i;
            }
        }
    }

    /**
     * Calculate the size of the code needed to push the current frame
     * in preparation for an invocation.
     */
    public int visitPushFrameArgumentsSize() {
        int size = 0;
        {
            int i = 0;
            while (i < frameStack.length) {
                size += storeStackElementSize(frameLocal.length + i, frameStack[frameStack.length - i - 1]);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameLocal.length) {
                size += loadFrameElementSize(i, frameLocal[i]);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameStack.length) {
                size += loadFrameElementSize(frameLocal.length + frameStack.length -i - 1, frameStack[i]);
                ++i;
            }
        }
        return size;
    }

    public static int visitPushFrameArgumentsMaxSize(int maxStack, int maxLocals) {
        int size = 0;
        size += frameLoadStoreMaxSize(maxLocals, maxLocals + maxStack) * 2;
        size += frameLoadStoreMaxSize(0, maxLocals);
        return size;
    }

    private static void storeStackElement(MethodVisitor mv, int index, Object el) {
        if (el == Opcodes.TOP) {
            ; // nothing
        } else if (el == Opcodes.INTEGER) {
            mv.visitVarInsn(Opcodes.ISTORE, index);
        } else if (el == Opcodes.FLOAT) {
            mv.visitVarInsn(Opcodes.FSTORE, index);
        } else if (el == Opcodes.DOUBLE) {
            mv.visitVarInsn(Opcodes.DSTORE, index);
        } else if (el == Opcodes.LONG) {
            mv.visitVarInsn(Opcodes.LSTORE, index);
        } else if (el == Opcodes.NULL) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el == Opcodes.UNINITIALIZED_THIS) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el instanceof String) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el instanceof Label) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else {
            throw new RuntimeException("unknown frame element");
        }
    }

    private static int storeStackElementSize(int index, Object el) {
        if (el == Opcodes.TOP) {
            return 0; // nothing
        } else {
            if (index < 4) {
                return 1;
            } else if (index >= 256) {
                return 6;
            } else {
                return 3;
            }
        }
    }

    private static void appendFrameTypeDescriptor(StringBuilder b, Object d, HashMap<Label, String> labelTypes) {
        if (d == Opcodes.INTEGER)
            b.append("I");
        else if (d == Opcodes.FLOAT)
            b.append("F");
        else if (d == Opcodes.LONG)
            b.append("J");
        else if (d == Opcodes.DOUBLE)
            b.append("D");
        else if (d instanceof String) {
            appendFrameReferenceTypeDescriptor(b, (String) d, 0);
            b.append(";");
        } else if (d instanceof Label) {
            String name = labelTypes.get(d);
            if (name == null) {
                throw new RuntimeException("label without associated type");
            }
            appendFrameReferenceTypeDescriptor(b, name, 0);
            b.append(";");
        } else if (d == Opcodes.TOP) {
            ; // it's not the TOP that counts, but what's before it
        } else {
            // #### UNINITIALIZED_THIS is missing
            throw new RuntimeException("can't handle this frame element");
        }
    }

    private static void appendFrameReferenceTypeDescriptor(StringBuilder b, String name, int index) {
        // internal names and descriptors don't relate well
        while (name.charAt(index) == '[') {
            b.append("[");
            ++index;
        }
        b.append("L");
        b.append(name, index, name.length());
    }


    /**
     * In a split method, reconstruct the stack from the parameters.
     */
    public void reconstructStack(MethodVisitor mw) {
        int localSize = frameLocal.length;
        int i = 0, size = frameStack.length;
        // the relative frame indices correspond to the original stack indices
        while (i < size) {
            loadFrameElement(mw, localSize + i, frameStack[i]);
            ++i;
        }
    }

    /**
     * Calculate code size needed to reconstruct the stack from the parameters.
     */
    public int reconstructStackSize() {
        int codeSize = 0;
        int localSize = frameLocal.length;
        int i = 0, size = frameStack.length;
        while (i < size) {
            codeSize += loadFrameElementSize(i + localSize, frameStack[i]);
            ++i;
        }
        return codeSize;
    }

    public static int reconstructStackMaxSize(int maxStack, int maxLocals) {
        return frameLoadStoreMaxSize(maxLocals, maxStack + maxLocals);
    }

    private static void loadFrameElement(MethodVisitor mv, int index, Object el) {
        if (el == Opcodes.TOP) {
            ; // nothing
        } else if (el == Opcodes.INTEGER) {
            mv.visitVarInsn(Opcodes.ILOAD, index);
        } else if (el == Opcodes.FLOAT) {
            mv.visitVarInsn(Opcodes.FLOAD, index);
        } else if (el == Opcodes.DOUBLE) {
            mv.visitVarInsn(Opcodes.DLOAD, index);
        } else if (el == Opcodes.LONG) {
            mv.visitVarInsn(Opcodes.LLOAD, index);
        } else if (el == Opcodes.NULL) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else if (el == Opcodes.UNINITIALIZED_THIS) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else if (el instanceof String) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else if (el instanceof Label) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else {
            throw new RuntimeException("unknown frame element");
        }
    }

    private static int loadFrameElementSize(int index, Object el) {
        if (el == Opcodes.TOP) {
            return 0; // nothing
        } else if ((el == Opcodes.NULL) || (el == Opcodes.UNINITIALIZED_THIS) || (el instanceof Label)) {
            return 1;
        } else {
            if (index < 4) {
                return 1;
            } else if (index >= 256) {
                return 6;
            } else {
                return 3;
            }
        }
    }

    /**
     * Calculate the maximum size of either storing or loading a bunch
     * of locals at indices.
     *
     * @param start inclusive
     * @param end exclusive
     */
    private static int frameLoadStoreMaxSize(int start, int end) {
        int size = 0;
        if (start < 4) {
            int m = Math.min(4, end);
            size += m - start;
            start = m;
        }
        if (start < 256) {
            int m = Math.min(256, end);
            size += (m - start) * 3;
            start = m;
        }
        size += (end - start) * 6;
        return size;
    }
    

}