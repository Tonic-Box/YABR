package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.value.*;
import com.tonic.analysis.ssa.visitor.AbstractIRVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final LlvmLoweringConfig config;
    private String definedSymbol;

    SsaToLlvmLowerer(IRMethod method, LlvmFunctionBuilder fb, DeclareCollector declares,
                     LlvmLoweringConfig config) {
        this.method = method;
        this.fb = fb;
        this.declares = declares;
        this.config = config;
    }

    String definedSymbol() {
        return definedSymbol;
    }

    /** Lowers the method to its complete {@code define ... { ... }} text. */
    String lowerFunction() {
        if (method.getEntryBlock() == null) {
            throw UnsupportedLowering.reject("method without a body");
        }
        if (!method.isStatic()) {
            throw UnsupportedLowering.reject("instance method (receiver is a reference)");
        }

        String descriptor = method.getDescriptor();
        LlvmType returnType = IrTypeMapper.mapReturn(descriptor);
        List<LlvmType> paramTypes = IrTypeMapper.mapParams(descriptor);
        definedSymbol = SymbolMangler.mangle(method.getOwnerClass(), method.getName(), descriptor);

        List<String> params = new ArrayList<>();
        List<SSAValue> paramValues = method.getParameters();
        for (int i = 0; i < paramTypes.size(); i++) {
            params.add(paramTypes.get(i).render() + " %v" + paramValues.get(i).getId());
        }

        for (IRBlock block : method.getBlocksInOrder()) {
            fb.label("B" + block.getId());
            for (PhiInstruction phi : block.getPhiInstructions()) {
                phi.accept(this);
            }
            for (IRInstruction instr : block.getInstructions()) {
                instr.accept(this);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("define ").append(returnType.render()).append(' ').append(definedSymbol)
            .append('(').append(String.join(", ", params)).append(") {\n");
        sb.append(String.join("\n", fb.lines())).append("\n}");
        return sb.toString();
    }

    // ---- operand resolution -------------------------------------------------

    private LlvmValue operand(Value value) {
        if (value instanceof Constant) {
            return constant((Constant) value);
        }
        SSAValue ssa = (SSAValue) value;
        IRInstruction def = ssa.getDefinition();
        if (def instanceof ConstantInstruction) {
            return constant(((ConstantInstruction) def).getConstant());
        }
        return LlvmValue.register(IrTypeMapper.map(ssa.getType()), ssa.getId());
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
        throw UnsupportedLowering.reject("constant " + c.getClass().getSimpleName());
    }

    /** LLVM float/double literal: {@code 0x} + the 64-bit IEEE-754 bit pattern (exact, no rounding). */
    private static String fpHex(double d) {
        return String.format("0x%016X", Double.doubleToRawLongBits(d));
    }

    private String reg(IRInstruction instr) {
        return "%v" + instr.getResult().getId();
    }

    // ---- supported instructions ---------------------------------------------

    @Override
    public Void visitConstant(ConstantInstruction constant) {
        return null; // inlined at use site (see operand())
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
        String pred = icmpPredicate(cond);
        LlvmValue left = operand(branch.getLeft());
        String cmp = fb.freshTemp();
        if (branch.getRight() != null) {
            LlvmValue right = operand(branch.getRight());
            fb.emit(cmp + " = icmp " + pred + " " + left.type.render() + " " + left.text + ", " + right.text);
        } else {
            fb.emit(cmp + " = icmp " + pred + " " + left.type.render() + " " + left.text + ", 0");
        }
        fb.emit("br i1 " + cmp + ", label %B" + branch.getTrueTarget().getId()
            + ", label %B" + branch.getFalseTarget().getId());
        return null;
    }

    private static String icmpPredicate(CompareOp cond) {
        switch (cond) {
            case EQ: case IFEQ: return "eq";
            case NE: case IFNE: return "ne";
            case LT: case IFLT: return "slt";
            case GE: case IFGE: return "sge";
            case GT: case IFGT: return "sgt";
            case LE: case IFLE: return "sle";
            case ACMPEQ: case ACMPNE: case IFNULL: case IFNONNULL:
                throw UnsupportedLowering.reject("reference comparison " + cond);
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
            case ATHROW:
                throw UnsupportedLowering.reject("athrow");
            case MONITORENTER:
            case MONITOREXIT:
                throw UnsupportedLowering.reject("monitor");
            case ARRAYLENGTH:
                throw UnsupportedLowering.reject("arraylength");
            default:
                throw UnsupportedLowering.reject("simple op " + simple.getOp());
        }
    }

    @Override
    public Void visitInvoke(InvokeInstruction invoke) {
        if (invoke.getInvokeType() != InvokeType.STATIC) {
            throw UnsupportedLowering.reject("invoke " + invoke.getInvokeType());
        }
        String descriptor = invoke.getDescriptor();
        LlvmType retTy = IrTypeMapper.mapReturn(descriptor);
        List<LlvmType> paramTys = IrTypeMapper.mapParams(descriptor);
        String mangled = SymbolMangler.mangle(invoke.getOwner(), invoke.getName(), descriptor);
        declares.note(mangled, retTy, paramTys);

        String args = invoke.getMethodArguments().stream()
            .map(a -> operand(a).typed())
            .collect(Collectors.joining(", "));
        if (invoke.getResult() != null) {
            fb.emit(reg(invoke) + " = call " + retTy.render() + " " + mangled + "(" + args + ")");
        } else {
            fb.emit("call " + retTy.render() + " " + mangled + "(" + args + ")");
        }
        return null;
    }

    // ---- unsupported (computational subset boundary) -------------------------

    @Override
    public Void visitFieldAccess(FieldAccessInstruction fieldAccess) {
        throw UnsupportedLowering.reject("field access " + fieldAccess.getOwner() + "." + fieldAccess.getName());
    }

    @Override
    public Void visitArrayAccess(ArrayAccessInstruction arrayAccess) {
        throw UnsupportedLowering.reject("array access");
    }

    @Override
    public Void visitNew(NewInstruction newInstr) {
        throw UnsupportedLowering.reject("new " + newInstr.getClassName());
    }

    @Override
    public Void visitNewArray(NewArrayInstruction newArray) {
        throw UnsupportedLowering.reject("newarray");
    }

    @Override
    public Void visitTypeCheck(TypeCheckInstruction typeCheck) {
        throw UnsupportedLowering.reject("type check");
    }

    @Override
    public Void visitCopy(CopyInstruction copy) {
        throw UnsupportedLowering.reject("copy (non-SSA artifact)");
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
