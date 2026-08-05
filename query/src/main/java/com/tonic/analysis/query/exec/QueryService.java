package com.tonic.analysis.query.exec;

import com.tonic.parser.ClassPool;
import com.tonic.analysis.query.ast.Query;
import com.tonic.analysis.query.parser.ParseException;
import com.tonic.analysis.query.parser.QueryParser;
import com.tonic.analysis.query.planner.ProbePlan;
import com.tonic.analysis.query.planner.QueryMatch;
import com.tonic.analysis.query.planner.QueryPlanner;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Front end for running query text against a class pool - parse, plan, then execute, either inline or on a single
 * daemon worker thread.
 */
public class QueryService
{

    private final ClassPool classPool;
    private final QueryParser parser;
    private final ExecutorService executorService;

    private QueryBatchRunner currentRunner;
    private Set<String> userClassNames;

    /**
     * Creates a service over a pool, with its own parser and a single-thread daemon executor for async runs.
     * @param classPool the classes to query
     */
    public QueryService(ClassPool classPool)
    {
        this.classPool = classPool;
        this.parser = new QueryParser();
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "QueryRunner");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Restricts subsequent runs to named classes; a null set searches the whole pool.
     * @param userClassNames the class names to keep
     */
    public void setUserClassNames(Set<String> userClassNames)
    {
        this.userClassNames = userClassNames;
    }

    /**
     * Parses query text into its syntax tree.
     * @param queryText the query source
     * @return the parsed query
     * @throws ParseException if the text is not a valid query
     */
    public Query parse(String queryText) throws ParseException
    {
        return parser.parse(queryText);
    }

    /**
     * Turns a parsed query into an executable plan with its scope filter.
     * @param query the parsed query
     * @return the probe plan
     */
    public ProbePlan plan(Query query)
    {
        QueryPlanner planner = new QueryPlanner();
        return planner.plan(query);
    }

    /**
     * Runs a query on the service's worker thread; a parse failure completes the future with an error result
     * rather than exceptionally.
     * @param queryText the query source
     * @param config the time budget, or null for the runner default
     * @param progressListener notified of run progress, or null
     * @return a future for the result
     */
    public CompletableFuture<QueryResult> executeAsync(String queryText, QueryConfig config, QueryBatchRunner.ProgressListener progressListener)
    {

        return CompletableFuture.supplyAsync(() -> {
            try
            {
                return execute(queryText, config, progressListener);
            }
            catch (ParseException e)
            {
                return new QueryResult(queryText, null, null, null, 0, false, e.getMessage());
            }
        }, executorService);
    }

    /**
     * Parses, plans, and runs a query on the calling thread, timing the whole sequence.
     * @param queryText the query source
     * @param config the time budget, or null for the runner default
     * @param progressListener notified of run progress, or null
     * @return the matches, elapsed time, and whether the run completed
     * @throws ParseException if the text is not a valid query
     */
    public QueryResult execute(String queryText, QueryConfig config, QueryBatchRunner.ProgressListener progressListener) throws ParseException
    {

        long startTime = System.currentTimeMillis();

        Query query = parse(queryText);
        ProbePlan plan = plan(query);

        QueryBatchRunner runner = new QueryBatchRunner(classPool);
        currentRunner = runner;

        if (userClassNames != null)
        {
            runner.setUserClassNames(userClassNames);
        }

        if (config != null)
        {
            runner.setTimeBudgetMs(config.timeBudgetMs());
        }

        QueryBatchRunner.QueryBatchResult batchResult = runner.run(plan, progressListener);
        currentRunner = null;

        long totalTime = System.currentTimeMillis() - startTime;

        return new QueryResult(
            queryText,
            query,
            plan,
            batchResult.matches(),
            totalTime,
            !batchResult.wasCancelled(),
            null
        );
    }

    /**
     * Asks the run in progress, if any, to stop early.
     */
    public void cancel()
    {
        if (currentRunner != null)
        {
            currentRunner.cancel();
        }
    }

    /**
     * Shuts down the worker thread; async runs already submitted still finish.
     */
    public void shutdown()
    {
        executorService.shutdown();
    }

    /**
     * The outcome of one query run - the source text, its parsed and planned forms, the matches, timing, and
     * either a completion flag or a parse error message.
     */
    public static final class QueryResult
    {
        private final String queryText;
        private final Query query;
        private final ProbePlan plan;
        private final List<QueryMatch> results;
        private final long executionTimeMs;
        private final boolean completed;
        private final String error;

        public QueryResult(String queryText, Query query, ProbePlan plan, List<QueryMatch> results, long executionTimeMs, boolean completed, String error)
        {
            this.queryText = queryText;
            this.query = query;
            this.plan = plan;
            this.results = results;
            this.executionTimeMs = executionTimeMs;
            this.completed = completed;
            this.error = error;
        }

        /**
         * @return the parsed query, or null if parsing failed
         */
        public Query query() { return query; }

        /**
         * @return the matches, or null if parsing failed
         */
        public List<QueryMatch> results() { return results; }

        /**
         * @return the elapsed wall-clock time in milliseconds
         */
        public long executionTimeMs() { return executionTimeMs; }

        /**
         * @return true if the run finished without being cancelled
         */
        public boolean completed() { return completed; }

        /**
         * @return the parse error message, or null if there was none
         */
        public String error() { return error; }

        /**
         * @return true if the run failed to parse
         */
        public boolean hasError()
        {
            return error != null;
        }

        /**
         * @return the number of matches, 0 when there are none
         */
        public int resultCount()
        {
            return results != null ? results.size() : 0;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof QueryResult)) return false;
            QueryResult that = (QueryResult) o;
            return executionTimeMs == that.executionTimeMs &&
                   completed == that.completed &&
                   Objects.equals(queryText, that.queryText) &&
                   Objects.equals(query, that.query) &&
                   Objects.equals(plan, that.plan) &&
                   Objects.equals(results, that.results) &&
                   Objects.equals(error, that.error);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(queryText, query, plan, results, executionTimeMs, completed, error);
        }
    }

    /**
     * Runtime knobs for a query run - currently just the wall-clock budget that aborts long scans.
     */
    public static final class QueryConfig
    {
        private final long timeBudgetMs;

        public QueryConfig(long timeBudgetMs)
        {
            this.timeBudgetMs = timeBudgetMs;
        }

        /**
         * @return the budget in milliseconds
         */
        public long timeBudgetMs()
        {
            return timeBudgetMs;
        }

        /**
         * @return a new builder, preset to a 60 second budget
         */
        public static Builder builder()
        {
            return new Builder();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof QueryConfig)) return false;
            return timeBudgetMs == ((QueryConfig) o).timeBudgetMs;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(timeBudgetMs);
        }

        /**
         * Accumulator for a QueryConfig.
         */
        public static class Builder
        {
            private long timeBudgetMs = 60_000;

            /**
             * Sets the wall-clock budget for a run.
             * @param ms the budget in milliseconds
             * @return this builder
             */
            public Builder timeBudgetMs(long ms)
            {
                this.timeBudgetMs = ms;
                return this;
            }

            /**
             * @return a config with the accumulated budget
             */
            public QueryConfig build()
            {
                return new QueryConfig(timeBudgetMs);
            }
        }
    }
}
