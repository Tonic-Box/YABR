package com.tonic.analysis.graph.export;

import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.node.*;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class SDGDOTExporter extends DOTExporter<SDG> {

    public SDGDOTExporter() {
        this(DOTExporterConfig.defaults());
    }

    public SDGDOTExporter(DOTExporterConfig config) {
        super(config);
    }

    @Override
    public void export(SDG sdg, Writer output) {
        try {
            writeHeader(output);

            if (config.isClusterByMethod()) {
                exportClustered(sdg, output);
            } else {
                exportFlat(sdg, output);
            }

            output.write("\n");
            exportInterproceduralEdges(sdg, output);

            if (config.isIncludeLegend()) {
                output.write("\n");
                writeSDGLegend(output);
            }

            writeFooter(output);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export SDG to DOT", e);
        }
    }

    private void exportClustered(SDG sdg, Writer w) throws IOException {
        int clusterIdx = 0;
        for (SDGEntryNode entry : sdg.getEntryNodes()) {
            String clusterName = "method_" + clusterIdx++;
            String clusterLabel = entry.getMethodName();

            startCluster(w, clusterName, clusterLabel);

            writeNodeDOT(w, entry, "    ");

            for (SDGFormalInNode formal : sdg.getFormalIns(entry)) {
                writeNodeDOT(w, formal, "    ");
            }
            for (SDGFormalOutNode formal : sdg.getFormalOuts(entry)) {
                writeNodeDOT(w, formal, "    ");
            }

            for (SDGCallNode call : sdg.getCallNodes(entry)) {
                writeNodeDOT(w, call, "    ");
                for (SDGActualInNode actual : sdg.getActualIns(call)) {
                    writeNodeDOT(w, actual, "    ");
                }
                for (SDGActualOutNode actual : sdg.getActualOuts(call)) {
                    writeNodeDOT(w, actual, "    ");
                }
            }

            endCluster(w);
        }
    }

    private void exportFlat(SDG sdg, Writer w) throws IOException {
        for (PDGNode node : sdg.getAllNodes()) {
            writeNodeDOT(w, node, "  ");
        }
    }

    private void writeNodeDOT(Writer w, PDGNode node, String indent) throws IOException {
        String id = "n" + node.getId();
        String label = getNodeLabel(node);
        String shape = getNodeShape(node);
        String fillColor = getNodeFillColor(node);

        w.write(indent + id + " [");
        w.write("label=\"" + escapeLabel(label) + "\"");
        w.write(", shape=" + shape);
        w.write(", style=filled");
        w.write(", fillcolor=\"" + fillColor + "\"");
        w.write("];\n");
    }

    private String getNodeLabel(PDGNode node) {
        StringBuilder sb = new StringBuilder();

        if (config.isShowNodeIds()) {
            sb.append(node.getId()).append(": ");
        }

        if (node instanceof SDGEntryNode) {
            SDGEntryNode entry = (SDGEntryNode) node;
            sb.append("ENTRY\\n").append(shortMethodName(entry.getMethodName()));
        } else if (node instanceof SDGCallNode) {
            SDGCallNode call = (SDGCallNode) node;
            sb.append("CALL\\n").append(call.getTargetName());
        } else if (node instanceof SDGFormalInNode) {
            SDGFormalInNode formal = (SDGFormalInNode) node;
            sb.append("F_IN: ").append(formal.getParameterName());
        } else if (node instanceof SDGFormalOutNode) {
            SDGFormalOutNode formal = (SDGFormalOutNode) node;
            sb.append("F_OUT: ").append(formal.getParameterName());
        } else if (node instanceof SDGActualInNode) {
            SDGActualInNode actual = (SDGActualInNode) node;
            sb.append("A_IN: ").append(actual.getParameterName());
        } else if (node instanceof SDGActualOutNode) {
            SDGActualOutNode actual = (SDGActualOutNode) node;
            sb.append("A_OUT: ").append(actual.getParameterName());
        } else {
            sb.append(node.getType().name());
        }

        return sb.toString();
    }

    private String shortMethodName(String fullName) {
        if (fullName == null) return "";
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < fullName.length() - 1) {
            return fullName.substring(lastDot + 1);
        }
        return fullName;
    }

    private String getNodeShape(PDGNode node) {
        if (node instanceof SDGEntryNode) return "house";
        if (node instanceof SDGCallNode) return "box";
        if (node instanceof SDGFormalInNode) return "ellipse";
        if (node instanceof SDGFormalOutNode) return "ellipse";
        if (node instanceof SDGActualInNode) return "diamond";
        if (node instanceof SDGActualOutNode) return "diamond";
        return "box";
    }

    private String getNodeFillColor(PDGNode node) {
        if (node instanceof SDGEntryNode) return "#90EE90";
        if (node instanceof SDGCallNode) return "#DDA0DD";
        if (node instanceof SDGFormalInNode) return "#87CEEB";
        if (node instanceof SDGFormalOutNode) return "#FFB6C1";
        if (node instanceof SDGActualInNode) return "#ADD8E6";
        if (node instanceof SDGActualOutNode) return "#FFC0CB";
        return "white";
    }

    private void exportInterproceduralEdges(SDG sdg, Writer w) throws IOException {
        w.write("  // Parameter edges\n");
        for (PDGEdge edge : sdg.getParameterEdges()) {
            writeEdgeDOT(w, edge);
        }

        w.write("\n  // Summary edges\n");
        for (PDGEdge edge : sdg.getSummaryEdges()) {
            writeEdgeDOT(w, edge);
        }
    }

    private void writeEdgeDOT(Writer w, PDGEdge edge) throws IOException {
        String source = "n" + edge.getSource().getId();
        String target = "n" + edge.getTarget().getId();
        String color = getEdgeColor(edge.getType());
        String style = getEdgeStyle(edge.getType());

        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("color", color);
        attrs.put("style", style);

        if (edge.getType() == PDGDependenceType.SUMMARY) {
            attrs.put("constraint", "false");
            attrs.put("penwidth", "2");
        }

        writeEdge(w, source, target, attrs);
    }

    private String getEdgeColor(PDGDependenceType type) {
        switch (type) {
            case PARAMETER_IN:
                return "#0066cc";
            case PARAMETER_OUT:
                return "#cc6600";
            case CALL:
                return "#9932CC";
            case RETURN:
                return "#DC143C";
            case SUMMARY:
                return "#228B22";
            default:
                return "black";
        }
    }

    private String getEdgeStyle(PDGDependenceType type) {
        if (type == PDGDependenceType.SUMMARY) {
            return "bold";
        }
        return "solid";
    }

    private void writeSDGLegend(Writer w) throws IOException {
        w.write("  subgraph cluster_legend {\n");
        w.write("    label=\"Legend\";\n");
        w.write("    style=rounded;\n");
        w.write("    bgcolor=\"#fffff0\";\n");
        w.write("\n");

        w.write("    legend_entry [label=\"Entry\", shape=house, style=filled, fillcolor=\"#90EE90\"];\n");
        w.write("    legend_call [label=\"Call\", shape=box, style=filled, fillcolor=\"#DDA0DD\"];\n");
        w.write("    legend_fin [label=\"Formal In\", shape=ellipse, style=filled, fillcolor=\"#87CEEB\"];\n");
        w.write("    legend_fout [label=\"Formal Out\", shape=ellipse, style=filled, fillcolor=\"#FFB6C1\"];\n");
        w.write("    legend_ain [label=\"Actual In\", shape=diamond, style=filled, fillcolor=\"#ADD8E6\"];\n");
        w.write("    legend_aout [label=\"Actual Out\", shape=diamond, style=filled, fillcolor=\"#FFC0CB\"];\n");
        w.write("\n");

        w.write("    legend_param_in [label=\"\", shape=plaintext];\n");
        w.write("    legend_param_out [label=\"\", shape=plaintext];\n");
        w.write("    legend_summary [label=\"\", shape=plaintext];\n");
        w.write("    legend_fin -> legend_param_in [label=\"param in\", color=\"#0066cc\"];\n");
        w.write("    legend_fout -> legend_param_out [label=\"param out\", color=\"#cc6600\"];\n");
        w.write("    legend_ain -> legend_summary [label=\"summary\", color=\"#228B22\", style=bold];\n");

        w.write("  }\n");
    }
}
