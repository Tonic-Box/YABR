package com.tonic.analysis.absexec;

import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.table.ExceptionTableEntry;
import com.tonic.utill.DescriptorUtil;
import com.tonic.utill.Modifiers;

import java.util.List;
import java.util.Map;

/**
 * One abstract execution path through a method: an operand {@link Stack} + {@link Variables}, a current
 * instruction cursor, and the list of {@link InsnContext}s it executed. Forks a fresh frame at every branch
 * target ({@link #fork}); a per-method visited-edge guard ({@link Execution#hasJumped}) terminates loops.
 * Port of RuneLite's {@code Frame}, reduced (no value domain, no mapping/step executor).
 */
public final class Frame {

    private final Execution execution;
    final MethodEntry method;
    private final List<Instruction> insns;
    private final Map<Instruction, Integer> indexOf;
    private final List<ExceptionTableEntry> exceptions;

    private Stack stack;
    private final Variables variables;
    private Instruction cur;
    private boolean executing = true;
    boolean jumped; // set by the dispatcher when it redirected cur (so execute() doesn't auto-advance)

    Frame(Execution execution, MethodEntry method, List<Instruction> insns,
          Map<Instruction, Integer> indexOf) {
        this.execution = execution;
        this.method = method;
        this.insns = insns;
        this.indexOf = indexOf;
        CodeAttribute code = method.getCodeAttribute();
        this.stack = new Stack(code.getMaxStack());
        this.variables = new Variables(code.getMaxLocals());
        this.exceptions = code.getExceptionTable();
    }

    /** Copy-constructor for forking at a branch. */
    private Frame(Frame other, Instruction startAt) {
        this.execution = other.execution;
        this.method = other.method;
        this.insns = other.insns;
        this.indexOf = other.indexOf;
        this.exceptions = other.exceptions;
        this.stack = new Stack(other.stack);
        this.variables = new Variables(other.variables);
        this.cur = startAt;
    }

    /** Seeds the LVT with the method's parameters (this + args), each a parameter {@link VarCtx}. */
    void initializeEntry() {
        int pos = 0;
        if (!Modifiers.isStatic(method.getAccess())) {
            variables.set(pos++, new VarCtx(false).markParameter());
        }
        for (String param : DescriptorUtil.parseParameterDescriptors(method.getDesc())) {
            boolean wide = param.equals("J") || param.equals("D");
            variables.set(pos, new VarCtx(wide).markParameter());
            pos += wide ? 2 : 1;
        }
        cur = insns.isEmpty() ? null : insns.get(0);
    }

    void run() {
        while (executing && cur != null) {
            Instruction insn = cur;
            jumped = false;
            InsnContext ictx = new InsnContext(insn, this);
            EffectDispatcher.execute(insn, this, ictx);
            execution.recordExecuted(insn);
            execution.accept(ictx);
            processExceptions(ictx);
            if (!executing) {
                break;
            }
            if (!jumped) {
                advance();
            }
        }
    }

    private void advance() {
        Integer idx = indexOf.get(cur);
        if (idx == null || idx + 1 >= insns.size()) {
            executing = false;
            return;
        }
        cur = insns.get(idx + 1);
    }

    // --- navigation helpers the dispatcher uses ---

    Stack stack() {
        return stack;
    }

    Variables variables() {
        return variables;
    }

    /** Unconditionally redirect this frame to {@code target} (goto). Guards against re-traversing an edge. */
    void jumpTo(InsnContext from, Instruction target) {
        if (target == null || execution.hasJumped(method, from.getInstruction(), target)) {
            executing = false;
            return;
        }
        cur = target;
        jumped = true;
    }

    /** Fork a new frame that begins executing at {@code target} (a conditional/switch branch). */
    void fork(InsnContext from, Instruction target) {
        if (target == null || execution.hasJumped(method, from.getInstruction(), target)) {
            return;
        }
        Frame f = new Frame(this, target);
        from.branch(f);
        execution.addFrame(f);
    }

    void stop() {
        executing = false;
    }

    Instruction instructionAtOffset(int offset) {
        return execution.instructionAtOffset(method, offset);
    }

    private void processExceptions(InsnContext ictx) {
        // Fork the handler ONCE per try region — at its first covered instruction — not at every instruction
        // in [startPc, endPc). (Forking per-instruction multiplies handler frames by the try-block length and
        // is the dominant cost.) This is enough to explore the handler's def-use, which is all we need.
        int off = ictx.getInstruction().getOffset();
        for (ExceptionTableEntry ex : exceptions) {
            if (off != ex.getStartPc()) {
                continue;
            }
            Instruction handler = instructionAtOffset(ex.getHandlerPc());
            if (handler == null || execution.hasJumped(method, ictx.getInstruction(), handler)) {
                continue;
            }
            Frame f = new Frame(this, handler);
            // handler entry: stack holds just the caught exception
            f.stack = new Stack(method.getCodeAttribute().getMaxStack());
            f.stack.push(new StackCtx(ictx, false));
            ictx.branch(f);
            execution.addFrame(f);
        }
    }
}
