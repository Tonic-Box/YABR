package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.ExceptionHandler;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.analysis.ssa.visitor.AbstractIRVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lowers one {@link IRMethod} (in true SSA form, post-lift) to a textual LLVM IR {@code define}.
 * Dispatches per-instruction via {@link AbstractIRVisitor}; supported visitors emit LLVM lines into
 * {@link LlvmFunctionBuilder}, everything else routes to {@link UnsupportedLowering}.
 *
 * <p>Scope (v1): constants, integer/float arithmetic + conversions, phis, integer branches,
 * switch, return, goto, and static invokes. The visitor emits native LLVM {@code phi} (no SSA
 * destruction) and names every SSA value {@code %v{id}}, so loop back-edge/forward references in
 * phis resolve symbolically with no ordering pass.
 */
final class SsaToLlvmLowerer extends AbstractIRVisitor<Void> {

    private final IRMethod method;
    private final LlvmFunctionBuilder fb;
    private final DeclareCollector declares;
    private final GlobalCollector globals;
    private final CStringPool strings;
    private final LlvmLoweringConfig config;
    private String definedSymbol;

    /** Try regions grouped by (tryStart,tryEnd); each gets one landingpad that dispatches its catches. */
    private final java.util.Map<IRBlock, EhRegion> coveredBy = new java.util.IdentityHashMap<>();
    private final List<EhRegion> regions = new ArrayList<>();
    private EhRegion currentUnwind;
    private int regionCounter;

    private static final class EhRegion {
        final String lpadLabel;
        final List<ExceptionHandler> handlers = new ArrayList<>();
        boolean used;

        EhRegion(String lpadLabel) {
            this.lpadLabel = lpadLabel;
        }
    }

    SsaToLlvmLowerer(IRMethod method, LlvmFunctionBuilder fb, DeclareCollector declares,
                     GlobalCollector globals, CStringPool strings, LlvmLoweringConfig config) {
        this.method = method;
        this.fb = fb;
        this.declares = declares;
        this.globals = globals;
        this.strings = strings;
        this.config = config;
    }

    String definedSymbol() {
        return definedSymbol;
    }

    /** True when the configured object model emits object/reference operations (vs rejecting them). */
    private boolean objects() {
        return config.getObjectModel() != LlvmLoweringConfig.ObjectModel.NONE;
    }

    /** Lowers the method to its complete {@code define ... { ... }} text. */
    String lowerFunction() {
        if (method.getEntryBlock() == null) {
            throw UnsupportedLowering.reject("method without a body");
        }
        String descriptor = method.getDescriptor();
        if (!objects()) {
            if (!method.isStatic()) {
                throw UnsupportedLowering.reject("instance method (receiver is a reference)");
            }
            if (signatureHasReferences(descriptor)) {
                throw UnsupportedLowering.reject("reference or array in signature");
            }
        }

        LlvmType returnType = IrTypeMapper.mapReturn(descriptor);
        definedSymbol = SymbolMangler.mangle(method.getOwnerClass(), method.getName(), descriptor);

        List<String> params = new ArrayList<>();
        for (SSAValue param : method.getParameters()) {
            params.add(IrTypeMapper.map(param.getType()).render() + " %v" + param.getId());
        }

        if (objects()) {
            buildExceptionRegions();
        }

        for (IRBlock block : method.getBlocksInOrder()) {
            fb.label("B" + block.getId());
            currentUnwind = coveredBy.get(block);
            for (PhiInstruction phi : block.getPhiInstructions()) {
                phi.accept(this);
            }
            for (IRInstruction instr : block.getInstructions()) {
                instr.accept(this);
            }
        }
        currentUnwind = null;

        boolean usedEh = false;
        for (EhRegion region : regions) {
            if (region.used) {
                usedEh = true;
                emitLandingpad(region);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("define ").append(returnType.render()).append(' ').append(definedSymbol)
            .append('(').append(String.join(", ", params)).append(") ");
        if (usedEh) {
            sb.append("personality ptr ").append(LlvmRuntimeAbi.personality(declares)).append(' ');
        }
        sb.append("{\n");
        sb.append(String.join("\n", fb.lines())).append("\n}");
        return sb.toString();
    }

    /** Groups handlers into try regions (by start/end block) and marks the blocks each region covers. */
    private void buildExceptionRegions() {
        List<ExceptionHandler> handlers = method.getExceptionHandlers();
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        java.util.Map<String, EhRegion> byRange = new java.util.LinkedHashMap<>();
        for (ExceptionHandler h : handlers) {
            String key = h.getTryStart().getId() + ":" + h.getTryEnd().getId();
            EhRegion region = byRange.computeIfAbsent(key, k -> {
                EhRegion r = new EhRegion("Lpad" + (regionCounter++));
                regions.add(r);
                return r;
            });
            region.handlers.add(h);
        }
        List<IRBlock> order = method.getBlocksInOrder();
        for (EhRegion region : regions) {
            ExceptionHandler first = region.handlers.get(0);
            int start = indexOfIdentity(order, first.getTryStart());
            int end = indexOfIdentity(order, first.getTryEnd());
            if (start < 0) {
                continue;
            }
            if (end < 0 || end <= start) {
                end = start + 1;
            }
            for (int i = start; i < end && i < order.size(); i++) {
                coveredBy.putIfAbsent(order.get(i), region);
            }
        }
    }

    private static int indexOfIdentity(List<IRBlock> blocks, IRBlock target) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i) == target) {
                return i;
            }
        }
        return -1;
    }

    /** Emits a region's landingpad: catch-all unwind, exception extraction, per-handler type dispatch. */
    private void emitLandingpad(EhRegion region) {
        fb.label(region.lpadLabel);
        String lpv = fb.freshTemp();
        fb.emit(lpv + " = landingpad { ptr, i32 } cleanup");
        String exc = fb.freshTemp();
        fb.emit(exc + " = extractvalue { ptr, i32 } " + lpv + ", 0");
        for (ExceptionHandler handler : region.handlers) {
            String target = "%B" + handler.getHandlerBlock().getId();
            if (handler.isCatchAll()) {
                fb.emit("br label " + target);
                return;
            }
            String typeName = strings.intern(handler.getCatchType().getInternalName());
            String matched = fb.freshTemp();
            fb.emit(matched + " = call i1 " + LlvmRuntimeAbi.matchCatch(declares)
                + "(ptr " + exc + ", ptr " + typeName + ")");
            String next = fb.freshLabel();
            fb.emit("br i1 " + matched + ", label " + target + ", label %" + next);
            fb.label(next);
        }
        fb.emit("resume { ptr, i32 } " + lpv);
    }

    // ---- operand resolution -------------------------------------------------

    private LlvmValue operand(Value value) {
        if (value instanceof Constant) {
            return constant((Constant) value);
        }
        SSAValue ssa = (SSAValue) value;
        IRInstruction def = ssa.getDefinition();
        if (def instanceof ConstantInstruction && isInlineConstant(((ConstantInstruction) def).getConstant())) {
            return constant(((ConstantInstruction) def).getConstant());
        }
        // Reference constants (String/Class) are materialized by a runtime call in visitConstant and
        // referenced by their %v{id} register, like any other defined value.
        return LlvmValue.register(IrTypeMapper.map(ssa.getType()), ssa.getId());
    }

    /** Constants that render as an inline literal (no defining instruction needed at the use site). */
    private static boolean isInlineConstant(Constant c) {
        return c instanceof IntConstant || c instanceof LongConstant
            || c instanceof FloatConstant || c instanceof DoubleConstant
            || c instanceof NullConstant;
    }

    private LlvmValue constant(Constant c) {
        if (c instanceof IntConstant) {
            return LlvmValue.constant(LlvmType.I32, Integer.toString(((IntConstant) c).getValue()));
        }
        if (c instanceof LongConstant) {
            return LlvmValue.constant(LlvmType.I64, Long.toString(((LongConstant) c).getValue()));
        }
        if (c instanceof FloatConstant) {
            return LlvmValue.constant(LlvmType.FLOAT, fpHex(((FloatConstant) c).getValue()));
        }
        if (c instanceof DoubleConstant) {
            return LlvmValue.constant(LlvmType.DOUBLE, fpHex(((DoubleConstant) c).getValue()));
        }
        if (c instanceof NullConstant) {
            return LlvmValue.constant(LlvmType.PTR, "null");
        }
        throw UnsupportedLowering.reject("constant " + c.getClass().getSimpleName());
    }

    /** LLVM float/double literal: {@code 0x} + the 64-bit IEEE-754 bit pattern (exact, no rounding). */
    private static String fpHex(double d) {
        return String.format("0x%016X", Double.doubleToRawLongBits(d));
    }

    private String reg(IRInstruction instr) {
        return "%v" + instr.getResult().getId();
    }

    /** True if any parameter or the return of the descriptor is a reference/array type. */
    private static boolean signatureHasReferences(String descriptor) {
        int end = descriptor.indexOf(')');
        for (int i = descriptor.indexOf('(') + 1; i < end; i++) {
            char c = descriptor.charAt(i);
            if (c == 'L' || c == '[') {
                return true;
            }
        }
        char ret = descriptor.charAt(end + 1);
        return ret == 'L' || ret == '[';
    }

    private static boolean isReferenceCompare(CompareOp cond) {
        return cond == CompareOp.ACMPEQ || cond == CompareOp.ACMPNE
            || cond == CompareOp.IFNULL || cond == CompareOp.IFNONNULL;
    }

    // ---- supported instructions ---------------------------------------------

    @Override
    public Void visitConstant(ConstantInstruction constant) {
        Constant c = constant.getConstant();
        if (isInlineConstant(c)) {
            return null; // inlined at use site (see operand())
        }
        if (!objects()) {
            throw UnsupportedLowering.reject("constant " + c.getClass().getSimpleName());
        }
        if (c instanceof StringConstant) {
            String s = ((StringConstant) c).getValue();
            String fn = LlvmRuntimeAbi.internString(declares);
            fb.emit(reg(constant) + " = call ptr " + fn + "(ptr " + strings.intern(s)
                + ", i32 " + CStringPool.utf8Length(s) + ")");
            return null;
        }
        if (c instanceof ClassConstant) {
            String fn = LlvmRuntimeAbi.classObject(declares);
            fb.emit(reg(constant) + " = call ptr " + fn + "(ptr "
                + strings.intern(((ClassConstant) c).getClassName()) + ")");
            return null;
        }
        if (c instanceof DynamicConstant) {
            DynamicConstant dc = (DynamicConstant) c;
            LlvmType retTy = IrTypeMapper.mapDescriptor(dc.getDescriptor());
            String sym = SymbolMangler.mangleCondy(dc.getName(), dc.getDescriptor(), dc.getBootstrapMethodIndex());
            declares.note(sym, retTy, java.util.Collections.emptyList());
            fb.emit(reg(constant) + " = call " + retTy.render() + " " + sym + "()");
            return null;
        }
        throw UnsupportedLowering.reject("constant " + c.getClass().getSimpleName());
    }

    @Override
    public Void visitBinaryOp(BinaryOpInstruction binaryOp) {
        BinaryOp op = binaryOp.getOp();
        LlvmType ty = IrTypeMapper.map(binaryOp.getResult().getType());
        switch (op) {
            case LCMP:
            case FCMPL:
            case FCMPG:
            case DCMPL:
            case DCMPG:
                lowerThreeWayCompare(binaryOp, op);
                return null;
            case SHL:
            case SHR:
            case USHR:
                lowerShift(binaryOp, op, ty);
                return null;
            default:
                LlvmValue l = operand(binaryOp.getLeft());
                LlvmValue r = operand(binaryOp.getRight());
                fb.emit(reg(binaryOp) + " = " + arithmeticOpcode(op, ty) + " " + ty.render()
                    + " " + l.text + ", " + r.text);
                return null;
        }
    }

    private static String arithmeticOpcode(BinaryOp op, LlvmType ty) {
        boolean fp = ty.isFloatingPoint();
        switch (op) {
            case ADD: return fp ? "fadd" : "add";
            case SUB: return fp ? "fsub" : "sub";
            case MUL: return fp ? "fmul" : "mul";
            case DIV: return fp ? "fdiv" : "sdiv";
            case REM: return fp ? "frem" : "srem";
            case AND: return "and";
            case OR:  return "or";
            case XOR: return "xor";
            default:  throw UnsupportedLowering.reject("binary op " + op);
        }
    }

    private void lowerShift(BinaryOpInstruction binaryOp, BinaryOp op, LlvmType ty) {
        LlvmValue value = operand(binaryOp.getLeft());
        LlvmValue amount = operand(binaryOp.getRight());
        // JVM shifts take an int amount; LLVM requires the amount type to match the value type.
        if (amount.type != ty) {
            String widened = fb.freshTemp();
            fb.emit(widened + " = zext " + amount.typed() + " to " + ty.render());
            amount = LlvmValue.temp(ty, widened);
        }
        // JVM masks the shift count (& 31 for int, & 63 for long); LLVM shifts are UB otherwise.
        String masked = fb.freshTemp();
        fb.emit(masked + " = and " + ty.render() + " " + amount.text + ", " + (ty.bitWidth() - 1));
        String opcode = op == BinaryOp.SHL ? "shl" : op == BinaryOp.SHR ? "ashr" : "lshr";
        fb.emit(reg(binaryOp) + " = " + opcode + " " + ty.render() + " " + value.text + ", " + masked);
    }

    private void lowerThreeWayCompare(BinaryOpInstruction binaryOp, BinaryOp op) {
        LlvmValue l = operand(binaryOp.getLeft());
        LlvmValue r = operand(binaryOp.getRight());
        LlvmType ty = l.type;
        String result = reg(binaryOp);
        if (!ty.isFloatingPoint()) {
            String lt = fb.freshTemp();
            String gt = fb.freshTemp();
            String s1 = fb.freshTemp();
            fb.emit(lt + " = icmp slt " + ty.render() + " " + l.text + ", " + r.text);
            fb.emit(gt + " = icmp sgt " + ty.render() + " " + l.text + ", " + r.text);
            fb.emit(s1 + " = select i1 " + gt + ", i32 1, i32 0");
            fb.emit(result + " = select i1 " + lt + ", i32 -1, i32 " + s1);
            return;
        }
        // Float/double: ...G pushes +1 on NaN, ...L pushes -1 on NaN.
        boolean nanIsGreater = op == BinaryOp.FCMPG || op == BinaryOp.DCMPG;
        String lt = fb.freshTemp();
        String gt = fb.freshTemp();
        String uno = fb.freshTemp();
        fb.emit(lt + " = fcmp olt " + ty.render() + " " + l.text + ", " + r.text);
        fb.emit(gt + " = fcmp ogt " + ty.render() + " " + l.text + ", " + r.text);
        fb.emit(uno + " = fcmp uno " + ty.render() + " " + l.text + ", " + r.text);
        String result1 = reg(binaryOp);
        if (nanIsGreater) {
            String gtOrNan = fb.freshTemp();
            String s1 = fb.freshTemp();
            fb.emit(gtOrNan + " = or i1 " + gt + ", " + uno);
            fb.emit(s1 + " = select i1 " + gtOrNan + ", i32 1, i32 0");
            fb.emit(result1 + " = select i1 " + lt + ", i32 -1, i32 " + s1);
        } else {
            String s1 = fb.freshTemp();
            String ltOrNan = fb.freshTemp();
            fb.emit(s1 + " = select i1 " + gt + ", i32 1, i32 0");
            fb.emit(ltOrNan + " = or i1 " + lt + ", " + uno);
            fb.emit(result1 + " = select i1 " + ltOrNan + ", i32 -1, i32 " + s1);
        }
    }

    @Override
    public Void visitUnaryOp(UnaryOpInstruction unaryOp) {
        UnaryOp op = unaryOp.getOp();
        LlvmValue v = operand(unaryOp.getOperand());
        LlvmType to = IrTypeMapper.map(unaryOp.getResult().getType());
        String result = reg(unaryOp);
        switch (op) {
            case NEG:
                if (to.isFloatingPoint()) {
                    fb.emit(result + " = fneg " + v.typed());
                } else {
                    fb.emit(result + " = sub " + to.render() + " 0, " + v.text);
                }
                return null;
            case I2B:
                narrowingConvert(result, v, LlvmType.I8, "sext");
                return null;
            case I2C:
                narrowingConvert(result, v, LlvmType.I16, "zext");
                return null;
            case I2S:
                narrowingConvert(result, v, LlvmType.I16, "sext");
                return null;
            default:
                fb.emit(result + " = " + conversionOpcode(op) + " " + v.typed() + " to " + to.render());
                return null;
        }
    }

    private void narrowingConvert(String result, LlvmValue v, LlvmType narrow, String extend) {
        String truncated = fb.freshTemp();
        fb.emit(truncated + " = trunc " + v.typed() + " to " + narrow.render());
        fb.emit(result + " = " + extend + " " + narrow.render() + " " + truncated + " to i32");
    }

    private static String conversionOpcode(UnaryOp op) {
        switch (op) {
            case I2L: return "sext";
            case L2I: return "trunc";
            case D2F: return "fptrunc";
            case F2D: return "fpext";
            case I2F: case I2D: case L2F: case L2D: return "sitofp";
            case F2I: case F2L: case D2I: case D2L: return "fptosi";
            default: throw UnsupportedLowering.reject("conversion " + op);
        }
    }

    @Override
    public Void visitPhi(PhiInstruction phi) {
        LlvmType ty = IrTypeMapper.map(phi.getResult().getType());
        List<String> arms = new ArrayList<>();
        for (IRBlock pred : phi.getIncomingBlocks()) {
            LlvmValue v = operand(phi.getIncoming(pred));
            arms.add("[" + v.text + ", %B" + pred.getId() + "]");
        }
        fb.emit("%v" + phi.getResult().getId() + " = phi " + ty.render() + " " + String.join(", ", arms));
        return null;
    }

    @Override
    public Void visitBranch(BranchInstruction branch) {
        CompareOp cond = branch.getCondition();
        if (isReferenceCompare(cond) && !objects()) {
            throw UnsupportedLowering.reject("reference comparison " + cond);
        }
        String pred = icmpPredicate(cond);
        LlvmValue left = operand(branch.getLeft());
        String cmp = fb.freshTemp();
        if (branch.getRight() != null) {
            LlvmValue right = operand(branch.getRight());
            fb.emit(cmp + " = icmp " + pred + " " + left.type.render() + " " + left.text + ", " + right.text);
        } else {
            String zero = left.type == LlvmType.PTR ? "null" : "0";
            fb.emit(cmp + " = icmp " + pred + " " + left.type.render() + " " + left.text + ", " + zero);
        }
        fb.emit("br i1 " + cmp + ", label %B" + branch.getTrueTarget().getId()
            + ", label %B" + branch.getFalseTarget().getId());
        return null;
    }

    private static String icmpPredicate(CompareOp cond) {
        switch (cond) {
            case EQ: case IFEQ: case ACMPEQ: case IFNULL: return "eq";
            case NE: case IFNE: case ACMPNE: case IFNONNULL: return "ne";
            case LT: case IFLT: return "slt";
            case GE: case IFGE: return "sge";
            case GT: case IFGT: return "sgt";
            case LE: case IFLE: return "sle";
            default:
                throw UnsupportedLowering.reject("compare " + cond);
        }
    }

    @Override
    public Void visitSwitch(SwitchInstruction switchInstr) {
        LlvmValue key = operand(switchInstr.getKey());
        StringBuilder sb = new StringBuilder();
        sb.append("switch ").append(key.typed())
            .append(", label %B").append(switchInstr.getDefaultTarget().getId()).append(" [ ");
        for (Map.Entry<Integer, IRBlock> e : switchInstr.getCases().entrySet()) {
            sb.append("i32 ").append(e.getKey()).append(", label %B").append(e.getValue().getId()).append(' ');
        }
        sb.append("]");
        fb.emit(sb.toString());
        return null;
    }

    @Override
    public Void visitReturn(ReturnInstruction returnInstr) {
        if (returnInstr.isVoidReturn()) {
            fb.emit("ret void");
        } else {
            LlvmType retTy = IrTypeMapper.mapReturn(method.getDescriptor());
            LlvmValue v = operand(returnInstr.getReturnValue());
            fb.emit("ret " + retTy.render() + " " + v.text);
        }
        return null;
    }

    @Override
    public Void visitSimple(SimpleInstruction simple) {
        switch (simple.getOp()) {
            case GOTO:
                fb.emit("br label %B" + simple.getTarget().getId());
                return null;
            case ATHROW: {
                if (!objects()) {
                    throw UnsupportedLowering.reject("athrow");
                }
                LlvmValue exc = operand(simple.getOperand());
                fb.emit("call void " + LlvmRuntimeAbi.throwException(declares) + "(ptr " + exc.text + ")");
                fb.emit("unreachable");
                return null;
            }
            case MONITORENTER: {
                if (!objects()) {
                    throw UnsupportedLowering.reject("monitor");
                }
                LlvmValue obj = operand(simple.getOperand());
                fb.emit("call void " + LlvmRuntimeAbi.monitorEnter(declares) + "(ptr " + obj.text + ")");
                return null;
            }
            case MONITOREXIT: {
                if (!objects()) {
                    throw UnsupportedLowering.reject("monitor");
                }
                LlvmValue obj = operand(simple.getOperand());
                fb.emit("call void " + LlvmRuntimeAbi.monitorExit(declares) + "(ptr " + obj.text + ")");
                return null;
            }
            case ARRAYLENGTH: {
                if (!objects()) {
                    throw UnsupportedLowering.reject("arraylength");
                }
                LlvmValue arr = operand(simple.getOperand());
                String sym = LlvmRuntimeAbi.arrayLength(declares);
                fb.emit(reg(simple) + " = call i32 " + sym + "(ptr " + arr.text + ")");
                return null;
            }
            default:
                throw UnsupportedLowering.reject("simple op " + simple.getOp());
        }
    }

    @Override
    public Void visitInvoke(InvokeInstruction invoke) {
        InvokeType type = invoke.getInvokeType();
        String descriptor = invoke.getDescriptor();
        LlvmType retTy = IrTypeMapper.mapReturn(descriptor);

        if (type == InvokeType.STATIC) {
            String mangled = SymbolMangler.mangle(invoke.getOwner(), invoke.getName(), descriptor);
            declares.note(mangled, retTy, IrTypeMapper.mapParams(descriptor));
            emitCall(invoke, retTy, mangled, typedArgs(null, invoke.getMethodArguments()));
            return null;
        }

        if (!objects()) {
            throw UnsupportedLowering.reject("invoke " + type);
        }

        if (type == InvokeType.DYNAMIC) {
            lowerInvokeDynamic(invoke, retTy, descriptor);
            return null;
        }

        LlvmValue receiver = operand(invoke.getReceiver());
        String args = typedArgs(receiver, invoke.getMethodArguments());

        if (type == InvokeType.SPECIAL) {
            String mangled = SymbolMangler.mangle(invoke.getOwner(), invoke.getName(), descriptor);
            List<LlvmType> params = new ArrayList<>();
            params.add(LlvmType.PTR);
            params.addAll(IrTypeMapper.mapParams(descriptor));
            declares.note(mangled, retTy, params);
            emitCall(invoke, retTy, mangled, args);
            return null;
        }

        // VIRTUAL / INTERFACE: resolve the target function pointer, then call it indirectly.
        String methodId = strings.intern(invoke.getOwner() + "." + invoke.getName() + " " + descriptor);
        String lookup = type == InvokeType.INTERFACE
            ? LlvmRuntimeAbi.itableLookup(declares)
            : LlvmRuntimeAbi.vtableLookup(declares);
        String fp = fb.freshTemp();
        fb.emit(fp + " = call ptr " + lookup + "(ptr " + receiver.text + ", ptr " + methodId + ")");
        emitCall(invoke, retTy, fp, args);
        return null;
    }

    /**
     * Emits a call result line; {@code callee} is a mangled symbol or a function-pointer register.
     * Inside a covered try region the call becomes an {@code invoke} unwinding to the region's
     * landingpad, with a fresh continuation label for the normal path.
     */
    private void emitCall(InvokeInstruction invoke, LlvmType retTy, String callee, String args) {
        String signature = retTy.render() + " " + callee + "(" + args + ")";
        if (currentUnwind != null) {
            currentUnwind.used = true;
            String cont = fb.freshLabel();
            String prefix = invoke.getResult() != null ? reg(invoke) + " = invoke " : "invoke ";
            fb.emit(prefix + signature + " to label %" + cont + " unwind label %" + currentUnwind.lpadLabel);
            fb.label(cont);
        } else if (invoke.getResult() != null) {
            fb.emit(reg(invoke) + " = call " + signature);
        } else {
            fb.emit("call " + signature);
        }
    }

    /** Renders the typed argument list, prepending {@code ptr <receiver>} when present. */
    private String typedArgs(LlvmValue receiver, List<Value> methodArguments) {
        List<String> parts = new ArrayList<>();
        if (receiver != null) {
            parts.add("ptr " + receiver.text);
        }
        for (Value a : methodArguments) {
            parts.add(operand(a).typed());
        }
        return String.join(", ", parts);
    }

    private void lowerInvokeDynamic(InvokeInstruction invoke, LlvmType retTy, String descriptor) {
        // Emitted against a per-call-site ABI symbol (no lambda-class synthesis): the descriptor's
        // argument types are the captured/dynamic arguments, and getArguments() carries all of them.
        String sym = SymbolMangler.mangleIndy(invoke.getOwner(), invoke.getName(), descriptor,
            invoke.getOriginalCpIndex());
        declares.note(sym, retTy, IrTypeMapper.mapParams(descriptor));
        emitCall(invoke, retTy, sym, typedArgs(null, invoke.getArguments()));
    }

    // ---- unsupported (computational subset boundary) -------------------------

    @Override
    public Void visitFieldAccess(FieldAccessInstruction fieldAccess) {
        if (!objects()) {
            throw UnsupportedLowering.reject("field access " + fieldAccess.getOwner() + "." + fieldAccess.getName());
        }
        String owner = fieldAccess.getOwner();
        String name = fieldAccess.getName();
        LlvmType ty = IrTypeMapper.mapDescriptor(fieldAccess.getDescriptor());
        if (fieldAccess.isStatic()) {
            String sym = SymbolMangler.mangleField(owner, name);
            globals.note(sym, ty, owner);
            if (fieldAccess.isLoad()) {
                fb.emit(reg(fieldAccess) + " = load " + ty.render() + ", ptr " + sym);
            } else {
                LlvmValue v = operand(fieldAccess.getValue());
                fb.emit("store " + ty.render() + " " + v.text + ", ptr " + sym);
            }
            return null;
        }

        LlvmValue obj = operand(fieldAccess.getObjectRef());
        if (fieldAccess.isLoad()) {
            String sym = SymbolMangler.mangleFieldAccessor("gf", owner, name, fieldAccess.getDescriptor());
            declares.note(sym, ty, List.of(LlvmType.PTR));
            fb.emit(reg(fieldAccess) + " = call " + ty.render() + " " + sym + "(ptr " + obj.text + ")");
        } else {
            String sym = SymbolMangler.mangleFieldAccessor("pf", owner, name, fieldAccess.getDescriptor());
            LlvmValue v = operand(fieldAccess.getValue());
            declares.note(sym, LlvmType.VOID, List.of(LlvmType.PTR, ty));
            fb.emit("call void " + sym + "(ptr " + obj.text + ", " + ty.render() + " " + v.text + ")");
        }
        return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessInstruction arrayAccess) {
        if (!objects()) {
            throw UnsupportedLowering.reject("array access");
        }
        LlvmValue array = operand(arrayAccess.getArray());
        LlvmValue index = operand(arrayAccess.getIndex());
        IRType elemType = arrayElementType(arrayAccess);
        char kind = elementKind(elemType);
        LlvmType elem = IrTypeMapper.map(elemType);
        if (arrayAccess.isLoad()) {
            String sym = LlvmRuntimeAbi.arrayLoad(declares, kind, elem);
            fb.emit(reg(arrayAccess) + " = call " + elem.render() + " " + sym
                + "(ptr " + array.text + ", i32 " + index.text + ")");
        } else {
            LlvmValue v = operand(arrayAccess.getValue());
            String sym = LlvmRuntimeAbi.arrayStore(declares, kind, elem);
            fb.emit("call void " + sym + "(ptr " + array.text + ", i32 " + index.text
                + ", " + elem.render() + " " + v.text + ")");
        }
        return null;
    }

    private static IRType arrayElementType(ArrayAccessInstruction arrayAccess) {
        IRType arrayType = arrayAccess.getArray().getType();
        if (arrayType instanceof ArrayType) {
            return ((ArrayType) arrayType).getElementType();
        }
        return arrayAccess.isLoad()
            ? arrayAccess.getResult().getType()
            : arrayAccess.getValue().getType();
    }

    /** JVM element kind suffix for array ABI symbols: z/b/c/s/i/j/f/d for primitives, {@code a} for references. */
    private static char elementKind(IRType elem) {
        if (elem instanceof PrimitiveType) {
            switch ((PrimitiveType) elem) {
                case BOOLEAN: return 'z';
                case BYTE:    return 'b';
                case CHAR:    return 'c';
                case SHORT:   return 's';
                case INT:     return 'i';
                case LONG:    return 'j';
                case FLOAT:   return 'f';
                case DOUBLE:  return 'd';
            }
        }
        return 'a';
    }

    /** A name string for interning: internal name for class references, descriptor for arrays/primitives. */
    private static String typeName(IRType type) {
        if (type instanceof ReferenceType) {
            return ((ReferenceType) type).getInternalName();
        }
        return type.getDescriptor();
    }

    @Override
    public Void visitNew(NewInstruction newInstr) {
        if (!objects()) {
            throw UnsupportedLowering.reject("new " + newInstr.getClassName());
        }
        String name = strings.intern(newInstr.getClassName());
        String sym = LlvmRuntimeAbi.newObject(declares);
        fb.emit(reg(newInstr) + " = call ptr " + sym + "(ptr " + name + ")");
        return null;
    }

    @Override
    public Void visitNewArray(NewArrayInstruction newArray) {
        if (!objects()) {
            throw UnsupportedLowering.reject("newarray");
        }
        IRType elem = newArray.getElementType();
        List<Value> dims = newArray.getDimensions();
        if (dims.size() == 1) {
            LlvmValue len = operand(dims.get(0));
            if (elem instanceof PrimitiveType) {
                String sym = LlvmRuntimeAbi.newPrimitiveArray(declares, elementKind(elem));
                fb.emit(reg(newArray) + " = call ptr " + sym + "(i32 " + len.text + ")");
            } else {
                String elemName = strings.intern(typeName(elem));
                String sym = LlvmRuntimeAbi.newRefArray(declares);
                fb.emit(reg(newArray) + " = call ptr " + sym + "(ptr " + elemName + ", i32 " + len.text + ")");
            }
            return null;
        }

        int n = dims.size();
        String dimsPtr = fb.freshTemp();
        fb.emit(dimsPtr + " = alloca [" + n + " x i32]");
        for (int i = 0; i < n; i++) {
            LlvmValue len = operand(dims.get(i));
            String slot = fb.freshTemp();
            fb.emit(slot + " = getelementptr [" + n + " x i32], ptr " + dimsPtr + ", i32 0, i32 " + i);
            fb.emit("store i32 " + len.text + ", ptr " + slot);
        }
        String arrayType = repeat('[', n) + elem.getDescriptor();
        String typeNameSym = strings.intern(arrayType);
        String sym = LlvmRuntimeAbi.newMultiArray(declares);
        fb.emit(reg(newArray) + " = call ptr " + sym + "(ptr " + typeNameSym + ", i32 " + n + ", ptr " + dimsPtr + ")");
        return null;
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    @Override
    public Void visitTypeCheck(TypeCheckInstruction typeCheck) {
        if (!objects()) {
            throw UnsupportedLowering.reject("type check");
        }
        LlvmValue obj = operand(typeCheck.getOperand());
        String name = strings.intern(typeName(typeCheck.getTargetType()));
        if (typeCheck.isCast()) {
            fb.emit(reg(typeCheck) + " = call ptr " + LlvmRuntimeAbi.checkCast(declares)
                + "(ptr " + obj.text + ", ptr " + name + ")");
        } else {
            fb.emit(reg(typeCheck) + " = call i32 " + LlvmRuntimeAbi.instanceOf(declares)
                + "(ptr " + obj.text + ", ptr " + name + ")");
        }
        return null;
    }

    @Override
    public Void visitCopy(CopyInstruction copy) {
        if (!objects()) {
            throw UnsupportedLowering.reject("copy (non-SSA artifact)");
        }
        // Post-SSA the only copies are the per-handler exception markers the lifter inserts at the top
        // of a catch block. Materialize the caught exception from the runtime so the handler block is
        // self-contained (defines its own exception value, independent of landingpad reachability).
        fb.emit(reg(copy) + " = call ptr " + LlvmRuntimeAbi.currentException(declares) + "()");
        return null;
    }

    // After SSA renaming, local load/store instructions remain but are vestigial: real uses are
    // rewired to SSA values (parameters, phi results, stored values) and loads are dead. Data flow
    // is fully carried by SSA values + phis, so these emit nothing.

    @Override
    public Void visitLoadLocal(LoadLocalInstruction loadLocal) {
        return null;
    }

    @Override
    public Void visitStoreLocal(StoreLocalInstruction storeLocal) {
        return null;
    }

    @Override
    protected Void defaultValue() {
        throw UnsupportedLowering.reject("instruction");
    }
}
