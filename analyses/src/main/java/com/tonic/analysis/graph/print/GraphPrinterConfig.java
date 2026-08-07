package com.tonic.analysis.graph.print;

/**
 * Immutable rendering settings shared by the dependence-graph printers, built
 * through {@link Builder} or one of the named presets.
 */
public class GraphPrinterConfig
{

    private final Verbosity verbosity;
    private final boolean showNodeIds;
    private final boolean showEdgeLabels;
    private final boolean showProperties;
    private final boolean showLineNumbers;
    private final boolean groupByMethod;
    private final boolean showStatistics;
    private final String indentString;
    private final int maxNodesPerMethod;
    private final boolean truncateLongLabels;
    private final int maxLabelLength;

    private GraphPrinterConfig(Builder builder)
    {
        this.verbosity = builder.verbosity;
        this.showNodeIds = builder.showNodeIds;
        this.showEdgeLabels = builder.showEdgeLabels;
        this.showProperties = builder.showProperties;
        this.showLineNumbers = builder.showLineNumbers;
        this.groupByMethod = builder.groupByMethod;
        this.showStatistics = builder.showStatistics;
        this.indentString = builder.indentString;
        this.maxNodesPerMethod = builder.maxNodesPerMethod;
        this.truncateLongLabels = builder.truncateLongLabels;
        this.maxLabelLength = builder.maxLabelLength;
    }

    /**
     * @return the verbosity
     */
    public Verbosity getVerbosity()
    {
        return verbosity;
    }

    /**
     * @return whether show node ids
     */
    public boolean isShowNodeIds()
    {
        return showNodeIds;
    }

    /**
     * @return whether show edge labels
     */
    public boolean isShowEdgeLabels()
    {
        return showEdgeLabels;
    }

    /**
     * @return whether show properties
     */
    public boolean isShowProperties()
    {
        return showProperties;
    }

    /**
     * @return whether show line numbers
     */
    public boolean isShowLineNumbers()
    {
        return showLineNumbers;
    }

    /**
     * @return whether group by method
     */
    public boolean isGroupByMethod()
    {
        return groupByMethod;
    }

    /**
     * @return whether show statistics
     */
    public boolean isShowStatistics()
    {
        return showStatistics;
    }

    /**
     * @return the indent string
     */
    public String getIndentString()
    {
        return indentString;
    }

    /**
     * @return the max nodes per method
     */
    public int getMaxNodesPerMethod()
    {
        return maxNodesPerMethod;
    }

    /**
     * @return whether truncate long labels
     */
    public boolean isTruncateLongLabels()
    {
        return truncateLongLabels;
    }

    /**
     * @return the max label length
     */
    public int getMaxLabelLength()
    {
        return maxLabelLength;
    }

    /**
     * @return a config with every builder default left in place
     */
    public static GraphPrinterConfig defaults()
    {
        return GraphPrinterConfig.builder().build();
    }

    /**
     * Builds a config that prints bare structure - no ids, edge labels, properties
     * or statistics.
     * @return the minimal config
     */
    public static GraphPrinterConfig minimal()
    {
        return GraphPrinterConfig.builder()
            .verbosity(Verbosity.MINIMAL)
            .showNodeIds(false)
            .showEdgeLabels(false)
            .showProperties(false)
            .showStatistics(false)
            .build();
    }

    /**
     * Builds a config at verbose verbosity with node properties printed.
     * @return the verbose config
     */
    public static GraphPrinterConfig verbose()
    {
        return GraphPrinterConfig.builder()
            .verbosity(Verbosity.VERBOSE)
            .showProperties(true)
            .build();
    }

    /**
     * Builds a config that prints everything: debug verbosity, properties on, no
     * label truncation and no node limit.
     * @return the debug config
     */
    public static GraphPrinterConfig debug()
    {
        return GraphPrinterConfig.builder()
            .verbosity(Verbosity.DEBUG)
            .showProperties(true)
            .truncateLongLabels(false)
            .maxNodesPerMethod(Integer.MAX_VALUE)
            .build();
    }

    /**
     * @return a fresh builder holding the default settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Mutable accumulator for the printer settings, seeded with the normal defaults.
     */
    public static class Builder
    {
        private Verbosity verbosity = Verbosity.NORMAL;
        private boolean showNodeIds = true;
        private boolean showEdgeLabels = true;
        private boolean showProperties = false;
        private boolean showLineNumbers = true;
        private boolean groupByMethod = true;
        private boolean showStatistics = true;
        private String indentString = "  ";
        private int maxNodesPerMethod = 100;
        private boolean truncateLongLabels = true;
        private int maxLabelLength = 60;

        /**
         * Sets the detail level printers compare against when deciding what to emit.
         * @param verbosity the detail level
         * @return this builder
         */
        public Builder verbosity(Verbosity verbosity)
        {
            this.verbosity = verbosity;
            return this;
        }

        /**
         * Sets whether each node is prefixed with its numeric id.
         * @param showNodeIds true to print ids
         * @return this builder
         */
        public Builder showNodeIds(boolean showNodeIds)
        {
            this.showNodeIds = showNodeIds;
            return this;
        }

        /**
         * Sets whether an edge carries its type name inline.
         * @param showEdgeLabels true to label edges
         * @return this builder
         */
        public Builder showEdgeLabels(boolean showEdgeLabels)
        {
            this.showEdgeLabels = showEdgeLabels;
            return this;
        }

        /**
         * Sets whether per-node detail lines are printed under each node.
         * @param showProperties true to print properties
         * @return this builder
         */
        public Builder showProperties(boolean showProperties)
        {
            this.showProperties = showProperties;
            return this;
        }

        /**
         * Sets whether source line numbers are printed with nodes.
         * @param showLineNumbers true to print line numbers
         * @return this builder
         */
        public Builder showLineNumbers(boolean showLineNumbers)
        {
            this.showLineNumbers = showLineNumbers;
            return this;
        }

        /**
         * Sets whether nodes are grouped under their owning procedure rather than
         * listed flat.
         * @param groupByMethod true to group by method
         * @return this builder
         */
        public Builder groupByMethod(boolean groupByMethod)
        {
            this.groupByMethod = groupByMethod;
            return this;
        }

        /**
         * Sets whether a trailing node and edge count section is printed.
         * @param showStatistics true to print statistics
         * @return this builder
         */
        public Builder showStatistics(boolean showStatistics)
        {
            this.showStatistics = showStatistics;
            return this;
        }

        /**
         * Sets the text emitted for one level of indentation.
         * @param indentString the indent unit
         * @return this builder
         */
        public Builder indentString(String indentString)
        {
            this.indentString = indentString;
            return this;
        }

        /**
         * Sets how many nodes are printed per method before the rest are elided.
         * @param maxNodesPerMethod the node budget
         * @return this builder
         */
        public Builder maxNodesPerMethod(int maxNodesPerMethod)
        {
            this.maxNodesPerMethod = maxNodesPerMethod;
            return this;
        }

        /**
         * Sets whether labels longer than the maximum length are cut short.
         * @param truncateLongLabels true to truncate
         * @return this builder
         */
        public Builder truncateLongLabels(boolean truncateLongLabels)
        {
            this.truncateLongLabels = truncateLongLabels;
            return this;
        }

        /**
         * Sets the character budget a label gets before truncation applies.
         * @param maxLabelLength the cut-off length
         * @return this builder
         */
        public Builder maxLabelLength(int maxLabelLength)
        {
            this.maxLabelLength = maxLabelLength;
            return this;
        }

        /**
         * Builds a config from the values set so far.
         * @return the immutable config
         */
        public GraphPrinterConfig build()
        {
            return new GraphPrinterConfig(this);
        }
    }
}
