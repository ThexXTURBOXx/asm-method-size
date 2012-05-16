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

/**
 * MethodWriterFactory for splitting too-large methods.
 *
 * @author Mike Sperber
 */

class SplitMethodWriterFactory implements MethodWriterFactory {
    // hack, hack, hack ...
    boolean split;
    Boolean computeMaxsOverride;
    Boolean computeFramesOverride;
    boolean register;
    // the horror
    static MethodWriter lastInstance;

    private final INameGenerator nameGenerator;

    public SplitMethodWriterFactory(INameGenerator nameGenerator) {
        setDefaults();
        this.nameGenerator = nameGenerator;
    }

    public SplitMethodWriterFactory() {
        this(new HashNameGenerator());
    }

    public void setDefaults() {
        split = true;
        computeMaxsOverride = null;
        computeFramesOverride = null;
        register = true;
    }

    public MethodWriter getMethodWriter(final ClassWriter cw,
                                        final int access,
                                        final String name,
                                        final String desc,
                                        final String signature,
                                        final String[] exceptions,
                                        final boolean computeMaxs,
                                        final boolean computeFrames) {
        MethodWriterDelegate cwd = split ? new SplitMethodWriterDelegate(ClassWriter.MAX_CODE_LENGTH, nameGenerator) : null;
        boolean cm = (computeMaxsOverride != null) ? computeMaxsOverride.booleanValue() : computeMaxs;
        boolean cf = (computeFramesOverride != null) ? computeFramesOverride.booleanValue() : computeFrames;
        lastInstance = new MethodWriter(cw, access, name, desc, signature, exceptions, cm, cf, register,
                                        cwd);
        return lastInstance;
    }
}