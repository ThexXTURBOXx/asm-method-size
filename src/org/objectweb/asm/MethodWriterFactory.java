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
 * A factory interface for method writers, to abstract over the {@link
 * MethodWriter} object that {@link ClassWriter} uses.
 */

public interface MethodWriterFactory {
    /**
     * Constructs a new {@link MethodWriter} object for use with
     * {@link ClassWriter}.
     *
     * @param cw the class writer in which the method must be added.
     * @param access the method's access flags (see {@link Opcodes}).
     * @param name the method's name.
     * @param desc the method's descriptor (see {@link Type}).
     * @param signature the method's signature. May be <tt>null</tt>.
     * @param exceptions the internal names of the method's exceptions. May be
     *        <tt>null</tt>.
     * @param computeMaxs <tt>true</tt> if the maximum stack size and number
     *        of local variables must be automatically computed.
     * @param computeFrames <tt>true</tt> if the stack map tables must be
     *        recomputed from scratch.
     */
    public MethodWriter getMethodWriter(
        final ClassWriter cw,
        final int access,
        final String name,
        final String desc,
        final String signature,
        final String[] exceptions,
        final boolean computeMaxs,
        final boolean computeFrames);

}