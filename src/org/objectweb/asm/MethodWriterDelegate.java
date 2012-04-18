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
package org.objectweb.asm;

/**
 * An interface for 
 */
public abstract class MethodWriterDelegate {

    /**
     * The class writer to which this method must be added.
     */
    ClassWriter cw;

    /**
     * Access flags of this method.
     */
    int access;

    /**
     * The index of the constant pool item that contains the name of this
     * method.
     */
    int name;

    /**
     * The index of the constant pool item that contains the descriptor of this
     * method.
     */
    int desc;

    /**
     * The descriptor of this method.
     */
    String descriptor;

    /**
     * The signature of this method.
     */
    String signature;

    /**
     * If not zero, indicates that the code of this method must be copied from
     * the ClassReader associated to this writer in <code>cw.cr</code>. More
     * precisely, this field gives the index of the first byte to copied from
     * <code>cw.cr.b</code>.
     */
    int classReaderOffset;

    /**
     * If not zero, indicates that the code of this method must be copied from
     * the ClassReader associated to this writer in <code>cw.cr</code>. More
     * precisely, this field gives the number of bytes to copied from
     * <code>cw.cr.b</code>.
     */
    int classReaderLength;

    /**
     * Number of exceptions that can be thrown by this method.
     */
    int exceptionCount;

    /**
     * The exceptions that can be thrown by this method. More precisely, this
     * array contains the indexes of the constant pool items that contain the
     * internal names of these exception classes.
     */
    int[] exceptions;

    /**
     * The annotation default attribute of this method. May be <tt>null</tt>.
     */
    ByteVector annd;

    /**
     * The runtime visible annotations of this method. May be <tt>null</tt>.
     */
    AnnotationWriter anns;

    /**
     * The runtime invisible annotations of this method. May be <tt>null</tt>.
     */
    AnnotationWriter ianns;

    /**
     * The runtime visible parameter annotations of this method. May be
     * <tt>null</tt>.
     */
    AnnotationWriter[] panns;

    /**
     * The runtime invisible parameter annotations of this method. May be
     * <tt>null</tt>.
     */
    AnnotationWriter[] ipanns;

    /**
     * The number of synthetic parameters of this method.
     */
    int synthetics;

    /**
     * The non standard attributes of the method.
     */
    Attribute attrs;

    /**
     * The bytecode of this method.
     */
    ByteVector code = new ByteVector();

    /**
     * Maximum stack size of this method.
     */
    int maxStack;

    /**
     * Maximum number of local variables for this method.
     */
    int maxLocals;

    /**
     *  Number of local variables in the current stack map frame.
     */
    int currentLocals;

    /**
     * Number of stack map frames in the StackMapTable attribute.
     */
    int frameCount;

    /**
     * The StackMapTable attribute.
     */
    ByteVector stackMap;

    /**
     * The offset of the last frame that was written in the StackMapTable
     * attribute.
     */
    int previousFrameOffset;

    /**
     * The last frame that was written in the StackMapTable attribute.
     *
     * @see #frame
     */
    int[] previousFrame;

    /**
     * Index of the next element to be added in {@link #frame}.
     */
    int frameIndex;

    /**
     * The current stack map frame. The first element contains the offset of the
     * instruction to which the frame corresponds, the second element is the
     * number of locals and the third one is the number of stack elements. The
     * local variables start at index 3 and are followed by the operand stack
     * values. In summary frame[0] = offset, frame[1] = nLocal, frame[2] =
     * nStack, frame[3] = nLocal. All types are encoded as integers, with the
     * same format as the one used in {@link Label}, but limited to BASE types.
     */
    int[] frame;

    /**
     * Number of elements in the exception handler list.
     */
    int handlerCount;

    /**
     * The first element in the exception handler list.
     */
    Handler firstHandler;

    /**
     * The last element in the exception handler list.
     */
    Handler lastHandler;

    /**
     * Number of entries in the LocalVariableTable attribute.
     */
    int localVarCount;

    /**
     * The LocalVariableTable attribute.
     */
    ByteVector localVar;

    /**
     * Number of entries in the LocalVariableTypeTable attribute.
     */
    int localVarTypeCount;

    /**
     * The LocalVariableTypeTable attribute.
     */
    ByteVector localVarType;

    /**
     * Number of entries in the LineNumberTable attribute.
     */
    int lineNumberCount;

    /**
     * The LineNumberTable attribute.
     */
    ByteVector lineNumber;

    /**
     * The non standard attributes of the method's code.
     */
    Attribute cattrs;

    /**
     * Indicates if some jump instructions are too small and need to be resized.
     */
    boolean resize;

    /**
     * The number of subroutines in this method.
     */
    int subroutines;

    /**
     * A list of labels. This list is the list of basic blocks in the method,
     * i.e. a list of Label objects linked to each other by their
     * {@link Label#successor} field, in the order they are visited by
     * {@link MethodVisitor#visitLabel}, and starting with the first basic block.
     */
    Label labels;

    /**
     * The (relative) maximum stack size after the last visited instruction.
     * This size is relative to the beginning of the current basic block, i.e.,
     * the true maximum stack size after the last visited instruction is equal
     * to the {@link Label#inputStackTop beginStackSize} of the current basic
     * block plus <tt>stackSize</tt>.
     */
    int maxStackSize;

    /**
     * Returns the size of the bytecode of this method.
     *
     * @return the size of the bytecode of this method.
     */
    public abstract int getSize();
   
    /**
     * Puts the bytecode of this method in the given byte vector.
     *
     * @param out the byte vector into which the bytecode of this method must be
     *        copied.
     */
    public abstract void put(ByteVector out);
}