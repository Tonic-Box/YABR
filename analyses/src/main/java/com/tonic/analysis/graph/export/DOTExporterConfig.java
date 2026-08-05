package com.tonic.analysis.graph.export;

/**
 * Immutable rendering settings for a {@link DOTExporter} - layout, fonts, label
 * truncation and the default node and edge styles.
 */
public class DOTExporterConfig
{

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

    private DOTExporterConfig(Builder builder)
    {
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

    /**
     * @return the graph name
     */
    public String getGraphName()
    {
        return graphName;
    }

    /**
     * @return whether directed
     */
    public boolean isDirected()
    {
        return directed;
    }

    /**
     * @return whether include legend
     */
    public boolean isIncludeLegend()
    {
        return includeLegend;
    }

    /**
     * @return whether cluster by method
     */
    public boolean isClusterByMethod()
    {
        return clusterByMethod;
    }

    /**
     * @return whether show node ids
     */
    public boolean isShowNodeIds()
    {
        return showNodeIds;
    }

    /**
     * @return whether truncate labels
     */
    public boolean isTruncateLabels()
    {
        return truncateLabels;
    }

    /**
     * @return the max label length
     */
    public int getMaxLabelLength()
    {
        return maxLabelLength;
    }

    /**
     * @return the font name
     */
    public String getFontName()
    {
        return fontName;
    }

    /**
     * @return the font size
     */
    public int getFontSize()
    {
        return fontSize;
    }

    /**
     * @return the rank dir
     */
    public String getRankDir()
    {
        return rankDir;
    }

    /**
     * @return the default node style
     */
    public NodeStyle getDefaultNodeStyle()
    {
        return defaultNodeStyle;
    }

    /**
     * @return the default edge style
     */
    public EdgeStyle getDefaultEdgeStyle()
    {
        return defaultEdgeStyle;
    }

    /**
     * @return a config with every setting left at its default
     */
    public static DOTExporterConfig defaults()
    {
        return DOTExporterConfig.builder().build();
    }

    /**
     * @return a config that lays the graph out left to right instead of top to bottom
     */
    public static DOTExporterConfig leftToRight()
    {
        return DOTExporterConfig.builder()
            .rankDir("LR")
            .build();
    }

    /**
     * @return a config with the legend, method clustering and node ids turned off
     */
    public static DOTExporterConfig compact()
    {
        return DOTExporterConfig.builder()
            .includeLegend(false)
            .clusterByMethod(false)
            .showNodeIds(false)
            .build();
    }

    /**
     * @return a new builder seeded with the default settings
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Mutable accumulator for the exporter settings.
     */
    public static class Builder
    {
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

        /**
         * Sets the identifier written after the digraph keyword.
         * @param graphName the DOT graph name
         * @return this builder
         */
        public Builder graphName(String graphName)
        {
            this.graphName = graphName;
            return this;
        }

        /**
         * Selects a digraph with arrow edges or a plain graph with undirected edges.
         * @param directed true to emit a digraph
         * @return this builder
         */
        public Builder directed(boolean directed)
        {
            this.directed = directed;
            return this;
        }

        /**
         * Controls whether a legend subgraph is emitted.
         * @param includeLegend true to emit the legend
         * @return this builder
         */
        public Builder includeLegend(boolean includeLegend)
        {
            this.includeLegend = includeLegend;
            return this;
        }

        /**
         * Controls whether nodes are grouped into per-method clusters.
         * @param clusterByMethod true to cluster
         * @return this builder
         */
        public Builder clusterByMethod(boolean clusterByMethod)
        {
            this.clusterByMethod = clusterByMethod;
            return this;
        }

        /**
         * Controls whether node ids appear in node labels.
         * @param showNodeIds true to show ids
         * @return this builder
         */
        public Builder showNodeIds(boolean showNodeIds)
        {
            this.showNodeIds = showNodeIds;
            return this;
        }

        /**
         * Controls whether over-long labels are cut down to the maximum length.
         * @param truncateLabels true to truncate
         * @return this builder
         */
        public Builder truncateLabels(boolean truncateLabels)
        {
            this.truncateLabels = truncateLabels;
            return this;
        }

        /**
         * Sets the label length at which truncation kicks in.
         * @param maxLabelLength the character budget for a label
         * @return this builder
         */
        public Builder maxLabelLength(int maxLabelLength)
        {
            this.maxLabelLength = maxLabelLength;
            return this;
        }

        /**
         * Sets the font applied to the graph, nodes and edges.
         * @param fontName the font family name
         * @return this builder
         */
        public Builder fontName(String fontName)
        {
            this.fontName = fontName;
            return this;
        }

        /**
         * Sets the graph and node font size; edges are rendered two points smaller.
         * @param fontSize the point size
         * @return this builder
         */
        public Builder fontSize(int fontSize)
        {
            this.fontSize = fontSize;
            return this;
        }

        /**
         * Sets the DOT rankdir, such as "TB" or "LR".
         * @param rankDir the layout direction
         * @return this builder
         */
        public Builder rankDir(String rankDir)
        {
            this.rankDir = rankDir;
            return this;
        }

        /**
         * Sets the style applied to nodes that carry no style of their own.
         * @param defaultNodeStyle the fallback node style
         * @return this builder
         */
        public Builder defaultNodeStyle(NodeStyle defaultNodeStyle)
        {
            this.defaultNodeStyle = defaultNodeStyle;
            return this;
        }

        /**
         * Sets the style applied to edges that carry no style of their own.
         * @param defaultEdgeStyle the fallback edge style
         * @return this builder
         */
        public Builder defaultEdgeStyle(EdgeStyle defaultEdgeStyle)
        {
            this.defaultEdgeStyle = defaultEdgeStyle;
            return this;
        }

        /**
         * @return an immutable config holding the accumulated settings
         */
        public DOTExporterConfig build()
        {
            return new DOTExporterConfig(this);
        }
    }

    /**
     * Shape and colour attributes applied to a DOT node.
     */
    public static class NodeStyle
    {
        private final String shape;
        private final String fillColor;
        private final String borderColor;
        private final String style;

        private NodeStyle(Builder builder)
        {
            this.shape = builder.shape;
            this.fillColor = builder.fillColor;
            this.borderColor = builder.borderColor;
            this.style = builder.style;
        }

        /**
         * @return the shape
         */
        public String getShape()
        {
            return shape;
        }

        /**
         * @return the fill color
         */
        public String getFillColor()
        {
            return fillColor;
        }

        /**
         * @return the border color
         */
        public String getBorderColor()
        {
            return borderColor;
        }

        /**
         * @return the style
         */
        public String getStyle()
        {
            return style;
        }

        /**
         * @return a filled white box with a black border
         */
        public static NodeStyle defaults()
        {
            return NodeStyle.builder().build();
        }

        /**
         * @return a new builder seeded with the default node style
         */
        public static Builder builder()
        {
            return new Builder();
        }

        /**
         * Mutable accumulator for a node style.
         */
        public static class Builder
        {
            private String shape = "box";
            private String fillColor = "white";
            private String borderColor = "black";
            private String style = "filled";

            /**
             * Sets the DOT node shape, such as "box" or "ellipse".
             * @param shape the shape name
             * @return this builder
             */
            public Builder shape(String shape)
            {
                this.shape = shape;
                return this;
            }

            /**
             * Sets the interior colour.
             * @param fillColor a DOT colour name or hex value
             * @return this builder
             */
            public Builder fillColor(String fillColor)
            {
                this.fillColor = fillColor;
                return this;
            }

            /**
             * Sets the outline colour.
             * @param borderColor a DOT colour name or hex value
             * @return this builder
             */
            public Builder borderColor(String borderColor)
            {
                this.borderColor = borderColor;
                return this;
            }

            /**
             * Sets the DOT style attribute, such as "filled" or "dashed".
             * @param style the style name
             * @return this builder
             */
            public Builder style(String style)
            {
                this.style = style;
                return this;
            }

            /**
             * @return an immutable node style holding the accumulated attributes
             */
            public NodeStyle build()
            {
                return new NodeStyle(this);
            }
        }
    }

    /**
     * Colour, line style and arrow head applied to a DOT edge.
     */
    public static class EdgeStyle
    {
        private final String color;
        private final String style;
        private final String arrowHead;

        private EdgeStyle(Builder builder)
        {
            this.color = builder.color;
            this.style = builder.style;
            this.arrowHead = builder.arrowHead;
        }

        /**
         * @return the color
         */
        public String getColor()
        {
            return color;
        }

        /**
         * @return the style
         */
        public String getStyle()
        {
            return style;
        }

        /**
         * @return the arrow head
         */
        public String getArrowHead()
        {
            return arrowHead;
        }

        /**
         * @return a solid black edge with a normal arrow head
         */
        public static EdgeStyle defaults()
        {
            return EdgeStyle.builder().build();
        }

        /**
         * @return a new builder seeded with the default edge style
         */
        public static Builder builder()
        {
            return new Builder();
        }

        /**
         * Mutable accumulator for an edge style.
         */
        public static class Builder
        {
            private String color = "black";
            private String style = "solid";
            private String arrowHead = "normal";

            /**
             * Sets the line colour.
             * @param color a DOT colour name or hex value
             * @return this builder
             */
            public Builder color(String color)
            {
                this.color = color;
                return this;
            }

            /**
             * Sets the DOT style attribute, such as "solid" or "dotted".
             * @param style the style name
             * @return this builder
             */
            public Builder style(String style)
            {
                this.style = style;
                return this;
            }

            /**
             * Sets the DOT arrowhead attribute, such as "normal" or "empty".
             * @param arrowHead the arrow head name
             * @return this builder
             */
            public Builder arrowHead(String arrowHead)
            {
                this.arrowHead = arrowHead;
                return this;
            }

            /**
             * @return an immutable edge style holding the accumulated attributes
             */
            public EdgeStyle build()
            {
                return new EdgeStyle(this);
            }
        }
    }
}
