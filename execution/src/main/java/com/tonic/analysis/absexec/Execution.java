package com.tonic.analysis.absexec;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A path-exploring abstract interpreter over one method's bytecode that builds operand-stack/local def-use
 * ({@link InsnContext}/{@link StackCtx}/{@link VarCtx}) without an abstract value domain. Reduced port of
 * RuneLite's {@code net.runelite.asm.execution.Execution} — intra-procedural (the deobfuscators only need
 * within-method def-use), forks a {@link Frame} per branch target, and terminates loops with a per-method
 * visited-edge guard.
 *
 * <p>Usage: {@code new Execution(method).addVisitor(ictx -> ...).run();}. The visitor sees every executed
 * instruction-context; {@link #wasExecuted} reports reachability.
 */
public final class Execution {

    private final MethodEntry method;
    private final List<Instruction> insns;
    private final Map<Instruction, Integer> indexOf = new IdentityHashMap<>();
    private final Map<Integer, Instruction> byOffset = new HashMap<>();
    private final Deque<Frame> work = new ArrayDeque<>();
    private final Set<Instruction> executed = new HashSet<>();
    private final Set<Long> jumpedEdges = new HashSet<>();
    private final List<Consumer<InsnContext>> visitors = new ArrayList<>();

    public Execution(MethodEntry method) {
        this(method, new CodeWriter(method).getInstructionList());
    }

    /**
     * Builds an execution over a caller-provided instruction list. Use this when the caller will subsequently
     * MUTATE those instructions (e.g. the in-place fold), so def-use {@link InsnContext#getInstruction()}
     * objects are identical to the ones the caller writes back.
     */
    public Execution(MethodEntry method, List<Instruction> insns) {
        this.method = method;
        this.insns = insns;
        for (int i = 0; i < insns.size(); i++) {
            Instruction in = insns.get(i);
            indexOf.put(in, i);
            byOffset.put(in.getOffset(), in);
        }
    }

    public Execution addVisitor(Consumer<InsnContext> v) {
        visitors.add(v);
        return this;
    }

    public void run() {
        if (insns.isEmpty()) {
            return;
        }
        Frame entry = new Frame(this, method, insns, indexOf);
        entry.initializeEntry();
        work.add(entry);
        int guard = 0;
        // Total frame-runs are bounded by distinct branch edges (the hasJumped guard), but a pathologically
        // branchy method can still spawn enough to dominate runtime; bail and leave such a method un-analyzed
        // rather than spin. Each frame.run() may execute the whole method, so this cap is deliberately small.
        int cap = Integer.getInteger("absexec.framecap", 4000);
        while (!work.isEmpty()) {
            if (++guard > cap) {
                break;
            }
            work.poll().run();
        }
    }

    public boolean wasExecuted(Instruction insn) {
        return executed.contains(insn);
    }

    // --- package API used by Frame ---

    void addFrame(Frame f) {
        work.add(f);
    }

    void recordExecuted(Instruction insn) {
        executed.add(insn);
    }

    void accept(InsnContext ictx) {
        for (Consumer<InsnContext> v : visitors) {
            v.accept(ictx);
        }
    }

    Instruction instructionAtOffset(MethodEntry m, int offset) {
        return byOffset.get(offset);
    }

    /** True (and records) if {@code from -> to} has already been traversed in this method (loop guard). */
    boolean hasJumped(MethodEntry m, Instruction from, Instruction to) {
        long key = (((long) from.getOffset()) << 32) ^ (to.getOffset() & 0xFFFFFFFFL);
        return !jumpedEdges.add(key);
    }
}
