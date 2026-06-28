package com.tonic.analysis.graph.print;

public class GraphPrinterConfig {

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

    private GraphPrinterConfig(Builder builder) {
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

    public Verbosity getVerbosity() {
        return verbosity;
    }

    public boolean isShowNodeIds() {
        return showNodeIds;
    }

    public boolean isShowEdgeLabels() {
        return showEdgeLabels;
    }

    public boolean isShowProperties() {
        return showProperties;
    }

    public boolean isShowLineNumbers() {
        return showLineNumbers;
    }

    public boolean isGroupByMethod() {
        return groupByMethod;
    }

    public boolean isShowStatistics() {
        return showStatistics;
    }

    public String getIndentString() {
        return indentString;
    }

    public int getMaxNodesPerMethod() {
        return maxNodesPerMethod;
    }

    public boolean isTruncateLongLabels() {
        return truncateLongLabels;
    }

    public int getMaxLabelLength() {
        return maxLabelLength;
    }

    public static GraphPrinterConfig defaults() {
        return GraphPrinterConfig.builder().build();
    }

    public static GraphPrinterConfig minimal() {
        return GraphPrinterConfig.builder()
            .verbosity(Verbosity.MINIMAL)
            .showNodeIds(false)
            .showEdgeLabels(false)
            .showProperties(false)
            .showStatistics(false)
            .build();
    }

    public static GraphPrinterConfig verbose() {
        return GraphPrinterConfig.builder()
            .verbosity(Verbosity.VERBOSE)
            .showProperties(true)
            .build();
    }

    public static GraphPrinterConfig debug() {
        return GraphPrinterConfig.builder()
            .verbosity(Verbosity.DEBUG)
            .showProperties(true)
            .truncateLongLabels(false)
            .maxNodesPerMethod(Integer.MAX_VALUE)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
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

        public Builder verbosity(Verbosity verbosity) {
            this.verbosity = verbosity;
            return this;
        }

        public Builder showNodeIds(boolean showNodeIds) {
            this.showNodeIds = showNodeIds;
            return this;
        }

        public Builder showEdgeLabels(boolean showEdgeLabels) {
            this.showEdgeLabels = showEdgeLabels;
            return this;
        }

        public Builder showProperties(boolean showProperties) {
            this.showProperties = showProperties;
            return this;
        }

        public Builder showLineNumbers(boolean showLineNumbers) {
            this.showLineNumbers = showLineNumbers;
            return this;
        }

        public Builder groupByMethod(boolean groupByMethod) {
            this.groupByMethod = groupByMethod;
            return this;
        }

        public Builder showStatistics(boolean showStatistics) {
            this.showStatistics = showStatistics;
            return this;
        }

        public Builder indentString(String indentString) {
            this.indentString = indentString;
            return this;
        }

        public Builder maxNodesPerMethod(int maxNodesPerMethod) {
            this.maxNodesPerMethod = maxNodesPerMethod;
            return this;
        }

        public Builder truncateLongLabels(boolean truncateLongLabels) {
            this.truncateLongLabels = truncateLongLabels;
            return this;
        }

        public Builder maxLabelLength(int maxLabelLength) {
            this.maxLabelLength = maxLabelLength;
            return this;
        }

        public GraphPrinterConfig build() {
            return new GraphPrinterConfig(this);
        }
    }
}
