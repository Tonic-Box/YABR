package com.tonic.analysis.oracle;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.instruction.BipushInstruction;
import com.tonic.analysis.instruction.IConstInstruction;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.analysis.instruction.LConstInstruction;
import com.tonic.analysis.instruction.Ldc2WInstruction;
import com.tonic.analysis.instruction.LdcInstruction;
import com.tonic.analysis.instruction.LdcWInstruction;
import com.tonic.analysis.instruction.SipushInstruction;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.LongItem;
import com.tonic.util.DescriptorUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Coverage-guided input generation for the recovery oracle. Instead of only random inputs, it seeds a
 * corpus, then mutates each interesting input one primitive slot at a time over a dictionary of
 * boundary values plus the integer/long constants that appear in the method's own bytecode (which are
 * exactly the values its branches compare against). An input is kept only if it reaches a
 * branch-direction not seen before, so the corpus grows toward covering more of the control flow
 * without a constraint solver. Objects stay opaque; only primitives and reference-nullness are mutated.
 */
final class CoverageGuidedInputs {

    private CoverageGuidedInputs() {
    }

    /**
     * @param probe runs the reference method on a spec and returns the branch edges it covered, or
     *              {@code null} if that input could not be executed.
     */
    static List<InputSpec> generate(String descriptor, long[] dict, long seed,
                                    int seedCount, int corpusCap, int probeBudget,
                                    Function<InputSpec, Set<String>> probe) {
        List<String> params = DescriptorUtil.parseParameterDescriptors(descriptor);
        Random rnd = new Random(seed);

        List<InputSpec> corpus = new ArrayList<>();
        Set<String> covered = new HashSet<>();
        Deque<InputSpec> queue = new ArrayDeque<>();
        int[] runs = {0};

        for (int s = 0; s < seedCount; s++) {
            InputSpec spec = randomSpec(params, dict, rnd);
            corpus.add(spec);
            queue.add(spec);
            probeAndMark(spec, probe, covered);
            runs[0]++;
        }

        while (!queue.isEmpty() && corpus.size() < corpusCap && runs[0] < probeBudget) {
            explore(queue.poll(), params, dict, corpus, queue, covered, probe, corpusCap, probeBudget, runs);
        }
        return corpus;
    }

    private static void explore(InputSpec parent, List<String> params, long[] dict,
                                List<InputSpec> corpus, Deque<InputSpec> queue, Set<String> covered,
                                Function<InputSpec, Set<String>> probe, int corpusCap, int probeBudget, int[] runs) {
        for (int i = 0; i < params.size(); i++) {
            String pdesc = params.get(i);
            if (InputSpec.isPrimitive(pdesc)) {
                for (long d : dict) {
                    if (parent.prim[i] == d) {
                        continue;
                    }
                    if (corpus.size() >= corpusCap || runs[0] >= probeBudget) {
                        return;
                    }
                    InputSpec m = parent.copy();
                    m.prim[i] = d;
                    runs[0]++;
                    if (probeAndMark(m, probe, covered)) {
                        corpus.add(m);
                        queue.add(m);
                    }
                }
            } else if (InputSpec.isNullableRef(pdesc)) {
                if (corpus.size() >= corpusCap || runs[0] >= probeBudget) {
                    return;
                }
                InputSpec m = parent.copy();
                m.refNull[i] = !m.refNull[i];
                runs[0]++;
                if (probeAndMark(m, probe, covered)) {
                    corpus.add(m);
                    queue.add(m);
                }
            }
        }
    }

    private static boolean probeAndMark(InputSpec spec, Function<InputSpec, Set<String>> probe, Set<String> covered) {
        Set<String> features = probe.apply(spec);
        if (features == null) {
            return false;
        }
        boolean fresh = false;
        for (String f : features) {
            if (covered.add(f)) {
                fresh = true;
            }
        }
        return fresh;
    }

    private static InputSpec randomSpec(List<String> params, long[] dict, Random rnd) {
        InputSpec spec = new InputSpec(params.size());
        for (int i = 0; i < params.size(); i++) {
            String d = params.get(i);
            if (InputSpec.isPrimitive(d)) {
                spec.prim[i] = (dict.length > 0 && rnd.nextInt(2) == 0)
                        ? dict[rnd.nextInt(dict.length)] : (rnd.nextInt(21) - 10);
            } else if (InputSpec.isNullableRef(d)) {
                spec.refNull[i] = rnd.nextInt(4) == 0;
            }
        }
        return spec;
    }

    /** Boundary values plus every int/long constant embedded in the method's bytecode (and c±1). */
    static long[] extractDictionary(MethodEntry method) {
        LinkedHashSet<Long> dict = new LinkedHashSet<>();
        for (long b : new long[]{0, 1, -1, 2, -2, 3, 8, 10, -10, 16, 100, 127, -128, 255, 256, 1000,
                Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE}) {
            dict.add(b);
        }
        try {
            for (Instruction ins : new CodeWriter(method).getInstructions()) {
                Long c = constantOf(ins);
                if (c != null) {
                    dict.add(c);
                    dict.add(c - 1);
                    dict.add(c + 1);
                }
            }
        } catch (Throwable ignored) {
        }
        long[] out = new long[dict.size()];
        int i = 0;
        for (long v : dict) {
            out[i++] = v;
        }
        return out;
    }

    private static Long constantOf(Instruction ins) {
        if (ins instanceof IConstInstruction) {
            return (long) ((IConstInstruction) ins).getValue();
        }
        if (ins instanceof BipushInstruction) {
            return (long) ((BipushInstruction) ins).getValue();
        }
        if (ins instanceof SipushInstruction) {
            return (long) ((SipushInstruction) ins).getValue();
        }
        if (ins instanceof LConstInstruction) {
            return ((LConstInstruction) ins).getValue();
        }
        if (ins instanceof LdcInstruction) {
            LdcInstruction ldc = (LdcInstruction) ins;
            Item<?> item = ldc.getConstPool().getItem(ldc.getCpIndex());
            if (item instanceof IntegerItem) {
                return (long) (int) ((IntegerItem) item).getValue();
            }
            if (item instanceof LongItem) {
                return ((LongItem) item).getValue();
            }
        }
        if (ins instanceof LdcWInstruction) {
            LdcWInstruction ldc = (LdcWInstruction) ins;
            Item<?> item = ldc.getConstPool().getItem(ldc.getCpIndex());
            if (item instanceof IntegerItem) {
                return (long) (int) ((IntegerItem) item).getValue();
            }
            if (item instanceof LongItem) {
                return ((LongItem) item).getValue();
            }
        }
        if (ins instanceof Ldc2WInstruction) {
            Ldc2WInstruction ldc = (Ldc2WInstruction) ins;
            Item<?> item = ldc.getConstPool().getItem(ldc.getCpIndex());
            if (item instanceof LongItem) {
                return ((LongItem) item).getValue();
            }
        }
        return null;
    }
}
