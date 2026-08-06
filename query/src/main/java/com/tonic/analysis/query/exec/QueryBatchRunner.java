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
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Executor of a {@link ProbePlan} over a class pool, evaluating the query condition on every candidate the plan's
 * scope filter admits and projecting each hit into a {@link QueryMatch} carrying the evaluator's evidence.
 */
public class QueryBatchRunner
{

    private static final AttributeRegistry REGISTRY = DefaultAttributes.create();

    private final ClassPool classPool;

    private long timeBudgetMs = 60_000;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private Set<String> userClassNames;

    private ClassHierarchy hierarchy;
    private boolean hierarchyAttempted;

    /**
     * Creates a runner over a pool, defaulting to a 60 second budget and no user-class restriction.
     * @param classPool the classes to search
     */
    public QueryBatchRunner(ClassPool classPool)
    {
        this.classPool = classPool;
    }

    /**
     * The class hierarchy for the current pool, built once on first use (so queries that don't use
     * {@code isSubtypeOf} never pay for it) and shared across every {@link EvalContext} of a run.
     */
    private ClassHierarchy hierarchy()
    {
        if (!hierarchyAttempted)
        {
            hierarchyAttempted = true;
            try
            {
                hierarchy = ClassHierarchyBuilder.build(classPool);
            }
            catch (Exception e)
            {
                hierarchy = null;
            }
        }
        return hierarchy;
    }

    /**
     * Restricts the search to named classes; a null or empty set searches the whole pool.
     * @param userClassNames the class names to keep
     */
    public void setUserClassNames(Set<String> userClassNames)
    {
        this.userClassNames = userClassNames;
    }

    /**
     * Sets the wall-clock budget after which a run stops early and reports what it has.
     * @param ms the budget in milliseconds
     */
    public void setTimeBudgetMs(long ms)
    {
        this.timeBudgetMs = ms;
    }

    /**
     * Requests that an in-flight run stop at the next candidate; the flag is cleared when a run starts.
     */
    public void cancel()
    {
        cancelled.set(true);
    }

    /**
     * Evaluates the plan's condition over every class or method the scope filter admits, then applies the query's
     * ordering and limit.
     * @param plan the planned query
     * @param listener notified of phase start, progress every 200 methods, and completion, or null
     * @return the matches and whether the run was cancelled
     */
    public QueryBatchResult run(ProbePlan plan, ProgressListener listener)
    {
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

        if (target == Target.CLASSES)
        {
            Set<ClassFile> candidates = scopeFilter.filterClasses(classes.stream());
            candidateCount = candidates.size();
            if (listener != null)
            {
                listener.onPhaseStart("Evaluating", candidateCount);
            }
            for (ClassFile cf : candidates)
            {
                if (cancelled.get() || overBudget(startTime)) break;
                EvalContext ctx = new EvalContext(cf, null, new EvidenceCollector(), this::hierarchy);
                if (condition == null || evaluator.eval(condition, new Subject.ClassSubject(cf, ctx)))
                {
                    matches.add(classMatch(cf));
                }
            }
        }
        else
        {
            Stream<MethodEntry> allMethods = classes.stream().flatMap(cf -> cf.getMethods().stream());
            Set<MethodEntry> candidates = scopeFilter.filterMethods(allMethods);
            candidateCount = candidates.size();
            if (listener != null)
            {
                listener.onPhaseStart("Evaluating", candidateCount);
            }
            int scanned = 0;
            for (ClassFile cf : classes)
            {
                if (cancelled.get() || overBudget(startTime)) break;
                for (MethodEntry method : cf.getMethods())
                {
                    if (!candidates.contains(method)) continue;
                    EvidenceCollector evidence = new EvidenceCollector();
                    EvalContext ctx = new EvalContext(cf, method, evidence, this::hierarchy);
                    if (condition == null || evaluator.eval(condition, new Subject.MethodSubject(method, ctx)))
                    {
                        matches.add(methodMatch(method, evidence));
                    }
                    if (listener != null && ++scanned % 200 == 0)
                    {
                        listener.onProgress(scanned, candidateCount, "Matched " + matches.size());
                    }
                }
            }
        }

        matches = applyOrderingAndLimit(matches, plan.originalQuery());

        if (listener != null)
        {
            listener.onComplete(matches.size());
        }

        return new QueryBatchResult(matches, cancelled.get());
    }

    /**
     * Applies the query's {@code ORDER BY} (sort by an attribute) then {@code LIMIT} (truncate).
     */
    private static List<QueryMatch> applyOrderingAndLimit(List<QueryMatch> matches, Query query)
    {
        OrderBy orderBy = query.orderBy();
        if (orderBy != null)
        {
            Comparator<QueryMatch> cmp = (a, b) ->
                    compareValues(a.getAttribute(orderBy.key()), b.getAttribute(orderBy.key()));
            matches.sort(orderBy.ascending() ? cmp : cmp.reversed());
        }
        Integer limit = query.limit();
        if (limit != null && limit >= 0 && matches.size() > limit)
        {
            return new ArrayList<>(matches.subList(0, limit));
        }
        return matches;
    }

    /**
     * Orders two attribute values numerically when both look numeric, else case-insensitively by text.
     */
    private static int compareValues(Object a, Object b)
    {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        Double na = toNumber(a);
        Double nb = toNumber(b);
        if (na != null && nb != null)
        {
            return Double.compare(na, nb);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static Double toNumber(Object o)
    {
        if (o instanceof Number)
        {
            return ((Number) o).doubleValue();
        }
        try
        {
            return Double.parseDouble(o.toString());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private boolean overBudget(long startTime)
    {
        return System.currentTimeMillis() - startTime > timeBudgetMs;
    }

    private QueryMatch methodMatch(MethodEntry method, EvidenceCollector evidence)
    {
        String className = method.getOwnerName();
        String methodName = method.getName();
        String desc = method.getDesc();

        List<QueryMatch> evidenceMatches = new ArrayList<>();
        for (EvidenceCollector.Hit hit : evidence.hits())
        {
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

    private QueryMatch classMatch(ClassFile cf)
    {
        return QueryMatch.builder(new QueryTarget.ClassTarget(cf.getClassName()))
                .attribute("class", cf.getClassName())
                .build();
    }

    /**
     * The outcome of one run - the ordered matches plus whether the run was cut short by cancellation.
     */
    public static final class QueryBatchResult
    {
        private final List<QueryMatch> matches;
        private final boolean wasCancelled;

        public QueryBatchResult(List<QueryMatch> matches, boolean wasCancelled)
        {
            this.matches = matches;
            this.wasCancelled = wasCancelled;
        }

        /**
         * @return the matches
         */
        public List<QueryMatch> matches()
        {
            return matches;
        }

        /**
         * @return true if the run stopped on a cancellation request
         */
        public boolean wasCancelled()
        {
            return wasCancelled;
        }
    }

    /**
     * Callback for run progress, invoked on the thread executing the run.
     */
    public interface ProgressListener
    {
        /**
         * Fired when a phase begins and its workload is known.
         *
         * @param phase the phase name
         * @param total the number of items the phase will process
         */
        void onPhaseStart(String phase, int total);
        /**
         * Fired periodically as items are processed, not once per item.
         *
         * @param current the number of items processed so far
         * @param total the number of items in the phase
         * @param message a short status line
         */
        void onProgress(int current, int total, String message);
        /**
         * Fired once the run has finished, including when it stopped early.
         *
         * @param matchCount the number of matches collected
         */
        void onComplete(int matchCount);
    }
}
