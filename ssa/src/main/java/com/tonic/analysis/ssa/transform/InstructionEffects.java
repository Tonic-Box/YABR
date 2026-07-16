package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.ir.ArrayAccessInstruction;
import com.tonic.analysis.ssa.ir.FieldAccessInstruction;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.SimpleInstruction;
import com.tonic.analysis.ssa.ir.SimpleOp;

/**
 * Side-effect classification for SSA instructions, shared by the dead-code passes so they agree on
 * what affects program state beyond producing an SSA value.
 *
 * <p>This is purely the state-effect question. Control transfer is <em>not</em> a side effect here:
 * terminators (goto/throw/branch/return/switch) are identified separately via
 * {@link IRInstruction#isTerminator()}.
 */
final class InstructionEffects {

    private InstructionEffects() {
    }

    /**
     * Returns true when the instruction affects program state beyond producing its SSA result:
     * an invocation, a field or array store, or a monitor operation.
     */
    static boolean hasSideEffects(IRInstruction instr) {
        if (instr instanceof InvokeInstruction) {
            return true;
        }
        if (instr instanceof FieldAccessInstruction) {
            return ((FieldAccessInstruction) instr).isStore();
        }
        if (instr instanceof ArrayAccessInstruction) {
            return ((ArrayAccessInstruction) instr).isStore();
        }
        if (instr instanceof SimpleInstruction) {
            SimpleOp op = ((SimpleInstruction) instr).getOp();
            return op == SimpleOp.MONITORENTER || op == SimpleOp.MONITOREXIT;
        }
        return false;
    }
}
