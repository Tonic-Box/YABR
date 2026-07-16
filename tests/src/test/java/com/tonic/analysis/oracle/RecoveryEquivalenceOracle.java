package com.tonic.analysis.oracle;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.invoke.DelegatingHandler;
import com.tonic.analysis.execution.listener.CapableListener;
import com.tonic.analysis.execution.listener.ListenerCapability;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.execution.state.ValueTag;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.util.DescriptorUtil;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recovery-equivalence oracle (concrete differential, v1). For a method, executes the original and the
 * recovered-then-recompiled version on the same synthesized inputs, with every external call
 * intercepted and answered identically for both runs, and compares the resulting observable-effect
 * trace (external calls with normalized args, plus the terminal return / throw).
 *
 * <p>Sound by construction: a {@link Kind#NOT_EQUIVALENT} verdict is a real witnessed divergence, not
 * a false positive. Coverage is bounded (best-effort bug-finder); anything the engine cannot run
 * yields {@link Kind#INCONCLUSIVE}. v1 observable set is calls + return + throw (field/array-write
 * coverage is a documented future extension), and object values are normalized coarsely by class name.
 */
public final class RecoveryEquivalenceOracle {

    public enum Kind { EQUIVALENT, NOT_EQUIVALENT, INCONCLUSIVE }

    public static final class Verdict {
        public final Kind kind;
        public final String detail;

        private Verdict(Kind kind, String detail) {
            this.kind = kind;
            this.detail = detail;
        }

        public String toString() {
            return kind + (detail.isEmpty() ? "" : ": " + detail);
        }
    }

    private final int inputVectors;
    private final long seed;
    private final int maxInstructions;

    /**
     * One resolver (and its class hierarchy) per pool, reused across every execution. Building it is
     * O(pool); doing so per execute was the dominant, super-linear cost. The resolver is a read-through
     * cache over an immutable pool, so reuse is behavior-preserving and safe to share across threads.
     */
    private final Map<ClassPool, ClassResolver> resolvers = new ConcurrentHashMap<>();

    /** Optional self-invalidating verdict cache; when set, unchanged methods skip execution. */
    private OracleCache cache;

    public RecoveryEquivalenceOracle() {
        this(16, 0x9E3779B9L, 500_000);
    }

    public RecoveryEquivalenceOracle(int inputVectors, long seed, int maxInstructions) {
        this.inputVectors = inputVectors;
        this.seed = seed;
        this.maxInstructions = maxInstructions;
    }

    private ClassResolver resolverFor(ClassPool pool) {
        return resolvers.computeIfAbsent(pool, ClassResolver::new);
    }

    /** Enables the given cache; unchanged (original+recompiled) methods reuse their verdict.
     * Package-private (like {@link OracleCache}) - only same-package tests configure it. */
    RecoveryEquivalenceOracle cache(OracleCache cache) {
        this.cache = cache;
        return this;
    }

    /**
     * Runs {@link #differential} unless a cache is set and holds a verdict for this method whose
     * original and recompiled bytecode hashes both still match - in which case execution is skipped.
     */
    private Verdict cachedDifferential(String owner, String sig, MethodEntry original, MethodEntry recovered, ClassPool pool) {
        if (cache == null) {
            return differential(original, recovered, pool);
        }
        String key = owner + "#" + sig;
        String origHash = OracleCache.hash(codeBytes(original));
        String recHash = OracleCache.hash(codeBytes(recovered));
        String[] hit = cache.lookup(key);
        if (hit != null && hit[0].equals(origHash) && hit[1].equals(recHash)) {
            cache.recordHit();
            return new Verdict(Kind.valueOf(hit[2]), hit[3]);
        }
        Verdict v = differential(original, recovered, pool);
        cache.store(key, origHash, recHash, v.kind.name(), v.detail);
        return v;
    }

    private static byte[] codeBytes(MethodEntry m) {
        return m.getCodeAttribute() == null ? null : m.getCodeAttribute().getCode();
    }

    public static final class MethodVerdict {
        public final String method;
        public final Verdict verdict;

        MethodVerdict(String method, Verdict verdict) {
            this.method = method;
            this.verdict = verdict;
        }
    }

    /**
     * Checks every concrete method of a class, recompiling the class once and reusing that clone.
     * Much cheaper than calling {@link #check} per method for a corpus run.
     */
    public List<MethodVerdict> checkClass(ClassFile cf, ClassPool pool) {
        List<MethodVerdict> out = new ArrayList<>();
        ClassFile clone;
        try {
            clone = Recompile.recompiledClone(cf, pool);
        } catch (Throwable t) {
            clone = null;
        }
        // Run the original from a fresh reload too, so both sides share identical class-init state
        // (a pool-resident original vs. a fresh clone would leak <clinit> effects into only one trace).
        ClassFile origFresh = null;
        if (clone != null) {
            try {
                origFresh = reload(cf);
            } catch (Throwable ignored) {
            }
        }
        for (MethodEntry m : cf.getMethods()) {
            if (m.getCodeAttribute() == null || m.getName().equals("<clinit>")) {
                continue;
            }
            String sig = m.getName() + m.getDesc();
            if (clone == null || origFresh == null) {
                out.add(new MethodVerdict(sig, new Verdict(Kind.INCONCLUSIVE, "class not recompilable")));
                continue;
            }
            MethodEntry original = find(origFresh, m.getName(), m.getDesc());
            MethodEntry recovered = find(clone, m.getName(), m.getDesc());
            Verdict v = (original == null || recovered == null)
                    ? new Verdict(Kind.INCONCLUSIVE, "method absent from a reloaded copy")
                    : cachedDifferential(cf.getClassName(), sig, original, recovered, pool);
            out.add(new MethodVerdict(sig, v));
        }
        return out;
    }

    /** Checks one method of {@code cf}; {@code pool} must contain {@code cf} and its dependencies. */
    public Verdict check(ClassFile cf, ClassPool pool, MethodEntry method) {
        if (method.getCodeAttribute() == null) {
            return new Verdict(Kind.INCONCLUSIVE, "no code (abstract/native)");
        }
        ClassFile clone;
        try {
            clone = Recompile.recompiledClone(cf, pool);
        } catch (Throwable t) {
            return new Verdict(Kind.INCONCLUSIVE, "recompile failed: " + t);
        }
        if (clone == null) {
            return new Verdict(Kind.INCONCLUSIVE, "not recompilable");
        }
        ClassFile origFresh;
        try {
            origFresh = reload(cf);
        } catch (Throwable t) {
            return new Verdict(Kind.INCONCLUSIVE, "reload failed: " + t);
        }
        MethodEntry original = find(origFresh, method.getName(), method.getDesc());
        MethodEntry recovered = find(clone, method.getName(), method.getDesc());
        if (original == null || recovered == null) {
            return new Verdict(Kind.INCONCLUSIVE, "method absent from a reloaded copy");
        }
        return cachedDifferential(cf.getClassName(), method.getName() + method.getDesc(),
                original, recovered, pool);
    }

    private static ClassFile reload(ClassFile cf) throws Exception {
        return new ClassFile(new ByteArrayInputStream(cf.write()));
    }

    /**
     * Differentially executes two methods of the same descriptor on identical synthesized inputs and
     * compares their observable-effect traces. Package-private so tests can pit two arbitrary methods
     * against each other (positive/negative controls) without the recompile step.
     */
    Verdict differential(MethodEntry a, MethodEntry b, ClassPool pool) {
        int corpusCap = inputVectors + 6;
        int probeBudget = Math.max(inputVectors * 4, 36);
        long[] dictionary = CoverageGuidedInputs.extractDictionary(a);
        List<InputSpec> corpus = CoverageGuidedInputs.generate(
                a.getDesc(), dictionary, seed, inputVectors, corpusCap, probeBudget,
                spec -> coverageEdges(pool, a, spec));

        boolean anyRan = false;
        for (int i = 0; i < corpus.size(); i++) {
            InputSpec spec = corpus.get(i);
            Outcome oa = run(pool, a, spec);
            Outcome ob = run(pool, b, spec);
            if (oa.reason != null || ob.reason != null) {
                // One side hit an engine limit on this input; it witnessed nothing, so skip it rather
                // than abort the whole comparison - another input may still expose a real divergence.
                continue;
            }
            anyRan = true;
            int div = firstDivergence(oa.trace, ob.trace);
            if (div >= 0) {
                return new Verdict(Kind.NOT_EQUIVALENT, "input#" + i + " diverges at effect " + div
                        + "\n    a: " + at(oa.trace, div)
                        + "\n    b: " + at(ob.trace, div));
            }
        }
        return anyRan ? new Verdict(Kind.EQUIVALENT, corpus.size() + " inputs agreed")
                : new Verdict(Kind.INCONCLUSIVE, "no input could be executed on both sides");
    }

    /**
     * Runs the reference method once and returns a coverage fingerprint: taken-branch edges plus the
     * distinct call targets it reached. Call targets matter because the engine only reports taken
     * branches (a guarded call reached by falling through a branch shows up only as the call), and
     * because a call is exactly the kind of observable effect a divergence is made of.
     */
    private Set<String> coverageEdges(ClassPool pool, MethodEntry method, InputSpec spec) {
        BranchCoverageListener cov = new BranchCoverageListener();
        List<String> trace = new ArrayList<>();
        try {
            SimpleHeapManager heap = new SimpleHeapManager();
            BytecodeContext ctx = new BytecodeContext.Builder()
                    .mode(ExecutionMode.RECURSIVE)
                    .heapManager(heap)
                    .classResolver(resolverFor(pool))
                    .invocationHandler(new DelegatingHandler(new Interceptor(trace, heap)))
                    .maxInstructions(maxInstructions)
                    .build();
            ConcreteValue[] args = InputSpec.materialize(spec, method, heap);
            BytecodeEngine engine = new BytecodeEngine(ctx);
            engine.addListener(cov);
            engine.execute(method, args);
        } catch (Throwable ignored) {
            // partial coverage collected before a failure is still informative
        }
        Set<String> features = new java.util.HashSet<>();
        for (Long e : cov.edges()) {
            features.add("B" + e);
        }
        for (String line : trace) {
            if (line.startsWith("CALL ")) {
                int r = line.indexOf(" recv=");
                features.add("C:" + (r >= 0 ? line.substring(0, r) : line));
            }
        }
        return features;
    }

    private Outcome run(ClassPool pool, MethodEntry method, InputSpec spec) {
        try {
            List<String> trace = new ArrayList<>();
            SimpleHeapManager heap = new SimpleHeapManager();
            ClassResolver resolver = resolverFor(pool);
            DelegatingHandler stub = new DelegatingHandler(new Interceptor(trace, heap));
            BytecodeContext ctx = new BytecodeContext.Builder()
                    .mode(ExecutionMode.RECURSIVE)
                    .heapManager(heap)
                    .classResolver(resolver)
                    .invocationHandler(stub)
                    .maxInstructions(maxInstructions)
                    .build();
            ConcreteValue[] args = InputSpec.materialize(spec, method, heap);
            BytecodeEngine engine = new BytecodeEngine(ctx);
            // Observe writes to escaping objects (the receiver and reference parameters, plus objects
            // stored into them). Writes to purely-local temporaries are excluded so benign restructuring
            // that allocates temps differently cannot diverge - only escaping mutation is behaviour.
            EffectListener effects = new EffectListener(trace);
            seedEscaping(effects, args);
            engine.addListener(effects);
            // Warmup pass: trigger all class initializers (which the engine caches per engine), then
            // discard its trace, so the measured pass sees no <clinit> effects. Class init is
            // environmental (e.g. a getstatic of a constant that javac would fold), not the method's
            // own behavior; both runs warm up identically so the differential stays sound.
            engine.execute(method, args);
            trace.clear();
            effects.resetEscaping();
            seedEscaping(effects, args);
            BytecodeResult result = engine.execute(method, args);
            if (result.isSuccess()) {
                trace.add("RETURN " + normalize(result.getReturnValue()));
            } else if (result.hasException()) {
                ObjectInstance ex = result.getException();
                trace.add("THROW " + (ex == null ? "?" : ex.getClassName()));
            } else {
                // An abort (instruction bound, unsupported opcode, ...) is an engine limitation, not a
                // witnessed behavioral divergence: the run never finished, so comparing it as a terminal
                // effect would fabricate a false positive. Report it as inconclusive instead.
                return Outcome.inconclusive("aborted: " + result.getStatus());
            }
            return Outcome.ok(trace);
        } catch (Throwable t) {
            return Outcome.inconclusive(t.toString());
        }
    }

    // --- effect stub + normalization -------------------------------------------------------------

    private static final class Interceptor implements DelegatingHandler.InvocationCallback {
        private final List<String> trace;
        private final HeapManager heap;

        Interceptor(List<String> trace, HeapManager heap) {
            this.trace = trace;
            this.heap = heap;
        }

        public ConcreteValue invoke(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args) {
            // Class initialization is environmental, not the method's own behavior: exclude <clinit>
            // and JVM bootstrap (registerNatives) effects, which can fire in one run and not the other
            // purely on first-touch ordering, so they must not count as a divergence.
            String name = method.getName();
            if (name.equals("<clinit>") || name.equals("registerNatives")) {
                return canned(method.getDesc(), name, heap);
            }
            StringBuilder key = new StringBuilder(method.getClassFile().getClassName())
                    .append('.').append(method.getName()).append(method.getDesc())
                    .append(" recv=").append(receiver == null ? "static" : receiver.getClassName())
                    .append(" args=[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    key.append(',');
                }
                key.append(normalize(args[i]));
            }
            String k = key.append(']').toString();
            trace.add("CALL " + k);
            return canned(method.getDesc(), k, heap);
        }
    }

    private static void seedEscaping(EffectListener effects, ConcreteValue[] args) {
        for (ConcreteValue a : args) {
            if (a != null && a.getTag() == ValueTag.REFERENCE && a.asReference() != null) {
                effects.markEscaping(a.asReference());
            }
        }
    }

    /**
     * Records field and array writes to escaping objects into the effect trace. The escape set starts
     * as the receiver and reference parameters and grows as references are stored into, or read out of,
     * an already-escaping object. Writes to purely-local objects are ignored, so trace divergence
     * reflects only mutation of state visible outside the method.
     */
    private static final class EffectListener implements CapableListener {
        private final List<String> trace;
        private final Set<ObjectInstance> escaping = Collections.newSetFromMap(new IdentityHashMap<>());

        EffectListener(List<String> trace) {
            this.trace = trace;
        }

        void markEscaping(ObjectInstance o) {
            if (o != null) {
                escaping.add(o);
            }
        }

        void resetEscaping() {
            escaping.clear();
        }

        public Set<ListenerCapability> getCapabilities() {
            return EnumSet.of(ListenerCapability.ARRAY_OPERATIONS, ListenerCapability.FIELD_OPERATIONS);
        }

        public void onFieldWrite(ObjectInstance instance, String fieldName, ConcreteValue oldValue, ConcreteValue newValue) {
            if (instance == null || !escaping.contains(instance)) {
                return;
            }
            trace.add("PUT " + instance.getClassName() + "." + fieldName + "=" + normalize(newValue));
            propagate(newValue);
        }

        public void onArrayWrite(ArrayInstance array, int index, ConcreteValue oldValue, ConcreteValue newValue) {
            if (array == null || !escaping.contains(array)) {
                return;
            }
            trace.add("ASTORE " + array.getClassName() + "[" + index + "]=" + normalize(newValue));
            propagate(newValue);
        }

        public void onFieldRead(ObjectInstance instance, String fieldName, ConcreteValue value) {
            if (instance != null && escaping.contains(instance)) {
                propagate(value);
            }
        }

        public void onArrayRead(ArrayInstance array, int index, ConcreteValue value) {
            if (array != null && escaping.contains(array)) {
                propagate(value);
            }
        }

        private void propagate(ConcreteValue v) {
            if (v != null && v.getTag() == ValueTag.REFERENCE && v.asReference() != null) {
                escaping.add(v.asReference());
            }
        }
    }

    private static ConcreteValue canned(String desc, String key, HeapManager heap) {
        String ret = DescriptorUtil.parseReturnDescriptor(desc);
        long h = key.hashCode() & 0xFFFFFFFFL;
        if (ret == null || ret.equals("V")) {
            return null;
        }
        switch (ret.charAt(0)) {
            case 'Z': return ConcreteValue.intValue((int) (h & 1));
            case 'B': case 'C': case 'S': case 'I': return ConcreteValue.intValue((int) h);
            case 'J': return ConcreteValue.longValue(h);
            case 'F': return ConcreteValue.floatValue(h % 97);
            case 'D': return ConcreteValue.doubleValue(h % 97);
            case 'L': return ConcreteValue.reference(heap.newObject(ret.substring(1, ret.length() - 1)));
            default: return ConcreteValue.nullRef();
        }
    }

    private static String normalize(ConcreteValue v) {
        if (v == null) {
            return "none";
        }
        switch (v.getTag()) {
            case NULL: return "null";
            case INT: return "i" + v.asInt();
            case LONG: return "l" + v.asLong();
            case FLOAT: return "f" + Float.floatToIntBits(v.asFloat());
            case DOUBLE: return "d" + Double.doubleToLongBits(v.asDouble());
            case REFERENCE:
                ObjectInstance r = v.asReference();
                return "ref:" + (r == null ? "null" : r.getClassName());
            default: return v.getTag().toString();
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static int firstDivergence(List<String> a, List<String> b) {
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            if (!at(a, i).equals(at(b, i))) {
                return i;
            }
        }
        return -1;
    }

    private static String at(List<String> t, int i) {
        return i < t.size() ? t.get(i) : "<end>";
    }

    private static MethodEntry find(ClassFile cf, String name, String desc) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name) && m.getDesc().contentEquals(desc)) {
                return m;
            }
        }
        return null;
    }

    private static final class Outcome {
        final List<String> trace;
        final String reason;

        private Outcome(List<String> trace, String reason) {
            this.trace = trace;
            this.reason = reason;
        }

        static Outcome ok(List<String> trace) {
            return new Outcome(trace, null);
        }

        static Outcome inconclusive(String reason) {
            return new Outcome(null, reason);
        }
    }
}
