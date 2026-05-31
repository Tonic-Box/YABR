package com.tonic.analysis.ssa.llvm.lift;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a {@link ParsedFunction} into a fully-wired {@link IRMethod}.
 *
 * <p>Two passes:
 * <ol>
 *   <li><b>Allocation</b> — allocate {@link SSAValue} for every {@code %v<id>}, allocate
 *       {@link IRBlock} for every label, register parameters, set entry block.</li>
 *   <li><b>Construction</b> — walk each block's lines, parse each instruction, wire
 *       CFG edges from terminators, and record pending phi arms for a final fixup.</li>
 * </ol>
 *
 * Synthesized temporaries ({@code %t<n>}) are collected into a side map used by
 * {@link TempFoldingPass} to collapse multi-step expansions.
 */
final class FunctionLifter {

    // ---- line patterns -------------------------------------------------------

    private static final Pattern VALUE_ASSIGN = Pattern.compile("^(%v\\d+|%t\\d+) = (.+)$");
    private static final Pattern BR_LABEL = Pattern.compile("^br label %([A-Za-z0-9_]+)$");
    private static final Pattern BR_COND = Pattern.compile(
        "^br i1 (%[vt]\\d+), label %([A-Za-z0-9_]+), label %([A-Za-z0-9_]+)$");
    private static final Pattern RET_VOID = Pattern.compile("^ret void$");
    private static final Pattern RET_VAL = Pattern.compile("^ret (\\S+) (.+)$");
    private static final Pattern SWITCH = Pattern.compile(
        "^switch (\\S+) (.+?), label %([A-Za-z0-9_]+) \\[(.*)\\]$");
    private static final Pattern SWITCH_CASE = Pattern.compile(
        "i\\d+ (-?\\d+), label %([A-Za-z0-9_]+)");
    private static final Pattern CALL_VOID = Pattern.compile(
        "^call void ((?:@\"[^\"]*\"|@\\S+|%[vt]\\d+))\\((.*)\\)$");
    private static final Pattern CALL_VAL = Pattern.compile(
        "^(%v\\d+) = call (\\S+) ((?:@\"[^\"]*\"|@\\S+|%[vt]\\d+))\\((.*)\\)$");
    private static final Pattern ICMP = Pattern.compile(
        "^(%t\\d+) = icmp (\\w+) (\\S+) (.+), (.+)$");
    private static final Pattern PHI = Pattern.compile(
        "^(%v\\d+) = phi (\\S+) (.+)$");

    // ---- fields --------------------------------------------------------------

    private final ParsedFunction pf;
    private final IRMethod method;
    /** %v<id> → SSAValue (package-private for TempFoldingPass). */
    final Map<String, SSAValue> vMap = new HashMap<>();
    /** label → IRBlock */
    private final Map<String, IRBlock> blockMap = new LinkedHashMap<>();
    /** Pending phi arms: block-label → (phi instruction, list of [value-text, pred-label] pairs) */
    private final Map<String, List<PendingPhi>> pendingPhis = new HashMap<>();
    /**
     * Raw icmp line keyed by temp register; consumed when the subsequent {@code br i1 %t<n>} is seen.
     * Also used by {@link TempFoldingPass}.
     */
    final Map<String, TempEntry> temps = new LinkedHashMap<>();

    FunctionLifter(ParsedFunction pf) {
        this.pf = pf;
        SymbolDemangle.Symbol sym = SymbolDemangle.parse(pf.symbol);
        String owner = sym != null ? sym.owner : "";
        String name = sym != null ? sym.name : pf.symbol;
        String descriptor = sym != null ? sym.descriptor : "()V";
        boolean isStatic = pf.params.isEmpty() || !pf.params.get(0).trim().startsWith("ptr");
        method = new IRMethod(owner, name, descriptor, isStatic);
        method.setReturnType(TypeParser.parse(pf.returnType));
    }

    IRMethod lift() {
        passOne();
        passTwo();
        fixupPhis();
        return method;
    }

    // ---- pass 1: allocate ---------------------------------------------------

    private void passOne() {
        // Allocate blocks
        for (ParsedFunction.ParsedBlock pb : pf.blocks) {
            IRBlock block = new IRBlock(pb.label);
            blockMap.put(pb.label, block);
            method.addBlock(block);
        }
        if (!blockMap.isEmpty()) {
            method.setEntryBlock(blockMap.values().iterator().next());
        }

        // Collect all %v<id> assignments to pre-allocate SSAValues with correct types
        for (ParsedFunction.ParsedBlock pb : pf.blocks) {
            for (String line : pb.lines) {
                Matcher m = VALUE_ASSIGN.matcher(line);
                if (!m.matches()) {
                    continue;
                }
                String reg = m.group(1);
                String rhs = m.group(2);
                if (reg.startsWith("%v")) {
                    IRType type = inferType(rhs);
                    SSAValue sv = new SSAValue(type);
                    vMap.put(reg, sv);
                }
                // %t<n> temps are handled during pass 2
            }
        }

        // Register parameters
        for (String param : pf.params) {
            param = param.trim();
            if (param.isEmpty()) {
                continue;
            }
            int sp = param.lastIndexOf(' ');
            String typeStr = sp > 0 ? param.substring(0, sp) : param;
            String regStr = sp > 0 ? param.substring(sp + 1) : "";
            IRType type = TypeParser.parse(typeStr.trim());
            SSAValue pv = vMap.computeIfAbsent(regStr.trim(), k -> new SSAValue(type));
            method.addParameter(pv);
        }
    }

    // ---- pass 2: construct ---------------------------------------------------

    private void passTwo() {
        for (ParsedFunction.ParsedBlock pb : pf.blocks) {
            IRBlock block = blockMap.get(pb.label);
            pendingPhis.put(pb.label, new ArrayList<>());

            for (int i = 0; i < pb.lines.size(); i++) {
                String line = pb.lines.get(i);

                // Skip non-computational ABI calls and unreachable
                if (line.equals("unreachable") || isAbiLine(line)) {
                    continue;
                }

                // goto
                Matcher brl = BR_LABEL.matcher(line);
                if (brl.matches()) {
                    IRBlock target = requireBlock(brl.group(1));
                    SimpleInstruction go = SimpleInstruction.createGoto(target);
                    block.addInstruction(go);
                    block.addSuccessor(target);
                    continue;
                }

                // conditional branch (requires previous icmp %t<n>)
                Matcher brc = BR_COND.matcher(line);
                if (brc.matches()) {
                    String condReg = brc.group(1);
                    IRBlock trueBlk = requireBlock(brc.group(2));
                    IRBlock falseBlk = requireBlock(brc.group(3));
                    TempEntry te = temps.get(condReg);
                    if (te != null && te.opcode.equals("icmp")) {
                        CompareOp cond = parseCompareOp(te.op, te.lhsType);
                        Value left = resolveValue(te.lhsText, te.lhsType);
                        if (te.rhsText.equals("0") || te.rhsText.equals("null")) {
                            block.addInstruction(new BranchInstruction(cond, left, trueBlk, falseBlk));
                        } else {
                            Value right = resolveValue(te.rhsText, te.lhsType);
                            block.addInstruction(new BranchInstruction(cond, left, right, trueBlk, falseBlk));
                        }
                    } else {
                        // fallback: use the temp as a boolean value
                        Value condVal = resolveValue(condReg, PrimitiveType.BOOLEAN);
                        block.addInstruction(new BranchInstruction(CompareOp.IFEQ, condVal, falseBlk, trueBlk));
                    }
                    block.addSuccessor(trueBlk);
                    block.addSuccessor(falseBlk);
                    continue;
                }

                // return void
                if (RET_VOID.matcher(line).matches()) {
                    block.addInstruction(new ReturnInstruction());
                    continue;
                }

                // return <type> <val>
                Matcher rv = RET_VAL.matcher(line);
                if (rv.matches()) {
                    IRType retTy = TypeParser.parse(rv.group(1));
                    Value retVal = resolveValue(rv.group(2), retTy);
                    block.addInstruction(new ReturnInstruction(retVal));
                    continue;
                }

                // switch
                Matcher sw = SWITCH.matcher(line);
                if (sw.matches()) {
                    IRType keyTy = TypeParser.parse(sw.group(1));
                    Value key = resolveValue(sw.group(2), keyTy);
                    IRBlock dflt = requireBlock(sw.group(3));
                    SwitchInstruction si = new SwitchInstruction(key, dflt);
                    block.addSuccessor(dflt);
                    String caseStr = sw.group(4).trim();
                    if (!caseStr.isEmpty()) {
                        Matcher cm = SWITCH_CASE.matcher(caseStr);
                        while (cm.find()) {
                            int val = Integer.parseInt(cm.group(1));
                            IRBlock caseBlk = requireBlock(cm.group(2));
                            si.addCase(val, caseBlk);
                            block.addSuccessor(caseBlk);
                        }
                    }
                    block.addInstruction(si);
                    continue;
                }

                // void call (static method call)
                Matcher cv = CALL_VOID.matcher(line);
                if (cv.matches()) {
                    String callee = cv.group(1);
                    List<Value> args = parseCallArgs(cv.group(2));
                    SymbolDemangle.Symbol sym = SymbolDemangle.parse(callee);
                    if (sym != null) {
                        block.addInstruction(new InvokeInstruction(
                            InvokeType.STATIC, sym.owner, sym.name, sym.descriptor, args));
                    }
                    continue;
                }

                // value-producing instruction: %v<id> = ...
                Matcher va = VALUE_ASSIGN.matcher(line);
                if (!va.matches()) {
                    continue;
                }
                String reg = va.group(1);
                String rhs = va.group(2);

                // phi
                Matcher pm = PHI.matcher(line);
                if (pm.matches()) {
                    SSAValue result = requireVReg(pm.group(1));
                    List<String[]> arms = parsePhiArms(pm.group(3));
                    PhiInstruction phi = new PhiInstruction(result);
                    block.addPhi(phi);
                    // defer incoming wiring until all blocks are done
                    for (String[] arm : arms) {
                        pendingPhis.get(pb.label).add(new PendingPhi(phi, TypeParser.parse(pm.group(2)), arm[0], arm[1]));
                    }
                    continue;
                }

                // value call
                Matcher callm = CALL_VAL.matcher(line);
                if (callm.matches()) {
                    SSAValue result = requireVReg(callm.group(1));
                    String callee = callm.group(3);
                    List<Value> args = parseCallArgs(callm.group(4));
                    SymbolDemangle.Symbol sym = SymbolDemangle.parse(callee);
                    if (sym != null) {
                        block.addInstruction(new InvokeInstruction(
                            result, InvokeType.STATIC, sym.owner, sym.name, sym.descriptor, args));
                    }
                    continue;
                }

                // icmp stored as temp — consumed by subsequent br
                Matcher im = ICMP.matcher(line);
                if (im.matches() && reg.startsWith("%t")) {
                    temps.put(reg, new TempEntry("icmp", im.group(2),
                        TypeParser.parse(im.group(3)), im.group(4).trim(), im.group(5).trim(), rhs));
                    continue;
                }

                // All remaining value-producing lines
                if (reg.startsWith("%t")) {
                    temps.put(reg, new TempEntry(null, null, null, null, null, rhs));
                    continue;
                }

                // %v<id> = binary/unary/convert
                SSAValue result = requireVReg(reg);
                IRInstruction instr = buildValueInstr(result, rhs);
                if (instr != null) {
                    block.addInstruction(instr);
                }
            }
        }
    }

    // ---- phi fixup -----------------------------------------------------------

    private void fixupPhis() {
        for (Map.Entry<String, List<PendingPhi>> e : pendingPhis.entrySet()) {
            for (PendingPhi pp : e.getValue()) {
                Value incoming = resolveValue(pp.valueText, pp.type);
                IRBlock pred = blockMap.get(pp.predLabel);
                if (pred != null) {
                    pp.phi.addIncoming(incoming, pred);
                }
            }
        }
    }

    // ---- instruction builders -----------------------------------------------

    private IRInstruction buildValueInstr(SSAValue result, String rhs) {
        // binary ops
        BinaryOp bop = tryParseBinaryOp(rhs);
        if (bop != null) {
            String[] parts = rhs.split("\\s+", 3);
            // "add i32 %v0, %v1"  →  parts[0]=opcode parts[1]=type parts[2]="left, right"
            String typeStr = parts.length > 1 ? parts[1] : "i32";
            IRType ty = TypeParser.parse(typeStr);
            String operands = parts.length > 2 ? parts[2] : "";
            String[] ops = splitOperands(operands);
            if (ops.length >= 2) {
                Value left = resolveValue(ops[0], ty);
                Value right = resolveValue(ops[1], ty);
                return new BinaryOpInstruction(result, bop, left, right);
            }
        }

        // unary / conversion (sext, trunc, zext, sitofp, fptosi, fpext, fptrunc, fneg, sub 0)
        UnaryOp uop = tryParseUnaryOp(rhs, result);
        if (uop != null) {
            return buildUnaryInstr(result, uop, rhs);
        }

        // integer negation: "sub i32 0, %v<id>"
        if (rhs.startsWith("sub ") && rhs.contains(" 0, ")) {
            IRType ty = TypeParser.parse(rhs.split("\\s+")[1]);
            String operand = rhs.substring(rhs.indexOf(", ") + 2).trim();
            return new UnaryOpInstruction(result, UnaryOp.NEG, resolveValue(operand, ty));
        }

        // select — produced by 3-way compare expansion, handled by TempFoldingPass
        // just record as temp materialisation for now
        return null;
    }

    private IRInstruction buildUnaryInstr(SSAValue result, UnaryOp uop, String rhs) {
        // Format: "<opcode> <srcType> <operand> to <dstType>" or "fneg <type> <operand>"
        String[] parts = rhs.split("\\s+");
        String srcType = parts.length > 1 ? parts[1] : "i32";
        String operandText = parts.length > 2 ? parts[2] : "";
        // strip trailing "to <dstType>" if present
        if (operandText.endsWith(",")) {
            operandText = operandText.substring(0, operandText.length() - 1);
        }
        Value operand = resolveValue(operandText, TypeParser.parse(srcType));
        return new UnaryOpInstruction(result, uop, operand);
    }

    // ---- helpers -------------------------------------------------------------

    private SSAValue requireVReg(String reg) {
        SSAValue sv = vMap.get(reg);
        if (sv == null) {
            throw new LlvmLiftException("undefined SSA register: " + reg);
        }
        return sv;
    }

    private IRBlock requireBlock(String label) {
        IRBlock b = blockMap.get(label);
        if (b == null) {
            throw new LlvmLiftException("undefined block label: " + label);
        }
        return b;
    }

    private Value resolveValue(String text, IRType type) {
        text = text.trim();
        if (text.startsWith("%v")) {
            SSAValue sv = vMap.get(text);
            if (sv == null) {
                sv = new SSAValue(type);
                vMap.put(text, sv);
            }
            return sv;
        }
        if (text.startsWith("%t")) {
            // Will be resolved by TempFoldingPass; return a placeholder SSAValue
            return vMap.computeIfAbsent(text, k -> new SSAValue(type));
        }
        if (text.equals("null")) {
            return NullConstant.INSTANCE;
        }
        if (text.startsWith("0x") || text.startsWith("0X")) {
            // IEEE-754 hex literal
            long bits = Long.parseUnsignedLong(text.substring(2), 16);
            if (type == PrimitiveType.FLOAT) {
                return new FloatConstant((float) Double.longBitsToDouble(bits));
            }
            return new DoubleConstant(Double.longBitsToDouble(bits));
        }
        // decimal integer
        try {
            long v = Long.parseLong(text);
            if (type == PrimitiveType.LONG) {
                return new LongConstant(v);
            }
            return IntConstant.of((int) v);
        } catch (NumberFormatException e) {
            // If it's a constant that doesn't parse, create a placeholder
            return IntConstant.of(0);
        }
    }

    private IRType inferType(String rhs) {
        // Extract the first type token from a binary/unary/phi/call/etc. expression
        String[] parts = rhs.trim().split("\\s+");
        if (parts.length < 2) {
            return PrimitiveType.INT;
        }
        String opcode = parts[0];
        // For phi the type is the second token
        if (opcode.equals("phi")) {
            return tryParseType(parts[1]);
        }
        // For most ops: "add i32 ...", "sext i32 ... to i64" → the type is parts[1]
        // But for conversions the result type comes after "to"
        String typeCandidate = parts[1];
        IRType base = tryParseType(typeCandidate);
        // For conversions look for "to <type>" at end
        for (int j = 0; j < parts.length - 1; j++) {
            if (parts[j].equals("to") && j + 1 < parts.length) {
                IRType dest = tryParseType(parts[j + 1]);
                if (dest != PrimitiveType.INT) {
                    return dest;
                }
            }
        }
        // call: "call i32 ..." or "call ptr ..."
        if (opcode.equals("call") || opcode.equals("invoke")) {
            return base;
        }
        // select result is i32 (only select patterns we emit produce i32)
        if (opcode.equals("select")) {
            return PrimitiveType.INT;
        }
        return base;
    }

    private static IRType tryParseType(String s) {
        try {
            return TypeParser.parse(s);
        } catch (LlvmLiftException e) {
            return PrimitiveType.INT;
        }
    }

    private List<Value> parseCallArgs(String argsText) {
        List<Value> result = new ArrayList<>();
        if (argsText == null || argsText.isBlank()) {
            return result;
        }
        // Split on ", " but not inside nested parens (our args have none)
        for (String arg : splitCallArgs(argsText)) {
            arg = arg.trim();
            if (arg.isEmpty()) {
                continue;
            }
            int sp = arg.lastIndexOf(' ');
            String typeStr = sp > 0 ? arg.substring(0, sp).trim() : "i32";
            String valStr = sp > 0 ? arg.substring(sp + 1).trim() : arg;
            IRType type = tryParseType(typeStr);
            result.add(resolveValue(valStr, type));
        }
        return result;
    }

    private static List<String> splitCallArgs(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(s.substring(start));
        return parts;
    }

    /** Splits {@code "left, right"} into 2 strings, handling quoted symbols in args. */
    private static String[] splitOperands(String s) {
        // Find the comma that separates the two operands (not inside quotes)
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                return new String[]{s.substring(0, i).trim(), s.substring(i + 1).trim()};
            }
        }
        return new String[]{s.trim()};
    }

    private static BinaryOp tryParseBinaryOp(String rhs) {
        String op = rhs.trim().split("\\s+")[0];
        switch (op) {
            case "add":
            case "fadd":
                return BinaryOp.ADD;
            case "sub":
            case "fsub":
                return BinaryOp.SUB; // "sub 0, x" (NEG) is caught first in buildValueInstr
            case "mul":
            case "fmul":
                return BinaryOp.MUL;
            case "sdiv":
            case "fdiv":
                return BinaryOp.DIV;
            case "srem":
            case "frem":
                return BinaryOp.REM;
            case "and":   return BinaryOp.AND;
            case "or":    return BinaryOp.OR;
            case "xor":   return BinaryOp.XOR;
            case "shl":   return BinaryOp.SHL;
            case "ashr":  return BinaryOp.SHR;
            case "lshr":  return BinaryOp.USHR;
            default:      return null;
        }
    }

    private static UnaryOp tryParseUnaryOp(String rhs, SSAValue result) {
        String op = rhs.trim().split("\\s+")[0];
        switch (op) {
            case "sext":    return inferSignedExtOp(rhs, result);
            case "trunc":
            case "zext":
                return null; // handled in TempFoldingPass
            case "sitofp":  return inferSiToFp(rhs, result);
            case "fptosi":  return inferFpToSi(rhs, result);
            case "fpext":   return UnaryOp.F2D;
            case "fptrunc": return UnaryOp.D2F;
            case "fneg":    return UnaryOp.NEG;
            default:        return null;
        }
    }

    private static UnaryOp inferSignedExtOp(String rhs, SSAValue result) {
        // "sext i32 %v<src> to i64" → I2L
        if (rhs.contains("to i64")) return UnaryOp.I2L;
        // plain sext from narrower → not from a direct JVM instruction; skip
        return null;
    }

    private static UnaryOp inferSiToFp(String rhs, SSAValue result) {
        if (rhs.contains("to double")) {
            return rhs.contains("i64") ? UnaryOp.L2D : UnaryOp.I2D;
        }
        return rhs.contains("i64") ? UnaryOp.L2F : UnaryOp.I2F;
    }

    private static UnaryOp inferFpToSi(String rhs, SSAValue result) {
        if (rhs.contains("to i64")) {
            return rhs.contains("double") ? UnaryOp.D2L : UnaryOp.F2L;
        }
        return rhs.contains("double") ? UnaryOp.D2I : UnaryOp.F2I;
    }

    private static CompareOp parseCompareOp(String pred, IRType type) {
        boolean isRef = type == ReferenceType.OBJECT;
        switch (pred) {
            case "eq":  return isRef ? CompareOp.ACMPEQ : CompareOp.EQ;
            case "ne":  return isRef ? CompareOp.ACMPNE : CompareOp.NE;
            case "slt": return CompareOp.LT;
            case "sle": return CompareOp.LE;
            case "sgt": return CompareOp.GT;
            case "sge": return CompareOp.GE;
            default:    return CompareOp.EQ;
        }
    }

    /** Parse phi arm list {@code "[%v0, %B3], [%v1, %B5]"} into pairs of [valueText, blockLabel]. */
    private static List<String[]> parsePhiArms(String armsText) {
        List<String[]> result = new ArrayList<>();
        Pattern arm = Pattern.compile("\\[([^,\\]]+),\\s*%([A-Za-z0-9_]+)\\]");
        Matcher m = arm.matcher(armsText);
        while (m.find()) {
            result.add(new String[]{m.group(1).trim(), m.group(2)});
        }
        return result;
    }

    private static boolean isAbiLine(String line) {
        return line.startsWith("call void @jvm_") || line.startsWith("call ptr @jvm_")
            || line.contains("@jvm_") || line.contains("@.str.");
    }

    // ---- inner data holders --------------------------------------------------

    static final class TempEntry {
        final String opcode;
        final String op;
        final IRType lhsType;
        final String lhsText;
        final String rhsText;
        final String rawRhs;

        TempEntry(String opcode, String op, IRType lhsType, String lhsText, String rhsText, String rawRhs) {
            this.opcode = opcode;
            this.op = op;
            this.lhsType = lhsType;
            this.lhsText = lhsText;
            this.rhsText = rhsText;
            this.rawRhs = rawRhs;
        }
    }

    private static final class PendingPhi {
        final PhiInstruction phi;
        final IRType type;
        final String valueText;
        final String predLabel;

        PendingPhi(PhiInstruction phi, IRType type, String valueText, String predLabel) {
            this.phi = phi;
            this.type = type;
            this.valueText = valueText;
            this.predLabel = predLabel;
        }
    }
}
