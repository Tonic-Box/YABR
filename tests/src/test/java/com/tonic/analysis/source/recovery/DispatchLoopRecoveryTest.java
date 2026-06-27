package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.emit.SourceEmitter;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the completeness guarantee: operations in unstructurable control flow (irreducible /
 * scrambled gotos) are never dropped from the recovered source. When the structured recovery
 * cannot represent the flow, recovery falls back to a faithful dispatch loop, so every reachable
 * call still appears.
 */
class DispatchLoopRecoveryTest {

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();
    }

    @Test
    void irreducibleControlFlowKeepsAllReachableCalls() throws IOException {
        // A <-> B two-entry cycle: B is entered from the entry (ifeq) AND from A (goto); A is
        // entered from the entry fall-through AND from B (goto). This multi-entry cycle is
        // irreducible, so the structured recovery cannot place every block on its spine.
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T")
            .publicStaticMethod("m", "(I)V");
        Label a = mb.newLabel();
        Label b = mb.newLabel();
        Label end = mb.newLabel();

        ClassFile cf = mb
            .iload(0)
            .ifeq(b)
            .label(a)
            .invokestatic("T", "markerA", "()V")
            .iload(0)
            .ifeq(end)
            .goto_(b)
            .label(b)
            .invokestatic("T", "markerB", "()V")
            .iload(0)
            .ifeq(end)
            .goto_(a)
            .label(end)
            .vreturn()
            .build();

        MethodEntry method = findMethod(cf, "m");
        IRMethod ir = TestUtils.liftMethod(method);
        BlockStmt body = new MethodRecoverer(ir, method).recover();
        String src = SourceEmitter.emit(body);

        assertTrue(src.contains("markerA"), "markerA call must not be dropped:\n" + src);
        assertTrue(src.contains("markerB"), "markerB call must not be dropped:\n" + src);
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("method not found: " + name);
    }
}
