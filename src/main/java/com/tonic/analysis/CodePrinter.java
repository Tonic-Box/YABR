package com.tonic.analysis;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.InstructionFactory;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.LocalVariableTableAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.parser.attribute.stack.StackMapFrame;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.utill.Opcode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bytecode disassembler for converting raw bytecode into human-readable format.
 *
 * <p>Decoding is delegated to {@link InstructionFactory} (the single bytecode decoder) and operand
 * formatting to {@link InstructionRenderer}, so this class only lays out the per-instruction lines
 * and, for the verbose profile, interleaves line numbers, local-variable and stack-frame markers,
 * and the exception table.
 */
public class CodePrinter {

    /**
     * Disassembles raw bytecode into the terse per-instruction listing.
     *
     * @param code      the method's bytecode
     * @param constPool the constant pool for resolving references
     * @return a string representing the disassembled bytecode
     */
    public static String prettyPrintCode(byte[] code, ConstPool constPool) {
        StringBuilder sb = new StringBuilder();
        InstructionRenderer renderer = new InstructionRenderer(sb, constPool);
        for (Instruction instr : InstructionFactory.parse(code, constPool)) {
            appendInstruction(sb, renderer, instr);
        }
        return sb.toString();
    }

    /**
     * Disassembles a method's {@link CodeAttribute} with the enrichments selected by {@code options}.
     * With {@link DisassemblyOptions#terse()} the output matches {@link #prettyPrintCode(byte[],
     * ConstPool)}.
     *
     * @param codeAttribute the method's Code attribute
     * @param options       the enrichments to include
     * @return the disassembled method
     */
    public static String prettyPrintCode(CodeAttribute codeAttribute, DisassemblyOptions options) {
        ClassFile classFile = codeAttribute.getClassFile();
        ConstPool constPool = classFile.getConstPool();
        byte[] code = codeAttribute.getCode();

        LineNumberTableAttribute lineNumbers = findAttribute(codeAttribute, LineNumberTableAttribute.class);
        LocalVariableTableAttribute localVariables = findAttribute(codeAttribute, LocalVariableTableAttribute.class);
        StackMapTableAttribute stackMap = findAttribute(codeAttribute, StackMapTableAttribute.class);

        Map<Integer, Integer> lineByPc = options.isLineNumbers() ? lineNumberMap(lineNumbers) : Map.of();
        Map<Integer, String> frameByPc = options.isStackMapFrames() ? frameMap(stackMap) : Map.of();

        DisassemblyContext context = new DisassemblyContext(constPool, classFile,
                localVariables != null ? localVariables.getLocalVariableTable() : null,
                options.isLocalVariables(), options.isResolveBootstraps());

        StringBuilder sb = new StringBuilder();
        if (options.isHeader()) {
            sb.append("// max_stack = ").append(codeAttribute.getMaxStack())
                    .append(", max_locals = ").append(codeAttribute.getMaxLocals()).append("\n");
        }

        InstructionRenderer renderer = new InstructionRenderer(sb, constPool, context);
        for (Instruction instr : InstructionFactory.parse(code, constPool)) {
            int pc = instr.getOffset();
            Integer line = lineByPc.get(pc);
            if (line != null) {
                sb.append("// line ").append(line).append("\n");
            }
            String frame = frameByPc.get(pc);
            if (frame != null) {
                sb.append("// frame: ").append(frame).append("\n");
            }
            appendInstruction(sb, renderer, instr);
        }

        if (options.isExceptionTable()) {
            appendExceptionTable(sb, constPool, codeAttribute.getExceptionTable());
        }
        return sb.toString();
    }

    private static void appendInstruction(StringBuilder sb, InstructionRenderer renderer, Instruction instr) {
        String mnemonic = Opcode.fromCode(instr.getOpcode()).getMnemonic();
        sb.append(String.format("%04d: %-20s", instr.getOffset(), mnemonic));
        renderer.render(instr);
        sb.append("\n");
    }

    private static void appendExceptionTable(StringBuilder sb, ConstPool constPool, List<ExceptionTableEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        sb.append("// Exception table:\n");
        sb.append(String.format("//   %-8s %-8s %-8s %s%n", "from", "to", "target", "type"));
        for (ExceptionTableEntry entry : entries) {
            sb.append(String.format("//   %-8d %-8d %-8d %s%n",
                    entry.getStartPc(), entry.getEndPc(), entry.getHandlerPc(), catchType(constPool, entry.getCatchType())));
        }
    }

    private static String catchType(ConstPool constPool, int catchTypeIndex) {
        if (catchTypeIndex == 0) {
            return "any";
        }
        Item<?> item = constPool.getItem(catchTypeIndex);
        if (item instanceof ClassRefItem) {
            return ((ClassRefItem) item).getClassName().replace('/', '.');
        }
        return "#" + catchTypeIndex;
    }

    private static Map<Integer, Integer> lineNumberMap(LineNumberTableAttribute attribute) {
        Map<Integer, Integer> map = new HashMap<>();
        if (attribute == null || attribute.getLineNumberTable() == null) {
            return map;
        }
        for (LineNumberTableEntry entry : attribute.getLineNumberTable()) {
            map.putIfAbsent(entry.getStartPc(), entry.getLineNumber());
        }
        return map;
    }

    private static Map<Integer, String> frameMap(StackMapTableAttribute attribute) {
        Map<Integer, String> map = new HashMap<>();
        if (attribute == null || attribute.getFrames() == null) {
            return map;
        }
        int pc = -1;
        for (StackMapFrame frame : attribute.getFrames()) {
            pc = (pc < 0) ? frame.getOffsetDelta() : pc + frame.getOffsetDelta() + 1;
            map.putIfAbsent(pc, frame.getClass().getSimpleName());
        }
        return map;
    }

    private static <T extends Attribute> T findAttribute(CodeAttribute codeAttribute, Class<T> type) {
        for (Attribute attribute : codeAttribute.getAttributes()) {
            if (type.isInstance(attribute)) {
                return type.cast(attribute);
            }
        }
        return null;
    }
}
