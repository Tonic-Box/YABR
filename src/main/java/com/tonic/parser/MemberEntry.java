package com.tonic.parser;

import com.tonic.parser.attribute.Attribute;
import lombok.Getter;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

@Getter
@Setter
public abstract class MemberEntry
{
    protected String ownerName, name, desc, key;
    protected int nameIndex, descIndex;
    protected ClassFile classFile;
    protected int access;
    protected List<Attribute> attributes;

    public abstract void write(DataOutputStream dos) throws IOException;
}
