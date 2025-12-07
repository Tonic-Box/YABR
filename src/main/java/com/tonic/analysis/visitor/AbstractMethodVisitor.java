package com.tonic.analysis.visitor;

import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.io.IOException;

/**
 * Abstract visitor for processing method entries and their code attributes.
 */
public abstract class AbstractMethodVisitor implements Visitor<MethodEntry> {
    /**
     * Processes the given method entry by visiting the method and its code attribute.
     *
     * @param methodEntry the method entry to process
     * @throws IOException if an I/O error occurs during processing
     */
    @Override
    public void process(MethodEntry methodEntry) throws IOException {
        methodEntry.accept(this);
        methodEntry.getCodeAttribute().accept(this);
    }

    /**
     * Visits a code attribute.
     *
     * @param codeAttribute the code attribute to visit
     */
    public void visit(CodeAttribute codeAttribute) {}

    /**
     * Visits a method entry.
     *
     * @param methodEntry the method entry to visit
     * @throws IOException if an I/O error occurs during visiting
     */
    public void visit(MethodEntry methodEntry) throws IOException {}
}
