package com.tonic.analysis.graph.export;

public class DOTExporterConfig {

    private final String graphName;
    private final boolean directed;
    private final boolean includeLegend;
    private final boolean clusterByMethod;
    private final boolean showNodeIds;
    private final boolean truncateLabels;
    private final int maxLabelLength;
    private final String fontName;
    private final int fontSize;
    private final String rankDir;
    private final NodeStyle defaultNodeStyle;
    private final EdgeStyle defaultEdgeStyle;

    private DOTExporterConfig(Builder builder) {
        this.graphName = builder.graphName;
        this.directed = builder.directed;
        this.includeLegend = builder.includeLegend;
        this.clusterByMethod = builder.clusterByMethod;
        this.showNodeIds = builder.showNodeIds;
        this.truncateLabels = builder.truncateLabels;
        this.maxLabelLength = builder.maxLabelLength;
        this.fontName = builder.fontName;
        this.fontSize = builder.fontSize;
        this.rankDir = builder.rankDir;
        this.defaultNodeStyle = builder.defaultNodeStyle;
        this.defaultEdgeStyle = builder.defaultEdgeStyle;
    }

    public String getGraphName() {
        return graphName;
    }

    public boolean isDirected() {
        return directed;
    }

    public boolean isIncludeLegend() {
        return includeLegend;
    }

    public boolean isClusterByMethod() {
        return clusterByMethod;
    }

    public boolean isShowNodeIds() {
        return showNodeIds;
    }

    public boolean isTruncateLabels() {
        return truncateLabels;
    }

    public int getMaxLabelLength() {
        return maxLabelLength;
    }

    public String getFontName() {
        return fontName;
    }

    public int getFontSize() {
        return fontSize;
    }

    public String getRankDir() {
        return rankDir;
    }

    public NodeStyle getDefaultNodeStyle() {
        return defaultNodeStyle;
    }

    public EdgeStyle getDefaultEdgeStyle() {
        return defaultEdgeStyle;
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String graphName = "G";
        private boolean directed = true;
        private boolean includeLegend = true;
        private boolean clusterByMethod = true;
        private boolean showNodeIds = true;
        private boolean truncateLabels = true;
        private int maxLabelLength = 40;
        private String fontName = "Helvetica";
        private int fontSize = 10;
        private String rankDir = "TB";
        private NodeStyle defaultNodeStyle = NodeStyle.defaults();
        private EdgeStyle defaultEdgeStyle = EdgeStyle.defaults();

        public Builder graphName(String graphName) {
            this.graphName = graphName;
            return this;
        }

        public Builder directed(boolean directed) {
            this.directed = directed;
            return this;
        }

        public Builder includeLegend(boolean includeLegend) {
            this.includeLegend = includeLegend;
            return this;
        }

        public Builder clusterByMethod(boolean clusterByMethod) {
            this.clusterByMethod = clusterByMethod;
            return this;
        }

        public Builder showNodeIds(boolean showNodeIds) {
            this.showNodeIds = showNodeIds;
            return this;
        }

        public Builder truncateLabels(boolean truncateLabels) {
            this.truncateLabels = truncateLabels;
            return this;
        }

        public Builder maxLabelLength(int maxLabelLength) {
            this.maxLabelLength = maxLabelLength;
            return this;
        }

        public Builder fontName(String fontName) {
            this.fontName = fontName;
            return this;
        }

        public Builder fontSize(int fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        public Builder rankDir(String rankDir) {
            this.rankDir = rankDir;
            return this;
        }

        public Builder defaultNodeStyle(NodeStyle defaultNodeStyle) {
            this.defaultNodeStyle = defaultNodeStyle;
            return this;
        }

        public Builder defaultEdgeStyle(EdgeStyle defaultEdgeStyle) {
            this.defaultEdgeStyle = defaultEdgeStyle;
            return this;
        }

        public DOTExporterConfig build() {
            return new DOTExporterConfig(this);
        }
    }

    public static class NodeStyle {
        private final String shape;
        private final String fillColor;
        private final String borderColor;
        private final String style;

        private NodeStyle(Builder builder) {
            this.shape = builder.shape;
            this.fillColor = builder.fillColor;
            this.borderColor = builder.borderColor;
            this.style = builder.style;
        }

        public String getShape() {
            return shape;
        }

        public String getFillColor() {
            return fillColor;
        }

        public String getBorderColor() {
            return borderColor;
        }

        public String getStyle() {
            return style;
        }

        public static NodeStyle defaults() {
            return NodeStyle.builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String shape = "box";
            private String fillColor = "white";
            private String borderColor = "black";
            private String style = "filled";

            public Builder shape(String shape) {
                this.shape = shape;
                return this;
            }

            public Builder fillColor(String fillColor) {
                this.fillColor = fillColor;
                return this;
            }

            public Builder borderColor(String borderColor) {
                this.borderColor = borderColor;
                return this;
            }

            public Builder style(String style) {
                this.style = style;
                return this;
            }

            public NodeStyle build() {
                return new NodeStyle(this);
            }
        }
    }

    public static class EdgeStyle {
        private final String color;
        private final String style;
        private final String arrowHead;

        private EdgeStyle(Builder builder) {
            this.color = builder.color;
            this.style = builder.style;
            this.arrowHead = builder.arrowHead;
        }

        public String getColor() {
            return color;
        }

        public String getStyle() {
            return style;
        }

        public String getArrowHead() {
            return arrowHead;
        }

        public static EdgeStyle defaults() {
            return EdgeStyle.builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String color = "black";
            private String style = "solid";
            private String arrowHead = "normal";

            public Builder color(String color) {
                this.color = color;
                return this;
            }

            public Builder style(String style) {
                this.style = style;
                return this;
            }

            public Builder arrowHead(String arrowHead) {
                this.arrowHead = arrowHead;
                return this;
            }

            public EdgeStyle build() {
                return new EdgeStyle(this);
            }
        }
    }
}
