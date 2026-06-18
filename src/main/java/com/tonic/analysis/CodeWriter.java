package com.tonic.analysis;

import com.tonic.analysis.frame.FrameGenerator;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.visitor.AbstractBytecodeVisitor;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.StackMapTableAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.analysis.instruction.*;
import com.tonic.utill.Logger;
import com.tonic.utill.Opcode;
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
        for (Instruction instr : InstructionFactory.parse(bytecode, constPool)) {
            instructions.put(instr.getOffset(), instr);
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
     * Returns a fresh, bytecode-ordered, random-access snapshot of the instructions. Identity-stable
     * (the same {@code Instruction} objects), so {@code indexOf(handle)} resolves by identity; a
     * snapshot rather than a live view, so it can be iterated while editing by handle. Re-call after an
     * edit to reflect the new state.
     *
     * @return the instructions in offset order
     */
    public List<Instruction> getInstructionList() {
        return new ArrayList<>(instructions.values());
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
        relink(new ArrayList<>(body), Collections.emptyList());
    }

    /**
     * As {@link #replaceBody(List)} but with an exception table whose entry PCs are interpreted against
     * {@code body}'s layout, bound by identity so regenerated frames cover the handler blocks.
     *
     * @param body       the new instruction stream, in order
     * @param exceptions the exception-table entries (catch_type indices in this method's pool)
     */
    public void replaceBody(List<Instruction> body, List<ExceptionTableEntry> exceptions) {
        List<Instruction> order = new ArrayList<>(body);
        relink(order, resolveRegions(exceptions, order));
    }

    /** Replaces the body with a cloned range, carrying its targets + exception regions by identity. */
    public void replaceBody(ClonedRange block) {
        requireSelfContained(block);
        List<Instruction> order = new ArrayList<>(block.instructions);
        requireTargetsPresent(block, order);
        branchTargets.putAll(block.targets);
        relink(order, block.regions);
    }

    /** Replaces the body with a cloned range plus an explicit exception table (targets by identity). */
    public void replaceBody(ClonedRange block, List<ExceptionTableEntry> exceptions) {
        requireSelfContained(block);
        List<Instruction> order = new ArrayList<>(block.instructions);
        requireTargetsPresent(block, order);
        branchTargets.putAll(block.targets);
        relink(order, resolveRegions(exceptions, order));
    }

    /** Inserts a cloned range before the handle, carrying its targets + exception regions by identity. */
    public void insertBefore(Instruction handle, ClonedRange block) {
        List<Instruction> order = orderWith(handle, block.instructions, true);
        requireTargetsPresent(block, order);
        branchTargets.putAll(block.targets);
        bindExternalTargets(block, handle);
        List<ExceptionRegionRef> regions = resolveExistingTable();
        regions.addAll(block.regions);
        relink(order, regions);
    }

    /** As {@link #insertBefore(Instruction, ClonedRange)} but binds external labels to host targets first. */
    public void insertBefore(Instruction handle, ClonedRange block, Map<String, Instruction> externalBindings) {
        externalBindings.forEach(block::bindLabel);
        insertBefore(handle, block);
    }

    /** Inserts a cloned range after the handle, carrying its targets + exception regions by identity. */
    public void insertAfter(Instruction handle, ClonedRange block) {
        List<Instruction> order = orderWith(handle, block.instructions, false);
        requireTargetsPresent(block, order);
        branchTargets.putAll(block.targets);
        bindExternalTargets(block, successorOf(handle));
        List<ExceptionRegionRef> regions = resolveExistingTable();
        regions.addAll(block.regions);
        relink(order, regions);
    }

    /** As {@link #insertAfter(Instruction, ClonedRange)} but binds external labels to host targets first. */
    public void insertAfter(Instruction handle, ClonedRange block, Map<String, Instruction> externalBindings) {
        externalBindings.forEach(block::bindLabel);
        insertAfter(handle, block);
    }

    /**
     * Splices several cloned bodies before {@code at}, chaining them so each body's continuation exits
     * (e.g. from {@link ClonedRange#redirectReturns()}) fall through into the next body's entry and the
     * last body's into {@code at}. Use this to fold multiple bodies before one instruction: repeated
     * {@code insertBefore(at, …)} would instead bind every body's continuation to {@code at}, so earlier
     * bodies would skip later ones (a silent miscompile). External-label bindings carried by the bodies
     * are preserved. A no-op for an empty list.
     */
    public void insertChainBefore(Instruction at, List<ClonedRange> bodies) {
        List<ClonedRange> chain = new ArrayList<>();
        for (ClonedRange b : bodies) {
            if (!b.instructions.isEmpty()) {
                chain.add(b);
            }
        }
        if (chain.isEmpty()) {
            return;
        }

        List<Instruction> instrs = new ArrayList<>();
        Map<Instruction, List<Instruction>> targets = new IdentityHashMap<>();
        List<ExceptionRegionRef> regions = new ArrayList<>();
        Map<String, List<Instruction>> external = new LinkedHashMap<>();
        Map<String, Instruction> bindings = new HashMap<>();
        List<Instruction> continuation = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            ClonedRange b = chain.get(i);
            instrs.addAll(b.instructions);
            targets.putAll(b.targets);
            regions.addAll(b.regions);
            external.putAll(b.externalLabels);
            bindings.putAll(b.bindings);
            if (i + 1 < chain.size()) {
                Instruction nextEntry = chain.get(i + 1).instructions.get(0);
                for (Instruction c : b.continuationBranches) {
                    targets.put(c, new ArrayList<>(Collections.singletonList(nextEntry)));
                }
            } else {
                continuation.addAll(b.continuationBranches);
            }
        }
        ClonedRange combined = new ClonedRange(instrs, targets, regions, external, continuation);
        combined.bindings.putAll(bindings);
        insertBefore(at, combined);
    }

    /**
     * Fails loud if a carried branch target isn't present in the post-splice instruction set — e.g. an
     * out-of-range branch whose target was carried by identity from another method. Out-of-range branch
     * carry is same-method-only; this converts what would be a class-load {@code VerifyError} into a
     * located build-time error.
     */
    private static void requireTargetsPresent(ClonedRange block, List<Instruction> order) {
        Set<Instruction> present = Collections.newSetFromMap(new IdentityHashMap<>());
        present.addAll(order);
        for (List<Instruction> tg : block.targets.values()) {
            for (Instruction t : tg) {
                if (t != null && !present.contains(t)) {
                    throw new IllegalStateException(
                            "branch target not present in host method; out-of-range branch carry is same-method-only");
                }
            }
        }
    }

    /** A whole-body replacement has no host context, so it cannot carry external/continuation branches. */
    private static void requireSelfContained(ClonedRange block) {
        if (!block.externalLabels.isEmpty() || !block.continuationBranches.isEmpty()) {
            throw new IllegalStateException(
                    "replaceBody cannot resolve external/continuation branches (no host successor); "
                            + "use insertBefore/insertAfter");
        }
    }

    /**
     * Resolves a spliced block's external labels (to their bound host instructions) and continuation
     * branches (to {@code continuationTarget}) into the identity-target map relink consumes.
     */
    private void bindExternalTargets(ClonedRange block, Instruction continuationTarget) {
        for (Map.Entry<String, List<Instruction>> e : block.externalLabels.entrySet()) {
            Instruction host = block.bindings.get(e.getKey());
            if (host == null) {
                throw new IllegalStateException("unbound external label: " + e.getKey());
            }
            if (!instructions.containsValue(host)) {
                throw new IllegalArgumentException("external label target is not part of this method: " + e.getKey());
            }
            for (Instruction branch : e.getValue()) {
                branchTargets.put(branch, new ArrayList<>(Collections.singletonList(host)));
            }
        }
        if (!block.continuationBranches.isEmpty()) {
            if (continuationTarget == null) {
                throw new IllegalStateException(
                        "snippet has continuation branches but the splice point has no host successor");
            }
            for (Instruction branch : block.continuationBranches) {
                branchTargets.put(branch, new ArrayList<>(Collections.singletonList(continuationTarget)));
            }
        }
    }

    /** The instruction immediately after {@code handle} in the current layout, or null if it is last. */
    private Instruction successorOf(Instruction handle) {
        boolean seen = false;
        for (Instruction i : instructions.values()) {
            if (seen) {
                return i;
            }
            if (i == handle) {
                seen = true;
            }
        }
        return null;
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
        relink(orderWith(handle, block, true));
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
        relink(orderWith(handle, block, false));
    }

    /** Builds the post-edit instruction order with {@code block} spliced before/after {@code handle}. */
    private List<Instruction> orderWith(Instruction handle, List<Instruction> block, boolean before) {
        requirePresent(handle);
        List<Instruction> order = new ArrayList<>();
        for (Instruction instr : instructions.values()) {
            if (before && instr == handle) {
                order.addAll(block);
            }
            order.add(instr);
            if (!before && instr == handle) {
                order.addAll(block);
            }
        }
        return order;
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
     * Snapshots this writer's full instruction list and its by-identity branch/switch targets into a
     * {@link ClonedRange}, so an externally-assembled body can be spliced into another method via
     * {@link #insertBefore(Instruction, ClonedRange)} / {@link #replaceBody(ClonedRange)}. The
     * snapshot reuses the live instruction objects; the target map is copied defensively.
     */
    public ClonedRange toClonedRange() {
        return toClonedRange(Collections.emptyList());
    }

    /**
     * As {@link #toClonedRange()} but also carries exception regions. Each entry's PCs are interpreted
     * against this writer's own layout and bound by instruction identity, so a try/catch survives being
     * spliced at any offset/alignment.
     */
    public ClonedRange toClonedRange(List<ExceptionTableEntry> regionEntries) {
        return toClonedRange(regionEntries, Collections.emptyMap(), Collections.emptyList());
    }

    /**
     * As {@link #toClonedRange(List)} but also records branches whose targets lie outside the snippet:
     * {@code externalOffsets} (branch offsets per external label name, bound at splice via
     * {@link ClonedRange#bindLabel}) and {@code continuationOffsets} (branches that fall through into
     * the host, auto-bound to the splice successor). Offsets are interpreted against this writer's
     * layout; these branches are omitted from the auto-resolved target snapshot as they have no
     * in-snippet target.
     */
    public ClonedRange toClonedRange(List<ExceptionTableEntry> regionEntries,
                                     Map<String, List<Integer>> externalOffsets,
                                     List<Integer> continuationOffsets) {
        resolveAllBranchTargets();
        List<Instruction> snapshot = new ArrayList<>(instructions.values());

        Set<Instruction> unresolved = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, List<Instruction>> external = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : externalOffsets.entrySet()) {
            List<Instruction> branches = new ArrayList<>();
            for (int off : e.getValue()) {
                Instruction b = instructions.get(off);
                if (b != null) {
                    branches.add(b);
                    unresolved.add(b);
                }
            }
            external.put(e.getKey(), branches);
        }
        List<Instruction> continuation = new ArrayList<>();
        for (int off : continuationOffsets) {
            Instruction b = instructions.get(off);
            if (b != null) {
                continuation.add(b);
                unresolved.add(b);
            }
        }

        Map<Instruction, List<Instruction>> targets = new IdentityHashMap<>();
        for (Map.Entry<Instruction, List<Instruction>> e : branchTargets.entrySet()) {
            if (!unresolved.contains(e.getKey())) {
                targets.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
        }

        List<ExceptionRegionRef> regions = new ArrayList<>();
        for (ExceptionTableEntry ex : regionEntries) {
            ExceptionRegionRef r = resolveRegion(ex, instructions);
            if (r != null) {
                regions.add(r);
            }
        }
        return new ClonedRange(snapshot, targets, regions, external, continuation);
    }

    /** A try/catch region bound to instruction identities so it survives relayout. */
    private static final class ExceptionRegionRef {
        final Instruction start;
        final Instruction last;
        final Instruction handler;
        final int catchType;

        ExceptionRegionRef(Instruction start, Instruction last, Instruction handler, int catchType) {
            this.start = start;
            this.last = last;
            this.handler = handler;
            this.catchType = catchType;
        }
    }

    /**
     * A cloned instruction range: the fresh instructions, the by-identity targets of any branch/switch
     * among them, and any exception regions (also by identity). Splicing it via
     * {@link #insertBefore(Instruction, ClonedRange)} / {@link #replaceBody(ClonedRange)} carries all of
     * these into the host so they relink correctly regardless of where (and at what 4-byte alignment)
     * the block lands.
     */
    public static final class ClonedRange {
        private final List<Instruction> instructions;
        private final Map<Instruction, List<Instruction>> targets;
        private final List<ExceptionRegionRef> regions;
        private final Map<String, List<Instruction>> externalLabels;
        private final List<Instruction> continuationBranches;
        private final Map<String, Instruction> bindings = new HashMap<>();

        ClonedRange(List<Instruction> instructions, Map<Instruction, List<Instruction>> targets) {
            this(instructions, targets, Collections.emptyList());
        }

        ClonedRange(List<Instruction> instructions, Map<Instruction, List<Instruction>> targets,
                    List<ExceptionRegionRef> regions) {
            this(instructions, targets, regions, Collections.emptyMap(), Collections.emptyList());
        }

        ClonedRange(List<Instruction> instructions, Map<Instruction, List<Instruction>> targets,
                    List<ExceptionRegionRef> regions, Map<String, List<Instruction>> externalLabels,
                    List<Instruction> continuationBranches) {
            this.instructions = instructions;
            this.targets = targets;
            this.regions = regions;
            this.externalLabels = externalLabels;
            this.continuationBranches = new ArrayList<>(continuationBranches);
        }

        public List<Instruction> instructions() {
            return instructions;
        }

        /**
         * Binds an external label (declared via {@code CodeBuilder.externalLabel}) to an instruction in
         * the host method; must be called before splicing. Returns this range for chaining.
         */
        public ClonedRange bindLabel(String name, Instruction hostTarget) {
            bindings.put(name, hostTarget);
            return this;
        }

        /**
         * Rewrites every cloned {@code return} into a continuation exit (a placeholder {@code goto}
         * recorded as a continuation branch), so an inlined body's returns fall through to the splice
         * successor instead of returning from the host. Branches that targeted a rewritten return are
         * repointed at its goto. Opt-in: leave it off to relocate a body whose returns should stay
         * returns. Must be spliced via {@code insertBefore}/{@code insertAfter} (which supply the
         * successor); {@code replaceBody} has none and will reject the continuation. Returns this range.
         * <p>
         * To fold several such bodies before one instruction, use
         * {@link CodeWriter#insertChainBefore(Instruction, java.util.List)} — repeated
         * {@code insertBefore(at, …)} would bind every body's continuation to {@code at}, so earlier
         * bodies would skip later ones.
         */
        public ClonedRange redirectReturns() {
            Map<Instruction, Instruction> rewritten = new IdentityHashMap<>();
            for (int i = 0; i < instructions.size(); i++) {
                if (instructions.get(i) instanceof ReturnInstruction) {
                    Instruction goto_ = new GotoInstruction(GOTO.getCode(), 0, (short) 0);
                    rewritten.put(instructions.get(i), goto_);
                    instructions.set(i, goto_);
                    continuationBranches.add(goto_);
                }
            }
            if (!rewritten.isEmpty()) {
                for (List<Instruction> tg : targets.values()) {
                    for (int k = 0; k < tg.size(); k++) {
                        Instruction repl = rewritten.get(tg.get(k));
                        if (repl != null) {
                            tg.set(k, repl);
                        }
                    }
                }
            }
            return this;
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
                // In-range targets map to their clone; an out-of-range target is carried by identity so
                // it resolves against the host at splice time (rather than dangling as null).
                out.add(t == null ? null : map.getOrDefault(t, t));
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
            Instruction local = remapLocalVar(i, localOffset);
            if (local != null) {
                return local;
            }
        }
        return genericCopy(i);
    }

    @FunctionalInterface
    private interface LocalCtor {
        Instruction make(int opcode, int index);
    }

    /**
     * Re-emits a local-variable instruction (any compact/general/wide load, store, iinc or ret) with
     * its index shifted by {@code localOffset}, choosing the canonical narrowest encoding: the compact
     * {@code xload_<n>} form for index 0-3, the general form for 4-255, and the {@code wide} form for
     * index (or iinc constant) beyond a byte. Returns null when {@code i} is not a local-variable op.
     */
    private Instruction remapLocalVar(Instruction i, int localOffset) {
        Opcode general = localGeneralOpcode(i);
        if (general == null) {
            return null;
        }
        int index = localVarIndex(i) + localOffset;
        int constValue = i instanceof IIncInstruction ? ((IIncInstruction) i).getConstValue()
                : i instanceof WideIIncInstruction ? ((WideIIncInstruction) i).getConstValue() : 0;
        return buildLocalVar(general, index, constValue);
    }

    /** The general-form opcode of a local-variable instruction (the modified opcode for a wide), or null. */
    private static Opcode localGeneralOpcode(Instruction i) {
        if (i instanceof ILoadInstruction) return ILOAD;
        if (i instanceof LLoadInstruction) return LLOAD;
        if (i instanceof FLoadInstruction) return FLOAD;
        if (i instanceof DLoadInstruction) return DLOAD;
        if (i instanceof ALoadInstruction) return ALOAD;
        if (i instanceof IStoreInstruction) return ISTORE;
        if (i instanceof LStoreInstruction) return LSTORE;
        if (i instanceof FStoreInstruction) return FSTORE;
        if (i instanceof DStoreInstruction) return DSTORE;
        if (i instanceof AStoreInstruction) return ASTORE;
        if (i instanceof IIncInstruction || i instanceof WideIIncInstruction) return IINC;
        if (i instanceof RetInstruction) return RET;
        if (i instanceof WideInstruction) return ((WideInstruction) i).getModifiedOpcode();
        return null;
    }

    /** Builds the canonical narrowest encoding of a local-variable op identified by its general opcode. */
    private Instruction buildLocalVar(Opcode general, int index, int constValue) {
        switch (general) {
            case ILOAD:  return loadStore(ILOAD_0, ILOAD, index, (op, x) -> new ILoadInstruction(op, 0, x));
            case LLOAD:  return loadStore(LLOAD_0, LLOAD, index, (op, x) -> new LLoadInstruction(op, 0, x));
            case FLOAD:  return loadStore(FLOAD_0, FLOAD, index, (op, x) -> new FLoadInstruction(op, 0, x));
            case DLOAD:  return loadStore(DLOAD_0, DLOAD, index, (op, x) -> new DLoadInstruction(op, 0, x));
            case ALOAD:  return loadStore(ALOAD_0, ALOAD, index, (op, x) -> new ALoadInstruction(op, 0, x));
            case ISTORE: return loadStore(ISTORE_0, ISTORE, index, (op, x) -> new IStoreInstruction(op, 0, x));
            case LSTORE: return loadStore(LSTORE_0, LSTORE, index, (op, x) -> new LStoreInstruction(op, 0, x));
            case FSTORE: return loadStore(FSTORE_0, FSTORE, index, (op, x) -> new FStoreInstruction(op, 0, x));
            case DSTORE: return loadStore(DSTORE_0, DSTORE, index, (op, x) -> new DStoreInstruction(op, 0, x));
            case ASTORE: return loadStore(ASTORE_0, ASTORE, index, (op, x) -> new AStoreInstruction(op, 0, x));
            case IINC:
                if (index <= 0xFF && constValue >= Byte.MIN_VALUE && constValue <= Byte.MAX_VALUE) {
                    return new IIncInstruction(IINC.getCode(), 0, index, constValue);
                }
                return new WideInstruction(WIDE.getCode(), 0, IINC, index, constValue);
            case RET:
                return index <= 0xFF ? new RetInstruction(RET.getCode(), 0, index)
                        : new WideInstruction(WIDE.getCode(), 0, RET, index);
            default:
                throw new IllegalStateException("not a local-variable opcode: " + general);
        }
    }

    /** Compact ({@code _<n>}, index 0-3), general (4-255), or wide (&gt;255) encoding of a load/store. */
    private Instruction loadStore(Opcode compact0, Opcode general, int index, LocalCtor ctor) {
        if (index >= 0 && index <= 3) {
            return ctor.make(compact0.getCode() + index, index);
        }
        if (index <= 0xFF) {
            return ctor.make(general.getCode(), index);
        }
        return new WideInstruction(WIDE.getCode(), 0, general, index);
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
        relink(newOrder, resolveExistingTable());
    }

    /**
     * As {@link #relink(List)} but with the exception table supplied by identity ({@code regions},
     * resolved by the caller against the correct baseline) rather than read from the current table.
     * The table is rebuilt from each region's post-layout offsets, which is robust against the offset
     * collisions a freshly spliced block would otherwise cause in an offset-keyed remap.
     */
    private void relink(List<Instruction> newOrder, List<ExceptionRegionRef> regions) {
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
        rebuildExceptionTable(regions, newOff);
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
            int slot = localVarIndex(i);
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

    /** The local-variable slot a load/store/iinc/ret (compact, general, or wide) references, or -1. */
    private static int localVarIndex(Instruction i) {
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
        if (i instanceof WideIIncInstruction) return ((WideIIncInstruction) i).getVarIndex();
        if (i instanceof WideInstruction) return ((WideInstruction) i).getVarIndex();
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
        if (i instanceof LLoadInstruction || i instanceof LStoreInstruction
                || i instanceof DLoadInstruction || i instanceof DStoreInstruction) {
            return 2;
        }
        if (i instanceof WideInstruction) {
            Opcode m = ((WideInstruction) i).getModifiedOpcode();
            if (m == LLOAD || m == LSTORE || m == DLOAD || m == DSTORE) {
                return 2;
            }
        }
        return 1;
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
    private List<ExceptionRegionRef> resolveExistingTable() {
        List<ExceptionRegionRef> refs = new ArrayList<>();
        for (ExceptionTableEntry ex : codeAttribute.getExceptionTable()) {
            ExceptionRegionRef r = resolveRegion(ex, instructions);
            if (r != null) {
                refs.add(r);
            }
        }
        return refs;
    }

    /** Resolves PC-based entries to identity-based regions against {@code body}'s own layout. */
    private static List<ExceptionRegionRef> resolveRegions(List<ExceptionTableEntry> entries, List<Instruction> body) {
        Map<Integer, Instruction> byOffset = new HashMap<>();
        for (Instruction i : body) {
            byOffset.put(i.getOffset(), i);
        }
        List<ExceptionRegionRef> refs = new ArrayList<>();
        for (ExceptionTableEntry ex : entries) {
            ExceptionRegionRef r = resolveRegion(ex, byOffset);
            if (r != null) {
                refs.add(r);
            }
        }
        return refs;
    }

    /**
     * Binds one entry to instruction identities: start/handler by exact offset, and the protected
     * region's last instruction (inclusive) as the greatest offset below the exclusive end_pc. Returns
     * {@code null} if any boundary cannot be resolved.
     */
    private static ExceptionRegionRef resolveRegion(ExceptionTableEntry ex, Map<Integer, Instruction> byOffset) {
        Instruction start = byOffset.get(ex.getStartPc());
        Instruction handler = byOffset.get(ex.getHandlerPc());
        Instruction last = null;
        int best = -1;
        for (Map.Entry<Integer, Instruction> e : byOffset.entrySet()) {
            int off = e.getKey();
            if (off < ex.getEndPc() && off > best) {
                best = off;
                last = e.getValue();
            }
        }
        if (start == null || handler == null || last == null) {
            return null;
        }
        return new ExceptionRegionRef(start, last, handler, ex.getCatchType());
    }

    /** Rebuilds the exception table from identity regions, deriving PCs from the final layout. */
    private void rebuildExceptionTable(List<ExceptionRegionRef> regions, Map<Instruction, Integer> newOff) {
        List<ExceptionTableEntry> table = codeAttribute.getExceptionTable();
        table.clear();
        for (ExceptionRegionRef r : regions) {
            Integer start = newOff.get(r.start);
            Integer last = newOff.get(r.last);
            Integer handler = newOff.get(r.handler);
            if (start == null || last == null || handler == null) {
                continue;
            }
            int endPc = last + instructionLength(r.last, last);
            table.add(new ExceptionTableEntry(start, endPc, handler, r.catchType));
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

    /** Regenerates the StackMapTable from the corrected bytecode (best-effort) and raises max_stack. */
    private void regenerateFrames() {
        try {
            FrameGenerator fg = new FrameGenerator(constPool);
            fg.updateStackMapTable(methodEntry);
            raiseMaxStack(fg.getMaxStack());
        } catch (Exception e) {
            Logger.error("Frame regeneration after edit failed: " + e.getMessage());
        }
    }

    /**
     * Computes {@code max_stack} over the control-flow graph (via {@link FrameGenerator}) and raises the
     * method's value if the linear estimate under-counts — correct for loops, joins, and handler entry
     * states where a textual scan can miss the true peak. Best-effort: a failure leaves the existing
     * value untouched. Returns the resulting max_stack.
     */
    public int computeMaxStack() {
        try {
            raiseMaxStack(new FrameGenerator(constPool).computeMaxStack(methodEntry));
        } catch (Exception e) {
            Logger.error("max_stack computation failed: " + e.getMessage());
        }
        return maxStack;
    }

    /** Raises max_stack (and the CodeAttribute) to {@code candidate} when it exceeds the current value. */
    private void raiseMaxStack(int candidate) {
        if (candidate > maxStack) {
            maxStack = candidate;
            codeAttribute.setMaxStack(maxStack);
        }
    }

    /**
     * Rebuilds the bytecode array from the current instructions.
     */
    protected void rebuildBytecode() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (Instruction instr : instructions.values()) {
                instr.write(dos);
            }
            dos.flush();
            bytecode = baos.toByteArray();
            codeAttribute.setCode(bytecode);
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
        computeMaxStack();
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
