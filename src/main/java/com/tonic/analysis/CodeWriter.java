package com.tonic.analysis;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.parser.attribute.stack.*;
import com.tonic.analysis.instruction.*;
import com.tonic.utill.Logger;
import com.tonic.utill.ReturnType;
import lombok.Getter;

import java.io.ByteArrayOutputStream;

import static com.tonic.utill.Opcode.*;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * A class for analyzing and modifying the bytecode of a MethodEntry.
 * It allows iterating over bytecode instructions, inserting new instructions,
 * and automatically updating stack and local variable information.
 */
@Getter
public class CodeWriter {
    private final MethodEntry methodEntry;
    private final CodeAttribute codeAttribute;
    private byte[] bytecode;
    private final ConstPool constPool;

    protected final Map<Integer, Instruction> instructions = new TreeMap<>();

    /**
     * Branch/switch instruction -> the target instructions it jumps to, by identity. Index 0 is the
     * default/sole target; for switches, indices 1..n follow in case order. Resolved at parse time and
     * maintained across structural edits by {@link #relink}, so branch targets survive offset shifts
     * (the internal "label" model). A null target means the original branch pointed outside any known
     * instruction boundary and is left untouched.
     */
    private final Map<Instruction, List<Instruction>> branchTargets = new IdentityHashMap<>();

    private int maxStack;
    private int maxLocals;

    /**
     * -- GETTER --
     *  Returns whether the bytecode has been modified since loading.
     *
     * @return true if modified, false otherwise
     */
    @Getter
    private boolean modified = false;

    /**
     * Constructs a CodeWriter for the given MethodEntry.
     *
     * @param methodEntry The MethodEntry to manipulate.
     */
    public CodeWriter(MethodEntry methodEntry) {
        this.methodEntry = methodEntry;
        this.codeAttribute = methodEntry.getCodeAttribute();
        if (this.codeAttribute == null) {
            throw new IllegalArgumentException("MethodEntry does not contain a CodeAttribute.");
        }
        this.bytecode = this.codeAttribute.getCode();
        this.constPool = methodEntry.getClassFile().getConstPool();
        parseBytecode();
    }

    /**
     * Parses the bytecode into individual instructions.
     */
    protected void parseBytecode() {
        instructions.clear();
        int offset = 0;
        boolean debugMode = false;
        while (offset < bytecode.length) {
            int opcode = Byte.toUnsignedInt(bytecode[offset]);
            Instruction instr = InstructionFactory.createInstruction(opcode, offset, bytecode, constPool);
            instructions.put(offset, instr);
            int instrLength = instr.getLength();
            if (instr instanceof LdcWInstruction) {
                LdcWInstruction ldc = (LdcWInstruction) instr;
                if (ldc.getCpIndex() <= 0) {
                    debugMode = true;
                    System.err.println("Invalid LDC_W at offset " + offset + " with cpIndex=" + ldc.getCpIndex());
                    System.err.println("Bytecode bytes at offset: " +
                        String.format("%02X %02X %02X", bytecode[offset],
                            offset+1 < bytecode.length ? bytecode[offset+1] : 0,
                            offset+2 < bytecode.length ? bytecode[offset+2] : 0));
                    System.err.println("First 30 bytes of method bytecode:");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(30, bytecode.length); i++) {
                        sb.append(String.format("%02X ", bytecode[i]));
                    }
                    System.err.println(sb);
                    System.err.println("Previously parsed instructions:");
                    for (var entry : instructions.entrySet()) {
                        System.err.println("  offset " + entry.getKey() + ": " + entry.getValue().getClass().getSimpleName() + " length=" + entry.getValue().getLength());
                    }
                }
            }
            offset += instrLength;
        }
        this.maxStack = codeAttribute.getMaxStack();
        this.maxLocals = codeAttribute.getMaxLocals();
        resolveAllBranchTargets();
    }

    /** Rebuilds {@link #branchTargets} from the current instruction offsets (consistent state). */
    private void resolveAllBranchTargets() {
        branchTargets.clear();
        for (Instruction instr : instructions.values()) {
            List<Instruction> targets = resolveTargets(instr);
            if (targets != null) {
                branchTargets.put(instr, targets);
            }
        }
    }

    /**
     * Iterates over all instructions.
     *
     * @return An Iterable of Instructions.
     */
    public Iterable<Instruction> getInstructions() {
        return instructions.values();
    }

    /**
     * Returns the total number of instructions in this method.
     *
     * @return instruction count
     */
    public int getInstructionCount()
    {
        return instructions.size();
    }

    /**
     * Gets the sequential instruction index for the instruction at the given bytecode offset.
     * This maps bytecode offset to sequential instruction number (0, 1, 2, ...).
     *
     * @param offset The bytecode offset
     * @return The sequential instruction index, or -1 if no instruction at that offset
     */
    public int getInstructionIndex(int offset) {
        int index = 0;
        for (Integer key : instructions.keySet()) {
            if (key == offset) {
                return index;
            }
            index++;
        }
        return -1;
    }

    /**
     * Gets the instruction at a specific sequential index (0, 1, 2, ...).
     *
     * @param index The sequential instruction index
     * @return The instruction at that index, or null if out of bounds
     */
    public Instruction getInstructionAt(int index) {
        if (index < 0 || index >= instructions.size()) {
            return null;
        }
        int i = 0;
        for (Instruction instr : instructions.values()) {
            if (i == index) {
                return instr;
            }
            i++;
        }
        return null;
    }

    /**
     * Gets the bytecode offset for the instruction at a specific sequential index.
     *
     * @param index The sequential instruction index
     * @return The bytecode offset, or -1 if out of bounds
     */
    public int getOffsetAt(int index) {
        if (index < 0 || index >= instructions.size()) {
            return -1;
        }
        int i = 0;
        for (Integer offset : instructions.keySet()) {
            if (i == index) {
                return offset;
            }
            i++;
        }
        return -1;
    }

    /**
     * Finds the instruction index that contains or follows the given bytecode offset.
     * Useful for exception handler matching.
     *
     * @param targetOffset The bytecode offset to search for
     * @return The instruction index, or -1 if not found
     */
    public int findInstructionIndexForOffset(int targetOffset) {
        int index = 0;
        for (Map.Entry<Integer, Instruction> entry : instructions.entrySet()) {
            int instrOffset = entry.getKey();
            if (instrOffset == targetOffset) {
                return index;
            }
            if (instrOffset > targetOffset) {
                return index > 0 ? index - 1 : 0;
            }
            index++;
        }
        return instructions.size() - 1;
    }

    /**
     * Inserts an instruction before whatever instruction currently sits at {@code offset} (or appends
     * it when {@code offset == code length}). Routes through {@link #relink}, so branch/switch targets,
     * the exception table, and frames are all kept correct — including when the insertion point lies
     * within a branch span (which the previous offset-only implementation corrupted).
     *
     * @param offset   the bytecode offset of the instruction to insert before
     * @param newInstr the new instruction to insert
     */
    public void insertInstruction(int offset, Instruction newInstr) {
        if (!instructions.containsKey(offset) && offset != bytecode.length) {
            throw new IllegalArgumentException("Invalid bytecode offset: " + offset);
        }
        List<Instruction> order = new ArrayList<>();
        boolean inserted = false;
        for (Instruction instr : instructions.values()) {
            if (instr.getOffset() == offset) {
                order.add(newInstr);
                inserted = true;
            }
            order.add(instr);
        }
        if (!inserted) {
            order.add(newInstr);
        }
        relink(order);
    }

    /**
     * Removes an instruction, identified by handle (object identity), and relinks the method.
     * Throws if the instruction is the target of a branch/switch (retarget or replace it instead).
     *
     * @param handle the instruction to remove (an object currently in this method)
     */
    public void removeInstruction(Instruction handle) {
        requirePresent(handle);
        for (List<Instruction> targets : branchTargets.values()) {
            if (targets.contains(handle)) {
                throw new IllegalStateException("Cannot remove an instruction that is a branch/switch target: " + handle);
            }
        }
        List<Instruction> order = new ArrayList<>();
        for (Instruction instr : instructions.values()) {
            if (instr != handle) {
                order.add(instr);
            }
        }
        branchTargets.remove(handle);
        relink(order);
    }

    /**
     * Replaces an instruction (by handle) with another, preserving control flow: any branch/switch
     * that targeted the old instruction is retargeted to the replacement. If {@code replacement} is
     * itself a branch, register its target first via {@link #setBranchTarget}/{@link #setSwitchTargets}.
     *
     * @param handle      the instruction to replace
     * @param replacement the new instruction
     */
    public void replaceInstruction(Instruction handle, Instruction replacement) {
        requirePresent(handle);
        for (List<Instruction> targets : branchTargets.values()) {
            for (int k = 0; k < targets.size(); k++) {
                if (targets.get(k) == handle) {
                    targets.set(k, replacement);
                }
            }
        }
        List<Instruction> order = new ArrayList<>();
        for (Instruction instr : instructions.values()) {
            order.add(instr == handle ? replacement : instr);
        }
        branchTargets.remove(handle);
        relink(order);
    }

    /**
     * Replaces this method's entire instruction stream with {@code body} (e.g. a cloned/grafted body),
     * then relinks: offsets, branch/switch targets, and frames are recomputed. Branches in {@code body}
     * must either be self-contained (relative offsets valid for the block, as produced by
     * {@link #cloneRange}) or have their targets registered via {@link #setBranchTarget}. The exception
     * table is cleared (set a new one separately if needed).
     *
     * @param body the new instruction stream, in order
     */
    public void replaceBody(List<Instruction> body) {
        codeAttribute.getExceptionTable().clear();
        relink(new ArrayList<>(body));
    }

    /**
     * As {@link #replaceBody(List)} but installs {@code exceptions} as the method's exception table
     * before relinking (so regenerated frames cover the handler blocks). Entry PCs are interpreted
     * against {@code body}'s layout.
     *
     * @param body       the new instruction stream, in order
     * @param exceptions the exception-table entries (catch_type indices in this method's pool)
     */
    public void replaceBody(List<Instruction> body,
                            List<com.tonic.parser.attribute.table.ExceptionTableEntry> exceptions) {
        codeAttribute.getExceptionTable().clear();
        codeAttribute.getExceptionTable().addAll(exceptions);
        relink(new ArrayList<>(body));
    }

    /** Replaces the body with a cloned range, carrying its branch/switch targets by identity. */
    public void replaceBody(ClonedRange block) {
        branchTargets.putAll(block.targets);
        replaceBody(block.instructions);
    }

    /** Replaces the body with a cloned range plus an exception table, carrying targets by identity. */
    public void replaceBody(ClonedRange block,
                            List<com.tonic.parser.attribute.table.ExceptionTableEntry> exceptions) {
        branchTargets.putAll(block.targets);
        replaceBody(block.instructions, exceptions);
    }

    /** Inserts a cloned range before the handle, carrying its branch/switch targets by identity. */
    public void insertBefore(Instruction handle, ClonedRange block) {
        branchTargets.putAll(block.targets);
        insertBefore(handle, block.instructions);
    }

    /** Inserts a cloned range after the handle, carrying its branch/switch targets by identity. */
    public void insertAfter(Instruction handle, ClonedRange block) {
        branchTargets.putAll(block.targets);
        insertAfter(handle, block.instructions);
    }

    /**
     * Inserts {@code newInstr} immediately before the given instruction handle.
     */
    public void insertBefore(Instruction handle, Instruction newInstr) {
        insertBefore(handle, Collections.singletonList(newInstr));
    }

    /**
     * Inserts a block of instructions (e.g. a cloned method body) immediately before the handle.
     * Branch targets internal to the block are honored; register any branch that targets outside the
     * block via {@link #setBranchTarget} before calling.
     */
    public void insertBefore(Instruction handle, List<Instruction> block) {
        requirePresent(handle);
        List<Instruction> order = new ArrayList<>();
        for (Instruction instr : instructions.values()) {
            if (instr == handle) {
                order.addAll(block);
            }
            order.add(instr);
        }
        relink(order);
    }

    /**
     * Inserts {@code newInstr} immediately after the given instruction handle.
     */
    public void insertAfter(Instruction handle, Instruction newInstr) {
        insertAfter(handle, Collections.singletonList(newInstr));
    }

    /**
     * Inserts a block of instructions immediately after the handle.
     */
    public void insertAfter(Instruction handle, List<Instruction> block) {
        requirePresent(handle);
        List<Instruction> order = new ArrayList<>();
        for (Instruction instr : instructions.values()) {
            order.add(instr);
            if (instr == handle) {
                order.addAll(block);
            }
        }
        relink(order);
    }

    /**
     * Registers the target of a (typically newly created) branch instruction by identity, so that the
     * relink pass can compute its relative offset. Use this when building branches that jump to an
     * existing instruction handle rather than via a raw relative offset.
     */
    public void setBranchTarget(Instruction branch, Instruction target) {
        branchTargets.put(branch, new ArrayList<>(Collections.singletonList(target)));
    }

    /**
     * Registers the targets of a switch instruction by identity: the default target followed by one
     * target per case in case order.
     */
    public void setSwitchTargets(Instruction switchInstr, Instruction defaultTarget, List<Instruction> caseTargets) {
        List<Instruction> targets = new ArrayList<>(caseTargets.size() + 1);
        targets.add(defaultTarget);
        targets.addAll(caseTargets);
        branchTargets.put(switchInstr, targets);
    }

    private void requirePresent(Instruction handle) {
        if (!instructions.containsValue(handle)) {
            throw new IllegalArgumentException("Instruction is not part of this method: " + handle);
        }
    }

    /**
     * A cloned instruction range: the fresh instructions plus the by-identity targets of any
     * branch/switch among them. Splicing it via {@link #insertBefore(Instruction, ClonedRange)} /
     * {@link #replaceBody(ClonedRange)} carries the targets into the host so they relink correctly
     * regardless of where (and at what 4-byte alignment) the block lands.
     */
    public static final class ClonedRange {
        private final List<Instruction> instructions;
        private final Map<Instruction, List<Instruction>> targets;

        ClonedRange(List<Instruction> instructions, Map<Instruction, List<Instruction>> targets) {
            this.instructions = instructions;
            this.targets = targets;
        }

        public List<Instruction> instructions() {
            return instructions;
        }
    }

    /**
     * Clones a contiguous instruction range {@code [from, to]} (inclusive) into a fresh list, shifting
     * every local-variable index by {@code localOffset} and recomputing branch/switch relative offsets
     * for the cloned block's own layout (the inliner's "clone with label remap + local offset"). The
     * returned block is translation-invariant for branch-only blocks; for blocks containing a
     * {@code switch}, prefer {@link #cloneRangeWithTargets} + the {@link ClonedRange} splice overloads,
     * which carry targets by identity and are correct at any alignment.
     *
     * @param from        first instruction of the range (a handle in this method)
     * @param to          last instruction of the range (inclusive)
     * @param localOffset value added to every local-variable index in the clone
     * @return the cloned instructions, in order
     */
    public List<Instruction> cloneRange(Instruction from, Instruction to, int localOffset) {
        return cloneRangeWithTargets(from, to, localOffset, null, null).instructions;
    }

    /**
     * As {@link #cloneRange(Instruction, Instruction, int)} but returns a {@link ClonedRange} that also
     * carries each cloned branch/switch's targets by identity, and remaps every constant-pool reference
     * through {@code cpRemap} (old index &rarr; new index in {@code targetPool}) — used by cross-class
     * grafting to re-resolve operands into the target pool ({@code ldc} widens to {@code ldc_w} if a
     * remapped index exceeds 255).
     *
     * @param from        first instruction (inclusive)
     * @param to          last instruction (inclusive)
     * @param localOffset value added to every local-variable index
     * @param targetPool  the constant pool the clones will live in (null to keep this method's pool)
     * @param cpRemap     old cp index &rarr; new cp index, or null for no remap
     * @return the cloned range with identity-tracked targets
     */
    public ClonedRange cloneRangeWithTargets(Instruction from, Instruction to, int localOffset,
                                             ConstPool targetPool, java.util.function.IntUnaryOperator cpRemap) {
        List<Instruction> src = new ArrayList<>();
        boolean in = false;
        for (Instruction i : instructions.values()) {
            if (i == from) {
                in = true;
            }
            if (in) {
                src.add(i);
            }
            if (i == to && in) {
                break;
            }
        }
        if (src.isEmpty()) {
            throw new IllegalArgumentException("Range start is not part of this method");
        }
        if (src.get(src.size() - 1) != to) {
            throw new IllegalArgumentException("Range end does not follow start in this method");
        }

        Map<Instruction, Instruction> map = new IdentityHashMap<>();
        List<Instruction> clones = new ArrayList<>(src.size());
        ConstPool pool = targetPool != null ? targetPool : constPool;
        for (Instruction i : src) {
            Instruction c = cloneOne(i, localOffset, pool, cpRemap);
            map.put(i, c);
            clones.add(c);
        }

        // Lay out the block 0-based to compute correct internal relative offsets (for the list path).
        Map<Instruction, Integer> blockOff = new IdentityHashMap<>();
        int run = 0;
        for (Instruction c : clones) {
            blockOff.put(c, run);
            run += isSwitch(c) ? switchBaseLength(c) + paddingAfterOpcode(run) : c.getLength();
        }
        for (int k = 0; k < src.size(); k++) {
            Instruction s = src.get(k);
            Instruction c = clones.get(k);
            if (isBranch(s) || isSwitch(s)) {
                Instruction rebuilt = rebuildBranch(c, blockOff.get(c), cloneTargetsFor(s, map), blockOff);
                clones.set(k, rebuilt);
                map.put(s, rebuilt);
                blockOff.put(rebuilt, blockOff.get(c));
            } else {
                c.setOffset(blockOff.get(c));
            }
        }

        // Build the by-identity target table against the final clone objects.
        Map<Instruction, List<Instruction>> targets = new IdentityHashMap<>();
        for (Instruction s : src) {
            if (isBranch(s) || isSwitch(s)) {
                targets.put(map.get(s), cloneTargetsFor(s, map));
            }
        }
        return new ClonedRange(clones, targets);
    }

    /** Maps a source branch/switch's targets to their clones (internal) or null (external to the range). */
    private List<Instruction> cloneTargetsFor(Instruction src, Map<Instruction, Instruction> map) {
        List<Instruction> tg = branchTargets.get(src);
        if (tg == null) {
            tg = resolveTargets(src);
        }
        List<Instruction> out = new ArrayList<>();
        if (tg != null) {
            for (Instruction t : tg) {
                out.add(t == null ? null : map.get(t));
            }
        }
        return out;
    }

    /** Copies a single instruction for {@link #cloneRange}, shifting local indices and remapping cp refs. */
    private Instruction cloneOne(Instruction i, int localOffset, ConstPool pool,
                                 java.util.function.IntUnaryOperator cpRemap) {
        if (isBranch(i) || isSwitch(i)) {
            return structuralCopy(i);
        }
        if (cpRemap != null) {
            Instruction remapped = remapCpBearing(i, pool, cpRemap);
            if (remapped != null) {
                return remapped;
            }
        }
        if (localOffset != 0) {
            if (i instanceof ILoadInstruction) {
                return new ILoadInstruction(ILOAD.getCode(), 0, ((ILoadInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof LLoadInstruction) {
                return new LLoadInstruction(LLOAD.getCode(), 0, ((LLoadInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof FLoadInstruction) {
                return new FLoadInstruction(FLOAD.getCode(), 0, ((FLoadInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof DLoadInstruction) {
                return new DLoadInstruction(DLOAD.getCode(), 0, ((DLoadInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof ALoadInstruction) {
                return new ALoadInstruction(ALOAD.getCode(), 0, ((ALoadInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof IStoreInstruction) {
                return new IStoreInstruction(ISTORE.getCode(), 0, ((IStoreInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof LStoreInstruction) {
                return new LStoreInstruction(LSTORE.getCode(), 0, ((LStoreInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof FStoreInstruction) {
                return new FStoreInstruction(FSTORE.getCode(), 0, ((FStoreInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof DStoreInstruction) {
                return new DStoreInstruction(DSTORE.getCode(), 0, ((DStoreInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof AStoreInstruction) {
                return new AStoreInstruction(ASTORE.getCode(), 0, ((AStoreInstruction) i).getVarIndex() + localOffset);
            }
            if (i instanceof IIncInstruction) {
                IIncInstruction x = (IIncInstruction) i;
                return new IIncInstruction(IINC.getCode(), 0, x.getVarIndex() + localOffset, x.getConstValue());
            }
            if (i instanceof WideInstruction || i instanceof WideIIncInstruction || i instanceof RetInstruction) {
                throw new UnsupportedOperationException(
                        "cloneRange with a nonzero localOffset does not support wide/ret local ops: " + i);
            }
        }
        return genericCopy(i);
    }

    /** A fresh copy of a non-branch instruction via re-parse (operands preserved, offset 0). */
    private Instruction genericCopy(Instruction i) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                i.write(dos);
            }
            byte[] bytes = baos.toByteArray();
            return InstructionFactory.createInstruction(bytes[0] & 0xFF, 0, bytes, constPool);
        } catch (IOException e) {
            throw new RuntimeException("Failed to clone instruction: " + i, e);
        }
    }

    /** A structural copy of a branch/switch at offset 0 (relative offsets fixed later by relink). */
    private Instruction structuralCopy(Instruction i) {
        int op = i.getOpcode();
        if (i instanceof GotoInstruction) {
            return ((GotoInstruction) i).getType() == GotoInstruction.GotoType.GOTO_WIDE
                    ? new GotoInstruction(op, 0, 0) : new GotoInstruction(op, 0, (short) 0);
        }
        if (i instanceof ConditionalBranchInstruction) {
            return new ConditionalBranchInstruction(op, 0, (short) 0);
        }
        if (i instanceof JsrInstruction) {
            return new JsrInstruction(op, 0, 0);
        }
        if (i instanceof TableSwitchInstruction) {
            TableSwitchInstruction t = (TableSwitchInstruction) i;
            return new TableSwitchInstruction(op, 0, 0, 0, t.getLow(), t.getHigh(),
                    new LinkedHashMap<>(t.getJumpOffsets()));
        }
        LookupSwitchInstruction l = (LookupSwitchInstruction) i;
        return new LookupSwitchInstruction(op, 0, 0, 0, l.getNpairs(), new LinkedHashMap<>(l.getMatchOffsets()));
    }

    /**
     * Rebuilds a constant-pool-referencing instruction with its index remapped into {@code pool} via
     * {@code cpRemap}. Returns null for instructions that carry no cp reference. {@code ldc} widens to
     * {@code ldc_w} if the remapped index exceeds a byte.
     */
    private Instruction remapCpBearing(Instruction i, ConstPool pool, java.util.function.IntUnaryOperator cpRemap) {
        int op = i.getOpcode();
        if (i instanceof InvokeVirtualInstruction) {
            return new InvokeVirtualInstruction(pool, op, 0, cpRemap.applyAsInt(((InvokeVirtualInstruction) i).getMethodIndex()));
        }
        if (i instanceof InvokeSpecialInstruction) {
            return new InvokeSpecialInstruction(pool, op, 0, cpRemap.applyAsInt(((InvokeSpecialInstruction) i).getMethodIndex()));
        }
        if (i instanceof InvokeStaticInstruction) {
            return new InvokeStaticInstruction(pool, op, 0, cpRemap.applyAsInt(((InvokeStaticInstruction) i).getMethodIndex()));
        }
        if (i instanceof InvokeInterfaceInstruction) {
            InvokeInterfaceInstruction x = (InvokeInterfaceInstruction) i;
            return new InvokeInterfaceInstruction(pool, op, 0, cpRemap.applyAsInt(x.getMethodIndex()), x.getCount());
        }
        if (i instanceof GetFieldInstruction) {
            return new GetFieldInstruction(pool, op, 0, cpRemap.applyAsInt(((GetFieldInstruction) i).getFieldIndex()));
        }
        if (i instanceof PutFieldInstruction) {
            return new PutFieldInstruction(pool, op, 0, cpRemap.applyAsInt(((PutFieldInstruction) i).getFieldIndex()));
        }
        if (i instanceof NewInstruction) {
            return new NewInstruction(pool, op, 0, cpRemap.applyAsInt(((NewInstruction) i).getClassIndex()));
        }
        if (i instanceof CheckCastInstruction) {
            return new CheckCastInstruction(pool, op, 0, cpRemap.applyAsInt(((CheckCastInstruction) i).getClassIndex()));
        }
        if (i instanceof InstanceOfInstruction) {
            return new InstanceOfInstruction(pool, op, 0, cpRemap.applyAsInt(((InstanceOfInstruction) i).getClassIndex()));
        }
        if (i instanceof ANewArrayInstruction) {
            ANewArrayInstruction x = (ANewArrayInstruction) i;
            return new ANewArrayInstruction(pool, op, 0, cpRemap.applyAsInt(x.getClassIndex()), x.getCount());
        }
        if (i instanceof MultiANewArrayInstruction) {
            MultiANewArrayInstruction x = (MultiANewArrayInstruction) i;
            return new MultiANewArrayInstruction(pool, op, 0, cpRemap.applyAsInt(x.getClassIndex()), x.getDimensions());
        }
        if (i instanceof LdcInstruction) {
            int ni = cpRemap.applyAsInt(((LdcInstruction) i).getCpIndex());
            return ni > 0xFF ? new LdcWInstruction(pool, LDC_W.getCode(), 0, ni)
                    : new LdcInstruction(pool, op, 0, ni);
        }
        if (i instanceof LdcWInstruction) {
            return new LdcWInstruction(pool, op, 0, cpRemap.applyAsInt(((LdcWInstruction) i).getCpIndex()));
        }
        if (i instanceof Ldc2WInstruction) {
            return new Ldc2WInstruction(pool, op, 0, cpRemap.applyAsInt(((Ldc2WInstruction) i).getCpIndex()));
        }
        if (i instanceof InvokeDynamicInstruction) {
            return new InvokeDynamicInstruction(pool, op, 0, cpRemap.applyAsInt(((InvokeDynamicInstruction) i).getCpIndex()));
        }
        return null;
    }


    /**
     * Recomputes the layout of an edited instruction stream and writes back correct bytecode. Given
     * the new ordered instruction list (the result of an insert/remove/replace), this assigns fresh
     * offsets (recomputing switch padding), relinks every branch/switch to its target by identity (so
     * targets survive arbitrary shifts), remaps the exception table, drops now-stale debug tables, and
     * regenerates the StackMapTable. This is the single correct backend for all structural edits and
     * fixes the prior gap where branch/switch targets were not recomputed after a shift.
     *
     * @param newOrder the instructions in their new order; survivors keep identity, new ones are spliced in
     */
    private void relink(List<Instruction> newOrder) {
        // 1. Layout: provisional new offsets + per-instruction lengths (switch padding from new offset).
        Map<Instruction, Integer> newOff = layout(newOrder);
        Map<Integer, Instruction> newByOffset = new HashMap<>();
        for (Instruction i : newOrder) {
            newByOffset.put(newOff.get(i), i);
        }

        // 2. Freshly inserted branches (no target entry yet) resolve against the new contiguous layout.
        for (Instruction i : newOrder) {
            if ((isBranch(i) || isSwitch(i)) && !branchTargets.containsKey(i)) {
                List<Instruction> t = resolveTargetsUsing(i, newOff.get(i), newByOffset);
                if (t != null) {
                    branchTargets.put(i, t);
                }
            }
        }

        // 2b. Widen any branch whose span now exceeds the 16-bit range (goto->goto_w, conditional->
        // inverted-conditional + goto_w). Mutates newOrder/branchTargets; re-layout afterwards.
        newOrder = widenBranches(newOrder);
        newOff = layout(newOrder);
        int newCodeLength = newOff.isEmpty() ? 0
                : newOff.get(newOrder.get(newOrder.size() - 1))
                  + instructionLength(newOrder.get(newOrder.size() - 1), newOff.get(newOrder.get(newOrder.size() - 1)));

        // 3. Old->new offset map for attribute remap (survivors carry their pre-edit offset).
        Map<Integer, Integer> oldToNew = new HashMap<>();
        for (Instruction i : newOrder) {
            oldToNew.put(i.getOffset(), newOff.get(i));
        }
        oldToNew.put(bytecode.length, newCodeLength);

        // 4. Reconstruct branches/switches with new offsets+relatives; move others to their new offset.
        Map<Instruction, Instruction> remap = new IdentityHashMap<>();
        for (Instruction i : newOrder) {
            int off = newOff.get(i);
            if (isBranch(i) || isSwitch(i)) {
                remap.put(i, rebuildBranch(i, off, branchTargets.get(i), newOff));
            } else {
                i.setOffset(off);
                remap.put(i, i);
            }
        }

        // 5. Rebuild the instruction map and branch-target table with the remapped objects.
        Map<Integer, Instruction> rebuilt = new TreeMap<>();
        for (Instruction i : newOrder) {
            rebuilt.put(newOff.get(i), remap.get(i));
        }
        Map<Instruction, List<Instruction>> newTargets = new IdentityHashMap<>();
        for (Map.Entry<Instruction, List<Instruction>> e : branchTargets.entrySet()) {
            Instruction src = remap.get(e.getKey());
            if (src == null) {
                continue;
            }
            List<Instruction> mapped = new ArrayList<>(e.getValue().size());
            for (Instruction t : e.getValue()) {
                mapped.add(t == null ? null : remap.getOrDefault(t, t));
            }
            newTargets.put(src, mapped);
        }
        instructions.clear();
        instructions.putAll(rebuilt);
        branchTargets.clear();
        branchTargets.putAll(newTargets);

        // 6. Attributes + bytecode + frames.
        remapExceptionTable(oldToNew);
        dropTransientDebugAttributes();
        modified = true;
        ensureMaxLocals();
        rebuildBytecode();
        regenerateFrames();
    }

    /** Raises maxLocals to cover every local-variable slot the current instruction stream references. */
    private void ensureMaxLocals() {
        int needed = maxLocals;
        for (Instruction i : instructions.values()) {
            int slot = localSlot(i);
            if (slot >= 0) {
                needed = Math.max(needed, slot + localSlotSize(i));
            }
        }
        if (needed > maxLocals) {
            maxLocals = needed;
        }
        if (maxLocals > codeAttribute.getMaxLocals()) {
            codeAttribute.setMaxLocals(maxLocals);
        }
    }

    private static int localSlot(Instruction i) {
        if (i instanceof ILoadInstruction) return ((ILoadInstruction) i).getVarIndex();
        if (i instanceof LLoadInstruction) return ((LLoadInstruction) i).getVarIndex();
        if (i instanceof FLoadInstruction) return ((FLoadInstruction) i).getVarIndex();
        if (i instanceof DLoadInstruction) return ((DLoadInstruction) i).getVarIndex();
        if (i instanceof ALoadInstruction) return ((ALoadInstruction) i).getVarIndex();
        if (i instanceof IStoreInstruction) return ((IStoreInstruction) i).getVarIndex();
        if (i instanceof LStoreInstruction) return ((LStoreInstruction) i).getVarIndex();
        if (i instanceof FStoreInstruction) return ((FStoreInstruction) i).getVarIndex();
        if (i instanceof DStoreInstruction) return ((DStoreInstruction) i).getVarIndex();
        if (i instanceof AStoreInstruction) return ((AStoreInstruction) i).getVarIndex();
        if (i instanceof IIncInstruction) return ((IIncInstruction) i).getVarIndex();
        if (i instanceof RetInstruction) return ((RetInstruction) i).getVarIndex();
        return -1;
    }

    /** Assigns sequential offsets to {@code order}, recomputing switch padding from each offset. */
    private static Map<Instruction, Integer> layout(List<Instruction> order) {
        Map<Instruction, Integer> off = new IdentityHashMap<>();
        int run = 0;
        for (Instruction i : order) {
            off.put(i, run);
            run += instructionLength(i, run);
        }
        return off;
    }

    private static int instructionLength(Instruction i, int offset) {
        return isSwitch(i) ? switchBaseLength(i) + paddingAfterOpcode(offset) : i.getLength();
    }

    /**
     * Widens any branch whose target span exceeds the signed 16-bit range, iterating to a fixpoint
     * (widths only grow, so it converges): {@code goto -> goto_w}, and a conditional branch becomes an
     * inverted conditional skipping a {@code goto_w} to the original target. Switch offsets are 32-bit
     * and never overflow. {@code jsr} has no modeled wide form and raises an exception (obsolete since
     * Java 6). Mutates {@code order} and {@link #branchTargets}; returns the (possibly grown) list.
     */
    private List<Instruction> widenBranches(List<Instruction> order) {
        while (true) {
            Map<Instruction, Integer> off = layout(order);
            int idx = -1;
            for (int k = 0; k < order.size(); k++) {
                Instruction b = order.get(k);
                if (!isBranch(b)) {
                    continue;
                }
                List<Instruction> tg = branchTargets.get(b);
                if (tg == null || tg.isEmpty() || tg.get(0) == null) {
                    continue;
                }
                Integer to = off.get(tg.get(0));
                if (to == null) {
                    continue;
                }
                int rel = to - off.get(b);
                if (rel >= Short.MIN_VALUE && rel <= Short.MAX_VALUE) {
                    continue;
                }
                if (b instanceof JsrInstruction) {
                    throw new UnsupportedOperationException("branch widening for jsr (jsr_w) is unsupported");
                }
                if (b instanceof GotoInstruction
                        && ((GotoInstruction) b).getType() == GotoInstruction.GotoType.GOTO_WIDE) {
                    continue;
                }
                idx = k;
                break;
            }
            if (idx < 0) {
                return order;
            }
            Instruction b = order.get(idx);
            if (b instanceof GotoInstruction) {
                GotoInstruction wide = new GotoInstruction(GOTO_W.getCode(), 0, 0);
                order.set(idx, wide);
                branchTargets.put(wide, branchTargets.remove(b));
            } else {
                ConditionalBranchInstruction c = (ConditionalBranchInstruction) b;
                if (idx + 1 >= order.size()) {
                    throw new IllegalStateException("conditional branch at method end cannot be widened");
                }
                Instruction skip = order.get(idx + 1);
                List<Instruction> origTargets = branchTargets.remove(c);
                ConditionalBranchInstruction inverted =
                        new ConditionalBranchInstruction(invertConditionalOpcode(c.getType()), 0, (short) 0);
                GotoInstruction wide = new GotoInstruction(GOTO_W.getCode(), 0, 0);
                order.set(idx, inverted);
                order.add(idx + 1, wide);
                branchTargets.put(inverted, new ArrayList<>(Collections.singletonList(skip)));
                branchTargets.put(wide, origTargets);
            }
        }
    }

    private static int invertConditionalOpcode(ConditionalBranchInstruction.BranchType t) {
        ConditionalBranchInstruction.BranchType inverse;
        switch (t) {
            case IFEQ: inverse = ConditionalBranchInstruction.BranchType.IFNE; break;
            case IFNE: inverse = ConditionalBranchInstruction.BranchType.IFEQ; break;
            case IFLT: inverse = ConditionalBranchInstruction.BranchType.IFGE; break;
            case IFGE: inverse = ConditionalBranchInstruction.BranchType.IFLT; break;
            case IFGT: inverse = ConditionalBranchInstruction.BranchType.IFLE; break;
            case IFLE: inverse = ConditionalBranchInstruction.BranchType.IFGT; break;
            case IF_ICMPEQ: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPNE; break;
            case IF_ICMPNE: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPEQ; break;
            case IF_ICMPLT: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPGE; break;
            case IF_ICMPGE: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPLT; break;
            case IF_ICMPGT: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPLE; break;
            case IF_ICMPLE: inverse = ConditionalBranchInstruction.BranchType.IF_ICMPGT; break;
            case IF_ACMPEQ: inverse = ConditionalBranchInstruction.BranchType.IF_ACMPNE; break;
            case IF_ACMPNE: inverse = ConditionalBranchInstruction.BranchType.IF_ACMPEQ; break;
            case IFNULL: inverse = ConditionalBranchInstruction.BranchType.IFNONNULL; break;
            case IFNONNULL: inverse = ConditionalBranchInstruction.BranchType.IFNULL; break;
            default: throw new IllegalStateException("cannot invert conditional " + t);
        }
        return inverse.getOpcode();
    }

    private static int localSlotSize(Instruction i) {
        return (i instanceof LLoadInstruction || i instanceof LStoreInstruction
                || i instanceof DLoadInstruction || i instanceof DStoreInstruction) ? 2 : 1;
    }

    private static boolean isBranch(Instruction i) {
        return i instanceof GotoInstruction || i instanceof ConditionalBranchInstruction
                || i instanceof JsrInstruction;
    }

    private static boolean isSwitch(Instruction i) {
        return i instanceof TableSwitchInstruction || i instanceof LookupSwitchInstruction;
    }

    /** Padding bytes after a switch opcode so its operands align to a 4-byte boundary from method start. */
    private static int paddingAfterOpcode(int offset) {
        return (4 - ((offset + 1) % 4)) % 4;
    }

    /** A switch instruction's length excluding alignment padding. */
    private static int switchBaseLength(Instruction i) {
        if (i instanceof TableSwitchInstruction) {
            TableSwitchInstruction t = (TableSwitchInstruction) i;
            return 13 + (t.getHigh() - t.getLow() + 1) * 4;
        }
        LookupSwitchInstruction l = (LookupSwitchInstruction) i;
        return 9 + l.getNpairs() * 8;
    }

    private List<Instruction> resolveTargets(Instruction instr) {
        return resolveTargetsUsing(instr, instr.getOffset(), instructions);
    }

    private List<Instruction> resolveTargetsUsing(Instruction instr, int base, Map<Integer, Instruction> byOffset) {
        if (instr instanceof GotoInstruction) {
            GotoInstruction g = (GotoInstruction) instr;
            int rel = g.getType() == GotoInstruction.GotoType.GOTO_WIDE
                    ? g.getBranchOffsetWide() : g.getBranchOffset();
            return new ArrayList<>(Collections.singletonList(byOffset.get(base + rel)));
        }
        if (instr instanceof ConditionalBranchInstruction) {
            int rel = ((ConditionalBranchInstruction) instr).getBranchOffset();
            return new ArrayList<>(Collections.singletonList(byOffset.get(base + rel)));
        }
        if (instr instanceof JsrInstruction) {
            int rel = ((JsrInstruction) instr).getBranchOffset();
            return new ArrayList<>(Collections.singletonList(byOffset.get(base + rel)));
        }
        if (instr instanceof TableSwitchInstruction) {
            TableSwitchInstruction t = (TableSwitchInstruction) instr;
            List<Instruction> targets = new ArrayList<>();
            targets.add(byOffset.get(base + t.getDefaultOffset()));
            for (int key = t.getLow(); key <= t.getHigh(); key++) {
                int rel = t.getJumpOffsets().getOrDefault(key, t.getDefaultOffset());
                targets.add(byOffset.get(base + rel));
            }
            return targets;
        }
        if (instr instanceof LookupSwitchInstruction) {
            LookupSwitchInstruction l = (LookupSwitchInstruction) instr;
            List<Instruction> targets = new ArrayList<>();
            targets.add(byOffset.get(base + l.getDefaultOffset()));
            for (Map.Entry<Integer, Integer> e : l.getMatchOffsets().entrySet()) {
                targets.add(byOffset.get(base + e.getValue()));
            }
            return targets;
        }
        return null;
    }

    /** Rebuilds a branch/switch at a new offset with relative offsets recomputed from its targets. */
    private Instruction rebuildBranch(Instruction i, int newOff, List<Instruction> targets,
                                      Map<Instruction, Integer> newOffMap) {
        int op = i.getOpcode();
        if (i instanceof GotoInstruction) {
            Integer t = targetOffset(targets, 0, newOffMap);
            if (t == null) { i.setOffset(newOff); return i; }
            int rel = t - newOff;
            return ((GotoInstruction) i).getType() == GotoInstruction.GotoType.GOTO_WIDE
                    ? new GotoInstruction(op, newOff, rel) : new GotoInstruction(op, newOff, (short) rel);
        }
        if (i instanceof ConditionalBranchInstruction) {
            Integer t = targetOffset(targets, 0, newOffMap);
            if (t == null) { i.setOffset(newOff); return i; }
            return new ConditionalBranchInstruction(op, newOff, (short) (t - newOff));
        }
        if (i instanceof JsrInstruction) {
            Integer t = targetOffset(targets, 0, newOffMap);
            if (t == null) { i.setOffset(newOff); return i; }
            return new JsrInstruction(op, newOff, t - newOff);
        }
        if (i instanceof TableSwitchInstruction) {
            TableSwitchInstruction t = (TableSwitchInstruction) i;
            Integer def = targetOffset(targets, 0, newOffMap);
            if (def == null) { i.setOffset(newOff); return i; }
            Map<Integer, Integer> jumps = new LinkedHashMap<>();
            int idx = 1;
            for (int key = t.getLow(); key <= t.getHigh(); key++) {
                Integer to = targetOffset(targets, idx++, newOffMap);
                jumps.put(key, (to == null ? def : to) - newOff);
            }
            return new TableSwitchInstruction(op, newOff, paddingAfterOpcode(newOff),
                    def - newOff, t.getLow(), t.getHigh(), jumps);
        }
        LookupSwitchInstruction l = (LookupSwitchInstruction) i;
        Integer def = targetOffset(targets, 0, newOffMap);
        if (def == null) { i.setOffset(newOff); return i; }
        Map<Integer, Integer> matches = new LinkedHashMap<>();
        int idx = 1;
        for (Integer key : l.getMatchOffsets().keySet()) {
            Integer to = targetOffset(targets, idx++, newOffMap);
            matches.put(key, (to == null ? def : to) - newOff);
        }
        return new LookupSwitchInstruction(op, newOff, paddingAfterOpcode(newOff),
                def - newOff, l.getNpairs(), matches);
    }

    private static Integer targetOffset(List<Instruction> targets, int idx, Map<Instruction, Integer> newOffMap) {
        if (targets == null || idx >= targets.size() || targets.get(idx) == null) {
            return null;
        }
        return newOffMap.get(targets.get(idx));
    }

    /** Remaps exception-table PCs through the old->new offset map. catch_type is a cp index, unchanged. */
    private void remapExceptionTable(Map<Integer, Integer> oldToNew) {
        for (com.tonic.parser.attribute.table.ExceptionTableEntry ex : codeAttribute.getExceptionTable()) {
            ex.setStartPc(oldToNew.getOrDefault(ex.getStartPc(), ex.getStartPc()));
            ex.setEndPc(oldToNew.getOrDefault(ex.getEndPc(), ex.getEndPc()));
            ex.setHandlerPc(oldToNew.getOrDefault(ex.getHandlerPc(), ex.getHandlerPc()));
        }
    }

    /** Drops debug tables whose offsets are invalidated by a structural edit (entries are immutable). */
    private void dropTransientDebugAttributes() {
        codeAttribute.getAttributes().removeIf(a -> {
            String n = a.getClass().getSimpleName();
            return n.equals("LineNumberTableAttribute")
                    || n.equals("LocalVariableTableAttribute")
                    || n.equals("LocalVariableTypeTableAttribute");
        });
    }

    /** Regenerates the StackMapTable from the corrected bytecode (best-effort). */
    private void regenerateFrames() {
        try {
            new FrameGenerator(constPool).updateStackMapTable(methodEntry);
        } catch (Exception e) {
            Logger.error("Frame regeneration after edit failed: " + e.getMessage());
        }
    }

    /**
     * Rebuilds the bytecode array from the current instructions.
     */
    protected void rebuildBytecode() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (Instruction instr : instructions.values()) {
                Logger.info("Writing instruction at offset " + instr.getOffset() + ": " + instr);
                instr.write(dos);
            }
            dos.flush();
            bytecode = baos.toByteArray();
            codeAttribute.setCode(bytecode);
            Logger.info("Rebuilt bytecode: " + Arrays.toString(bytecode));
        } catch (IOException e) {
            Logger.error("Failed to rebuild bytecode: " + e.getMessage());
        }
    }

    /**
     * Analyzes the current bytecode to update maxStack and maxLocals.
     * This is a simplified analysis and may not cover all cases.
     */
    public void analyze() {
        int currentStack = 0;
        int peakStack = 0;
        int currentLocals = maxLocals;

        for (Instruction instr : instructions.values()) {
            int stackChange = instr.getStackChange();
            currentStack += stackChange;
            if (currentStack > peakStack) {
                peakStack = currentStack;
            }

            if (instr.getLocalChange() > 0) {
                currentLocals += instr.getLocalChange();
                if (currentLocals > maxLocals) {
                    maxLocals = currentLocals;
                }
            }
        }

        if (peakStack > maxStack) {
            maxStack = peakStack;
            codeAttribute.setMaxStack(maxStack);
        }
        if (currentLocals > maxLocals) {
            maxLocals = currentLocals;
            codeAttribute.setMaxLocals(maxLocals);
        }
    }

    /**
     * Writes the modified bytecode back to the MethodEntry.
     *
     * @throws IOException If an I/O error occurs.
     */
    public void write() throws IOException {
        analyze();
        rebuildBytecode();
    }

    /**
     * Checks if the CodeAttribute has a valid StackMapTable.
     *
     * @return true if a StackMapTable exists with frames
     */
    public boolean hasValidStackMapTable() {
        for (var attr : codeAttribute.getAttributes()) {
            if (attr instanceof StackMapTableAttribute) {
                StackMapTableAttribute smt = (StackMapTableAttribute) attr;
                return smt.getFrames() != null && !smt.getFrames().isEmpty();
            }
        }
        return false;
    }

    /**
     * Computes and updates the StackMapTable frames for this method.
     * This is an opt-in operation - call this after making bytecode modifications.
     * <p>
     * If the bytecode hasn't been modified and a valid StackMapTable exists,
     * this method preserves the existing frames.
     * <p>
     * Usage:
     * <pre>
     * CodeWriter codeWriter = new CodeWriter(method);
     * // ... insert instructions ...
     * codeWriter.write();
     * codeWriter.computeFrames(); // Regenerate StackMapTable
     * </pre>
     */
    public void computeFrames() {
        if (!modified && hasValidStackMapTable()) {
            Logger.info("Preserving existing StackMapTable (bytecode not modified)");
            return;
        }

        Logger.info("Computing StackMapTable frames for method: " + methodEntry.getName());
        FrameGenerator generator = new FrameGenerator(constPool);
        generator.updateStackMapTable(methodEntry);
        Logger.info("StackMapTable computation complete");
    }

    public void forceComputeFrames() {
        Logger.info("Force computing StackMapTable frames for method: " + methodEntry.getName());
        FrameGenerator generator = new FrameGenerator(constPool);
        generator.updateStackMapTable(methodEntry);
        Logger.info("StackMapTable computation complete");
    }

    /**
     * Returns the size of the bytecode array in bytes.
     *
     * @return bytecode size
     */
    public int getBytecodeSize()
    {
        return bytecode.length;
    }

    /**
     * Checks if the bytecode ends with a return instruction.
     *
     * @return true if ends with return, false otherwise
     */
    public boolean endsWithReturn()
    {
        if (bytecode.length < 1)
        {
            return false;
        }
        int lastOpcode = Byte.toUnsignedInt(bytecode[bytecode.length - 1]);
        return ReturnType.isReturnOpcode(lastOpcode);
    }

    /**
     * A factory for creating Instruction instances based on opcode.
     */
    public static class InstructionFactory {
        public static Instruction createInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool) {
            Logger.info("Parsing opcode: 0x" + Integer.toHexString(opcode) + " at offset: " + offset);
            switch (opcode) {
                case 0x00:
                    return new NopInstruction(opcode, offset);

                case 0x01:
                    return new AConstNullInstruction(opcode, offset);

                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05:
                case 0x06:
                case 0x07:
                case 0x08:
                    int iconstValue = (opcode == ICONST_M1.getCode()) ? -1 : (opcode - ICONST_0.getCode());
                    return new IConstInstruction(opcode, offset, iconstValue);

                case 0x09:
                case 0x0A:
                    long lconstValue = (opcode == LCONST_0.getCode()) ? 0L : 1L;
                    return new LConstInstruction(opcode, offset, lconstValue);

                case 0x0B:
                case 0x0C:
                case 0x0D:
                    float fconstValue = (opcode == FCONST_0.getCode()) ? 0.0f : ((opcode == FCONST_1.getCode()) ? 1.0f : 2.0f);
                    return new FConstInstruction(opcode, offset, fconstValue);

                case 0x0E:
                case 0x0F:
                    double dconstValue = (opcode == DCONST_0.getCode()) ? 0.0 : 1.0;
                    return new DConstInstruction(opcode, offset, dconstValue);

                case 0x10:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    byte bipushValue = bytecode[offset + 1];
                    return new BipushInstruction(opcode, offset, bipushValue);

                case 0x11:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    short sipushValue = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                    return new SipushInstruction(opcode, offset, sipushValue);

                case 0x12:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldcIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new LdcInstruction(constPool, opcode, offset, ldcIndex);

                case 0x13:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldcWIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    Logger.info("LDC_W: cpIndex = " + ldcWIndex);
                    return new LdcWInstruction(constPool, opcode, offset, ldcWIndex);

                case 0x14:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int ldc2WIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new Ldc2WInstruction(constPool, opcode, offset, ldc2WIndex);

                case 0x15:
                case 0x16:
                case 0x17:
                case 0x18:
                case 0x19:
                    return createLoadInstruction(opcode, offset, bytecode, getLoadInstructionName(opcode));

                case 0x1A:
                case 0x1B:
                case 0x1C:
                case 0x1D:
                    int iloadIndex = opcode - 0x1A;
                    return new ILoadInstruction(opcode, offset, iloadIndex);

                case 0x1E:
                case 0x1F:
                case 0x20:
                case 0x21:
                    int lloadIndex = opcode - 0x1E;
                    return new LLoadInstruction(opcode, offset, lloadIndex);

                case 0x22:
                case 0x23:
                case 0x24:
                case 0x25:
                    int floadIndex = opcode - 0x22;
                    return new FLoadInstruction(opcode, offset, floadIndex);

                case 0x26:
                case 0x27:
                case 0x28:
                case 0x29:
                    int dloadIndex = opcode - 0x26;
                    return new DLoadInstruction(opcode, offset, dloadIndex);

                case 0x2A:
                case 0x2B:
                case 0x2C:
                case 0x2D:
                    int aloadIndex = opcode - 0x2A;
                    return new ALoadInstruction(opcode, offset, aloadIndex);

                case 0x2E:
                    return new IALoadInstruction(opcode, offset);

                case 0x2F:
                    return new LALoadInstruction(opcode, offset);

                case 0x30:
                    return new FALoadInstruction(opcode, offset);

                case 0x31:
                    return new DALoadInstruction(opcode, offset);

                case 0x32:
                    return new AALoadInstruction(opcode, offset);

                case 0x33:
                    return new BALOADInstruction(opcode, offset);

                case 0x34:
                    return new CALoadInstruction(opcode, offset);

                case 0x35:
                    return new SALoadInstruction(opcode, offset);

                case 0x36:
                case 0x37:
                case 0x38:
                case 0x39:
                case 0x3A:
                    return createStoreInstruction(opcode, offset, bytecode, getStoreInstructionName(opcode));

                case 0x3B:
                case 0x3C:
                case 0x3D:
                case 0x3E:
                    int istoreIndex = opcode - 0x3B;
                    return new IStoreInstruction(opcode, offset, istoreIndex);

                case 0x3F:
                case 0x40:
                case 0x41:
                case 0x42:
                    int lstoreIndex = opcode - 0x3F;
                    return new LStoreInstruction(opcode, offset, lstoreIndex);

                case 0x43:
                case 0x44:
                case 0x45:
                case 0x46:
                    int fstoreIndex = opcode - 0x43;
                    return new FStoreInstruction(opcode, offset, fstoreIndex);

                case 0x47:
                case 0x48:
                case 0x49:
                case 0x4A:
                    int dstoreIndex = opcode - 0x47;
                    return new DStoreInstruction(opcode, offset, dstoreIndex);

                case 0x4B:
                case 0x4C:
                case 0x4D:
                case 0x4E:
                    int astoreIndex = opcode - 0x4B;
                    return new AStoreInstruction(opcode, offset, astoreIndex);

                case 0x4F:
                    return new IAStoreInstruction(opcode, offset);

                case 0x50:
                    return new LAStoreInstruction(opcode, offset);

                case 0x51:
                    return new FAStoreInstruction(opcode, offset);

                case 0x52:
                    return new DAStoreInstruction(opcode, offset);

                case 0x53:
                    return new AAStoreInstruction(opcode, offset);

                case 0x54:
                    return new BAStoreInstruction(opcode, offset);

                case 0x55:
                    return new CAStoreInstruction(opcode, offset);

                case 0x56:
                    return new SAStoreInstruction(opcode, offset);

                case 0x57:
                    return new PopInstruction(opcode, offset);

                case 0x58:
                    return new Pop2Instruction(opcode, offset);

                case 0x59:
                case 0x5A:
                case 0x5B:
                case 0x5C:
                case 0x5D:
                case 0x5E:
                    return new DupInstruction(opcode, offset);

                case 0x5F:
                    return new SwapInstruction(opcode, offset);

                case 0x60:
                case 0x61:
                case 0x62:
                case 0x63:
                case 0x64:
                case 0x65:
                case 0x66:
                case 0x67:
                case 0x68:
                case 0x69:
                case 0x6A:
                case 0x6B:
                case 0x6C:
                case 0x6D:
                case 0x6E:
                case 0x6F:
                case 0x70:
                case 0x71:
                case 0x72:
                case 0x73:
                    return new ArithmeticInstruction(opcode, offset);

                case 0x74:
                    return new INegInstruction(opcode, offset);

                case 0x75:
                    return new LNegInstruction(opcode, offset);

                case 0x76:
                    return new FNegInstruction(opcode, offset);

                case 0x77:
                    return new DNegInstruction(opcode, offset);

                case 0x78:
                case 0x79:
                case 0x7A:
                case 0x7B:
                case 0x7C:
                case 0x7D:
                    return new ArithmeticShiftInstruction(opcode, offset);

                case 0x7E:
                case 0x7F:
                    return new IAndInstruction(opcode, offset);

                case 0x80:
                case 0x81:
                    return new IOrInstruction(opcode, offset);

                case 0x82:
                case 0x83:
                    return new IXorInstruction(opcode, offset);

                case 0x84:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int iincVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    int iincConst = bytecode[offset + 2];
                    return new IIncInstruction(opcode, offset, iincVarIndex, iincConst);

                case 0x85:
                    return new I2LInstruction(opcode, offset);

                case 0x86:
                case 0x87:
                case 0x88:
                case 0x89:
                case 0x8A:
                case 0x8B:
                case 0x8C:
                case 0x8D:
                case 0x8E:
                case 0x8F:
                case 0x90:
                    return new ConversionInstruction(opcode, offset);

                case 0x91:
                case 0x92:
                case 0x93:
                    return new NarrowingConversionInstruction(opcode, offset);

                case 0x94:
                case 0x95:
                case 0x96:
                case 0x97:
                case 0x98:
                    return new CompareInstruction(opcode, offset);

                case 0x99:
                case 0x9A:
                case 0x9B:
                case 0x9C:
                case 0x9D:
                case 0x9E:
                case 0x9F:
                case 0xA0:
                case 0xA1:
                case 0xA2:
                case 0xA3:
                case 0xA4:
                case 0xA5:
                case 0xA6:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    short branchOffsetCond = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                    return new ConditionalBranchInstruction(opcode, offset, branchOffsetCond);

                case 0xA7:
                case 0xC8:
                    return parseGotoInstruction(opcode, offset, bytecode);

                case 0xA8:
                case 0xC9:
                    return parseJsrInstruction(opcode, offset, bytecode);

                case 0xA9:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int retVarIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new RetInstruction(opcode, offset, retVarIndex);

                case 0xB6:
                case 0xB7:
                case 0xB8:
                    return parseInvokeInstruction(opcode, offset, bytecode, constPool);

                case 0xB9:
                    if (offset + 4 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int invokeInterfaceIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    int count = Byte.toUnsignedInt(bytecode[offset + 3]);
                    return new InvokeInterfaceInstruction(constPool, opcode, offset, invokeInterfaceIndex, count);

                case 0xBA:
                    if (offset + 4 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int invokedynamicCpIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new InvokeDynamicInstruction(constPool, opcode, offset, invokedynamicCpIndex);

                case 0xB2:
                case 0xB4:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int fieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new GetFieldInstruction(constPool, opcode, offset, fieldRefIndex);

                case 0xB3:
                case 0xB5:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int putFieldRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new PutFieldInstruction(constPool, opcode, offset, putFieldRefIndex);

                case 0xBB:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int newClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new NewInstruction(constPool, opcode, offset, newClassIndex);

                case 0xBC:
                    if (offset + 1 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int newarrayTypeCode = Byte.toUnsignedInt(bytecode[offset + 1]);
                    return new NewArrayInstruction(opcode, offset, newarrayTypeCode, 1);

                case 0xBD:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int anewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new ANewArrayInstruction(constPool, opcode, offset, anewarrayClassIndex, 2);

                case 0xBE:
                    return new ArrayLengthInstruction(opcode, offset);

                case 0xBF:
                    return new ATHROWInstruction(opcode, offset);

                case 0xC0:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int typeIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new CheckCastInstruction(constPool, opcode, offset, typeIndex);

                case 0xC5:
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int multianewarrayClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    int dimensions = Byte.toUnsignedInt(bytecode[offset + 3]);
                    return new MultiANewArrayInstruction(constPool, opcode, offset, multianewarrayClassIndex, dimensions);

                case 0xAB:
                    return parseLookupSwitchInstruction(opcode, offset, bytecode);

                case 0xAA:
                    return parseTableSwitchInstruction(opcode, offset, bytecode);

                case 0xC4:
                    return parseWideInstruction(opcode, offset, bytecode);

                case 0xAC:
                case 0xAD:
                case 0xAE:
                case 0xAF:
                case 0xB0:
                case 0xB1:
                    return new ReturnInstruction(opcode, offset);

                case 0xC1:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int instanceOfClassIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
                    return new InstanceOfInstruction(constPool, opcode, offset, instanceOfClassIndex);

                case 0xC3:
                    return new MonitorExitInstruction(opcode, offset);

                case 0xC2:
                    return new MonitorEnterInstruction(opcode, offset);

                case 0xC6:
                case 0xC7:
                    if (offset + 2 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    short branchOffsetNull = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                    return new ConditionalBranchInstruction(opcode, offset, branchOffsetNull);

                default:
                    return new UnknownInstruction(opcode, offset, 1);
            }
        }

        /**
         * Helper method to get the instruction name based on opcode.
         *
         * @param opcode The opcode.
         * @return The name of the load instruction.
         */
        private static String getLoadInstructionName(int opcode) {
            if (opcode == ILOAD.getCode()) return "ILOAD";
            if (opcode == LLOAD.getCode()) return "LLOAD";
            if (opcode == FLOAD.getCode()) return "FLOAD";
            if (opcode == DLOAD.getCode()) return "DLOAD";
            if (opcode == ALOAD.getCode()) return "ALOAD";
            return "UNKNOWN_LOAD";
        }

        /**
         * Helper method to get the instruction name based on opcode.
         *
         * @param opcode The opcode.
         * @return The name of the store instruction.
         */
        private static String getStoreInstructionName(int opcode) {
            if (opcode == ISTORE.getCode()) return "ISTORE";
            if (opcode == LSTORE.getCode()) return "LSTORE";
            if (opcode == FSTORE.getCode()) return "FSTORE";
            if (opcode == DSTORE.getCode()) return "DSTORE";
            if (opcode == ASTORE.getCode()) return "ASTORE";
            return "UNKNOWN_STORE";
        }

        /**
         * Helper method to create Load Instructions.
         *
         * @param opcode           The opcode of the load instruction.
         * @param offset           The bytecode offset.
         * @param bytecode         The entire bytecode array.
         * @param instructionName  The name of the instruction (e.g., "ILOAD").
         * @return A LoadInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction createLoadInstruction(int opcode, int offset, byte[] bytecode, String instructionName) {
            int operandBytes = 1;
            if (offset + operandBytes >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
            switch (instructionName) {
                case "ILOAD":
                    return new ILoadInstruction(opcode, offset, varIndex);
                case "LLOAD":
                    return new LLoadInstruction(opcode, offset, varIndex);
                case "FLOAD":
                    return new FLoadInstruction(opcode, offset, varIndex);
                case "DLOAD":
                    return new DLoadInstruction(opcode, offset, varIndex);
                case "ALOAD":
                    return new ALoadInstruction(opcode, offset, varIndex);
                default:
                    return new UnknownInstruction(opcode, offset, operandBytes + 1);
            }
        }

        /**
         * Helper method to create Store Instructions.
         *
         * @param opcode           The opcode of the store instruction.
         * @param offset           The bytecode offset.
         * @param bytecode         The entire bytecode array.
         * @param instructionName  The name of the instruction (e.g., "ISTORE").
         * @return A StoreInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction createStoreInstruction(int opcode, int offset, byte[] bytecode, String instructionName) {
            int operandBytes = 1;
            if (offset + operandBytes >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int varIndex = Byte.toUnsignedInt(bytecode[offset + 1]);
            switch (instructionName) {
                case "ISTORE":
                    return new IStoreInstruction(opcode, offset, varIndex);
                case "LSTORE":
                    return new LStoreInstruction(opcode, offset, varIndex);
                case "FSTORE":
                    return new FStoreInstruction(opcode, offset, varIndex);
                case "DSTORE":
                    return new DStoreInstruction(opcode, offset, varIndex);
                case "ASTORE":
                    return new AStoreInstruction(opcode, offset, varIndex);
                default:
                    return new UnknownInstruction(opcode, offset, operandBytes + 1);
            }
        }

        /**
         * Parses a GOTO instruction (0xA7) or GOTO_W (0xC8).
         *
         * @param opcode   The opcode of the GOTO instruction.
         * @param offset   The bytecode offset.
         * @param bytecode The entire bytecode array.
         * @return A GotoInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction parseGotoInstruction(int opcode, int offset, byte[] bytecode) {
            if (opcode == GOTO.getCode()) {
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                short branchOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new GotoInstruction(opcode, offset, branchOffset);
            } else if (opcode == GOTO_W.getCode()) {
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                int branchOffset = (bytecode[offset + 1] << 24) | ((bytecode[offset + 2] & 0xFF) << 16) |
                        ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
                return new GotoInstruction(opcode, offset, branchOffset);
            } else {
                return new UnknownInstruction(opcode, offset, 1);
            }
        }

        /**
         * Parses a JSR instruction (0xA8) or JSR_W (0xC9).
         *
         * @param opcode   The opcode of the JSR instruction.
         * @param offset   The bytecode offset.
         * @param bytecode The entire bytecode array.
         * @return A JsrInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction parseJsrInstruction(int opcode, int offset, byte[] bytecode) {
            if (opcode == JSR.getCode()) {
                if (offset + 2 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                short jsrOffset = (short) (((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF));
                return new JsrInstruction(opcode, offset, jsrOffset);
            } else if (opcode == JSR_W.getCode()) {
                if (offset + 4 >= bytecode.length) {
                    return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                }
                int jsrOffset = (bytecode[offset + 1] << 24) | ((bytecode[offset + 2] & 0xFF) << 16) |
                        ((bytecode[offset + 3] & 0xFF) << 8) | (bytecode[offset + 4] & 0xFF);
                return new JsrInstruction(opcode, offset, jsrOffset);
            } else {
                return new UnknownInstruction(opcode, offset, 1);
            }
        }

        /**
         * Parses INVOKEVIRTUAL, INVOKESPECIAL, and INVOKESTATIC instructions (0xB6 - 0xB8).
         *
         * @param opcode     The opcode of the invoke instruction.
         * @param offset     The bytecode offset.
         * @param bytecode   The entire bytecode array.
         * @param constPool  The constant pool associated with the class.
         * @return The corresponding InvokeInstruction instance.
         */
        private static Instruction parseInvokeInstruction(int opcode, int offset, byte[] bytecode, ConstPool constPool) {
            if (offset + 2 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }
            int methodRefIndex = ((bytecode[offset + 1] & 0xFF) << 8) | (bytecode[offset + 2] & 0xFF);
            if (opcode == INVOKEVIRTUAL.getCode()) {
                return new InvokeVirtualInstruction(constPool, opcode, offset, methodRefIndex);
            } else if (opcode == INVOKESPECIAL.getCode()) {
                return new InvokeSpecialInstruction(constPool, opcode, offset, methodRefIndex);
            } else if (opcode == INVOKESTATIC.getCode()) {
                return new InvokeStaticInstruction(constPool, opcode, offset, methodRefIndex);
            }
            return new UnknownInstruction(opcode, offset, 3);
        }

        /**
         * Parses a LOOKUPSWITCH instruction starting at the given offset.
         *
         * @param opcode    The opcode of the instruction (0xAB).
         * @param offset    The bytecode offset of the instruction.
         * @param bytecode  The entire bytecode array.
         * @return A LookupSwitchInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction parseLookupSwitchInstruction(int opcode, int offset, byte[] bytecode) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int defaultOffsetPos = offset + 1 + padding;
            int npairsPos = defaultOffsetPos + 4;

            if (npairsPos + 4 > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            int defaultOffset = ((bytecode[defaultOffsetPos] & 0xFF) << 24) |
                    ((bytecode[defaultOffsetPos + 1] & 0xFF) << 16) |
                    ((bytecode[defaultOffsetPos + 2] & 0xFF) << 8) |
                    (bytecode[defaultOffsetPos + 3] & 0xFF);

            int npairs = ((bytecode[npairsPos] & 0xFF) << 24) |
                    ((bytecode[npairsPos + 1] & 0xFF) << 16) |
                    ((bytecode[npairsPos + 2] & 0xFF) << 8) |
                    (bytecode[npairsPos + 3] & 0xFF);

            int pairsStart = npairsPos + 4;
            int pairsLength = npairs * 8;

            if (pairsStart + pairsLength > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            Map<Integer, Integer> matchOffsets = new LinkedHashMap<>();
            for (int i = 0; i < npairs; i++) {
                int keyPos = pairsStart + i * 8;
                int jumpOffsetPos = keyPos + 4;

                int key = ((bytecode[keyPos] & 0xFF) << 24) |
                        ((bytecode[keyPos + 1] & 0xFF) << 16) |
                        ((bytecode[keyPos + 2] & 0xFF) << 8) |
                        (bytecode[keyPos + 3] & 0xFF);

                int jumpOffset = ((bytecode[jumpOffsetPos] & 0xFF) << 24) |
                        ((bytecode[jumpOffsetPos + 1] & 0xFF) << 16) |
                        ((bytecode[jumpOffsetPos + 2] & 0xFF) << 8) |
                        (bytecode[jumpOffsetPos + 3] & 0xFF);

                matchOffsets.put(key, jumpOffset);
            }

            return new LookupSwitchInstruction(opcode, offset, padding, defaultOffset, npairs, matchOffsets);
        }

        /**
         * Parses a TABLESWITCH instruction starting at the given offset.
         *
         * @param opcode    The opcode of the instruction (0xAA).
         * @param offset    The bytecode offset of the instruction.
         * @param bytecode  The entire bytecode array.
         * @return A TableSwitchInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction parseTableSwitchInstruction(int opcode, int offset, byte[] bytecode) {
            int padding = (4 - ((offset + 1) % 4)) % 4;
            int defaultOffsetPos = offset + 1 + padding;
            int lowPos = defaultOffsetPos + 4;
            int highPos = lowPos + 4;

            if (highPos + 4 > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            int defaultOffset = ((bytecode[defaultOffsetPos] & 0xFF) << 24) |
                    ((bytecode[defaultOffsetPos + 1] & 0xFF) << 16) |
                    ((bytecode[defaultOffsetPos + 2] & 0xFF) << 8) |
                    (bytecode[defaultOffsetPos + 3] & 0xFF);

            int low = ((bytecode[lowPos] & 0xFF) << 24) |
                    ((bytecode[lowPos + 1] & 0xFF) << 16) |
                    ((bytecode[lowPos + 2] & 0xFF) << 8) |
                    (bytecode[lowPos + 3] & 0xFF);

            int high = ((bytecode[highPos] & 0xFF) << 24) |
                    ((bytecode[highPos + 1] & 0xFF) << 16) |
                    ((bytecode[highPos + 2] & 0xFF) << 8) |
                    (bytecode[highPos + 3] & 0xFF);

            int jumpOffsetsStart = highPos + 4;
            int jumpOffsetsLength = (high - low + 1) * 4;

            if (jumpOffsetsStart + jumpOffsetsLength > bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            Map<Integer, Integer> jumpOffsets = new LinkedHashMap<>();
            for (int i = 0; i <= high - low; i++) {
                int jumpOffsetPos = jumpOffsetsStart + i * 4;
                int jumpOffset = ((bytecode[jumpOffsetPos] & 0xFF) << 24) |
                        ((bytecode[jumpOffsetPos + 1] & 0xFF) << 16) |
                        ((bytecode[jumpOffsetPos + 2] & 0xFF) << 8) |
                        (bytecode[jumpOffsetPos + 3] & 0xFF);
                int key = low + i;
                jumpOffsets.put(key, jumpOffset);
            }

            return new TableSwitchInstruction(opcode, offset, padding, defaultOffset, low, high, jumpOffsets);
        }

        /**
         * Parses a WIDE instruction starting at the given offset.
         *
         * @param opcode    The opcode of the instruction (0xC4).
         * @param offset    The bytecode offset of the instruction.
         * @param bytecode  The entire bytecode array.
         * @return A WideInstruction instance or UnknownInstruction if malformed.
         */
        private static Instruction parseWideInstruction(int opcode, int offset, byte[] bytecode) {
            if (offset + 1 >= bytecode.length) {
                return new UnknownInstruction(opcode, offset, bytecode.length - offset);
            }

            int modifiedOpcodeCode = Byte.toUnsignedInt(bytecode[offset + 1]);

            switch (modifiedOpcodeCode) {
                case 0x15:
                case 0x16:
                case 0x17:
                case 0x18:
                case 0x19:
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int varIndexLoad = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    switch (modifiedOpcodeCode) {
                        case 0x15:
                            return new ILoadInstruction(opcode, offset, varIndexLoad);
                        case 0x16:
                            return new LLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x17:
                            return new FLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x18:
                            return new DLoadInstruction(opcode, offset, varIndexLoad);
                        case 0x19:
                            return new ALoadInstruction(opcode, offset, varIndexLoad);
                        default:
                            return new UnknownInstruction(opcode, offset, 4);
                    }

                case 0x36:
                case 0x37:
                case 0x38:
                case 0x39:
                case 0x3A:
                    if (offset + 3 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int varIndexStore = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    switch (modifiedOpcodeCode) {
                        case 0x36:
                            return new IStoreInstruction(opcode, offset, varIndexStore);
                        case 0x37:
                            return new LStoreInstruction(opcode, offset, varIndexStore);
                        case 0x38:
                            return new FStoreInstruction(opcode, offset, varIndexStore);
                        case 0x39:
                            return new DStoreInstruction(opcode, offset, varIndexStore);
                        case 0x3A:
                            return new AStoreInstruction(opcode, offset, varIndexStore);
                        default:
                            return new UnknownInstruction(opcode, offset, 4);
                    }

                case 0x84:
                    if (offset + 5 >= bytecode.length) {
                        return new UnknownInstruction(opcode, offset, bytecode.length - offset);
                    }
                    int wideVarIndex = ((bytecode[offset + 2] & 0xFF) << 8) | (bytecode[offset + 3] & 0xFF);
                    int wideConstValue = ((bytecode[offset + 4] & 0xFF) << 8) | (bytecode[offset + 5] & 0xFF);
                    return new WideIIncInstruction(opcode, offset, wideVarIndex, wideConstValue);

                default:
                    return new UnknownInstruction(opcode, offset, 2);
            }
        }
    }

    /**
     * Inserts an INVOKEVIRTUAL instruction at the specified bytecode offset.
     *
     * @param offset          The bytecode offset to insert the instruction at.
     * @param methodRefIndex  The index into the constant pool for the method reference.
     */
    public InvokeVirtualInstruction insertInvokeVirtual(int offset, int methodRefIndex) {
        InvokeVirtualInstruction instr = new InvokeVirtualInstruction(constPool, INVOKEVIRTUAL.getCode(), offset, methodRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKESPECIAL instruction at the specified bytecode offset.
     *
     * @param offset          The bytecode offset to insert the instruction at.
     * @param methodRefIndex  The index into the constant pool for the method reference.
     */
    public InvokeSpecialInstruction insertInvokeSpecial(int offset, int methodRefIndex) {
        InvokeSpecialInstruction instr = new InvokeSpecialInstruction(constPool, INVOKESPECIAL.getCode(), offset, methodRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKESTATIC instruction at the specified bytecode offset.
     *
     * @param offset          The bytecode offset to insert the instruction at.
     * @param methodRefIndex  The index into the constant pool for the method reference.
     */
    public InvokeStaticInstruction insertInvokeStatic(int offset, int methodRefIndex) {
        InvokeStaticInstruction instr = new InvokeStaticInstruction(constPool, INVOKESTATIC.getCode(), offset, methodRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKEINTERFACE instruction at the specified bytecode offset.
     *
     * @param offset                  The bytecode offset to insert the instruction at.
     * @param interfaceMethodRefIndex The index into the constant pool for the interface method reference.
     * @param count                   The count of arguments for the interface method.
     */
    public InvokeInterfaceInstruction insertInvokeInterface(int offset, int interfaceMethodRefIndex, int count) {
        InvokeInterfaceInstruction instr = new InvokeInterfaceInstruction(constPool, INVOKEINTERFACE.getCode(), offset, interfaceMethodRefIndex, count);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an INVOKEDYNAMIC instruction at the specified bytecode offset.
     *
     * @param offset  The bytecode offset to insert the instruction at.
     * @param cpIndex The constant pool index to the CONSTANT_InvokeDynamic_info entry.
     */
    public InvokeDynamicInstruction insertInvokeDynamic(int offset, int cpIndex) {
        InvokeDynamicInstruction instr = new InvokeDynamicInstruction(constPool, INVOKEDYNAMIC.getCode(), offset, cpIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ALOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public ALoadInstruction insertALoad(int offset, int index) {
        ALoadInstruction instr = new ALoadInstruction(ALOAD.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ASTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public AStoreInstruction insertAStore(int offset, int index) {
        AStoreInstruction instr = new AStoreInstruction(ASTORE.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a GETSTATIC instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public GetFieldInstruction insertGetStatic(int offset, int fieldRefIndex) {
        GetFieldInstruction instr = new GetFieldInstruction(constPool, GETSTATIC.getCode(), offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a GETFIELD instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public GetFieldInstruction insertGetField(int offset, int fieldRefIndex) {
        GetFieldInstruction instr = new GetFieldInstruction(constPool, GETFIELD.getCode(), offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a PUTSTATIC instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public PutFieldInstruction insertPutStatic(int offset, int fieldRefIndex) {
        PutFieldInstruction instr = new PutFieldInstruction(constPool, PUTSTATIC.getCode(), offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a PUTFIELD instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param fieldRefIndex The index into the constant pool for the field reference.
     */
    public PutFieldInstruction insertPutField(int offset, int fieldRefIndex) {
        PutFieldInstruction instr = new PutFieldInstruction(constPool, PUTFIELD.getCode(), offset, fieldRefIndex);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ILOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public ILoadInstruction insertILoad(int offset, int index) {
        ILoadInstruction instr = new ILoadInstruction(ILOAD.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an ISTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public IStoreInstruction insertIStore(int offset, int index) {
        IStoreInstruction instr = new IStoreInstruction(ISTORE.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an LLOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public LLoadInstruction insertLLoad(int offset, int index) {
        LLoadInstruction instr = new LLoadInstruction(LLOAD.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an LSTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public LStoreInstruction insertLStore(int offset, int index) {
        LStoreInstruction instr = new LStoreInstruction(LSTORE.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an FLOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public FLoadInstruction insertFLoad(int offset, int index) {
        FLoadInstruction instr = new FLoadInstruction(FLOAD.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an FSTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public FStoreInstruction insertFStore(int offset, int index) {
        FStoreInstruction instr = new FStoreInstruction(FSTORE.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a DLOAD instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to load from.
     */
    public DLoadInstruction insertDLoad(int offset, int index) {
        DLoadInstruction instr = new DLoadInstruction(DLOAD.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a DSTORE instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param index  The local variable index to store into.
     */
    public DStoreInstruction insertDStore(int offset, int index) {
        DStoreInstruction instr = new DStoreInstruction(DSTORE.getCode(), offset, index);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts an IINC instruction at the specified bytecode offset.
     *
     * @param offset The bytecode offset to insert the instruction at.
     * @param varIndex The local variable index to increment.
     * @param increment The constant by which to increment the variable.
     */
    public IIncInstruction insertIInc(int offset, int varIndex, int increment) {
        IIncInstruction instr = new IIncInstruction(IINC.getCode(), offset, varIndex, increment);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a GOTO instruction at the specified bytecode offset with a signed short branch offset.
     *
     * @param offset       The bytecode offset to insert the GOTO instruction at.
     * @param branchOffset The signed short branch offset relative to the GOTO instruction.
     */
    public GotoInstruction insertGoto(int offset, short branchOffset) {
        GotoInstruction gotoInstr = new GotoInstruction(GOTO.getCode(), offset, branchOffset);
        insertInstruction(offset, gotoInstr);
        return gotoInstr;
    }

    /**
     * Inserts a GOTO_W instruction at the specified bytecode offset with a 32-bit branch offset.
     *
     * @param offset       The bytecode offset to insert the GOTO_W instruction at.
     * @param branchOffset The signed 32-bit branch offset relative to the GOTO_W instruction.
     * @throws IllegalArgumentException If the specified offset is invalid.
     */
    public GotoInstruction insertGotoW(int offset, int branchOffset) {
        GotoInstruction gotoWInstr = new GotoInstruction(GOTO_W.getCode(), offset, branchOffset);
        insertInstruction(offset, gotoWInstr);
        return gotoWInstr;
    }

    /**
     * Inserts a NEW instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param classRefIndex The index into the constant pool for the class reference.
     */
    public NewInstruction insertNew(int offset, int classRefIndex) {
        NewInstruction newInstr = new NewInstruction(constPool, NEW.getCode(), offset, classRefIndex);
        insertInstruction(offset, newInstr);
        return newInstr;
    }

    /**
     * Inserts an LDC instruction at the specified bytecode offset.
     *
     * @param offset             The bytecode offset to insert the instruction at.
     * @param constantPoolIndex  The index into the constant pool for the constant.
     */
    public LdcInstruction insertLDC(int offset, int constantPoolIndex) {
        LdcInstruction ldcInstr = new LdcInstruction(constPool, LDC.getCode(), offset, constantPoolIndex);
        insertInstruction(offset, ldcInstr);
        return ldcInstr;
    }

    /**
     * Inserts an LDC_W instruction at the specified bytecode offset.
     *
     * @param offset            The bytecode offset to insert the instruction at.
     * @param constantPoolIndex The 2-byte index into the constant pool for the constant.
     */
    public LdcWInstruction insertLDCW(int offset, int constantPoolIndex) {
        Logger.info("Inserting LDC_W at offset " + offset + " with index " + constantPoolIndex);
        LdcWInstruction ldcWInstr = new LdcWInstruction(constPool, LDC_W.getCode(), offset, constantPoolIndex);
        insertInstruction(offset, ldcWInstr);
        return ldcWInstr;
    }

    /**
     * Inserts a TABLESWITCH instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param padding       The number of padding bytes (0-3 for 4-byte alignment).
     * @param defaultOffset The branch target offset if no key matches.
     * @param low           The lowest key value.
     * @param high          The highest key value.
     * @param jumpOffsets   The map of key to branch target offsets.
     * @return The created TableSwitchInstruction.
     */
    public TableSwitchInstruction insertTableSwitch(int offset, int padding,
                                                     int defaultOffset, int low, int high, Map<Integer, Integer> jumpOffsets) {
        TableSwitchInstruction instr = new TableSwitchInstruction(
            TABLESWITCH.getCode(), offset, padding, defaultOffset, low, high, jumpOffsets);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Inserts a LOOKUPSWITCH instruction at the specified bytecode offset.
     *
     * @param offset        The bytecode offset to insert the instruction at.
     * @param padding       The number of padding bytes (0-3 for 4-byte alignment).
     * @param defaultOffset The default branch target offset.
     * @param npairs        The number of key-offset pairs.
     * @param matchOffsets  The map of keys to branch target offsets.
     * @return The created LookupSwitchInstruction.
     */
    public LookupSwitchInstruction insertLookupSwitch(int offset, int padding,
                                                       int defaultOffset, int npairs, Map<Integer, Integer> matchOffsets) {
        LookupSwitchInstruction instr = new LookupSwitchInstruction(
            LOOKUPSWITCH.getCode(), offset, padding, defaultOffset, npairs, matchOffsets);
        insertInstruction(offset, instr);
        return instr;
    }

    /**
     * Appends a new instruction to the end of the bytecode.
     *
     * @param newInstr The Instruction to append.
     */
    public void appendInstruction(Instruction newInstr) {
        int appendOffset = bytecode.length;
        instructions.put(appendOffset, newInstr);
        rebuildBytecode();
        parseBytecode();
    }


    /**
     * Accepts a BytecodeVisitor to traverse and operate on the instructions.
     *
     * @param visitor The BytecodeVisitor implementation.
     */
    public void accept(AbstractBytecodeVisitor visitor) {
        for (Map.Entry<Integer, Instruction> entry : instructions.entrySet()) {
            Instruction instr = entry.getValue();
            instr.accept(visitor);
        }
    }

    /**
     * Lifts this method's bytecode to SSA-form IR.
     *
     * @return The IRMethod in SSA form
     */
    public IRMethod toSSA() {
        SSA ssa = new SSA(constPool);
        return ssa.lift(methodEntry);
    }

    /**
     * Lowers an SSA-form IRMethod back to bytecode and updates this method.
     *
     * @param irMethod The IRMethod to lower
     */
    public void fromSSA(IRMethod irMethod) {
        SSA ssa = new SSA(constPool);
        ssa.lower(irMethod, methodEntry);
        this.bytecode = codeAttribute.getCode();
        parseBytecode();
        this.modified = true;
    }

    /**
     * Lifts to SSA, runs standard optimizations, and lowers back to bytecode.
     */
    public void optimizeSSA() {
        SSA ssa = new SSA(constPool).withStandardOptimizations();
        IRMethod irMethod = ssa.lift(methodEntry);
        ssa.runTransforms(irMethod);
        ssa.lower(irMethod, methodEntry);
        this.bytecode = codeAttribute.getCode();
        parseBytecode();
        this.modified = true;
    }
}
