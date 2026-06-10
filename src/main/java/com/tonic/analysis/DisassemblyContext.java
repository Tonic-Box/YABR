package com.tonic.analysis;

import com.tonic.analysis.instruction.InvokeDynamicInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.BootstrapMethodsAttribute;
import com.tonic.parser.attribute.MethodParametersAttribute;
import com.tonic.parser.attribute.table.BootstrapMethod;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.parser.attribute.table.MethodParameter;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.utill.DescriptorUtil;
import com.tonic.utill.Modifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * Supplies the verbose enrichments for disassembly: local-variable names/types (from the
 * LocalVariableTable) and resolved invokedynamic bootstraps (from the class's BootstrapMethods) that
 * {@link InstructionRenderer} appends to operands, plus the method {@link #signature(MethodEntry)}
 * header. Disabled enrichments yield empty strings, so the renderer needs no extra guards.
 */
final class DisassemblyContext {

    private final ConstPool constPool;
    private final ClassFile classFile;
    private final List<LocalVariableTableEntry> localTable;
    private final boolean localVariables;
    private final boolean resolveBootstraps;

    DisassemblyContext(ConstPool constPool, ClassFile classFile, List<LocalVariableTableEntry> localTable,
                       boolean localVariables, boolean resolveBootstraps) {
        this.constPool = constPool;
        this.classFile = classFile;
        this.localTable = localTable;
        this.localVariables = localVariables;
        this.resolveBootstraps = resolveBootstraps;
    }

    /**
     * Returns a {@code  // name: descriptor} annotation for the local slot live at the given offset,
     * or an empty string when local annotation is disabled or the slot is unnamed here.
     *
     * @param pc   the bytecode offset of the referencing instruction
     * @param slot the local variable slot
     * @return the annotation, or an empty string
     */
    String localAnnotation(int pc, int slot) {
        if (!localVariables) {
            return "";
        }
        LocalVariableTableEntry entry = entryForSlot(pc, slot);
        if (entry == null) {
            return "";
        }
        return "  // " + utf8(entry.getNameIndex()) + ": " + utf8(entry.getDescriptorIndex());
    }

    /**
     * Renders a method's signature as {@code name(param: descriptor, ...)}, resolving parameter names
     * from the MethodParameters attribute, then the LocalVariableTable, falling back to {@code argN}.
     * Parameter types always come from the descriptor; the implicit {@code this} slot is omitted.
     *
     * @param method the method to describe
     * @return the rendered signature
     */
    String signature(MethodEntry method) {
        List<String> paramTypes = DescriptorUtil.parseParameterDescriptors(method.getDesc());
        List<String> declaredNames = methodParameterNames(method);
        boolean isStatic = Modifiers.isStatic(method.getAccess());

        int slot = isStatic ? 0 : 1;
        StringBuilder sb = new StringBuilder(method.getName()).append('(');
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String type = paramTypes.get(i);
            String name = i < declaredNames.size() ? declaredNames.get(i) : null;
            if (name == null) {
                LocalVariableTableEntry entry = entryForSlot(0, slot);
                if (entry != null) {
                    name = utf8(entry.getNameIndex());
                }
            }
            sb.append(name != null ? name : "arg" + i).append(": ").append(type);
            slot += ("J".equals(type) || "D".equals(type)) ? 2 : 1;
        }
        return sb.append(')').toString();
    }

    private List<String> methodParameterNames(MethodEntry method) {
        for (Attribute attribute : method.getAttributes()) {
            if (attribute instanceof MethodParametersAttribute) {
                List<MethodParameter> params = ((MethodParametersAttribute) attribute).getParameters();
                List<String> names = new ArrayList<>(params.size());
                for (MethodParameter param : params) {
                    names.add(param.getNameIndex() != 0 ? utf8(param.getNameIndex()) : null);
                }
                return names;
            }
        }
        return List.of();
    }

    private LocalVariableTableEntry entryForSlot(int pc, int slot) {
        if (localTable == null) {
            return null;
        }
        for (LocalVariableTableEntry entry : localTable) {
            if (entry.getIndex() == slot
                    && pc >= entry.getStartPc()
                    && pc < entry.getStartPc() + entry.getLengthPc()) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Returns a {@code  // BSM: handle [args]} annotation resolving the invokedynamic's bootstrap
     * method and static arguments, or an empty string when bootstrap resolution is disabled or the
     * bootstrap cannot be located.
     *
     * @param instr the invokedynamic instruction
     * @return the annotation, or an empty string
     */
    String bootstrap(InvokeDynamicInstruction instr) {
        if (!resolveBootstraps || classFile == null) {
            return "";
        }
        BootstrapMethodsAttribute attribute = classFile.getBootstrapMethodsAttribute();
        if (attribute == null) {
            return "";
        }
        List<BootstrapMethod> methods = attribute.getBootstrapMethods();
        int index = instr.getBootstrapMethodAttrIndex();
        if (methods == null || index < 0 || index >= methods.size()) {
            return "";
        }
        BootstrapMethod method = methods.get(index);
        StringBuilder sb = new StringBuilder("  // BSM: ")
                .append(ConstPoolFormat.methodHandle(constPool, method.getBootstrapMethodRef()));
        List<Integer> arguments = method.getBootstrapArguments();
        if (arguments != null && !arguments.isEmpty()) {
            sb.append(" [");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(ConstPoolFormat.constant(constPool, arguments.get(i)));
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private String utf8(int index) {
        Item<?> item = constPool.getItem(index);
        return item instanceof Utf8Item ? ((Utf8Item) item).getValue() : "?";
    }
}
