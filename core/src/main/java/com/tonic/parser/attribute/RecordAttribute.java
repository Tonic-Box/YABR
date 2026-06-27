package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Record attribute (Java 16, JEP 395).
 * Marks a class as a record and lists its components; a class is a record iff it carries
 * this attribute (there is no ACC_RECORD access flag).
 * <p>
 * Each component is {@code (name_index, descriptor_index, attributes[])}, where the indices are
 * CONSTANT_Utf8 entries and the per-component attributes (typically {@code Signature} for generic
 * components) are read through the standard {@link Attribute} factory so their constant-pool
 * indices stay modeled. Modeled (rather than opaque) so the decompiler can reconstruct the
 * {@code record Name(T a, U b)} header.
 */
@Getter
public class RecordAttribute extends Attribute {

    @Getter
    public static class Component {
        private final int nameIndex;
        private final int descriptorIndex;
        private final List<Attribute> attributes;

        public Component(int nameIndex, int descriptorIndex, List<Attribute> attributes) {
            this.nameIndex = nameIndex;
            this.descriptorIndex = descriptorIndex;
            this.attributes = attributes;
        }
    }

    private List<Component> components;

    public RecordAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public RecordAttribute(String name, ClassFile hostClass, int nameIndex, int length) {
        super(name, hostClass, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int componentCount = classFile.readUnsignedShort();
        this.components = new ArrayList<>(componentCount);
        for (int i = 0; i < componentCount; i++) {
            int nameIndex = classFile.readUnsignedShort();
            int descriptorIndex = classFile.readUnsignedShort();
            int attrCount = classFile.readUnsignedShort();
            List<Attribute> attrs = new ArrayList<>(attrCount);
            for (int a = 0; a < attrCount; a++) {
                attrs.add(Attribute.get(classFile, classFile.getConstPool(), null));
            }
            components.add(new Component(nameIndex, descriptorIndex, attrs));
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(components.size());
        for (Component c : components) {
            dos.writeShort(c.getNameIndex());
            dos.writeShort(c.getDescriptorIndex());
            dos.writeShort(c.getAttributes().size());
            for (Attribute attr : c.getAttributes()) {
                attr.write(dos);
            }
        }
    }

    @Override
    public void updateLength() {
        int len = 2;
        for (Component c : components) {
            len += 6;
            for (Attribute attr : c.getAttributes()) {
                attr.updateLength();
                len += 6 + attr.length;
            }
        }
        this.length = len;
    }

    /** Component (name, descriptor) pairs resolved against the constant pool, in declaration order. */
    public List<String[]> getComponentNameAndDescriptors() {
        List<String[]> out = new ArrayList<>(components.size());
        for (Component c : components) {
            out.add(new String[]{resolveUtf8(c.getNameIndex()), resolveUtf8(c.getDescriptorIndex())});
        }
        return out;
    }

    private String resolveUtf8(int index) {
        Item<?> item = getClassFile().getConstPool().getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : "Unknown";
    }

    @Override
    public String toString() {
        return "RecordAttribute{components=" + getComponentNameAndDescriptors().size() + "}";
    }
}
