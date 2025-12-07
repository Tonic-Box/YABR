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

    public ModuleAttribute(String name, ClassFile parent, int nameIndex, int length) {
        super(name, parent, nameIndex, length);
    }

    @Override
    public void read(ClassFile classFile, int length) {
        int startIndex = classFile.getIndex();
        this.moduleNameIndex = classFile.readUnsignedShort();
        this.moduleFlags = classFile.readUnsignedShort();
        this.moduleVersionIndex = classFile.readUnsignedShort();

        int requiresCount = classFile.readUnsignedShort();
        this.requires = new ArrayList<>(requiresCount);
        for (int i = 0; i < requiresCount; i++) {
            int requiresIndex = classFile.readUnsignedShort();
            int requiresFlags = classFile.readUnsignedShort();
            int requiresVersionIndex = classFile.readUnsignedShort();
            requires.add(new Requires(getClassFile().getConstPool(), requiresIndex, requiresFlags, requiresVersionIndex));
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
            exports.add(new Exports(getClassFile().getConstPool(), exportsIndex, exportsFlags, exportsTo));
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
            opens.add(new Opens(getClassFile().getConstPool(), opensIndex, opensFlags, opensTo));
        }

        int usesCount = classFile.readUnsignedShort();
        this.uses = new ArrayList<>(usesCount);
        for (int i = 0; i < usesCount; i++) {
            int usesIndex = classFile.readUnsignedShort();
            uses.add(new Uses(getClassFile().getConstPool(), usesIndex));
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
            provides.add(new Provides(getClassFile().getConstPool(), providesIndex, providesWith));
        }

        int bytesRead = classFile.getIndex() - startIndex;

        if (bytesRead != length) {
            Logger.error("Warning: ModuleAttribute read mismatch. Expected: " + length + ", Read: " + bytesRead);
        }
    }

    @Override
    protected void writeInfo(DataOutputStream dos) throws IOException {
        dos.writeShort(moduleNameIndex);
        dos.writeShort(moduleFlags);
        dos.writeShort(moduleVersionIndex);

        dos.writeShort(requires.size());
        for (Requires req : requires) {
            dos.writeShort(req.getRequiresIndex());
            dos.writeShort(req.getRequiresFlags());
            dos.writeShort(req.getRequiresVersionIndex());
        }

        dos.writeShort(exports.size());
        for (Exports exp : exports) {
            dos.writeShort(exp.getExportsIndex());
            dos.writeShort(exp.getExportsFlags());
            List<Integer> exportsTo = exp.getExportsTo();
            dos.writeShort(exportsTo.size());
            for (int to : exportsTo) {
                dos.writeShort(to);
            }
        }

        dos.writeShort(opens.size());
        for (Opens op : opens) {
            dos.writeShort(op.getOpensIndex());
            dos.writeShort(op.getOpensFlags());
            List<Integer> opensTo = op.getOpensTo();
            dos.writeShort(opensTo.size());
            for (int to : opensTo) {
                dos.writeShort(to);
            }
        }

        dos.writeShort(uses.size());
        for (Uses use : uses) {
            dos.writeShort(use.getUsesIndex());
        }

        dos.writeShort(provides.size());
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
        int size = 6;

        size += 2;
        size += requires.size() * 6;

        size += 2;
        for (Exports exp : exports) {
            size += 6;
            size += exp.getExportsTo().size() * 2;
        }

        size += 2;
        for (Opens op : opens) {
            size += 6;
            size += op.getOpensTo().size() * 2;
        }

        size += 2;
        size += uses.size() * 2;

        size += 2;
        for (Provides prov : provides) {
            size += 4;
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
        Item<?> utf8Item = getClassFile().getConstPool().getItem(moduleNameIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue().replace('/', '.');
        }
        return "Unknown";
    }

    private String resolveModuleVersion() {
        if (moduleVersionIndex == 0) {
            return "None";
        }
        Item<?> utf8Item = getClassFile().getConstPool().getItem(moduleVersionIndex);
        if (utf8Item instanceof Utf8Item) {
            return ((Utf8Item) utf8Item).getValue();
        }
        return "Unknown";
    }
}
