package com.tonic.analysis.visitor;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.constpool.Item;

import java.io.IOException;

public class AbstractClassVisitor implements Visitor<ClassFile>
{
    protected ClassFile classFile;
    @Override
    public void process(ClassFile classFile) throws IOException {
        this.classFile = classFile;
        classFile.accept(this);
        classFile.rebuild();
    }
    public void visitMethod(MethodEntry methodEntry) {
        for (Attribute attribute : methodEntry.getAttributes()) {
            visitMethodAttributes(methodEntry, attribute);
        }
    }
    public void visitField(FieldEntry fieldEntry)
    {
        for (Attribute attribute : fieldEntry.getAttributes()) {
            visitFieldAttributes(fieldEntry, attribute);
        }
    }
    public void visitConstPoolItem(Item<?> constPoolItem) {

    }
    public void visitClassAttribute(Attribute attribute) {

    }

    public void visitMethodAttributes(MethodEntry methodEntry, Attribute attribute) {

    }

    public void visitFieldAttributes(FieldEntry fieldEntry, Attribute attribute) {

    }
}
