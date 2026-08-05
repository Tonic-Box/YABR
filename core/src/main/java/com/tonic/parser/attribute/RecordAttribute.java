package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Record attribute: the component list that marks a class as a record (there is no
 * ACC_RECORD flag), modeled so the record header can be reconstructed.
 */
public class RecordAttribute extends Attribute
{

    /**
     * A record component: name and descriptor Utf8 indices plus per-component attributes.
     */
    public static class Component
    {
        private final int nameIndex;
        private final int descriptorIndex;
        private final List<Attribute> attributes;

        public Component(int nameIndex, int descriptorIndex, List<Attribute> attributes)
        {
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
            this.attributes = attributes;
        }

        /**
         * @return the name index
         */
        public int getNameIndex()
        {
            return nameIndex;
        }

        /**
         * @return the descriptor index
         */
        public int getDescriptorIndex()
        {
            return descriptorIndex;
        }

        /**
         * @return the attributes
         */
        public List<Attribute> getAttributes()
        {
            return attributes;
        }
    }

    private List<Component> components;

    /**
     * Creates the attribute shell for parsing, attached to a member.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public RecordAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, nameIndex, length);
    }

    /**
     * Creates the attribute shell for parsing, attached to a class.
     * @param name the attribute name
     * @param hostClass the class the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public RecordAttribute(String name, ClassFile hostClass, int nameIndex, int length)
    {
        super(name, hostClass, nameIndex, length);
    }

    /**
     * @return the components
     */
    public List<Component> getComponents()
    {
        return components;
    }

    @Override
    public void read(ClassFile classFile, int length)
    {
        int componentCount = classFile.readUnsignedShort();
        this.components = new ArrayList<>(componentCount);
        for (int i = 0; i < componentCount; i++)
        {
            int nameIndex = classFile.readUnsignedShort();
            int descriptorIndex = classFile.readUnsignedShort();
            int attrCount = classFile.readUnsignedShort();
            List<Attribute> attrs = new ArrayList<>(attrCount);
            for (int a = 0; a < attrCount; a++)
            {
                attrs.add(Attribute.get(classFile, classFile.getConstPool(), null));
            }
            components.add(new Component(nameIndex, descriptorIndex, attrs));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException
    {
        dos.writeShort(components.size());
        for (Component c : components)
        {
            dos.writeShort(c.getNameIndex());
            dos.writeShort(c.getDescriptorIndex());
            dos.writeShort(c.getAttributes().size());
            for (Attribute attr : c.getAttributes())
            {
                attr.write(dos);
            }
        }
    }

    @Override
    public void updateLength()
    {
        int len = 2;
        for (Component c : components)
        {
            len += 6;
            for (Attribute attr : c.getAttributes())
            {
                attr.updateLength();
                len += 6 + attr.length;
            }
        }
        this.length = len;
    }

    /**
     * Resolves every component's name and descriptor against the constant pool.
     *
     * @return one {name, descriptor} pair per component in declaration order,
     *         with "Unknown" substituted for an index that is not a UTF-8 entry
     */
    public List<String[]> getComponentNameAndDescriptors()
    {
        List<String[]> out = new ArrayList<>(components.size());
        for (Component c : components)
        {
            out.add(new String[]{resolveUtf8(c.getNameIndex()), resolveUtf8(c.getDescriptorIndex())});
        }
        return out;
    }

    private String resolveUtf8(int index)
    {
        Item<?> item = getClassFile().getConstPool().getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : "Unknown";
    }

    @Override
    public String toString()
    {
        return "RecordAttribute{components=" + getComponentNameAndDescriptors().size() + "}";
    }
}
