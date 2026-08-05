package com.tonic.builder;

import com.tonic.analysis.Bytecode;
import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.util.AccessBuilder;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.parser.constpool.*;
import com.tonic.type.MethodHandle;
import com.tonic.type.TypeDescriptor;
import com.tonic.util.ReturnType;

import java.util.*;

import static com.tonic.analysis.instruction.ArithmeticInstruction.ArithmeticType;
import static com.tonic.util.Opcode.*;

/**
 * A fluent assembler for a method body that records instructions as sized ops and resolves named
 * labels to branch offsets when the body is built or assembled.
 */
public class CodeBuilder
{

    private final MethodBuilder parent;
    private final List<BytecodeOp> ops = new ArrayList<>();
    private final List<SizedOp> sizedOps = new ArrayList<>();
    private final List<ExceptionRegion> exceptionRegions = new ArrayList<>();
    private final Map<String, Label> labels = new HashMap<>();

    CodeBuilder(MethodBuilder parent)
    {
        this.parent = parent;
    }

    /**
     * Creates a standalone builder for authoring a detached instruction snippet, independent of the
     * {@link ClassBuilder}/{@link MethodBuilder} chain. Use the same fluent API, then materialize the
     * snippet with {@link #assemble(ClassFile)} and splice it into an existing method via
     * {@code CodeWriter.insertBefore}/{@code replaceBody}. {@link #end()} is not available on a
     * detached builder; {@code invokedynamic} is (its bootstrap method is added to the
     * {@link #assemble(ClassFile)} target's {@code BootstrapMethods}).
     * @return a builder with no enclosing method
     */
    public static CodeBuilder detached()
    {
        return new CodeBuilder(null);
    }

    private Label getOrCreateLabel(String name)
    {
        return labels.computeIfAbsent(name, Label::new);
    }

    /**
     * Declares {@code name} as an external branch target - an instruction in the host method the
     * snippet will be spliced into, not one defined in the snippet. Branches to it are left unresolved
     * by {@link #assemble(ClassFile)} and must be bound at splice time via
     * {@code ClonedRange.bindLabel} (or a {@code Map} splice overload). Only meaningful on a detached
     * builder destined for {@code assemble}.
     * @param name the label name, created on first use
     * @return this builder
     */
    public CodeBuilder externalLabel(String name)
    {
        getOrCreateLabel(name).external = true;
        return this;
    }

    /**
     * Defines a branch target at the current position.
     * @param name the label name, created on first use
     * @return this builder
     */
    public CodeBuilder label(String name)
    {
        Label l = getOrCreateLabel(name);
        sizedOps.add(new LabelOp(l));
        return this;
    }

    /**
     * Appends the shortest int constant push for the value.
     * @param value the int to push
     * @return this builder
     */
    public CodeBuilder iconst(int value)
    {
        int size = computeIconstSize(value);
        addOp((bc, cw) -> bc.addIConst(value), size);
        return this;
    }

    private int computeIconstSize(int value)
    {
        if (value >= -1 && value <= 5) return 1;
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return 2;
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return 3;
        return 2;
    }

    /**
     * Appends a long constant push, using {@code lconst_0}/{@code lconst_1} where possible.
     * @param value the long to push
     * @return this builder
     */
    public CodeBuilder lconst(long value)
    {
        int size = (value == 0L || value == 1L) ? 1 : 3;
        addOp((bc, cw) -> bc.addLConst(value), size);
        return this;
    }

    /**
     * Appends a float constant push, using {@code fconst_0}/{@code fconst_1}/{@code fconst_2} where possible.
     * @param value the float to push
     * @return this builder
     */
    public CodeBuilder fconst(float value)
    {
        int size = (value == 0.0f || value == 1.0f || value == 2.0f) ? 1 : 2;
        addOp((bc, cw) -> bc.addFConst(value), size);
        return this;
    }

    /**
     * Appends a double constant push, using {@code dconst_0}/{@code dconst_1} where possible.
     * @param value the double to push
     * @return this builder
     */
    public CodeBuilder dconst(double value)
    {
        int size = (value == 0.0 || value == 1.0) ? 1 : 3;
        addOp((bc, cw) -> bc.addDConst(value), size);
        return this;
    }

    /**
     * Appends {@code aconst_null}.
     * @return this builder
     */
    public CodeBuilder aconst_null()
    {
        addOp((bc, cw) -> bc.addAConstNull(), 1);
        return this;
    }

    /**
     * Appends a constant load, routing numeric boxes to the matching typed constant push.
     * @param value a String, Integer, Long, Float or Double
     * @return this builder
     * @throws IllegalArgumentException if the value is of any other type
     */
    public CodeBuilder ldc(Object value)
    {
        if (value instanceof String)
        {
            addOp((bc, cw) -> bc.addLdc((String) value), 2);
        }
        else if (value instanceof Integer)
        {
            return iconst((Integer) value);
        }
        else if (value instanceof Long)
        {
            return lconst((Long) value);
        }
        else if (value instanceof Float)
        {
            return fconst((Float) value);
        }
        else if (value instanceof Double)
        {
            return dconst((Double) value);
        }
        else
        {
            throw new IllegalArgumentException("Unsupported ldc value: " + value);
        }
        return this;
    }

    /**
     * Appends {@code bipush}, pushing a signed byte widened to int.
     * @param value the byte-ranged value to push
     * @return this builder
     */
    public CodeBuilder bipush(int value)
    {
        addOp((bc, cw) -> cw.appendInstruction(new BipushInstruction(BIPUSH.getCode(), cw.getBytecodeSize(), value)), 2);
        return this;
    }

    /**
     * Appends {@code sipush}, pushing a signed short widened to int.
     * @param value the short-ranged value to push
     * @return this builder
     */
    public CodeBuilder sipush(int value)
    {
        addOp((bc, cw) -> cw.appendInstruction(new SipushInstruction(SIPUSH.getCode(), cw.getBytecodeSize(), value)), 3);
        return this;
    }

    /**
     * Appends a int load from a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder iload(int index)
    {
        addOp((bc, cw) -> bc.addILoad(index), 2);
        return this;
    }

    /**
     * Appends a long load from a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder lload(int index)
    {
        addOp((bc, cw) -> bc.addLLoad(index), 2);
        return this;
    }

    /**
     * Appends a float load from a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder fload(int index)
    {
        addOp((bc, cw) -> bc.addFLoad(index), 2);
        return this;
    }

    /**
     * Appends a double load from a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder dload(int index)
    {
        addOp((bc, cw) -> bc.addDLoad(index), 2);
        return this;
    }

    /**
     * Appends a reference load from a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder aload(int index)
    {
        addOp((bc, cw) -> bc.addALoad(index), 2);
        return this;
    }

    /**
     * Appends a int store to a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder istore(int index)
    {
        addOp((bc, cw) -> bc.addIStore(index), 2);
        return this;
    }

    /**
     * Appends a long store to a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder lstore(int index)
    {
        addOp((bc, cw) -> cw.insertLStore(cw.getBytecodeSize(), index), 2);
        return this;
    }

    /**
     * Appends a float store to a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder fstore(int index)
    {
        addOp((bc, cw) -> cw.insertFStore(cw.getBytecodeSize(), index), 2);
        return this;
    }

    /**
     * Appends a double store to a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder dstore(int index)
    {
        addOp((bc, cw) -> cw.insertDStore(cw.getBytecodeSize(), index), 2);
        return this;
    }

    /**
     * Appends a reference store to a local slot.
     * @param index the local variable slot
     * @return this builder
     */
    public CodeBuilder astore(int index)
    {
        addOp((bc, cw) -> bc.addAStore(index), 2);
        return this;
    }

    /**
     * Appends {@code iaload}, loading a int element from an array.
     * @return this builder
     */
    public CodeBuilder iaload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new IALoadInstruction(IALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code laload}, loading a long element from an array.
     * @return this builder
     */
    public CodeBuilder laload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new LALoadInstruction(LALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code faload}, loading a float element from an array.
     * @return this builder
     */
    public CodeBuilder faload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new FALoadInstruction(FALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code daload}, loading a double element from an array.
     * @return this builder
     */
    public CodeBuilder daload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DALoadInstruction(DALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code aaload}, loading a reference element from an array.
     * @return this builder
     */
    public CodeBuilder aaload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new AALoadInstruction(AALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code baload}, loading a byte or boolean element from an array.
     * @return this builder
     */
    public CodeBuilder baload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new BALOADInstruction(BALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code caload}, loading a char element from an array.
     * @return this builder
     */
    public CodeBuilder caload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CALoadInstruction(CALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code saload}, loading a short element from an array.
     * @return this builder
     */
    public CodeBuilder saload()
    {
        addOp((bc, cw) -> cw.appendInstruction(new SALoadInstruction(SALOAD.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code iastore}, storing a int element into an array.
     * @return this builder
     */
    public CodeBuilder iastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new IAStoreInstruction(IASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lastore}, storing a long element into an array.
     * @return this builder
     */
    public CodeBuilder lastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new LAStoreInstruction(LASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fastore}, storing a float element into an array.
     * @return this builder
     */
    public CodeBuilder fastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new FAStoreInstruction(FASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dastore}, storing a double element into an array.
     * @return this builder
     */
    public CodeBuilder dastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DAStoreInstruction(DASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code aastore}, storing a reference element into an array.
     * @return this builder
     */
    public CodeBuilder aastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new AAStoreInstruction(AASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code bastore}, storing a byte or boolean element into an array.
     * @return this builder
     */
    public CodeBuilder bastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new BAStoreInstruction(BASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code castore}, storing a char element into an array.
     * @return this builder
     */
    public CodeBuilder castore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CAStoreInstruction(CASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code sastore}, storing a short element into an array.
     * @return this builder
     */
    public CodeBuilder sastore()
    {
        addOp((bc, cw) -> cw.appendInstruction(new SAStoreInstruction(SASTORE.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code arraylength}, replacing an array reference with its length.
     * @return this builder
     */
    public CodeBuilder arraylength()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArrayLengthInstruction(ARRAYLENGTH.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code pop}, discarding the top one-slot value.
     * @return this builder
     */
    public CodeBuilder pop()
    {
        addOp((bc, cw) -> cw.appendInstruction(new PopInstruction(POP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code pop2}, discarding the top two slots.
     * @return this builder
     */
    public CodeBuilder pop2()
    {
        addOp((bc, cw) -> cw.appendInstruction(new Pop2Instruction(POP2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dup}, duplicating the top one-slot value.
     * @return this builder
     */
    public CodeBuilder dup()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dup_x1}, duplicating the top value one slot down.
     * @return this builder
     */
    public CodeBuilder dup_x1()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP_X1.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dup_x2}, duplicating the top value two slots down.
     * @return this builder
     */
    public CodeBuilder dup_x2()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP_X2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dup2}, duplicating the top two slots.
     * @return this builder
     */
    public CodeBuilder dup2()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dup2_x1}, duplicating the top two slots one slot down.
     * @return this builder
     */
    public CodeBuilder dup2_x1()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP2_X1.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dup2_x2}, duplicating the top two slots two slots down.
     * @return this builder
     */
    public CodeBuilder dup2_x2()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DupInstruction(DUP2_X2.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code swap}, exchanging the top two one-slot values.
     * @return this builder
     */
    public CodeBuilder swap()
    {
        addOp((bc, cw) -> cw.appendInstruction(new SwapInstruction(SWAP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code iadd}, adding the top two int values.
     * @return this builder
     */
    public CodeBuilder iadd()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code isub}, subtracting the top two int values.
     * @return this builder
     */
    public CodeBuilder isub()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.ISUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code imul}, multiplying the top two int values.
     * @return this builder
     */
    public CodeBuilder imul()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code idiv}, dividing the top two int values.
     * @return this builder
     */
    public CodeBuilder idiv()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code irem}, taking the remainder of the top two int values.
     * @return this builder
     */
    public CodeBuilder irem()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.IREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ineg}, negating the top int value.
     * @return this builder
     */
    public CodeBuilder ineg()
    {
        addOp((bc, cw) -> cw.appendInstruction(new INegInstruction(INEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ladd}, adding the top two long values.
     * @return this builder
     */
    public CodeBuilder ladd()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lsub}, subtracting the top two long values.
     * @return this builder
     */
    public CodeBuilder lsub()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LSUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lmul}, multiplying the top two long values.
     * @return this builder
     */
    public CodeBuilder lmul()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ldiv}, dividing the top two long values.
     * @return this builder
     */
    public CodeBuilder ldiv()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lrem}, taking the remainder of the top two long values.
     * @return this builder
     */
    public CodeBuilder lrem()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.LREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lneg}, negating the top long value.
     * @return this builder
     */
    public CodeBuilder lneg()
    {
        addOp((bc, cw) -> cw.appendInstruction(new LNegInstruction(LNEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fadd}, adding the top two float values.
     * @return this builder
     */
    public CodeBuilder fadd()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fsub}, subtracting the top two float values.
     * @return this builder
     */
    public CodeBuilder fsub()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FSUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fmul}, multiplying the top two float values.
     * @return this builder
     */
    public CodeBuilder fmul()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fdiv}, dividing the top two float values.
     * @return this builder
     */
    public CodeBuilder fdiv()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code frem}, taking the remainder of the top two float values.
     * @return this builder
     */
    public CodeBuilder frem()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.FREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fneg}, negating the top float value.
     * @return this builder
     */
    public CodeBuilder fneg()
    {
        addOp((bc, cw) -> cw.appendInstruction(new FNegInstruction(FNEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dadd}, adding the top two double values.
     * @return this builder
     */
    public CodeBuilder dadd()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DADD.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dsub}, subtracting the top two double values.
     * @return this builder
     */
    public CodeBuilder dsub()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DSUB.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dmul}, multiplying the top two double values.
     * @return this builder
     */
    public CodeBuilder dmul()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DMUL.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ddiv}, dividing the top two double values.
     * @return this builder
     */
    public CodeBuilder ddiv()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DDIV.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code drem}, taking the remainder of the top two double values.
     * @return this builder
     */
    public CodeBuilder drem()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticInstruction(ArithmeticType.DREM.getOpcode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dneg}, negating the top double value.
     * @return this builder
     */
    public CodeBuilder dneg()
    {
        addOp((bc, cw) -> cw.appendInstruction(new DNegInstruction(DNEG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code iand}, bitwise and of the top two int values.
     * @return this builder
     */
    public CodeBuilder iand()
    {
        addOp((bc, cw) -> cw.appendInstruction(new IAndInstruction(IAND.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ior}, bitwise or of the top two int values.
     * @return this builder
     */
    public CodeBuilder ior()
    {
        addOp((bc, cw) -> cw.appendInstruction(new IOrInstruction(IOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ixor}, bitwise exclusive or of the top two int values.
     * @return this builder
     */
    public CodeBuilder ixor()
    {
        addOp((bc, cw) -> cw.appendInstruction(new IXorInstruction(IXOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ishl}, shifting a int left.
     * @return this builder
     */
    public CodeBuilder ishl()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(ISHL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ishr}, arithmetic right shift of a int.
     * @return this builder
     */
    public CodeBuilder ishr()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(ISHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code iushr}, logical right shift of a int.
     * @return this builder
     */
    public CodeBuilder iushr()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(IUSHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code land}, bitwise and of the top two long values.
     * @return this builder
     */
    public CodeBuilder land()
    {
        addOp((bc, cw) -> cw.appendInstruction(new LandInstruction(LAND.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lor}, bitwise or of the top two long values.
     * @return this builder
     */
    public CodeBuilder lor()
    {
        addOp((bc, cw) -> cw.appendInstruction(new LorInstruction(LOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lxor}, bitwise exclusive or of the top two long values.
     * @return this builder
     */
    public CodeBuilder lxor()
    {
        addOp((bc, cw) -> cw.appendInstruction(new LXorInstruction(LXOR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lshl}, shifting a long left.
     * @return this builder
     */
    public CodeBuilder lshl()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(LSHL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lshr}, arithmetic right shift of a long.
     * @return this builder
     */
    public CodeBuilder lshr()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(LSHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lushr}, logical right shift of a long.
     * @return this builder
     */
    public CodeBuilder lushr()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ArithmeticShiftInstruction(LUSHR.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code i2l}, widening an int to long.
     * @return this builder
     */
    public CodeBuilder i2l()
    {
        addOp((bc, cw) -> cw.appendInstruction(new I2LInstruction(I2L.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code i2f}, converting an int to float.
     * @return this builder
     */
    public CodeBuilder i2f()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(I2F.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code i2d}, converting an int to double.
     * @return this builder
     */
    public CodeBuilder i2d()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(I2D.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code l2i}, narrowing a long to int.
     * @return this builder
     */
    public CodeBuilder l2i()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(L2I.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code l2f}, converting a long to float.
     * @return this builder
     */
    public CodeBuilder l2f()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(L2F.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code l2d}, converting a long to double.
     * @return this builder
     */
    public CodeBuilder l2d()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(L2D.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code f2i}, converting a float to int.
     * @return this builder
     */
    public CodeBuilder f2i()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(F2I.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code f2l}, converting a float to long.
     * @return this builder
     */
    public CodeBuilder f2l()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(F2L.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code f2d}, widening a float to double.
     * @return this builder
     */
    public CodeBuilder f2d()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(F2D.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code d2i}, converting a double to int.
     * @return this builder
     */
    public CodeBuilder d2i()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(D2I.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code d2l}, converting a double to long.
     * @return this builder
     */
    public CodeBuilder d2l()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(D2L.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code d2f}, narrowing a double to float.
     * @return this builder
     */
    public CodeBuilder d2f()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ConversionInstruction(D2F.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code i2b}, truncating an int to byte.
     * @return this builder
     */
    public CodeBuilder i2b()
    {
        addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(I2B.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code i2c}, truncating an int to char.
     * @return this builder
     */
    public CodeBuilder i2c()
    {
        addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(I2C.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code i2s}, truncating an int to short.
     * @return this builder
     */
    public CodeBuilder i2s()
    {
        addOp((bc, cw) -> cw.appendInstruction(new NarrowingConversionInstruction(I2S.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code lcmp}, pushing -1, 0 or 1 for the top two longs.
     * @return this builder
     */
    public CodeBuilder lcmp()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(LCMP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fcmpl}, comparing two floats and pushing -1 when either is NaN.
     * @return this builder
     */
    public CodeBuilder fcmpl()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(FCMPL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code fcmpg}, comparing two floats and pushing 1 when either is NaN.
     * @return this builder
     */
    public CodeBuilder fcmpg()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(FCMPG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dcmpl}, comparing two doubles and pushing -1 when either is NaN.
     * @return this builder
     */
    public CodeBuilder dcmpl()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(DCMPL.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code dcmpg}, comparing two doubles and pushing 1 when either is NaN.
     * @return this builder
     */
    public CodeBuilder dcmpg()
    {
        addOp((bc, cw) -> cw.appendInstruction(new CompareInstruction(DCMPG.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code ifeq}, branching when the top int is zero.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifeq(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFEQ.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code ifne}, branching when the top int is non-zero.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifne(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFNE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code iflt}, branching when the top int is less than zero.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder iflt(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFLT.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code ifge}, branching when the top int is at least zero.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifge(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFGE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code ifgt}, branching when the top int is greater than zero.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifgt(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFGT.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code ifle}, branching when the top int is at most zero.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifle(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFLE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_icmpeq}, branching when the top two ints are equal.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_icmpeq(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPEQ.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_icmpne}, branching when the top two ints differ.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_icmpne(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPNE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_icmplt}, branching when the first int is less than the second.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_icmplt(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPLT.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_icmpge}, branching when the first int is at least the second.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_icmpge(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPGE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_icmpgt}, branching when the first int is greater than the second.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_icmpgt(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPGT.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_icmple}, branching when the first int is at most the second.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_icmple(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ICMPLE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_acmpeq}, branching when the top two references are the same object.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_acmpeq(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ACMPEQ.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code if_acmpne}, branching when the top two references are different objects.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder if_acmpne(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IF_ACMPNE.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code ifnull}, branching when the top reference is null.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifnull(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFNULL.getCode(), target, 3));
        return this;
    }

    /**
     * Appends {@code ifnonnull}, branching when the top reference is non-null.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder ifnonnull(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(IFNONNULL.getCode(), target, 3));
        return this;
    }

    /**
     * Appends an unconditional {@code goto}.
     * @param labelName the target label, created on first use
     * @return this builder
     */
    public CodeBuilder goto_(String labelName)
    {
        Label target = getOrCreateLabel(labelName);
        sizedOps.add(new BranchOp(GOTO.getCode(), target, 3));
        return this;
    }

    /**
     * Emits a {@code tableswitch} over the contiguous keys {@code low..high}. {@code caseLabels} gives
     * the target label for each key in ascending order (so {@code caseLabels.length == high - low + 1});
     * {@code defaultLabel} is the fallthrough. All labels must be {@code label()}-defined in the snippet
     * (switch targets are not external/continuation-bindable). Padding to the 4-byte boundary is
     * computed automatically.
     * @param low the lowest key
     * @param high the highest key
     * @param defaultLabel label taken when the value is outside the key range
     * @param caseLabels target labels for the keys in ascending order
     * @return this builder
     * @throws IllegalArgumentException if high is below low, or the label count does not match the key range
     */
    public CodeBuilder tableswitch(int low, int high, String defaultLabel, String... caseLabels)
    {
        if (high < low)
        {
            throw new IllegalArgumentException("tableswitch high < low");
        }
        if (caseLabels.length != high - low + 1)
        {
            throw new IllegalArgumentException(
                    "tableswitch expects " + (high - low + 1) + " case labels, got " + caseLabels.length);
        }
        List<Label> cases = new ArrayList<>(caseLabels.length);
        for (String name : caseLabels)
        {
            cases.add(getOrCreateLabel(name));
        }
        sizedOps.add(new TableSwitchOp(low, high, getOrCreateLabel(defaultLabel), cases));
        return this;
    }

    /**
     * Emits a {@code lookupswitch} mapping each key in {@code cases} to its target label, with
     * {@code defaultLabel} as the fallthrough. Keys are sorted ascending as the JVM requires. All labels
     * must be {@code label()}-defined in the snippet. Padding is computed automatically.
     * @param defaultLabel label taken when no key matches
     * @param cases key to target label mapping
     * @return this builder
     */
    public CodeBuilder lookupswitch(String defaultLabel, Map<Integer, String> cases)
    {
        Map<Integer, Label> sorted = new TreeMap<>();
        for (Map.Entry<Integer, String> e : cases.entrySet())
        {
            sorted.put(e.getKey(), getOrCreateLabel(e.getValue()));
        }
        sizedOps.add(new LookupSwitchOp(getOrCreateLabel(defaultLabel), sorted));
        return this;
    }

    /**
     * Appends {@code invokevirtual}, adding the method reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @return this builder
     */
    public CodeBuilder invokevirtual(String owner, String name, String descriptor)
    {
        addOp((bc, cw) -> bc.addInvokeVirtual(owner, name, descriptor), 3);
        return this;
    }

    /**
     * Appends {@code invokespecial}, adding the method reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @return this builder
     */
    public CodeBuilder invokespecial(String owner, String name, String descriptor)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            int methodRef = cp.addMethodRef(owner, name, descriptor);
            bc.addInvokeSpecial(methodRef);
        }, 3);
        return this;
    }

    /**
     * Appends {@code invokestatic}, adding the method reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the method name
     * @param descriptor the method descriptor
     * @return this builder
     */
    public CodeBuilder invokestatic(String owner, String name, String descriptor)
    {
        addOp((bc, cw) -> bc.addInvokeStatic(owner, name, descriptor), 3);
        return this;
    }

    /**
     * Appends {@code invokeinterface}, adding the interface method reference and deriving the argument
     * slot count from the descriptor.
     * @param owner internal name of the declaring interface
     * @param name the method name
     * @param descriptor the method descriptor
     * @return this builder
     */
    public CodeBuilder invokeinterface(String owner, String name, String descriptor)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            InterfaceRefItem interfaceRef = cp.findOrAddInterfaceRef(owner, name, descriptor);
            int interfaceMethodRef = interfaceRef.getIndex(cp);
            int argCount = countInterfaceArgs(descriptor);
            bc.addInvokeInterface(interfaceMethodRef, argCount);
        }, 5);
        return this;
    }

    private int countInterfaceArgs(String descriptor)
    {
        int count = 1;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')')
        {
            char c = descriptor.charAt(i);
            if (c == 'L')
            {
                while (descriptor.charAt(i) != ';') i++;
                count++;
            }
            else if (c == '[')
            {
                while (descriptor.charAt(i) == '[') i++;
                if (descriptor.charAt(i) == 'L')
                {
                    while (descriptor.charAt(i) != ';') i++;
                }
                count++;
            }
            else if (c == 'J' || c == 'D')
            {
                count += 2;
            }
            else
            {
                count++;
            }
            i++;
        }
        return count;
    }

    /**
     * Appends {@code invokedynamic}, registering the bootstrap method and its static arguments on the
     * host class - the enclosing class builder, or the {@link #assemble(ClassFile)} target when detached.
     * @param name the call site name
     * @param descriptor the call site descriptor
     * @param bootstrap handle of the bootstrap method
     * @param bsmArgs static bootstrap arguments; String, Integer, Long, Float, Double, TypeDescriptor and
     *         MethodHandle are encoded, any other type is skipped
     * @return this builder
     */
    public CodeBuilder invokedynamic(String name, String descriptor, MethodHandle bootstrap, Object... bsmArgs)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            int methodRef = cp.addMethodRef(bootstrap.getOwner(), bootstrap.getName(), bootstrap.getDescriptor());
            int methodHandle = cp.addMethodHandle(bootstrap.getTag(), methodRef);

            List<Integer> argIndices = new ArrayList<>();
            for (Object arg : bsmArgs)
            {
                if (arg instanceof String)
                {
                    argIndices.add(cp.findOrAddString((String) arg).getIndex(cp));
                }
                else if (arg instanceof Integer)
                {
                    argIndices.add(cp.findOrAddInteger((Integer) arg).getIndex(cp));
                }
                else if (arg instanceof Long)
                {
                    argIndices.add(cp.findOrAddLong((Long) arg).getIndex(cp));
                }
                else if (arg instanceof Float)
                {
                    argIndices.add(cp.findOrAddFloat((Float) arg).getIndex(cp));
                }
                else if (arg instanceof Double)
                {
                    argIndices.add(cp.findOrAddDouble((Double) arg).getIndex(cp));
                }
                else if (arg instanceof TypeDescriptor)
                {
                    argIndices.add(cp.addMethodType(((TypeDescriptor) arg).getDescriptor()));
                }
                else if (arg instanceof MethodHandle)
                {
                    MethodHandle mh = (MethodHandle) arg;
                    int ref = cp.addMethodRef(mh.getOwner(), mh.getName(), mh.getDescriptor());
                    argIndices.add(cp.addMethodHandle(mh.getTag(), ref));
                }
            }

            int bsmIndex = parent != null
                    ? parent.getParent().addBootstrapMethod(methodHandle, argIndices)
                    : bc.getCodeWriter().getMethodEntry().getClassFile().addBootstrapMethod(methodHandle, argIndices);
            int nameAndType = cp.addNameAndType(name, descriptor);
            int indyIndex = cp.addInvokeDynamic(bsmIndex, nameAndType);
            bc.addInvokeDynamic(indyIndex);
        }, 5);
        return this;
    }

    /**
     * Appends {@code getfield}, adding the field reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the field name
     * @param descriptor the field type descriptor
     * @return this builder
     */
    public CodeBuilder getfield(String owner, String name, String descriptor)
    {
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

    /**
     * Appends {@code putfield}, adding the field reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the field name
     * @param descriptor the field type descriptor
     * @return this builder
     */
    public CodeBuilder putfield(String owner, String name, String descriptor)
    {
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

    /**
     * Appends {@code getstatic}, adding the field reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the field name
     * @param descriptor the field type descriptor
     * @return this builder
     */
    public CodeBuilder getstatic(String owner, String name, String descriptor)
    {
        addOp((bc, cw) -> bc.addGetStatic(owner, name, descriptor), 3);
        return this;
    }

    /**
     * Appends {@code putstatic}, adding the field reference to the constant pool.
     * @param owner internal name of the declaring class
     * @param name the field name
     * @param descriptor the field type descriptor
     * @return this builder
     */
    public CodeBuilder putstatic(String owner, String name, String descriptor)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            FieldRefItem fieldRef = cp.findOrAddFieldRef(owner, name, descriptor);
            bc.addPutStatic(fieldRef.getIndex(cp));
        }, 3);
        return this;
    }

    /**
     * Appends {@code new}, adding the class reference to the constant pool.
     * @param type internal name of the class to allocate
     * @return this builder
     */
    public CodeBuilder new_(String type)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.insertNew(cw.getBytecodeSize(), classRefIndex);
        }, 3);
        return this;
    }

    /**
     * Appends {@code newarray} for a primitive element type.
     * @param arrayType the JVM array type code for the element
     * @return this builder
     */
    public CodeBuilder newarray(int arrayType)
    {
        addOp((bc, cw) -> cw.appendInstruction(new NewPrimitiveArrayInstruction(NEWARRAY.getCode(), cw.getBytecodeSize(), arrayType, 0)), 2);
        return this;
    }

    /**
     * Appends {@code anewarray} for a reference element type.
     * @param type internal name of the element class
     * @return this builder
     */
    public CodeBuilder anewarray(String type)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new ANewArrayInstruction(cp, ANEWARRAY.getCode(), cw.getBytecodeSize(), classRefIndex, 0));
        }, 3);
        return this;
    }

    /**
     * Appends {@code multianewarray}, consuming one length from the stack per dimension.
     * @param descriptor internal name of the array class
     * @param dims number of dimensions to allocate
     * @return this builder
     */
    public CodeBuilder multianewarray(String descriptor, int dims)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(descriptor);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new MultiANewArrayInstruction(cp, MULTIANEWARRAY.getCode(), cw.getBytecodeSize(), classRefIndex, dims));
        }, 4);
        return this;
    }

    /**
     * Appends {@code checkcast}.
     * @param type internal name to cast the top reference to
     * @return this builder
     */
    public CodeBuilder checkcast(String type)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new CheckCastInstruction(cp, CHECKCAST.getCode(), cw.getBytecodeSize(), classRefIndex));
        }, 3);
        return this;
    }

    /**
     * Appends {@code instanceof}, replacing the top reference with 1 when it is of the given type.
     * @param type internal name to test against
     * @return this builder
     */
    public CodeBuilder instanceof_(String type)
    {
        addOp((bc, cw) -> {
            ConstPool cp = bc.getConstPool();
            ClassRefItem classRef = cp.findOrAddClass(type);
            int classRefIndex = cp.getIndexOf(classRef);
            cw.appendInstruction(new InstanceOfInstruction(cp, INSTANCEOF.getCode(), cw.getBytecodeSize(), classRefIndex));
        }, 3);
        return this;
    }

    /**
     * Appends {@code ireturn}, returning an int.
     * @return this builder
     */
    public CodeBuilder ireturn()
    {
        addOp((bc, cw) -> bc.addReturn(ReturnType.IRETURN), 1);
        return this;
    }

    /**
     * Appends {@code lreturn}, returning a long.
     * @return this builder
     */
    public CodeBuilder lreturn()
    {
        addOp((bc, cw) -> bc.addReturn(ReturnType.LRETURN), 1);
        return this;
    }

    /**
     * Appends {@code freturn}, returning a float.
     * @return this builder
     */
    public CodeBuilder freturn()
    {
        addOp((bc, cw) -> bc.addReturn(ReturnType.FRETURN), 1);
        return this;
    }

    /**
     * Appends {@code dreturn}, returning a double.
     * @return this builder
     */
    public CodeBuilder dreturn()
    {
        addOp((bc, cw) -> bc.addReturn(ReturnType.DRETURN), 1);
        return this;
    }

    /**
     * Appends {@code areturn}, returning a reference.
     * @return this builder
     */
    public CodeBuilder areturn()
    {
        addOp((bc, cw) -> bc.addReturn(ReturnType.ARETURN), 1);
        return this;
    }

    /**
     * Appends {@code return}, returning from a void method.
     * @return this builder
     */
    public CodeBuilder vreturn()
    {
        addOp((bc, cw) -> bc.addReturn(ReturnType.RETURN), 1);
        return this;
    }

    /**
     * Appends {@code athrow}, throwing the reference on top of the stack.
     * @return this builder
     */
    public CodeBuilder athrow()
    {
        addOp((bc, cw) -> cw.appendInstruction(new ATHROWInstruction(ATHROW.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Records an exception-table region, resolved to offsets when the body is built or assembled.
     * @param startLabel label at the first protected instruction
     * @param endLabel label just past the protected range
     * @param handlerLabel label at the handler entry
     * @param exceptionType internal name of the caught class, null to catch any throwable
     * @return this builder
     */
    public CodeBuilder trycatch(String startLabel, String endLabel, String handlerLabel, String exceptionType)
    {
        Label start = getOrCreateLabel(startLabel);
        Label end = getOrCreateLabel(endLabel);
        Label handler = getOrCreateLabel(handlerLabel);
        exceptionRegions.add(new ExceptionRegion(start, end, handler, exceptionType));
        return this;
    }

    /**
     * Appends {@code nop}.
     * @return this builder
     */
    public CodeBuilder nop()
    {
        addOp((bc, cw) -> cw.appendInstruction(new NopInstruction(NOP.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code monitorenter}, acquiring the monitor of the top reference.
     * @return this builder
     */
    public CodeBuilder monitorenter()
    {
        addOp((bc, cw) -> cw.appendInstruction(new MonitorEnterInstruction(MONITORENTER.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code monitorexit}, releasing the monitor of the top reference.
     * @return this builder
     */
    public CodeBuilder monitorexit()
    {
        addOp((bc, cw) -> cw.appendInstruction(new MonitorExitInstruction(MONITOREXIT.getCode(), cw.getBytecodeSize())), 1);
        return this;
    }

    /**
     * Appends {@code iinc}, adding a constant to an int local in place.
     * @param varIndex the local variable slot
     * @param increment the signed amount to add
     * @return this builder
     */
    public CodeBuilder iinc(int varIndex, int increment)
    {
        addOp((bc, cw) -> bc.addIInc(varIndex, increment), 3);
        return this;
    }

    /**
     * Ends the body and hands back to the enclosing method builder.
     * @return the enclosing MethodBuilder, or null when this builder was created by {@link #detached()}
     */
    public MethodBuilder end()
    {
        return parent;
    }

    private void addOp(BytecodeOp op, int size)
    {
        ops.add(op);
        sizedOps.add(new LegacyOp(op, size));
    }

    /**
     * Resolves labels and emits the recorded ops into the CodeWriter bound to {@code bc}, laying the
     * snippet out from offset 0. Shared by {@link #buildCode} and {@link #assemble}; does not install
     * exception regions or serialize.
     * @return the resolved label offsets plus any unresolved external/continuation branch offsets
     */
    private EmitResult emitInto(Bytecode bc)
    {
        CodeWriter cw = bc.getCodeWriter();
        Map<Label, Integer> labelOffsets = new HashMap<>();
        Map<String, List<Integer>> externalRefs = new LinkedHashMap<>();
        List<Integer> continuationRefs = new ArrayList<>();

        if (!labels.isEmpty())
        {
            int maxIterations = 10;
            for (int iter = 0; iter < maxIterations; iter++)
            {
                int offset = 0;
                boolean changed = false;
                for (SizedOp op : sizedOps)
                {
                    if (op instanceof LabelOp)
                    {
                        Label label = ((LabelOp) op).label;
                        Integer oldOffset = labelOffsets.get(label);
                        if (oldOffset == null || oldOffset != offset)
                        {
                            labelOffsets.put(label, offset);
                            label.bind(offset);
                            changed = true;
                        }
                    }
                    else
                    {
                        offset += op.getSizeAt(offset);
                    }
                }
                if (!changed) break;
            }

            int codeLength = 0;
            for (SizedOp op : sizedOps)
            {
                codeLength += op.getSizeAt(codeLength);
            }

            int currentOffset = 0;
            for (SizedOp op : sizedOps)
            {
                if (op instanceof BranchOp)
                {
                    Label t = ((BranchOp) op).target;
                    if (t.external)
                    {
                        externalRefs.computeIfAbsent(t.name, k -> new ArrayList<>()).add(currentOffset);
                    }
                    else if (Integer.valueOf(codeLength).equals(labelOffsets.get(t)))
                    {
                        continuationRefs.add(currentOffset);
                    }
                }
                op.emit(bc, cw, labelOffsets, currentOffset);
                currentOffset += op.getSizeAt(currentOffset);
            }
        }
        else
        {
            for (BytecodeOp op : ops)
            {
                op.apply(bc, cw);
            }
        }
        return new EmitResult(labelOffsets, externalRefs, continuationRefs);
    }

    /**
     * Result of {@link #emitInto}: bound label offsets plus unresolved external/continuation branches.
     */
    private static final class EmitResult
    {
        final Map<Label, Integer> labelOffsets;
        final Map<String, List<Integer>> externalRefs;
        final List<Integer> continuationRefs;

        EmitResult(Map<Label, Integer> labelOffsets, Map<String, List<Integer>> externalRefs,
                   List<Integer> continuationRefs)
                   {
            this.labelOffsets = labelOffsets;
            this.externalRefs = externalRefs;
            this.continuationRefs = continuationRefs;
        }
    }

    void buildCode(MethodEntry method, ConstPool constPool) throws java.io.IOException
    {
        Bytecode bc = new Bytecode(method);
        EmitResult emit = emitInto(bc);
        if (!emit.externalRefs.isEmpty() || !emit.continuationRefs.isEmpty())
        {
            throw new IllegalStateException(
                    "external/continuation labels are only resolvable when splicing via assemble(); "
                            + "they are not valid in a complete method build");
        }
        Map<Label, Integer> labelOffsets = emit.labelOffsets;

        if (!exceptionRegions.isEmpty())
        {
            CodeAttribute codeAttr = method.getCodeAttribute();
            for (ExceptionRegion region : exceptionRegions)
            {
                int startPc = labelOffsets.get(region.start);
                int endPc = labelOffsets.get(region.end);
                int handlerPc = labelOffsets.get(region.handler);

                int catchType = 0;
                if (region.exceptionType != null)
                {
                    ClassRefItem classRef = constPool.findOrAddClass(region.exceptionType);
                    catchType = constPool.getIndexOf(classRef);
                }

                ExceptionTableEntry entry = new ExceptionTableEntry(startPc, endPc, handlerPc, catchType);
                codeAttr.getExceptionTable().add(entry);
            }
        }

        bc.finalizeBytecode();
    }

    /**
     * Materializes the recorded ops into a detached, self-contained instruction snapshot resolved
     * against {@code target}'s constant pool, ready to splice into any method that {@code target}
     * owns via {@code CodeWriter.insertBefore(handle, ClonedRange)} or {@code replaceBody(ClonedRange)}.
     * Branch and switch targets - and any {@code trycatch} regions - are carried by identity, so the
     * snippet relinks correctly at any insertion point. Constant-pool references created here (classes,
     * members, ldc constants, exception catch types) are added to {@code target}, so the spliced
     * indices are valid where used.
     * @param target the class whose constant pool backs the snippet and which will host the result
     * @return the assembled snippet
     */
    public CodeWriter.ClonedRange assemble(ClassFile target)
    {
        Bytecode bc = new Bytecode(scratchMethod(target));
        EmitResult emit = emitInto(bc);
        return bc.getCodeWriter().toClonedRange(
                resolveExceptionEntries(target, emit.labelOffsets),
                emit.externalRefs,
                emit.continuationRefs);
    }

    /**
     * Resolves the recorded try/catch regions to exception-table entries against {@code target}'s pool.
     */
    private List<ExceptionTableEntry> resolveExceptionEntries(ClassFile target, Map<Label, Integer> labelOffsets)
    {
        List<ExceptionTableEntry> entries = new ArrayList<>();
        for (ExceptionRegion region : exceptionRegions)
        {
            int catchType = 0;
            if (region.exceptionType != null)
            {
                ConstPool cp = target.getConstPool();
                catchType = cp.getIndexOf(cp.findOrAddClass(region.exceptionType));
            }
            entries.add(new ExceptionTableEntry(
                    labelOffsets.get(region.start),
                    labelOffsets.get(region.end),
                    labelOffsets.get(region.handler),
                    catchType));
        }
        return entries;
    }

    /**
     * Builds a throwaway static method bound to {@code target} purely to host a {@link CodeWriter}
     * during {@link #assemble}. It is never added to the class or written, and reuses an existing Utf8
     * (the class name, always present) for its name/descriptor so it adds nothing to the constant pool
     * - only the snippet's own references (added during emit) persist.
     */
    private MethodEntry scratchMethod(ClassFile target)
    {
        ConstPool cp = target.getConstPool();
        int utf8 = cp.utf8Index(target.getClassName());
        CodeAttribute codeAttr = new CodeAttribute("Code", (MethodEntry) null, utf8, 0);
        codeAttr.setMaxStack(0);
        codeAttr.setMaxLocals(0);
        codeAttr.setCode(new byte[0]);
        codeAttr.setAttributes(new ArrayList<>());
        List<Attribute> attrs = new ArrayList<>();
        attrs.add(codeAttr);
        MethodEntry scratch = new MethodEntry(target, new AccessBuilder().setStatic().build(), utf8, utf8, attrs);
        codeAttr.setParent(scratch);
        return scratch;
    }

    @FunctionalInterface
    private interface BytecodeOp
    {
        void apply(Bytecode bc, CodeWriter cw);
    }

    private static class Label
    {
        private final String name;
        private int offset = -1;
        private boolean external;

        Label(String name)
        {
            this.name = name;
        }

        boolean isBound()
        {
            return offset >= 0;
        }

        int getOffset()
        {
            if (!isBound()) throw new IllegalStateException("Label not bound");
            return offset;
        }

        void bind(int offset)
        {
            this.offset = offset;
        }
    }

    private static abstract class SizedOp
    {
        abstract int getSize();
        /**
         * Byte length at {@code offset}; offset-dependent for switches (4-byte-aligned padding).
         */
        int getSizeAt(int offset)
        {
            return getSize();
        }
        abstract void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset);
    }

    private static class LabelOp extends SizedOp
    {
        final Label label;

        LabelOp(Label label)
        {
            this.label = label;
        }

        @Override
        int getSize()
        {
            return 0;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset)
        {
        }
    }

    private static class LegacyOp extends SizedOp
    {
        final BytecodeOp op;
        final int size;

        LegacyOp(BytecodeOp op, int size)
        {
            this.op = op;
            this.size = size;
        }

        @Override
        int getSize()
        {
            return size;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset)
        {
            op.apply(bc, cw);
        }
    }

    private static class BranchOp extends SizedOp
    {
        final int opcode;
        final Label target;
        final int size;

        BranchOp(int opcode, Label target, int size)
        {
            this.opcode = opcode;
            this.target = target;
            this.size = size;
        }

        @Override
        int getSize()
        {
            return size;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset)
        {
            int relativeOffset;
            if (target.external)
            {
                // Target lives in the host; emit a placeholder relative offset and bind at splice time.
                relativeOffset = 0;
            }
            else
            {
                Integer targetOffset = labelOffsets.get(target);
                if (targetOffset == null) throw new IllegalStateException("Label not found in label map");
                relativeOffset = targetOffset - currentOffset;
            }

            if (opcode == GOTO.getCode())
            {
                cw.appendInstruction(new GotoInstruction(opcode, currentOffset, (short) relativeOffset));
            }
            else if (opcode == GOTO_W.getCode())
            {
                cw.appendInstruction(new GotoInstruction(opcode, currentOffset, relativeOffset));
            }
            else
            {
                cw.appendInstruction(new ConditionalBranchInstruction(opcode, currentOffset, (short) relativeOffset));
            }
        }
    }

    /**
     * Padding bytes after the opcode so the jump table aligns to a 4-byte boundary from the method start.
     */
    private static int switchPadding(int offset)
    {
        return (4 - ((offset + 1) % 4)) % 4;
    }

    private static int resolveSwitchTarget(Map<Label, Integer> labelOffsets, Label target, int switchOffset)
    {
        Integer to = labelOffsets.get(target);
        if (to == null)
        {
            throw new IllegalStateException("switch target label not defined: " + target.name);
        }
        return to - switchOffset;
    }

    private static class TableSwitchOp extends SizedOp
    {
        final int low;
        final int high;
        final Label defaultLabel;
        final List<Label> cases;

        TableSwitchOp(int low, int high, Label defaultLabel, List<Label> cases)
        {
            this.low = low;
            this.high = high;
            this.defaultLabel = defaultLabel;
            this.cases = cases;
        }

        @Override
        int getSize()
        {
            return getSizeAt(0);
        }

        @Override
        int getSizeAt(int offset)
        {
            return 1 + switchPadding(offset) + 12 + (high - low + 1) * 4;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset)
        {
            int defaultRel = resolveSwitchTarget(labelOffsets, defaultLabel, currentOffset);
            Map<Integer, Integer> jumps = new LinkedHashMap<>();
            for (int key = low; key <= high; key++)
            {
                jumps.put(key, resolveSwitchTarget(labelOffsets, cases.get(key - low), currentOffset));
            }
            cw.appendInstruction(new TableSwitchInstruction(
                    TABLESWITCH.getCode(), currentOffset, switchPadding(currentOffset), defaultRel, low, high, jumps));
        }
    }

    private static class LookupSwitchOp extends SizedOp
    {
        final Label defaultLabel;
        final Map<Integer, Label> cases;

        LookupSwitchOp(Label defaultLabel, Map<Integer, Label> cases)
        {
            this.defaultLabel = defaultLabel;
            this.cases = cases;
        }

        @Override
        int getSize()
        {
            return getSizeAt(0);
        }

        @Override
        int getSizeAt(int offset)
        {
            return 1 + switchPadding(offset) + 8 + cases.size() * 8;
        }

        @Override
        void emit(Bytecode bc, CodeWriter cw, Map<Label, Integer> labelOffsets, int currentOffset)
        {
            int defaultRel = resolveSwitchTarget(labelOffsets, defaultLabel, currentOffset);
            Map<Integer, Integer> matches = new LinkedHashMap<>();
            for (Map.Entry<Integer, Label> e : cases.entrySet())
            {
                matches.put(e.getKey(), resolveSwitchTarget(labelOffsets, e.getValue(), currentOffset));
            }
            cw.appendInstruction(new LookupSwitchInstruction(
                    LOOKUPSWITCH.getCode(), currentOffset, switchPadding(currentOffset), defaultRel,
                    cases.size(), matches));
        }
    }

    private static class ExceptionRegion
    {
        Label start;
        Label end;
        Label handler;
        String exceptionType;

        ExceptionRegion(Label start, Label end, Label handler, String exceptionType)
        {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.exceptionType = exceptionType;
        }
    }
}
