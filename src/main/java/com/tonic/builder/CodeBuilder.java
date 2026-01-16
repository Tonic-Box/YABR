package com.tonic.builder;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.*;
import com.tonic.type.MethodHandle;
import com.tonic.type.TypeDescriptor;
import com.tonic.utill.Opcode;
import com.tonic.utill.ReturnType;

import java.util.*;

import static com.tonic.analysis.instruction.ArithmeticInstruction.ArithmeticType;
import static com.tonic.utill.Opcode.*;

public class CodeBuilder {

    private final MethodBuilder parent;
    private final List<BytecodeOp> ops = new ArrayList<>();
    private final List<SizedOp> sizedOps = new ArrayList<>();
    private final List<ExceptionRegion> exceptionRegions = new ArrayList<>();
    private final Map<String, Label> labels = new HashMap<>();

    CodeBuilder(MethodBuilder parent) {
        this.parent = parent;
    }

    private Label getOrCreateLabel(String name) {
        return labels.computeIfAbsent(name, k -> new Label());
    }

    public CodeBuilder label(String name) {
        Label l = getOrCreateLabel(name);
        sizedOps.add(new LabelOp(l));
        return this;
    }

    public CodeBuilder iconst(int value) {
        int size = computeIconstSize(value);
        addOp((bc, cw) -> bc.addIConst(value), size);
        return this;
    }

    private int computeIconstSize(int value) {
        if (value >= -1 && value <= 5) return 1;
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return 2;
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return 3;
        return 2;
    }

    public CodeBuilder lconst(long value) {
        int size = (value == 0L || value == 1L) ? 1 : 3;
        addOp((bc, cw) -> bc.addLConst(value), size);
        return this;
    }

    public CodeBuilder fconst(float value) {
        int size = (value == 0.0f || value == 1.0f || value == 2.0f) ? 1 : 2;
        addOp((bc, cw) -> bc.addFConst(value), size);
        return this;
    }

    public CodeBuilder dconst(double value) {
        int size = (value == 0.0 || value == 1.0) ? 1 : 3;
        addOp((bc, cw) -> bc.addDConst(value), size);
        return this;
    }

    public CodeBuilder aconst_null() {
        addOp((bc, cw) -> bc.addAConstNull(), 1);
        return this;
    }

    public CodeBuilder ldc(Object value) {
        if (value instanceof String) {
            addOp((bc, cw) -> bc.addLdc((String) value), 2);
        } else if (value instanceof Integer) {
            return iconst((Integer) value);
        } else if (value instanceof Long) {
            return lconst((Long) value);
        } else if (value instanceof Float) {
            return fconst((Float) value);
        } else if (value instanceof Double) {
            return dconst((Double) value);
        } else {
            throw new IllegalArgumentException("Unsupported ldc value: " + value);
        }
        return this;
    }

    public CodeBuilder bipush(int value) {
        addOp((bc, cw) -> {
            cw.appendInstruction(new BipushInstruction(BIPUSH.getCode(), cw.getBytecodeSize(), value));
        }, 2);
        return this;
    }

    public CodeBuilder sipush(int value) {
        addOp((bc, cw) -> {
            cw.appendInstruction(new SipushInstruction(SIPUSH.getCode(), cw.getBytecodeSize(), value));
        }, 3);
        return this;
    }

    public CodeBuilder iload(int index) {
        addOp((bc, cw) -> bc.addILoad(index), 2);
        return this;
    }

    public CodeBuilder lload(int index) {
        addOp((bc, cw) -> bc.addLLoad(index), 2);
        return this;
    }

    public CodeBuilder fload(int index) {
        addOp((bc, cw) -> bc.addFLoad(index), 2);
        return this;
    }

    public CodeBuilder dload(int index) {
        addOp((bc, cw) -> bc.addDLoad(index), 2);
        return this;
    }

    public CodeBuilder aload(int index) {
        addOp((bc, cw) -> bc.addALoad(index), 2);
        return this;
    }

    public CodeBuilder istore(int index) {
        addOp((bc, cw) -> bc.addIStore(index), 2);
        return this;
    }

    public CodeBuilder lstore(int index) {
        addOp((bc, cw) -> cw.insertLStore(cw.getBytecodeSize(), index), 2);
        return this;
    }

    public CodeBuilder fstore(int index) {
        addOp((bc, cw) -> cw.insertFStore(cw.getBytecodeSize(), index), 2);
        return this;
    }

    public CodeBuilder dstore(int index) {
        addOp((bc, cw) -> cw.insertDStore(cw.getBytecodeSize(), index), 2);
        return this;
    }

    public CodeBuilder astore(int index) {
        addOp((bc, cw) -> bc.addAStore(index), 2);
        return this;
    }

    public CodeBuilder iaload() {
        addOp((bc, cw) -> cw.appendInstruction(new IALoadInstruction(IALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder laload() {
        addOp((bc, cw) -> cw.appendInstruction(new LALoadInstruction(LALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder faload() {
        addOp((bc, cw) -> cw.appendInstruction(new FALoadInstruction(FALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder daload() {
        addOp((bc, cw) -> cw.appendInstruction(new DALoadInstruction(DALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder aaload() {
        addOp((bc, cw) -> cw.appendInstruction(new AALoadInstruction(AALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder baload() {
        addOp((bc, cw) -> cw.appendInstruction(new BALOADInstruction(BALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder caload() {
        addOp((bc, cw) -> cw.appendInstruction(new CALoadInstruction(CALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder saload() {
        addOp((bc, cw) -> cw.appendInstruction(new SALoadInstruction(SALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder iastore() {
        addOp((bc, cw) -> cw.appendInstruction(new IAStoreInstruction(IASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lastore() {
        addOp((bc, cw) -> cw.appendInstruction(new LAStoreInstruction(LASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fastore() {
        addOp((bc, cw) -> cw.appendInstruction(new FAStoreInstruction(FASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dastore() {
        addOp((bc, cw) -> cw.appendInstruction(new DAStoreInstruction(DASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder aastore() {
        addOp((bc, cw) -> cw.appendInstruction(new AAStoreInstruction(AASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder bastore() {
        addOp((bc, cw) -> cw.appendInstruction(new BAStoreInstruction(BASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder castore() {
        addOp((bc, cw) -> cw.appendInstruction(new CAStoreInstruction(CASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder sastore() {
        addOp((bc, cw) -> cw.appendInstruction(new SAStoreInstruction(SASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder arraylength() {
        addOp((bc, cw) -> cw.appendInstruction(new ArrayLengthInstruction(ARRAYLENGTH.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder pop() {
        addOp((bc, cw) -> cw.appendInstruction(new PopInstruction(POP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder pop2() {
        addOp((bc, cw) -> cw.appendInstruction(new Pop2Instruction(POP2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dup() {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dup_x1() {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP_X1.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dup_x2() {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP_X2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dup2() {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dup2_x1() {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP2_X1.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dup2_x2() {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP2_X2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder swap() {
        addOp((bc, cw) -> cw.appendInstruction(new SwapInstruction(SWAP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder iadd() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder isub() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.ISUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder imul() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder idiv() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder irem() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ineg() {
        addOp((bc, cw) -> cw.appendInstruction(new INegInstruction(INEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ladd() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lsub() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LSUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lmul() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ldiv() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lrem() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lneg() {
        addOp((bc, cw) -> cw.appendInstruction(new LNegInstruction(LNEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fadd() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fsub() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FSUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fmul() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fdiv() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder frem() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fneg() {
        addOp((bc, cw) -> cw.appendInstruction(new FNegInstruction(FNEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dadd() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dsub() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DSUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dmul() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ddiv() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder drem() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dneg() {
        addOp((bc, cw) -> cw.appendInstruction(new DNegInstruction(DNEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder iand() {
        addOp((bc, cw) -> cw.appendInstruction(new IAndInstruction(IAND.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ior() {
        addOp((bc, cw) -> cw.appendInstruction(new IOrInstruction(IOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ixor() {
        addOp((bc, cw) -> cw.appendInstruction(new IXorInstruction(IXOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ishl() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(ISHL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ishr() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(ISHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder iushr() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(IUSHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder land() {
        addOp((bc, cw) -> cw.appendInstruction(new LandInstruction(LAND.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lor() {
        addOp((bc, cw) -> cw.appendInstruction(new LorInstruction(LOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lxor() {
        addOp((bc, cw) -> cw.appendInstruction(new LXorInstruction(LXOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lshl() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(LSHL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lshr() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(LSHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lushr() {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(LUSHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder i2l() {
        addOp((bc, cw) -> cw.appendInstruction(new I2LInstruction(I2L.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder i2f() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(I2F.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder i2d() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(I2D.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder l2i() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(L2I.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder l2f() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(L2F.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder l2d() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(L2D.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder f2i() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(F2I.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder f2l() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(F2L.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder f2d() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(F2D.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder d2i() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(D2I.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder d2l() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(D2L.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder d2f() {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(D2F.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder i2b() {
        addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(I2B.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder i2c() {
        addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(I2C.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder i2s() {
        addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(I2S.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder lcmp() {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(LCMP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fcmpl() {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(FCMPL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder fcmpg() {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(FCMPG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dcmpl() {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(DCMPL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder dcmpg() {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(DCMPG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder ifeq(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFEQ.getCode(), target, 3));
        return this;
    }

    public CodeBuilder ifne(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFNE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder iflt(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFLT.getCode(), target, 3));
        return this;
    }

    public CodeBuilder ifge(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFGE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder ifgt(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFGT.getCode(), target, 3));
        return this;
    }

    public CodeBuilder ifle(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFLE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_icmpeq(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPEQ.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_icmpne(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPNE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_icmplt(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPLT.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_icmpge(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPGE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_icmpgt(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPGT.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_icmple(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPLE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_acmpeq(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ACMPEQ.getCode(), target, 3));
        return this;
    }

    public CodeBuilder if_acmpne(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ACMPNE.getCode(), target, 3));
        return this;
    }

    public CodeBuilder ifnull(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFNULL.getCode(), target, 3));
        return this;
    }

    public CodeBuilder ifnonnull(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFNONNULL.getCode(), target, 3));
        return this;
    }

    public CodeBuilder goto_(String labelName) {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(GOTO.getCode(), target, 3));
        return this;
    }

    public CodeBuilder invokevirtual(String owner, String name, String descriptor) {
        addOp((bc, cw) -> bc.addInvokeVirtual(owner, name, descriptor), 3);
        return this;
    }

    public CodeBuilder invokespecial(String owner, String name, String descriptor) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            int methodRef = cp.addMethodRef(owner, name, descriptor);
            bc.addInvokeSpecial(methodRef);
        }, 3);
        return this;
    }

    public CodeBuilder invokestatic(String owner, String name, String descriptor) {
        addOp((bc, cw) -> bc.addInvokeStatic(owner, name, descriptor), 3);
        return this;
    }

    public CodeBuilder invokeinterface(String owner, String name, String descriptor) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            InterfaceRefItem interfaceRef = cp.findOrAddInterfaceRef(owner, name, descriptor);
            int interfaceMethodRef = interfaceRef.getIndex(cp);
            int argCount = countInterfaceArgs(descriptor);
            bc.addInvokeInterface(interfaceMethodRef, argCount);
        }, 5);
        return this;
    }

    private int countInterfaceArgs(String descriptor) {
        int count = 1;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                while (descriptor.charAt(i) != ';') i++;
                count++;
            } else if (c == '[') {
                while (descriptor.charAt(i) == '[') i++;
                if (descriptor.charAt(i) == 'L') {
                    while (descriptor.charAt(i) != ';') i++;
                }
                count++;
            } else if (c == 'J' || c == 'D') {
                count += 2;
            } else {
                count++;
            }
            i++;
        }
        return count;
    }

    public CodeBuilder invokedynamic(String name, String descriptor, MethodHandle bootstrap, Object... bsmArgs) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            int methodRef = cp.addMethodRef(bootstrap.getOwner(), bootstrap.getName(), bootstrap.getDescriptor());
            int methodHandle = cp.addMethodHandle(bootstrap.getTag(), methodRef);

            List<Integer> argIndices = new ArrayList<>();
            for (Object arg : bsmArgs) {
                if (arg instanceof String) {
                    argIndices.add(cp.findOrAddString((String) arg).getIndex(cp));
                } else if (arg instanceof Integer) {
                    argIndices.add(cp.findOrAddInteger((Integer) arg).getIndex(cp));
                } else if (arg instanceof Long) {
                    argIndices.add(cp.findOrAddLong((Long) arg).getIndex(cp));
                } else if (arg instanceof Float) {
                    argIndices.add(cp.findOrAddFloat((Float) arg).getIndex(cp));
                } else if (arg instanceof Double) {
                    argIndices.add(cp.findOrAddDouble((Double) arg).getIndex(cp));
                } else if (arg instanceof TypeDescriptor) {
                    argIndices.add(cp.addMethodType(((TypeDescriptor) arg).getDescriptor()));
                } else if (arg instanceof MethodHandle) {
                    MethodHandle mh = (MethodHandle) arg;
                    int ref = cp.addMethodRef(mh.getOwner(), mh.getName(), mh.getDescriptor());
                    argIndices.add(cp.addMethodHandle(mh.getTag(), ref));
                }
            }

            int bsmIndex = parent.getParent().addBootstrapMethod(methodHandle, argIndices);
            int nameAndType = cp.addNameAndType(name, descriptor);
            int indyIndex = cp.addInvokeDynamic(bsmIndex, nameAndType);
            bc.addInvokeDynamic(indyIndex);
        }, 5);
        return this;
    }

    public CodeBuilder getfield(String owner, String name, String descriptor) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            Utf8Item fieldNameUtf8 = cp.findOrAddUtf8(name);
            Utf8Item fieldDescUtf8 = cp.findOrAddUtf8(descriptor);
            ClassRefItem classRef = cp.findOrAddClass(owner);
            NameAndTypeRefItem nameAndType = cp.findOrAddNameAndType(fieldNameUtf8.getIndex(cp), fieldDescUtf8.getIndex(cp));
            FieldRefItem fieldRef = cp.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
            int fieldRefIndex = cp.getIndexOf(fieldRef);
            bc.addGetField(fieldRefIndex);
        }, 3);
        return this;
    }

    public CodeBuilder putfield(String owner, String name, String descriptor) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            Utf8Item fieldNameUtf8 = cp.findOrAddUtf8(name);
            Utf8Item fieldDescUtf8 = cp.findOrAddUtf8(descriptor);
            ClassRefItem classRef = cp.findOrAddClass(owner);
            NameAndTypeRefItem nameAndType = cp.findOrAddNameAndType(fieldNameUtf8.getIndex(cp), fieldDescUtf8.getIndex(cp));
            FieldRefItem fieldRef = cp.findOrAddField(classRef.getClassName(), nameAndType.getName(), nameAndType.getDescriptor());
            int fieldRefIndex = cp.getIndexOf(fieldRef);
            bc.addPutField(fieldRefIndex);
        }, 3);
        return this;
    }

    public CodeBuilder getstatic(String owner, String name, String descriptor) {
        addOp((bc, cw) -> bc.addGetStatic(owner, name, descriptor), 3);
        return this;
    }

    public CodeBuilder putstatic(String owner, String name, String descriptor) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            FieldRefItem fieldRef = cp.findOrAddFieldRef(owner, name, descriptor);
            bc.addPutStatic(fieldRef.getIndex(cp));
        }, 3);
        return this;
    }

    public CodeBuilder new_(String type) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.insertNew(cw.getBytecodeSize(), classRefIndex);
        }, 3);
        return this;
    }

    public CodeBuilder newarray(int arrayType) {
        addOp((bc, cw) -> cw.appendInstruction(new NewArrayInstruction(NEWARRAY.getCode(), cw.getBytecodeSize(), arrayType, 0)), 2);
        return this;
    }

    public CodeBuilder anewarray(String type) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new ANewArrayInstruction(cp, ANEWARRAY.getCode(), cw.getBytecodeSize(), classRefIndex, 0));
        }, 3);
        return this;
    }

    public CodeBuilder multianewarray(String descriptor, int dims) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(descriptor);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new MultiANewArrayInstruction(cp, MULTIANEWARRAY.getCode(), cw.getBytecodeSize(), classRefIndex, dims));
        }, 4);
        return this;
    }

    public CodeBuilder checkcast(String type) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new CheckCastInstruction(cp, CHECKCAST.getCode(), cw.getBytecodeSize(), classRefIndex));
        }, 3);
        return this;
    }

    public CodeBuilder instanceof_(String type) {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new InstanceOfInstruction(cp, INSTANCEOF.getCode(), cw.getBytecodeSize(), classRefIndex));
        }, 3);
        return this;
    }

    public CodeBuilder ireturn() {
        addOp((bc, cw) -> bc.addReturn(ReturnType.IRETURN), 1);
        return this;
    }

    public CodeBuilder lreturn() {
        addOp((bc, cw) -> bc.addReturn(ReturnType.LRETURN), 1);
        return this;
    }

    public CodeBuilder freturn() {
        addOp((bc, cw) -> bc.addReturn(ReturnType.FRETURN), 1);
        return this;
    }

    public CodeBuilder dreturn() {
        addOp((bc, cw) -> bc.addReturn(ReturnType.DRETURN), 1);
        return this;
    }

    public CodeBuilder areturn() {
        addOp((bc, cw) -> bc.addReturn(ReturnType.ARETURN), 1);
        return this;
    }

    public CodeBuilder vreturn() {
        addOp((bc, cw) -> bc.addReturn(ReturnType.RETURN), 1);
        return this;
    }

    public CodeBuilder athrow() {
        addOp((bc, cw) -> cw.appendInstruction(new ATHROWInstruction(ATHROW.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder trycatch(String startLabel, String endLabel, String handlerLabel, String exceptionType) {
        Label start = getOrCreateLabel(startLabel);
        Label end = getOrCreateLabel(endLabel);
        Label handler = getOrCreateLabel(handlerLabel);
        exceptionRegions.add(new ExceptionRegion(start, end, handler, exceptionType));
        return this;
    }

    public CodeBuilder nop() {
        addOp((bc, cw) -> cw.appendInstruction(new NopInstruction(NOP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder monitorenter() {
        addOp((bc, cw) -> cw.appendInstruction(new MonitorEnterInstruction(MONITORENTER.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder monitorexit() {
        addOp((bc, cw) -> cw.appendInstruction(new MonitorExitInstruction(MONITOREXIT.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    public CodeBuilder iinc(int varIndex, int increment) {
        addOp((bc, cw) -> bc.addIInc(varIndex, increment), 3);
        return this;
    }

    public MethodBuilder end() {
        return parent;
    }

    private void addOp(BytecodeOp op, int size) {
        ops.add(op);
        sizedOps.add(new LegacyOp(op, size));
    }

    void buildCode(MethodEntry method, ConstPool constPool) throws java.io.IOException {
        Bytecode bc = new Bytecode(method);
        CodeWriter cw = bc.getCodeWriter();

        Map<Label, Integer> labelOffsets = new HashMap<>();

        if (!labels.isEmpty()) {
            int maxIterations = 10;
            for (int iter = 0; iter < maxIterations; iter++) {
                int offset = 0;
                boolean changed = false;
                for (SizedOp op : sizedOps) {
                    if (op instanceof LabelOp) {
                        Label label = ((LabelOp) op).label;
                        Integer oldOffset = labelOffsets.get(label);
                        if (oldOffset == null || oldOffset != offset) {
                            labelOffsets.put(label, offset);
                            label.bind(offset);
                            changed = true;
                        }
                    } else {
                        int size = op.getSizeAt(offset);
                        offset += size;
                    }
                }
                if (!changed) break;
            }

            int currentOffset = 0;
            for (SizedOp op : sizedOps) {
                op.emit(bc, cw, labelOffsets, currentOffset);
                currentOffset += op.getSizeAt(currentOffset);
            }
        } else {
            for (BytecodeOp op : ops) {
                op.apply(bc, cw);
            }
        }

        if (!exceptionRegions.isEmpty()) {
            CodeAttribute codeAttr = method.getCodeAttribute();
            for (ExceptionRegion region : exceptionRegions) {
                int startPc = labelOffsets.get(region.start);
                int endPc = labelOffsets.get(region.end);
                int handlerPc = labelOffsets.get(region.handler);

                int catchType = 0;
                if (region.exceptionType != null) {
                    ClassRefItem classRef = constPool.findOrAddClass(region.exceptionType);
                    catchType = constPool.getIndexOf(classRef);
                }

                ExceptionTableEntry entry = new ExceptionTableEntry(startPc, endPc, handlerPc, catchType);
                codeAttr.getExceptionTable().add(entry);
            }
        }

        bc.finalizeBytecode();
    }

    @FunctionalInterface
    private interface BytecodeOp {
        void apply(Bytecode bc, CodeWriter cw);
    }

    private static class Label {
        private int offset = -1;

        boolean isBound() {
            return offset >= 0;
        }

        int getOffset() {
            if (!isBound()) throw new IllegalStateException("Label not bound");
            return offset;
        }

        void bind(int offset) {
            this.offset = offset;
        }
    }

    private static abstract class SizedOp {
        abstract int getSize();
        int getSizeAt(int offset) {
            return getSize();
        }
        abstract void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset);
    }

    private static class LabelOp extends SizedOp {
        final Label label;

        LabelOp(Label label) {
            this.label = label;
        }

        @Override
        int getSize() {
            return 0;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
        }
    }

    private static class LegacyOp extends SizedOp {
        final BytecodeOp op;
        final int size;

        LegacyOp(BytecodeOp op, int size) {
            this.op = op;
            this.size = size;
        }

        @Override
        int getSize() {
            return size;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            op.apply(bc, cw);
        }
    }

    private static class BranchOp extends SizedOp {
        final int opcode;
        final Label target;
        final int size;

        BranchOp(int opcode, Label target, int size) {
            this.opcode = opcode;
            this.target = target;
            this.size = size;
        }

        @Override
        int getSize() {
            return size;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset) {
            Integer targetOffset = labelOffsets.get(target);
            if (targetOffset == null) throw new IllegalStateException("Label not found in label map");
            int relativeOffset = targetOffset - currentOffset;

            if (opcode == GOTO.getCode()) {
                cw.appendInstruction(new GotoInstruction(opcode, currentOffset, (short) relativeOffset));
            } else if (opcode == GOTO_W.getCode()) {
                cw.appendInstruction(new GotoInstruction(opcode, currentOffset, relativeOffset));
            } else {
                cw.appendInstruction(new ConditionalBranchInstruction(opcode, currentOffset, (short) relativeOffset));
            }
        }
    }

    private static class ExceptionRegion {
        Label start;
        Label end;
        Label handler;
        String exceptionType;

        ExceptionRegion(Label start, Label end, Label handler, String exceptionType) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.exceptionType = exceptionType;
        }
    }
}
