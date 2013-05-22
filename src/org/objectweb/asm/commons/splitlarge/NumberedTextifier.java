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

import java.text.DecimalFormat;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;

public class NumberedTextifier extends Textifier {

    private int insCount;
    private static DecimalFormat format = new DecimalFormat("0000");
    
    public NumberedTextifier() {
        super();
    }

    public NumberedTextifier(int api) {
        super(api);
    }

    @Override
    public Textifier visitMethod(
        int access,
        String name,
        String desc,
        String signature,
        String[] exceptions)
    {
        this.insCount = 0;
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    protected Textifier createTextifier() {
        return new NumberedTextifier(this.api);
    }

    private void newIns() {
       this.buf.setLength(0);
       this.buf.append(tab).append(format.format(insCount)).append(tab);
       ++insCount;
    }
    
    @Override
    public void visitInsn(int opcode) {
        this.newIns();
        buf.append(OPCODES[opcode]).append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        this.newIns();
        buf.append(OPCODES[opcode])
                .append(' ')
                .append(opcode == Opcodes.NEWARRAY
                        ? TYPES[operand]
                        : Integer.toString(operand))
                .append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        this.newIns();
        buf.append(OPCODES[opcode])
                .append(' ')
                .append(var)
                .append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        this.newIns();
        buf.append(OPCODES[opcode]).append(' ');
        appendDescriptor(INTERNAL_NAME, type);
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitFieldInsn(
        int opcode,
        String owner,
        String name,
        String desc)
    {
        this.newIns();
        buf.append(OPCODES[opcode]).append(' ');
        appendDescriptor(INTERNAL_NAME, owner);
        buf.append('.').append(name).append(" : ");
        appendDescriptor(FIELD_DESCRIPTOR, desc);
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitMethodInsn(
        int opcode,
        String owner,
        String name,
        String desc)
    {
        this.newIns();
        buf.append(OPCODES[opcode]).append(' ');
        appendDescriptor(INTERNAL_NAME, owner);
        buf.append('.').append(name).append(' ');
        appendDescriptor(METHOD_DESCRIPTOR, desc);
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name,
        String desc,
        Handle bsm,
        Object... bsmArgs)
    {
        this.newIns();
        buf.append("INVOKEDYNAMIC").append(' ');
        buf.append(name);
        appendDescriptor(METHOD_DESCRIPTOR, desc);
        buf.append(" [");
        appendHandle(bsm);
        buf.append(tab3).append("// arguments:");
        if(bsmArgs.length == 0) {
            buf.append(" none");
        } else {
            buf.append('\n').append(tab3);
            for(int i = 0; i < bsmArgs.length; i++) {
                Object cst = bsmArgs[i];
                if (cst instanceof String) {
                    Printer.appendString(buf, (String) cst);
                } else if (cst instanceof Type) {
                    buf.append(((Type) cst).getDescriptor()).append(".class");
                } else if (cst instanceof Handle) {
                    appendHandle((Handle) cst);
                } else {
                    buf.append(cst);
                }
                buf.append(", ");
            }
            buf.setLength(buf.length() - 2);
        }
        buf.append('\n');
        buf.append(tab2).append("]\n");
        text.add(buf.toString());
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        this.newIns();
        buf.append(OPCODES[opcode]).append(' ');
        appendLabel(label);
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitLdcInsn(Object cst) {
        this.newIns();
        buf.append("LDC ");
        if (cst instanceof String) {
            Printer.appendString(buf, (String) cst);
        } else if (cst instanceof Type) {
            buf.append(((Type) cst).getDescriptor()).append(".class");
        } else {
            buf.append(cst);
        }
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        this.newIns();
        buf
                .append("IINC ")
                .append(var)
                .append(' ')
                .append(increment)
                .append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitTableSwitchInsn(
        int min,
        int max,
        Label dflt,
        Label... labels)
    {
        this.newIns();
        buf.append("TABLESWITCH\n");
        for (int i = 0; i < labels.length; ++i) {
            buf.append(tab3).append(min + i).append(": ");
            appendLabel(labels[i]);
            buf.append('\n');
        }
        buf.append(tab3).append("default: ");
        appendLabel(dflt);
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        this.newIns();
        buf.append("LOOKUPSWITCH\n");
        for (int i = 0; i < labels.length; ++i) {
            buf.append(tab3).append(keys[i]).append(": ");
            appendLabel(labels[i]);
            buf.append('\n');
        }
        buf.append(tab3).append("default: ");
        appendLabel(dflt);
        buf.append('\n');
        text.add(buf.toString());
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        this.newIns();
        buf.append("MULTIANEWARRAY ");
        appendDescriptor(FIELD_DESCRIPTOR, desc);
        buf.append(' ').append(dims).append('\n');
        text.add(buf.toString());
    }

    
}
