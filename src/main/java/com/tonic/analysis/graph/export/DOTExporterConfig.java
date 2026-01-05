package com.tonic.analysis.graph.export;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DOTExporterConfig {

    @Builder.Default
    private String graphName = "G";

    @Builder.Default
    private boolean directed = true;

    @Builder.Default
    private boolean includeLegend = true;

    @Builder.Default
    private boolean clusterByMethod = true;

    @Builder.Default
    private boolean showNodeIds = true;

    @Builder.Default
    private boolean truncateLabels = true;

    @Builder.Default
    private int maxLabelLength = 40;

    @Builder.Default
    private String fontName = "Helvetica";

    @Builder.Default
    private int fontSize = 10;

    @Builder.Default
    private String rankDir = "TB";

    @Builder.Default
    private NodeStyle defaultNodeStyle = NodeStyle.defaults();

    @Builder.Default
    private EdgeStyle defaultEdgeStyle = EdgeStyle.defaults();

    public static DOTExporterConfig defaults() {
        return DOTExporterConfig.builder().build();
    }

    public static DOTExporterConfig leftToRight() {
        return DOTExporterConfig.builder()
            .rankDir("LR")
            .build();
    }

    public static DOTExporterConfig compact() {
        return DOTExporterConfig.builder()
            .includeLegend(false)
            .clusterByMethod(false)
            .showNodeIds(false)
            .build();
    }

    @Getter
    @Builder
    public static class NodeStyle {
        @Builder.Default
        private String shape = "box";
        @Builder.Default
        private String fillColor = "white";
        @Builder.Default
        private String borderColor = "black";
        @Builder.Default
        private String style = "filled";

        public static NodeStyle defaults() {
            return NodeStyle.builder().build();
        }
    }

    @Getter
    @Builder
    public static class EdgeStyle {
        @Builder.Default
        private String color = "black";
        @Builder.Default
        private String style = "solid";
        @Builder.Default
        private String arrowHead = "normal";

        public static EdgeStyle defaults() {
            return EdgeStyle.builder().build();
        }
    }
}
