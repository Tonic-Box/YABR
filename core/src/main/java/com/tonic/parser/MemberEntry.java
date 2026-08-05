package com.tonic.parser;

import com.tonic.parser.attribute.Attribute;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Abstract base for field and method entries, holding the shared name/descriptor/access state.
 */
public abstract class MemberEntry
{
    protected String ownerName, name, desc, key;
    protected int nameIndex, descIndex;
    protected ClassFile classFile;
    protected int access;
    protected List<Attribute> attributes;

    /**
     * @return the owner name
     */
    public String getOwnerName()
    {
        return ownerName;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the desc
     */
    public String getDesc()
    {
        return desc;
    }

    /**
     * @return the key
     */
    public String getKey()
    {
        return key;
    }

    /**
     * @return the name index
     */
    public int getNameIndex()
    {
        return nameIndex;
    }

    /**
     * @return the desc index
     */
    public int getDescIndex()
    {
        return descIndex;
    }

    /**
     * @return the class file
     */
    public ClassFile getClassFile()
    {
        return classFile;
    }

    /**
     * @return the access
     */
    public int getAccess()
    {
        return access;
    }

    /**
     * @return the attributes
     */
    public List<Attribute> getAttributes()
    {
        return attributes;
    }

    /**
     * @param ownerName the internal name of the declaring class
     */
    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    /**
     * @param name the member name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @param desc the member descriptor
     */
    public void setDesc(String desc)
    {
        this.desc = desc;
    }

    /**
     * @param key the name+descriptor lookup key
     */
    public void setKey(String key)
    {
        this.key = key;
    }

    /**
     * @param nameIndex constant-pool index of the name Utf8
     */
    public void setNameIndex(int nameIndex)
    {
        this.nameIndex = nameIndex;
    }

    /**
     * @param descIndex constant-pool index of the descriptor Utf8
     */
    public void setDescIndex(int descIndex)
    {
        this.descIndex = descIndex;
    }

    /**
     * @param classFile the owning ClassFile
     */
    public void setClassFile(ClassFile classFile)
    {
        this.classFile = classFile;
    }

    /**
     * @param access the access flags
     */
    public void setAccess(int access)
    {
        this.access = access;
    }

    /**
     * @param attributes the member attributes
     */
    public void setAttributes(List<Attribute> attributes)
    {
        this.attributes = attributes;
    }

    /**
     * Writes this member entry to the output stream.
     * @param dos the DataOutputStream to write to
     * @throws IOException if an I/O error occurs
     */
    public abstract void write(DataOutputStream dos) throws IOException;
}
