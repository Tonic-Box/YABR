package com.tonic.analysis.graph.print;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GraphPrinterConfig {

    @Builder.Default
    private Verbosity verbosity = Verbosity.NORMAL;

    @Builder.Default
    private boolean showNodeIds = true;

    @Builder.Default
    private boolean showEdgeLabels = true;

    @Builder.Default
    private boolean showProperties = false;

    @Builder.Default
    private boolean showLineNumbers = true;

    @Builder.Default
    private boolean groupByMethod = true;

    @Builder.Default
    private boolean showStatistics = true;

    @Builder.Default
    private String indentString = "  ";

    @Builder.Default
    private int maxNodesPerMethod = 100;

    @Builder.Default
    private boolean truncateLongLabels = true;

    @Builder.Default
    private int maxLabelLength = 60;

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
}
