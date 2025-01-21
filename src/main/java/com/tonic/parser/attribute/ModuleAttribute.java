package com.tonic.parser.attribute;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.module.*;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Module attribute.
 * Provides module information for the class.
 */
@Getter
public class ModuleAttribute extends Attribute {
    private int moduleNameIndex;
    private int moduleFlags;
    private int moduleVersionIndex;
    private List<Requires> requires;
    private List<Exports> exports;
    private List<Opens> opens;
    private List<Uses> uses;
    private List<Provides> provides;

    public ModuleAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex(); // Record starting index
        this.moduleNameIndex = classFile.readUnsignedShort();
        this.moduleFlags = classFile.readUnsignedShort();
        this.moduleVersionIndex = classFile.readUnsignedShort();

        int requiresCount = classFile.readUnsignedShort();
        this.requires = new ArrayList<>(requiresCount);
        for (int i = 0; i < requiresCount; i++) {
            int requiresIndex = classFile.readUnsignedShort();
            int requiresFlags = classFile.readUnsignedShort();
            int requiresVersionIndex = classFile.readUnsignedShort();
            requires.add(new Requires(parent.getClassFile().getConstPool(), requiresIndex, requiresFlags, requiresVersionIndex));
        }

        int exportsCount = classFile.readUnsignedShort();
        this.exports = new ArrayList<>(exportsCount);
        for (int i = 0; i < exportsCount; i++) {
            int exportsIndex = classFile.readUnsignedShort();
            int exportsFlags = classFile.readUnsignedShort();
            int exportsToCount = classFile.readUnsignedShort();
            List<Integer> exportsTo = new ArrayList<>(exportsToCount);
            for (int j = 0; j < exportsToCount; j++) {
                exportsTo.add(classFile.readUnsignedShort());
            }
            exports.add(new Exports(parent.getClassFile().getConstPool(), exportsIndex, exportsFlags, exportsTo));
        }

        int opensCount = classFile.readUnsignedShort();
        this.opens = new ArrayList<>(opensCount);
        for (int i = 0; i < opensCount; i++) {
            int opensIndex = classFile.readUnsignedShort();
            int opensFlags = classFile.readUnsignedShort();
            int opensToCount = classFile.readUnsignedShort();
            List<Integer> opensTo = new ArrayList<>(opensToCount);
            for (int j = 0; j < opensToCount; j++) {
                opensTo.add(classFile.readUnsignedShort());
            }
            opens.add(new Opens(parent.getClassFile().getConstPool(), opensIndex, opensFlags, opensTo));
        }

        int usesCount = classFile.readUnsignedShort();
        this.uses = new ArrayList<>(usesCount);
        for (int i = 0; i < usesCount; i++) {
            int usesIndex = classFile.readUnsignedShort();
            uses.add(new Uses(parent.getClassFile().getConstPool(), usesIndex));
        }

        int providesCount = classFile.readUnsignedShort();
        this.provides = new ArrayList<>(providesCount);
        for (int i = 0; i < providesCount; i++) {
            int providesIndex = classFile.readUnsignedShort();
            int providesWithCount = classFile.readUnsignedShort();
            List<Integer> providesWith = new ArrayList<>(providesWithCount);
            for (int j = 0; j < providesWithCount; j++) {
                providesWith.add(classFile.readUnsignedShort());
            }
            provides.add(new Provides(parent.getClassFile().getConstPool(), providesIndex, providesWith));
        }

        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length) {
            Logger.error("Warning: ModuleAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
            // Optionally, throw an exception or handle as needed
            // throw new IllegalStateException("ModuleAttribute read mismatch.");
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        // module_name_index (u2)
        dos.writeShort(moduleNameIndex);
        // module_flags (u2)
        dos.writeShort(moduleFlags);
        // module_version_index (u2)
        dos.writeShort(moduleVersionIndex);

        // requires_count (u2)
        dos.writeShort(requires.size());
        // each requires => requires_index(u2), requires_flags(u2), requires_version_index(u2)
        for (Requires req : requires) {
            dos.writeShort(req.getRequiresIndex());
            dos.writeShort(req.getRequiresFlags());
            dos.writeShort(req.getRequiresVersionIndex());
        }

        // exports_count (u2)
        dos.writeShort(exports.size());
        // each exports => exports_index(u2), exports_flags(u2), exports_to_count(u2), then exports_to[]
        for (Exports exp : exports) {
            dos.writeShort(exp.getExportsIndex());
            dos.writeShort(exp.getExportsFlags());
            List<Integer> exportsTo = exp.getExportsTo();
            dos.writeShort(exportsTo.size());
            for (int to : exportsTo) {
                dos.writeShort(to);
            }
        }

        // opens_count (u2)
        dos.writeShort(opens.size());
        // each opens => opens_index(u2), opens_flags(u2), opens_to_count(u2), then opens_to[]
        for (Opens op : opens) {
            dos.writeShort(op.getOpensIndex());
            dos.writeShort(op.getOpensFlags());
            List<Integer> opensTo = op.getOpensTo();
            dos.writeShort(opensTo.size());
            for (int to : opensTo) {
                dos.writeShort(to);
            }
        }

        // uses_count (u2)
        dos.writeShort(uses.size());
        // each uses => uses_index(u2)
        for (Uses use : uses) {
            dos.writeShort(use.getUsesIndex());
        }

        // provides_count (u2)
        dos.writeShort(provides.size());
        // each provides => provides_index(u2), provides_with_count(u2), then provides_with[]
        for (Provides prov : provides) {
            dos.writeShort(prov.getProvidesWithIndex());
            List<Integer> providesWith = prov.getProvidesWithArguments();
            dos.writeShort(providesWith.size());
            for (int withIndex : providesWith) {
                dos.writeShort(withIndex);
            }
        }
    }

    @Override
    public void updateLength() {
        // 2 + 2 + 2 for module_name_index, module_flags, module_version_index
        int size = 6;

        // requires_count (u2) + each requires 6 bytes
        size += 2;
        size += requires.size() * 6;

        // exports_count (u2) + each exports => index(2) + flags(2) + to_count(2) + to_count*2
        size += 2;
        for (Exports exp : exports) {
            size += 6; // index + flags + to_count
            size += exp.getExportsTo().size() * 2; // each 'to' is 2 bytes
        }

        // opens_count (u2) + each opens => index(2) + flags(2) + to_count(2) + to_count*2
        size += 2;
        for (Opens op : opens) {
            size += 6;
            size += op.getOpensTo().size() * 2;
        }

        // uses_count (u2) + each uses => index(2)
        size += 2;
        size += uses.size() * 2;

        // provides_count (u2) + each => provides_index(2) + provides_with_count(2) + provides_with_count*2
        size += 2;
        for (Provides prov : provides) {
            size += 4; // index + with_count
            size += prov.getProvidesWithArguments().size() * 2;
        }

        this.length = size;
    }


    @Override
    public String toString() {
        return "ModuleAttribute{" +
                "moduleName='" + resolveModuleName() + '\'' +
                ", moduleFlags=" + moduleFlags +
                ", moduleVersion='" + resolveModuleVersion() + '\'' +
                ", requires=" + requires +
                ", exports=" + exports +
                ", opens=" + opens +
                ", uses=" + uses +
                ", provides=" + provides +
                '}';
    }

    private String resolveModuleName() {
        Item<?> utf8Item = parent.getClassFile().getConstPool().getItem(moduleNameIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveModuleVersion() {
        if (moduleVersionIndex == 0) {
            return "None";
        }
        Item<?> utf8Item = parent.getClassFile().getConstPool().getItem(moduleVersionIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}
