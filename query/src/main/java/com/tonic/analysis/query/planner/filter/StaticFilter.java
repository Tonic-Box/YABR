package com.tonic.analysis.query.planner.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static prefilter that narrows candidate methods/classes without execution.
 */
public interface StaticFilter
{

    /**
     * Selects the methods that pass this filter.
     *
     * @param methods the candidate methods
     * @return the candidates that pass
     */
    Set<MethodEntry> filterMethods(Stream<MethodEntry> methods);

    /**
     * Selects the classes that pass this filter.
     *
     * @param classes the candidate classes
     * @return the candidates that pass
     */
    Set<ClassFile> filterClasses(Stream<ClassFile> classes);

    /**
     * Combines this filter with another, keeping only what both accept.
     *
     * @param other the filter to intersect with
     * @return the combined filter
     */
    default StaticFilter and(StaticFilter other)
    {
        return new CompositeFilter(this, other, CompositeFilter.Op.AND);
    }

    /**
     * Combines this filter with another, keeping what either accepts.
     *
     * @param other the filter to union with
     * @return the combined filter
     */
    default StaticFilter or(StaticFilter other)
    {
        return new CompositeFilter(this, other, CompositeFilter.Op.OR);
    }

    /**
     * @return a filter that accepts every candidate
     */
    static StaticFilter all()
    {
        return new AllFilter();
    }

    class AllFilter implements StaticFilter
    {
        /**
         * @param methods the candidate methods
         * @return every candidate
         */
        @Override
        public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods)
        {
            return methods.collect(Collectors.toSet());
        }

        /**
         * @param classes the candidate classes
         * @return every candidate
         */
        @Override
        public Set<ClassFile> filterClasses(Stream<ClassFile> classes)
        {
            return classes.collect(Collectors.toSet());
        }
    }

    class CompositeFilter implements StaticFilter
    {
        /**
         * How two filters' results are combined - intersection or union.
         */
        public enum Op { AND, OR }

        private final StaticFilter left;
        private final StaticFilter right;
        private final Op op;

        /**
         * Creates a filter that runs two filters over the same candidates.
         *
         * @param left the first filter
         * @param right the second filter
         * @param op how the two results are combined
         */
        public CompositeFilter(StaticFilter left, StaticFilter right, Op op)
        {
            this.left = left;
            this.right = right;
            this.op = op;
        }

        /**
         * Runs both filters over the candidates and combines their results.
         *
         * @param methods the candidate methods
         * @return the intersection of the two results under AND, their union under OR
         */
        @Override
        public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods)
        {
            Set<MethodEntry> all = methods.collect(Collectors.toSet());
            Set<MethodEntry> leftResult = left.filterMethods(all.stream());
            Set<MethodEntry> rightResult = right.filterMethods(all.stream());

            if (op == Op.AND)
            {
                leftResult.retainAll(rightResult);
            }
            else
            {
                leftResult.addAll(rightResult);
            }
            return leftResult;
        }

        /**
         * Runs both filters over the candidates and combines their results.
         *
         * @param classes the candidate classes
         * @return the intersection of the two results under AND, their union under OR
         */
        @Override
        public Set<ClassFile> filterClasses(Stream<ClassFile> classes)
        {
            Set<ClassFile> all = classes.collect(Collectors.toSet());
            Set<ClassFile> leftResult = left.filterClasses(all.stream());
            Set<ClassFile> rightResult = right.filterClasses(all.stream());

            if (op == Op.AND)
            {
                leftResult.retainAll(rightResult);
            }
            else
            {
                leftResult.addAll(rightResult);
            }
            return leftResult;
        }
    }
}
