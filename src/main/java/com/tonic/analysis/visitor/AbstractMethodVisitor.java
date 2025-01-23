package com.tonic.analysis.visitor;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.io.IOException;

public class AbstractMethodVisitor implements Visitor<MethodEntry> {
    @Override
    public void process(MethodEntry methodEntry) throws IOException {
        methodEntry.accept(this);
        methodEntry.getCodeAttribute().accept(this);
    }

    public void visit(CodeAttribute codeAttribute) {}

    public void visit(MethodEntry methodEntry) throws IOException {}
}
