package com.tonic.analysis.visitor;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.constpool.Item;

import java.io.IOException;

/**
 * Abstract visitor for processing class file elements including methods, fields,
 * constant pool items, and attributes.
 */
public abstract class AbstractClassVisitor implements Visitor<ClassFile>
{
    protected ClassFile classFile;

    /**
     * Processes the given class file by visiting its elements and rebuilding.
     *
     * @param classFile the class file to process
     * @throws IOException if an I/O error occurs during processing
     */
    @Override
    public void process(ClassFile classFile) throws IOException {
        this.classFile = classFile;
        classFile.accept(this);
        classFile.rebuild();
    }

    /**
     * Visits a method entry and its attributes.
     *
     * @param methodEntry the method entry to visit
     */
    public void visitMethod(MethodEntry methodEntry) {
        for (Attribute attribute : methodEntry.getAttributes()) {
            visitMethodAttributes(methodEntry, attribute);
        }
    }

    /**
     * Visits a field entry and its attributes.
     *
     * @param fieldEntry the field entry to visit
     */
    public void visitField(FieldEntry fieldEntry)
    {
        for (Attribute attribute : fieldEntry.getAttributes()) {
            visitFieldAttributes(fieldEntry, attribute);
        }
    }

    /**
     * Visits a constant pool item.
     *
     * @param constPoolItem the constant pool item to visit
     */
    public void visitConstPoolItem(Item<?> constPoolItem) {

    }

    /**
     * Visits a class-level attribute.
     *
     * @param attribute the attribute to visit
     */
    public void visitClassAttribute(Attribute attribute) {

    }

    /**
     * Visits a method-level attribute.
     *
     * @param methodEntry the method entry containing the attribute
     * @param attribute the attribute to visit
     */
    public void visitMethodAttributes(MethodEntry methodEntry, Attribute attribute) {

    }

    /**
     * Visits a field-level attribute.
     *
     * @param fieldEntry the field entry containing the attribute
     * @param attribute the attribute to visit
     */
    public void visitFieldAttributes(FieldEntry fieldEntry, Attribute attribute) {

    }
}
