package com.tonic.analysis.query.exec;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.analysis.query.ast.Condition;
import com.tonic.analysis.query.ast.OrderBy;
import com.tonic.analysis.query.ast.Query;
import com.tonic.analysis.query.ast.Target;
import com.tonic.analysis.query.eval.AttributeRegistry;
import com.tonic.analysis.query.eval.ConditionEvaluator;
import com.tonic.analysis.query.eval.DefaultAttributes;
import com.tonic.analysis.query.eval.EvalContext;
import com.tonic.analysis.query.eval.EvidenceCollector;
import com.tonic.analysis.query.eval.Subject;
import com.tonic.analysis.query.planner.ProbePlan;
import com.tonic.analysis.query.planner.QueryMatch;
import com.tonic.analysis.query.planner.QueryTarget;
import com.tonic.analysis.query.planner.filter.StaticFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executes a {@link ProbePlan} by evaluating its query condition over the scope-filtered candidates
 * with the registry-driven {@link ConditionEvaluator}. Matches are projected into {@link QueryMatch}
 * results whose evidence points at the bytecode sites the evaluator collected.
 */
public class QueryBatchRunner {

    private static final AttributeRegistry REGISTRY = DefaultAttributes.create();

    private final ClassPool classPool;

    private long timeBudgetMs = 60_000;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Set<String> userClassNames;

    public QueryBatchRunner(ClassPool classPool) {
        this.classPool = classPool;
    }

    public void setUserClassNames(Set<String> userClassNames) {
        this.userClassNames = userClassNames;
    }

    public void setTimeBudgetMs(long ms) {
        this.timeBudgetMs = ms;
    }

    public void cancel() {
        cancelled.set(true);
    }

    public QueryBatchResult run(ProbePlan plan, ProgressListener listener) {
        cancelled.set(false);
        long startTime = System.currentTimeMillis();

        StaticFilter scopeFilter = plan.staticFilter();
        Target target = plan.originalQuery().target();
        Condition condition = plan.originalQuery().condition();
        ConditionEvaluator evaluator = new ConditionEvaluator(REGISTRY);

        List<ClassFile> classes = classPool.getClasses().stream()
                .filter(cf -> userClassNames == null || userClassNames.isEmpty()
                        || userClassNames.contains(cf.getClassName()))
                .collect(Collectors.toList());

        List<QueryMatch> matches = new ArrayList<>();
        int candidateCount;

        if (target == Target.CLASSES) {
            Set<ClassFile> candidates = scopeFilter.filterClasses(classes.stream());
            candidateCount = candidates.size();
            if (listener != null) {
                listener.onPhaseStart("Evaluating", candidateCount);
            }
            for (ClassFile cf : candidates) {
                if (cancelled.get() || overBudget(startTime)) break;
                EvalContext ctx = new EvalContext(cf, null, new EvidenceCollector());
                if (condition == null || evaluator.eval(condition, new Subject.ClassSubject(cf, ctx))) {
                    matches.add(classMatch(cf));
                }
            }
        } else {
            Stream<MethodEntry> allMethods = classes.stream().flatMap(cf -> cf.getMethods().stream());
            Set<MethodEntry> candidates = scopeFilter.filterMethods(allMethods);
            candidateCount = candidates.size();
            if (listener != null) {
                listener.onPhaseStart("Evaluating", candidateCount);
            }
            int scanned = 0;
            for (ClassFile cf : classes) {
                if (cancelled.get() || overBudget(startTime)) break;
                for (MethodEntry method : cf.getMethods()) {
                    if (!candidates.contains(method)) continue;
                    EvidenceCollector evidence = new EvidenceCollector();
                    EvalContext ctx = new EvalContext(cf, method, evidence);
                    if (condition == null || evaluator.eval(condition, new Subject.MethodSubject(method, ctx))) {
                        matches.add(methodMatch(method, evidence));
                    }
                    if (listener != null && ++scanned % 200 == 0) {
                        listener.onProgress(scanned, candidateCount, "Matched " + matches.size());
                    }
                }
            }
        }

        matches = applyOrderingAndLimit(matches, plan.originalQuery());

        if (listener != null) {
            listener.onComplete(matches.size());
        }

        return new QueryBatchResult(matches, cancelled.get());
    }

    /** Applies the query's {@code ORDER BY} (sort by an attribute) then {@code LIMIT} (truncate). */
    private static List<QueryMatch> applyOrderingAndLimit(List<QueryMatch> matches, Query query) {
        OrderBy orderBy = query.orderBy();
        if (orderBy != null) {
            Comparator<QueryMatch> cmp = (a, b) ->
                    compareValues(a.getAttribute(orderBy.key()), b.getAttribute(orderBy.key()));
            matches.sort(orderBy.ascending() ? cmp : cmp.reversed());
        }
        Integer limit = query.limit();
        if (limit != null && limit >= 0 && matches.size() > limit) {
            return new ArrayList<>(matches.subList(0, limit));
        }
        return matches;
    }

    /** Orders two attribute values numerically when both look numeric, else case-insensitively by text. */
    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        Double na = toNumber(a);
        Double nb = toNumber(b);
        if (na != null && nb != null) {
            return Double.compare(na, nb);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static Double toNumber(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean overBudget(long startTime) {
        return System.currentTimeMillis() - startTime > timeBudgetMs;
    }

    private QueryMatch methodMatch(MethodEntry method, EvidenceCollector evidence) {
        String className = method.getOwnerName();
        String methodName = method.getName();
        String desc = method.getDesc();

        List<QueryMatch> evidenceMatches = new ArrayList<>();
        for (EvidenceCollector.Hit hit : evidence.hits()) {
            QueryTarget hitTarget = new QueryTarget.PCTarget(hit.className(), hit.methodName(), hit.descriptor(), hit.pc());
            evidenceMatches.add(QueryMatch.builder(hitTarget)
                    .attribute("detail", hit.label())
                    .build());
        }

        return QueryMatch.builder(new QueryTarget.MethodTarget(className, methodName, desc))
                .attribute("class", className)
                .attribute("method", methodName)
                .attribute("matches", evidenceMatches.size())
                .evidence(evidenceMatches)
                .build();
    }

    private QueryMatch classMatch(ClassFile cf) {
        return QueryMatch.builder(new QueryTarget.ClassTarget(cf.getClassName()))
                .attribute("class", cf.getClassName())
                .build();
    }

    public static final class QueryBatchResult {
        private final List<QueryMatch> matches;
        private final boolean wasCancelled;

        public QueryBatchResult(List<QueryMatch> matches, boolean wasCancelled) {
            this.matches = matches;
            this.wasCancelled = wasCancelled;
        }

        public List<QueryMatch> matches() {
            return matches;
        }

        public boolean wasCancelled() {
            return wasCancelled;
        }
    }

    public interface ProgressListener {
        void onPhaseStart(String phase, int total);
        void onProgress(int current, int total, String message);
        void onComplete(int matchCount);
    }
}
