package com.tonic.analysis.oracle;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RecoveryEquivalenceTest {

    /** Walking-skeleton smoke test: a correct method must never be reported NOT_EQUIVALENT. */
    @Test
    void pureMethodIsNotFalselyFlagged() throws Exception {
        byte[] bytes = ClassBuilder.create("com/test/OracleSmoke")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
                .code()
                    .iload(0)
                    .iload(1)
                    .iadd()
                    .ireturn()
                .end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry add = cf.getMethod("add", "(II)I");

        RecoveryEquivalenceOracle.Verdict verdict = new RecoveryEquivalenceOracle().check(cf, pool, add);
        System.out.println("[oracle smoke] add(II)I -> " + verdict);

        assertNotEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /** Positive control: two behaviourally different methods must be caught as NOT_EQUIVALENT. */
    @Test
    void divergentMethodsAreCaught() throws Exception {
        byte[] bytes = ClassBuilder.create("com/test/OraclePosCtl")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
                .code().iload(0).iload(1).iadd().ireturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sub", "(II)I")
                .code().iload(0).iload(1).isub().ireturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry add = cf.getMethod("add", "(II)I");
        MethodEntry sub = cf.getMethod("sub", "(II)I");

        RecoveryEquivalenceOracle.Verdict verdict =
                new RecoveryEquivalenceOracle().differential(add, sub, pool);
        System.out.println("[oracle poscontrol] add vs sub -> " + verdict);

        assertEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /**
     * The heart of the loop-merge bug class: a dropped call. Two methods that call the same sink a
     * different number of times must be caught (validates the call-interception + trace recording).
     */
    @Test
    void droppedCallIsCaught() throws Exception {
        String owner = "com/test/OracleCalls";
        byte[] bytes = ClassBuilder.create(owner)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sink", "(I)V")
                .code().vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "callsOnce", "(I)V")
                .code().iload(0).invokestatic(owner, "sink", "(I)V").vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "callsTwice", "(I)V")
                .code().iload(0).invokestatic(owner, "sink", "(I)V")
                       .iload(0).invokestatic(owner, "sink", "(I)V").vreturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry once = cf.getMethod("callsOnce", "(I)V");
        MethodEntry twice = cf.getMethod("callsTwice", "(I)V");

        RecoveryEquivalenceOracle.Verdict verdict =
                new RecoveryEquivalenceOracle().differential(once, twice, pool);
        System.out.println("[oracle dropcall] callsOnce vs callsTwice -> " + verdict);

        assertEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /**
     * A write of a different value to an escaping object's field must be caught - validates the
     * field-write observability (the observable set beyond calls + terminal).
     */
    @Test
    void fieldWriteDivergenceIsCaught() throws Exception {
        String owner = "com/test/OracleFields";
        byte[] bytes = ClassBuilder.create(owner)
                .addField(AccessFlags.ACC_PUBLIC, "x", "I").end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "setTo1", "(Lcom/test/OracleFields;)V")
                .code().aload(0).iconst(1).putfield(owner, "x", "I").vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "setTo2", "(Lcom/test/OracleFields;)V")
                .code().aload(0).iconst(2).putfield(owner, "x", "I").vreturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry setTo1 = cf.getMethod("setTo1", "(Lcom/test/OracleFields;)V");
        MethodEntry setTo2 = cf.getMethod("setTo2", "(Lcom/test/OracleFields;)V");

        RecoveryEquivalenceOracle.Verdict verdict =
                new RecoveryEquivalenceOracle().differential(setTo1, setTo2, pool);
        System.out.println("[oracle fieldwrite] setTo1 vs setTo2 -> " + verdict);

        assertEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /**
     * Two methods that call the same sinks in a different order must be caught: reordering
     * side-effecting calls is a real behavioural change, not benign restructuring.
     */
    @Test
    void callOrderDivergenceIsCaught() throws Exception {
        String owner = "com/test/OracleOrder";
        byte[] bytes = ClassBuilder.create(owner)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sinkA", "(I)V")
                .code().vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sinkB", "(I)V")
                .code().vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "callAB", "()V")
                .code().iconst(0).invokestatic(owner, "sinkA", "(I)V")
                       .iconst(0).invokestatic(owner, "sinkB", "(I)V").vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "callBA", "()V")
                .code().iconst(0).invokestatic(owner, "sinkB", "(I)V")
                       .iconst(0).invokestatic(owner, "sinkA", "(I)V").vreturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry ab = cf.getMethod("callAB", "()V");
        MethodEntry ba = cf.getMethod("callBA", "()V");

        RecoveryEquivalenceOracle.Verdict verdict =
                new RecoveryEquivalenceOracle().differential(ab, ba, pool);
        System.out.println("[oracle callorder] callAB vs callBA -> " + verdict);

        assertEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /**
     * Negative control: a behaviourally identical method that merely stores through an extra local
     * must NOT be flagged - benign restructuring is not a divergence.
     */
    @Test
    void benignRestructureIsEquivalent() throws Exception {
        byte[] bytes = ClassBuilder.create("com/test/OracleBenign")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "addDirect", "(II)I")
                .code().iload(0).iload(1).iadd().ireturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "addViaLocal", "(II)I")
                .code().iload(0).iload(1).iadd().istore(2).iload(2).ireturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry direct = cf.getMethod("addDirect", "(II)I");
        MethodEntry viaLocal = cf.getMethod("addViaLocal", "(II)I");

        RecoveryEquivalenceOracle.Verdict verdict =
                new RecoveryEquivalenceOracle().differential(direct, viaLocal, pool);
        System.out.println("[oracle benign] addDirect vs addViaLocal -> " + verdict);

        assertNotEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /** The same comparison must produce an identical verdict and counterexample across runs. */
    @Test
    void verdictIsDeterministic() throws Exception {
        byte[] bytes = ClassBuilder.create("com/test/OracleDet")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
                .code().iload(0).iload(1).iadd().ireturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sub", "(II)I")
                .code().iload(0).iload(1).isub().ireturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry add = cf.getMethod("add", "(II)I");
        MethodEntry sub = cf.getMethod("sub", "(II)I");

        RecoveryEquivalenceOracle.Verdict v1 = new RecoveryEquivalenceOracle().differential(add, sub, pool);
        RecoveryEquivalenceOracle.Verdict v2 = new RecoveryEquivalenceOracle().differential(add, sub, pool);
        System.out.println("[oracle determinism] run1=" + v1 + " run2=" + v2);

        assertEquals(v1.kind, v2.kind);
        assertEquals(v1.detail, v2.detail);
    }

    /**
     * Coverage guidance must reach a branch guarded by a specific constant that random inputs would
     * essentially never hit. withSink and noSink differ only inside {@code if (x == 987654321)}; the
     * constant is in the bytecode, so the dictionary feeds it and the divergence is caught.
     */
    @Test
    void coverageGuidanceReachesGuardedBranch() throws Exception {
        String owner = "com/test/OracleGuarded";
        byte[] bytes = ClassBuilder.create(owner)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "sink", "(I)V")
                .code().vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "withSink", "(I)V")
                .code().iload(0).ldc(987654321).if_icmpne("end")
                       .iload(0).invokestatic(owner, "sink", "(I)V")
                       .label("end").vreturn().end()
                .end()
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "noSink", "(I)V")
                .code().iload(0).ldc(987654321).if_icmpne("end")
                       .label("end").vreturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry withSink = cf.getMethod("withSink", "(I)V");
        MethodEntry noSink = cf.getMethod("noSink", "(I)V");

        RecoveryEquivalenceOracle.Verdict verdict =
                new RecoveryEquivalenceOracle().differential(withSink, noSink, pool);
        System.out.println("[oracle coverage] withSink vs noSink -> " + verdict);

        assertEquals(RecoveryEquivalenceOracle.Kind.NOT_EQUIVALENT, verdict.kind, verdict.toString());
    }

    /** A method the engine cannot run (native, no code) must be INCONCLUSIVE, never a crash. */
    @Test
    void nativeMethodIsInconclusive() throws Exception {
        byte[] bytes = ClassBuilder.create("com/test/OracleNative")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_NATIVE, "nat", "()V")
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry nat = cf.getMethod("nat", "()V");

        RecoveryEquivalenceOracle.Verdict verdict = new RecoveryEquivalenceOracle().check(cf, pool, nat);
        System.out.println("[oracle native] nat()V -> " + verdict);

        assertEquals(RecoveryEquivalenceOracle.Kind.INCONCLUSIVE, verdict.kind, verdict.toString());
    }

    /**
     * With a cache set, checking the same unchanged method twice reuses the verdict on the second call
     * without re-executing (one miss, then one hit). A hit requires the recompiled-bytecode hash to
     * match across runs, so this also confirms recompilation is deterministic.
     */
    @Test
    void cacheReusesVerdictWithoutReExecuting() throws Exception {
        byte[] bytes = ClassBuilder.create("com/test/OracleCacheCtl")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "add", "(II)I")
                .code().iload(0).iload(1).iadd().ireturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry add = cf.getMethod("add", "(II)I");

        OracleCache cache = new OracleCache();
        RecoveryEquivalenceOracle oracle = new RecoveryEquivalenceOracle().cache(cache);

        RecoveryEquivalenceOracle.Verdict first = oracle.check(cf, pool, add);
        RecoveryEquivalenceOracle.Verdict second = oracle.check(cf, pool, add);
        System.out.println("[oracle cache] first=" + first + " second=" + second
                + " hits=" + cache.hits() + " misses=" + cache.misses());

        assertEquals(first.kind, second.kind);
        assertEquals(first.detail, second.detail);
        assertEquals(1, cache.misses(), "first check should be a cache miss");
        assertEquals(1, cache.hits(), "second check should hit the cache (deterministic recompile)");
    }
}
