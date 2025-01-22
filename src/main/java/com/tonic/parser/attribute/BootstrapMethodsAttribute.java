package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the BootstrapMethods attribute.
 * Stores bootstrap method information for invokedynamic instructions.
 */
@Getter
public class BootstrapMethodsAttribute extends Attribute {
    private List<BootstrapMethod> bootstrapMethods;

    public BootstrapMethodsAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    public BootstrapMethodsAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex(); // Record starting index

        if (length < 2) {
            throw new IllegalArgumentException("BootstrapMethods attribute length must be at least 2, found: " + length);
        }

        int numBootstrapMethods = classFile.readUnsignedShort();
        this.bootstrapMethods = new ArrayList<>(numBootstrapMethods);
        for (int i = 0; i < numBootstrapMethods; i++) {
            if (classFile.getLength() - classFile.getIndex() < 4) {
                throw new IllegalArgumentException("Not enough bytes to read BootstrapMethod " + (i + 1));
            }
            int bootstrapMethodRef = classFile.readUnsignedShort();
            int numBootstrapArguments = classFile.readUnsignedShort();
            List<Integer> bootstrapArguments = new ArrayList<>(numBootstrapArguments);
            for (int j = 0; j < numBootstrapArguments; j++) {
                if (classFile.getLength() - classFile.getIndex() < 2) {
                    throw new IllegalArgumentException("Not enough bytes to read bootstrap argument " + (j + 1) + " of BootstrapMethod " + (i + 1));
                }
                bootstrapArguments.add(classFile.readUnsignedShort());
            }
            bootstrapMethods.add(new BootstrapMethod(bootstrapMethodRef, bootstrapArguments));
        }

        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length) {
            Logger.error("Warning: BootstrapMethodsAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
            // Optionally, throw an exception or handle as needed
            // throw new IllegalStateException("BootstrapMethodsAttribute read mismatch.");
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(bootstrapMethods.size());
        for (BootstrapMethod bm : bootstrapMethods) {
            dos.writeShort(bm.getBootstrapMethodRef());
            dos.writeShort(bm.getBootstrapArguments().size());
            for (Integer arg : bm.getBootstrapArguments()) {
                dos.writeShort(arg);
            }
        }
    }

    @Override
    public void updateLength() {
        int size = 2; // num_bootstrap_methods (u2)
        for (BootstrapMethod bm : bootstrapMethods) {
            size += 4; // bootstrap_method_ref (u2) + num_bootstrap_arguments (u2)
            size += 2 * bm.getBootstrapArguments().size(); // each argument is u2
        }
        this.length = size;
    }

    @Override
    public String toString() {
        return "BootstrapMethodsAttribute{bootstrapMethods=" + bootstrapMethods + "}";
    }
}
