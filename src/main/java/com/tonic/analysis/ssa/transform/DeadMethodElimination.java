package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.constpool.*;

import java.lang.reflect.Modifier;
import java.util.*;

import static com.tonic.utill.Opcode.*;

/**
 * Dead method elimination optimization.
 * Removes private methods that are never called from the class.
 * This is typically run after method inlining to clean up helper methods
 * that have been fully inlined.
 *
 * Note: This only removes private methods to ensure we don't break
 * external callers. Public, protected, and package-private methods
 * are preserved as they may be called from outside the class.
 */
public class DeadMethodElimination implements ClassTransform {

    @Override
    public String getName() {
        return "DeadMethodElimination";
    }

    @Override
    public boolean run(ClassFile classFile, SSA ssa) {
        String className = classFile.getClassName();

        Set<String> referencedMethods = buildReferencedMethods(classFile, className);

        List<MethodEntry> deadMethods = findDeadMethods(classFile, referencedMethods);

        if (deadMethods.isEmpty()) {
            return false;
        }

        for (MethodEntry method : deadMethods) {
            classFile.getMethods().remove(method);
        }

        return true;
    }

    /**
     * Builds the set of methods that are referenced (called) within the class.
     */
    private Set<String> buildReferencedMethods(ClassFile classFile, String className) {
        Set<String> referenced = new HashSet<>();

        for (MethodEntry method : classFile.getMethods()) {
            CodeAttribute code = method.getCodeAttribute();
            if (code == null) continue;

            byte[] bytecode = code.getCode();
            int i = 0;

            while (i < bytecode.length) {
                int opcode = bytecode[i] & 0xFF;

                if (isInvokeInstruction(opcode)) {
                    int cpIndex = ((bytecode[i + 1] & 0xFF) << 8) | (bytecode[i + 2] & 0xFF);

                    String targetOwner = resolveMethodOwner(classFile, cpIndex);
                    if (targetOwner != null && targetOwner.equals(className)) {
                        String targetName = resolveMethodName(classFile, cpIndex);
                        String targetDesc = resolveMethodDescriptor(classFile, cpIndex);
                        if (targetName != null && targetDesc != null) {
                            referenced.add(targetName + targetDesc);
                        }
                    }
                }

                i += getInstructionLength(opcode, bytecode, i);
            }
        }

        for (MethodEntry method : classFile.getMethods()) {
            if (isEntryPoint(method)) {
                referenced.add(method.getName() + method.getDesc());
            }
        }

        return referenced;
    }

    /**
     * Finds methods that are dead (not referenced and eligible for removal).
     */
    private List<MethodEntry> findDeadMethods(ClassFile classFile, Set<String> referencedMethods) {
        List<MethodEntry> dead = new ArrayList<>();

        for (MethodEntry method : classFile.getMethods()) {
            String key = method.getName() + method.getDesc();

            if (referencedMethods.contains(key)) {
                continue;
            }

            int access = method.getAccess();
            if (!Modifier.isPrivate(access)) {
                continue;
            }

            String name = method.getName();
            if (name.equals("<init>") || name.equals("<clinit>")) {
                continue;
            }

            dead.add(method);
        }

        return dead;
    }

    /**
     * Checks if a method is an entry point (should never be removed).
     */
    private boolean isEntryPoint(MethodEntry method) {
        String name = method.getName();
        int access = method.getAccess();

        if (name.equals("<init>") || name.equals("<clinit>")) {
            return true;
        }

        if (name.equals("main") && method.getDesc().equals("([Ljava/lang/String;)V")
                && Modifier.isPublic(access) && Modifier.isStatic(access)) {
            return true;
        }

        if (!Modifier.isPrivate(access)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if an opcode is an invoke instruction.
     */
    private boolean isInvokeInstruction(int opcode) {
        return opcode == INVOKEVIRTUAL.getCode()
                || opcode == INVOKESPECIAL.getCode()
                || opcode == INVOKESTATIC.getCode()
                || opcode == INVOKEINTERFACE.getCode();
    }

    /**
     * Resolves the owner class from a method reference constant pool entry.
     */
    private String resolveMethodOwner(ClassFile classFile, int cpIndex) {
        try {
            var item = classFile.getConstPool().getItem(cpIndex);
            if (item instanceof MethodRefItem) {
                MethodRefItem mri = (MethodRefItem) item;
                var classRef = classFile.getConstPool().getItem(mri.getValue().getClassIndex());
                if (classRef instanceof ClassRefItem) {
                    ClassRefItem cri = (ClassRefItem) classRef;
                    var nameUtf8 = classFile.getConstPool().getItem(cri.getValue());
                    if (nameUtf8 instanceof Utf8Item) {
                        Utf8Item ui = (Utf8Item) nameUtf8;
                        return ui.getValue();
                    }
                }
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem imri = (InterfaceRefItem) item;
                var classRef = classFile.getConstPool().getItem(imri.getValue().getClassIndex());
                if (classRef instanceof ClassRefItem) {
                    ClassRefItem cri = (ClassRefItem) classRef;
                    var nameUtf8 = classFile.getConstPool().getItem(cri.getValue());
                    if (nameUtf8 instanceof Utf8Item) {
                        Utf8Item ui = (Utf8Item) nameUtf8;
                        return ui.getValue();
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Resolves the method name from a method reference constant pool entry.
     */
    private String resolveMethodName(ClassFile classFile, int cpIndex) {
        try {
            var item = classFile.getConstPool().getItem(cpIndex);
            int natIndex = -1;
            if (item instanceof MethodRefItem) {
                MethodRefItem mri = (MethodRefItem) item;
                natIndex = mri.getValue().getNameAndTypeIndex();
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem imri = (InterfaceRefItem) item;
                natIndex = imri.getValue().getNameAndTypeIndex();
            }
            if (natIndex > 0) {
                var nat = classFile.getConstPool().getItem(natIndex);
                if (nat instanceof NameAndTypeRefItem) {
                    NameAndTypeRefItem nati = (NameAndTypeRefItem) nat;
                    var nameUtf8 = classFile.getConstPool().getItem(nati.getValue().getNameIndex());
                    if (nameUtf8 instanceof Utf8Item) {
                        Utf8Item ui = (Utf8Item) nameUtf8;
                        return ui.getValue();
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Resolves the method descriptor from a method reference constant pool entry.
     */
    private String resolveMethodDescriptor(ClassFile classFile, int cpIndex) {
        try {
            var item = classFile.getConstPool().getItem(cpIndex);
            int natIndex = -1;
            if (item instanceof MethodRefItem) {
                MethodRefItem mri = (MethodRefItem) item;
                natIndex = mri.getValue().getNameAndTypeIndex();
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem imri = (InterfaceRefItem) item;
                natIndex = imri.getValue().getNameAndTypeIndex();
            }
            if (natIndex > 0) {
                var nat = classFile.getConstPool().getItem(natIndex);
                if (nat instanceof NameAndTypeRefItem) {
                    NameAndTypeRefItem nati = (NameAndTypeRefItem) nat;
                    var descUtf8 = classFile.getConstPool().getItem(nati.getValue().getDescriptorIndex());
                    if (descUtf8 instanceof Utf8Item) {
                        Utf8Item ui = (Utf8Item) descUtf8;
                        return ui.getValue();
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Gets the length of a bytecode instruction.
     */
    private int getInstructionLength(int opcode, byte[] bytecode, int offset) {
        if (opcode == WIDE.getCode()) {
            int nextOpcode = bytecode[offset + 1] & 0xFF;
            if (nextOpcode == IINC.getCode()) {
                return 6;
            }
            return 4;
        }

        if (opcode == TABLESWITCH.getCode()) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int base = offset + 1 + padding;
            int low = ((bytecode[base + 4] & 0xFF) << 24) | ((bytecode[base + 5] & 0xFF) << 16)
                    | ((bytecode[base + 6] & 0xFF) << 8) | (bytecode[base + 7] & 0xFF);
            int high = ((bytecode[base + 8] & 0xFF) << 24) | ((bytecode[base + 9] & 0xFF) << 16)
                    | ((bytecode[base + 10] & 0xFF) << 8) | (bytecode[base + 11] & 0xFF);
            return 1 + padding + 12 + (high - low + 1) * 4;
        }

        if (opcode == LOOKUPSWITCH.getCode()) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int base = offset + 1 + padding;
            int npairs = ((bytecode[base + 4] & 0xFF) << 24) | ((bytecode[base + 5] & 0xFF) << 16)
                    | ((bytecode[base + 6] & 0xFF) << 8) | (bytecode[base + 7] & 0xFF);
            return 1 + padding + 8 + npairs * 8;
        }

        return INSTRUCTION_LENGTHS[opcode];
    }

    private static final int[] INSTRUCTION_LENGTHS = {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x00-0x0F
            2, 3, 2, 3, 3, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, // 0x10-0x1F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x20-0x2F
            1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, // 0x30-0x3F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x40-0x4F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x50-0x5F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x60-0x6F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x70-0x7F
            1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0x80-0x8F
            1, 1, 1, 1, 1, 1, 1, 1, 1, 3, 3, 3, 3, 3, 3, 3, // 0x90-0x9F
            3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 0, 0, 1, 1, 1, 1, // 0xA0-0xAF (AA/AB handled specially)
            1, 1, 3, 3, 3, 3, 3, 3, 5, 5, 3, 2, 3, 1, 1, 3, // 0xB0-0xBF
            3, 1, 1, 0, 4, 3, 3, 5, 5, 1, 1, 1, 1, 1, 1, 1, // 0xC0-0xCF (C4 handled specially)
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0xD0-0xDF
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, // 0xE0-0xEF
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1  // 0xF0-0xFF
    };
}
