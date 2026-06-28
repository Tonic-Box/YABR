package com.tonic.parser;

import com.tonic.parser.attribute.Attribute;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Abstract base class for field and method entries in a class file.
 */
public abstract class MemberEntry {
    protected String ownerName, name, desc, key;
    protected int nameIndex, descIndex;
    protected ClassFile classFile;
    protected int access;
    protected List<Attribute> attributes;

    public String getOwnerName() {
        return ownerName;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public String getKey() {
        return key;
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public int getDescIndex() {
        return descIndex;
    }

    public ClassFile getClassFile() {
        return classFile;
    }

    public int getAccess() {
        return access;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setNameIndex(int nameIndex) {
        this.nameIndex = nameIndex;
    }

    public void setDescIndex(int descIndex) {
        this.descIndex = descIndex;
    }

    public void setClassFile(ClassFile classFile) {
        this.classFile = classFile;
    }

    public void setAccess(int access) {
        this.access = access;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    /**
     * Writes this member entry to the output stream.
     *
     * @param dos the DataOutputStream to write to
     * @throws IOException if an I/O error occurs
     */
    public abstract void write(DataOutputStream dos) throws IOException;
}
