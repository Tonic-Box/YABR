package com.tonic.analysis.graph.export;

import java.io.StringWriter;
import java.io.Writer;
import java.io.IOException;
import java.util.Map;

public abstract class DOTExporter<T> {

    protected final DOTExporterConfig config;

    protected DOTExporter(DOTExporterConfig config) {
        this.config = config;
    }

    public String export(T graph) {
        StringWriter sw = new StringWriter();
        export(graph, sw);
        return sw.toString();
    }

    public abstract void export(T graph, Writer output);

    protected void writeHeader(Writer w) throws IOException {
        String graphType = config.isDirected() ? "digraph" : "graph";
        w.write(graphType + " " + config.getGraphName() + " {\n");
        w.write("  rankdir=" + config.getRankDir() + ";\n");
        w.write("  fontname=\"" + config.getFontName() + "\";\n");
        w.write("  fontsize=" + config.getFontSize() + ";\n");
        w.write("  node [fontname=\"" + config.getFontName() + "\", fontsize=" + config.getFontSize() + "];\n");
        w.write("  edge [fontname=\"" + config.getFontName() + "\", fontsize=" + (config.getFontSize() - 2) + "];\n");
        w.write("\n");
    }

    protected void writeFooter(Writer w) throws IOException {
        w.write("}\n");
    }

    protected void writeNode(Writer w, String id, String label, String shape, String fillColor, String borderColor) throws IOException {
        w.write("  " + id + " [");
        w.write("label=\"" + escapeLabel(label) + "\"");
        w.write(", shape=" + shape);
        w.write(", style=filled");
        w.write(", fillcolor=\"" + fillColor + "\"");
        w.write(", color=\"" + borderColor + "\"");
        w.write("];\n");
    }

    protected void writeNode(Writer w, String id, String label, Map<String, String> attrs) throws IOException {
        w.write("  " + id + " [");
        w.write("label=\"" + escapeLabel(label) + "\"");
        for (Map.Entry<String, String> attr : attrs.entrySet()) {
            w.write(", " + attr.getKey() + "=\"" + attr.getValue() + "\"");
        }
        w.write("];\n");
    }

    protected void writeEdge(Writer w, String source, String target, String label, String color, String style) throws IOException {
        String connector = config.isDirected() ? " -> " : " -- ";
        w.write("  " + source + connector + target + " [");
        if (label != null && !label.isEmpty()) {
            w.write("label=\"" + escapeLabel(label) + "\"");
            w.write(", ");
        }
        w.write("color=\"" + color + "\"");
        w.write(", style=" + style);
        w.write("];\n");
    }

    protected void writeEdge(Writer w, String source, String target, Map<String, String> attrs) throws IOException {
        String connector = config.isDirected() ? " -> " : " -- ";
        w.write("  " + source + connector + target);
        if (!attrs.isEmpty()) {
            w.write(" [");
            boolean first = true;
            for (Map.Entry<String, String> attr : attrs.entrySet()) {
                if (!first) w.write(", ");
                w.write(attr.getKey() + "=\"" + attr.getValue() + "\"");
                first = false;
            }
            w.write("]");
        }
        w.write(";\n");
    }

    protected void startCluster(Writer w, String name, String label) throws IOException {
        w.write("  subgraph cluster_" + name + " {\n");
        w.write("    label=\"" + escapeLabel(label) + "\";\n");
        w.write("    style=rounded;\n");
        w.write("    bgcolor=\"#f0f0f0\";\n");
    }

    protected void endCluster(Writer w) throws IOException {
        w.write("  }\n");
    }

    protected void writeLegend(Writer w, Map<String, String> items) throws IOException {
        w.write("  subgraph cluster_legend {\n");
        w.write("    label=\"Legend\";\n");
        w.write("    style=rounded;\n");
        w.write("    bgcolor=\"#fffff0\";\n");

        int i = 0;
        for (Map.Entry<String, String> item : items.entrySet()) {
            w.write("    legend_" + i + " [label=\"" + item.getKey() + "\", shape=plaintext];\n");
            i++;
        }

        w.write("  }\n");
    }

    protected String escapeLabel(String label) {
        if (label == null) return "";
        String escaped = label
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace("<", "\\<")
            .replace(">", "\\>")
            .replace("{", "\\{")
            .replace("}", "\\}");

        if (config.isTruncateLabels() && escaped.length() > config.getMaxLabelLength()) {
            escaped = escaped.substring(0, config.getMaxLabelLength() - 3) + "...";
        }

        return escaped;
    }

    protected String sanitizeId(String id) {
        return "n" + id.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
