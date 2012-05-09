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
    private Object[] frameLocal;
    private Object[] frameStack;

    public FrameData(int nLocal, Object[] frameLocal, int nStack, Object[] frameStack) {
        this.frameLocal = Arrays.copyOf(frameLocal, nLocal);
        this.frameStack = Arrays.copyOf(frameStack, nStack);
    }
    
    public FrameData(Object[] frameLocal, Object[] frameStack) {
        this(frameLocal.length, frameLocal, frameStack.length, frameStack);
    }

    public void visitFrame(MethodVisitor mv) {
        mv.visitFrame(Opcodes.F_NEW, frameLocal.length, frameLocal, frameStack.length, frameLocal);
    }

    /**
     * @param methodDescriptor method descriptor of host method
     * @param isStatic says whether host method is static
     */
    public String getDescriptor(final String methodDescriptor, final boolean isStatic) {
        StringBuilder b = new StringBuilder();

        b.append("(");
        {
            // for non-static methods, this is the first local, and implicit
            int i = isStatic ? 0 : 1;
            while (i < frameLocal.length) {
                appendFrameTypeDescriptor(b, frameLocal[i]);
                ++i;
            }
        }
        {
            int i = 0;
            while (i < frameStack.length) {
                appendFrameTypeDescriptor(b, frameStack[i]);
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
                // the last index is pushed first
                storeStackElement(mv, frameStack.length - i -1 + frameLocal.length, frameStack[i]);
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
            // the first index is popped first
            while (i < frameStack.length) {
                loadFrameElement(mv, i + frameLocal.length, frameStack[i]);
                ++i;
            }
        }
    }

    private void storeStackElement(MethodVisitor mv, int index, Object el) {
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
            mv.visitInsn(Opcodes.ASTORE);
        } else if (el == Opcodes.UNINITIALIZED_THIS) {
            mv.visitInsn(Opcodes.ASTORE);
        } else if (el instanceof String) {
            mv.visitVarInsn(Opcodes.ASTORE, index);
        } else if (el instanceof Label) {
            mv.visitInsn(Opcodes.ASTORE);
        } else {
            throw new RuntimeException("unknown frame element");
        }
    }

    private void appendFrameTypeDescriptor(StringBuilder b, Object d) {
        if (d == Opcodes.INTEGER)
            b.append("I");
        else if (d == Opcodes.FLOAT)
            b.append("F");
        else if (d == Opcodes.LONG)
            b.append("J");
        else if (d == Opcodes.DOUBLE)
            b.append("D");
        else if (d instanceof String) {
            b.append("L");
            b.append((String) d);
            b.append(";");
        } else
            // missing are TOP, and uninitialized / Labels
            throw new RuntimeException("can't handle this frame element yet"); // ####
    }


    /**
     * In a split method, reconstruct the stack from the parameters.
     */
    public void reconstructStack(MethodVisitor mw) {
        int localSize = frameLocal.length;
        int i = 0, size = frameStack.length;
        while (i < size) {
            loadFrameElement(mw, i + localSize, frameStack[i]);
            ++i;
        }
    }

    private void loadFrameElement(MethodVisitor mv, int index, Object el) {
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
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else if (el instanceof String) {
            mv.visitVarInsn(Opcodes.ALOAD, index);
        } else if (el instanceof Label) {
            mv.visitInsn(Opcodes.ACONST_NULL);
        } else {
            throw new RuntimeException("unknown frame element");
        }
    }

}