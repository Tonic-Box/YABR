package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises control transfers that leave a cloned range: {@code ClonedRange.redirectReturns()} (cloned
 * returns become continuation exits bound to the splice successor) and out-of-range branch targets
 * (carried by identity rather than dangling), via {@code cloneRangeWithTargets} + the existing splice.
 */
class ClonedRangeExitsTest {

    private static final int PUB_STATIC = AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC;

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static List<Instruction> list(CodeWriter cw) {
        List<Instruction> out = new ArrayList<>();
        cw.getInstructions().forEach(out::add);
        return out;
    }

    @Test
    void redirectReturnsContinuesToHost() throws Exception {
        // A cloned void `return` spliced into an int method would fail the verifier if executed;
        // redirected into a continuation goto, control reaches the host's `iconst 42; ireturn`.
        ClassFile cf = ClassBuilder.create("RR")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(PUB_STATIC, "v", "()V").code().nop().vreturn().end().end()
                .addMethod(PUB_STATIC, "run", "()I").code().iconst(42).ireturn().end().end()
                .build();

        CodeWriter src = new CodeWriter(method(cf, "v"));
        List<Instruction> b = list(src);
        CodeWriter.ClonedRange body = src
                .cloneRangeWithTargets(b.get(0), b.get(b.size() - 1), 0, null, null)
                .redirectReturns();

        CodeWriter host = new CodeWriter(method(cf, "run"));
        host.insertBefore(host.getInstructions().iterator().next(), body);
        host.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(42, (int) clazz.getMethod("run").invoke(null));
    }

    @Test
    void redirectReturnsRepointsBranchToRewrittenReturn() throws Exception {
        // `goto L; L: return` — the goto targets the return; after redirectReturns the goto must follow
        // the rewrite to the continuation, not a stale return object.
        ClassFile cf = ClassBuilder.create("RB")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(PUB_STATIC, "v", "()V").code()
                    .goto_("L").label("L").vreturn()
                .end().end()
                .addMethod(PUB_STATIC, "run", "()I").code().iconst(7).ireturn().end().end()
                .build();

        CodeWriter src = new CodeWriter(method(cf, "v"));
        List<Instruction> b = list(src);
        CodeWriter.ClonedRange body = src
                .cloneRangeWithTargets(b.get(0), b.get(b.size() - 1), 0, null, null)
                .redirectReturns();

        CodeWriter host = new CodeWriter(method(cf, "run"));
        host.insertBefore(host.getInstructions().iterator().next(), body);
        host.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(7, (int) clazz.getMethod("run").invoke(null));
    }

    @Test
    void redirectReturnsThenReplaceBodyThrows() throws Exception {
        ClassFile cf = ClassBuilder.create("RX")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(PUB_STATIC, "v", "()V").code().nop().vreturn().end().end()
                .addMethod(PUB_STATIC, "run", "()I").code().iconst(1).ireturn().end().end()
                .build();

        CodeWriter src = new CodeWriter(method(cf, "v"));
        List<Instruction> b = list(src);
        CodeWriter.ClonedRange body = src
                .cloneRangeWithTargets(b.get(0), b.get(b.size() - 1), 0, null, null)
                .redirectReturns();

        CodeWriter host = new CodeWriter(method(cf, "run"));
        assertThrows(IllegalStateException.class, () -> host.replaceBody(body));
    }

    @Test
    void outOfRangeBranchTargetCarriedByIdentity() throws Exception {
        // f(n): if (n>=0) goto POS; return -1; POS: return n*2;
        // Clone the partial range [iload_0, ifge POS] — whose ifge targets POS, outside the range —
        // and splice it back into the same method. The cloned ifge must resolve to the original POS.
        ClassFile cf = ClassBuilder.create("OOR")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(PUB_STATIC, "f", "(I)I").code()
                    .iload(0).ifge("pos")
                    .iconst(-1).ireturn()
                    .label("pos").iload(0).iconst(2).imul().ireturn()
                .end().end().build();

        CodeWriter cw = new CodeWriter(method(cf, "f"));
        List<Instruction> insns = list(cw);
        Instruction iload = insns.get(0);
        Instruction ifge = insns.get(1);
        Instruction retNeg = insns.get(2);   // iconst_m1 (start of the n<0 path)

        CodeWriter.ClonedRange dup = cw.cloneRangeWithTargets(iload, ifge, 0, null, null);
        cw.insertBefore(retNeg, dup);   // re-checks n before the n<0 path; jumps to the original POS
        cw.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(6, (int) clazz.getMethod("f", int.class).invoke(null, 3));
        assertEquals(-1, (int) clazz.getMethod("f", int.class).invoke(null, -5));
    }

    @Test
    void outOfRangeBranchSplicedIntoAnotherMethodThrows() throws Exception {
        // Cloning a partial range with an out-of-range branch and splicing it into a *different* method
        // (where the carried target doesn't exist) must fail loud, not emit a class-load VerifyError.
        ClassFile cf = ClassBuilder.create("X")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(PUB_STATIC, "f", "(I)I").code()
                    .iload(0).ifge("pos")
                    .iconst(-1).ireturn()
                    .label("pos").iload(0).iconst(2).imul().ireturn()
                .end().end()
                .addMethod(PUB_STATIC, "g", "()I").code().iconst(5).ireturn().end().end()
                .build();

        CodeWriter fcw = new CodeWriter(method(cf, "f"));
        List<Instruction> insns = list(fcw);
        CodeWriter.ClonedRange dup = fcw.cloneRangeWithTargets(insns.get(0), insns.get(1), 0, null, null);

        CodeWriter gcw = new CodeWriter(method(cf, "g"));
        Instruction gFirst = gcw.getInstructions().iterator().next();
        assertThrows(IllegalStateException.class, () -> gcw.insertBefore(gFirst, dup));
    }

    @Test
    void insertChainBeforeFoldsBodiesInSequence() throws Exception {
        // Fold two `inc` bodies (each `iinc 0,1; return`, returns redirected) before run's `iload 0`.
        // Chained, both run (n -> n+2); the skip hazard of repeated insertBefore would run only one.
        ClassFile cf = ClassBuilder.create("Fold")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(PUB_STATIC, "inc", "(I)V").code().iinc(0, 1).vreturn().end().end()
                .addMethod(PUB_STATIC, "run", "(I)I").code().iload(0).ireturn().end().end()
                .build();

        CodeWriter incCw = new CodeWriter(method(cf, "inc"));
        List<Instruction> ib = list(incCw);
        CodeWriter.ClonedRange b1 = incCw
                .cloneRangeWithTargets(ib.get(0), ib.get(ib.size() - 1), 0, null, null).redirectReturns();
        CodeWriter.ClonedRange b2 = incCw
                .cloneRangeWithTargets(ib.get(0), ib.get(ib.size() - 1), 0, null, null).redirectReturns();

        CodeWriter run = new CodeWriter(method(cf, "run"));
        Instruction iload = run.getInstructions().iterator().next();
        run.insertChainBefore(iload, java.util.Arrays.asList(b1, b2));
        run.write();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(7, (int) clazz.getMethod("run", int.class).invoke(null, 5));
    }
}
