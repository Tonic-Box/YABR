package com.tonic.parser;

import com.tonic.parser.attribute.Attribute;
import lombok.Getter;
import lombok.Setter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Abstract base class for field and method entries in a class file.
 */
@Getter
@Setter
public abstract class MemberEntry {
    protected String ownerName, name, desc, key;
    protected int nameIndex, descIndex;
    protected ClassFile classFile;
    protected int access;
    protected List<Attribute> attributes;

    /**
     * Writes this member entry to the output stream.
     *
     * @param dos the DataOutputStream to write to
     * @throws IOException if an I/O error occurs
     */
    public abstract void write(DataOutputStream dos) throws IOException;
}
