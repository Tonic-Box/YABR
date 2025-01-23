package com.tonic.analysis.visitor;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.ir.blocks.Block;
import com.tonic.analysis.ir.blocks.Expression;
import com.tonic.analysis.ir.blocks.Statement;
import com.tonic.parser.MethodEntry;
import java.io.IOException;
public abstract class AbstractBlockVisitor implements Visitor<MethodEntry> {
    protected CodeWriter codeWriter;
    protected MethodEntry method;
    @Override
    public void process(MethodEntry method) throws IOException {
        codeWriter = new CodeWriter(method);
        this.method = method;
        codeWriter.accept(this);
        codeWriter.write();
    }
    public void visit(Expression expression) {}
    public void visit(Statement statement) {}
    public void visit(Block other) {}
}