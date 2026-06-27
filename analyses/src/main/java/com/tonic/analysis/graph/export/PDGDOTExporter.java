package com.tonic.analysis.graph.export;

import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.edge.PDGDependenceType;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGRegionNode;
import java.io.IOException;
import java.io.Writer;

public class PDGDOTExporter extends DOTExporter<PDG> {

    public PDGDOTExporter() {
        this(DOTExporterConfig.defaults());
    }

    public PDGDOTExporter(DOTExporterConfig config) {
        super(config);
    }

    @Override
    public void export(PDG pdg, Writer output) {
        try {
            writeHeader(output);

            for (PDGNode node : pdg.getNodes()) {
                writeNodeDOT(output, node);
            }

            output.write("\n");

            for (PDGEdge edge : pdg.getEdges()) {
                writeEdgeDOT(output, edge);
            }

            if (config.isIncludeLegend()) {
                output.write("\n");
                writePDGLegend(output);
            }

            writeFooter(output);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export PDG to DOT", e);
        }
    }

    private void writeNodeDOT(Writer w, PDGNode node) throws IOException {
        String id = "n" + node.getId();
        String label = getNodeLabel(node);
        String shape = getNodeShape(node);
        String fillColor = getNodeFillColor(node);
        String borderColor = getNodeBorderColor(node);

        writeNode(w, id, label, shape, fillColor, borderColor);
    }

    private String getNodeLabel(PDGNode node) {
        StringBuilder sb = new StringBuilder();

        if (config.isShowNodeIds()) {
            sb.append(node.getId()).append(": ");
        }

        if (node instanceof PDGRegionNode) {
            sb.append(node.getType().name());
        } else if (node instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) node;
            if (instrNode.getInstruction() != null) {
                sb.append(instrNode.getInstruction());
            } else {
                sb.append("instruction");
            }
        } else {
            sb.append(node.getType().name());
        }

        return sb.toString();
    }

    private String getNodeShape(PDGNode node) {
        switch (node.getType()) {
            case ENTRY:
            case EXIT:
                return "ellipse";
            case PHI:
                return "octagon";
            case CALL_SITE:
                return "box";
            case BRANCH:
                return "diamond";
            default:
                return "box";
        }
    }

    private String getNodeFillColor(PDGNode node) {
        if (node.isTainted()) {
            return "#ffcccc";
        }

        switch (node.getType()) {
            case ENTRY:
                return "#90EE90";
            case EXIT:
                return "#FFB6C1";
            case PHI:
                return "#FFA500";
            case CALL_SITE:
                return "#DDA0DD";
            case BRANCH:
                return "#87CEEB";
            default:
                return "white";
        }
    }

    private String getNodeBorderColor(PDGNode node) {
        if (node.isTainted()) {
            return "#cc0000";
        }
        return "black";
    }

    private void writeEdgeDOT(Writer w, PDGEdge edge) throws IOException {
        String source = "n" + edge.getSource().getId();
        String target = "n" + edge.getTarget().getId();
        String label = getEdgeLabel(edge);
        String color = getEdgeColor(edge);
        String style = getEdgeStyle(edge);

        writeEdge(w, source, target, label, color, style);
    }

    private String getEdgeLabel(PDGEdge edge) {
        if (edge.getVariable() != null) {
            return edge.getVariable();
        }
        return "";
    }

    private String getEdgeColor(PDGEdge edge) {
        PDGDependenceType type = edge.getType();

        if (type.isControlDependency()) {
            if (type == PDGDependenceType.CONTROL_TRUE) {
                return "#00aa00";
            } else if (type == PDGDependenceType.CONTROL_FALSE) {
                return "#cc0000";
            }
            return "#006600";
        }

        if (type.isDataDependency()) {
            return "#0000cc";
        }

        if (type.isInterproceduralEdge()) {
            return "#9932CC";
        }

        return "black";
    }

    private String getEdgeStyle(PDGEdge edge) {
        if (edge.getType().isControlDependency()) {
            return "dashed";
        }
        return "solid";
    }

    private void writePDGLegend(Writer w) throws IOException {
        w.write("  subgraph cluster_legend {\n");
        w.write("    label=\"Legend\";\n");
        w.write("    style=rounded;\n");
        w.write("    bgcolor=\"#fffff0\";\n");
        w.write("    fontsize=10;\n");
        w.write("\n");

        w.write("    legend_entry [label=\"Entry\", shape=ellipse, style=filled, fillcolor=\"#90EE90\"];\n");
        w.write("    legend_exit [label=\"Exit\", shape=ellipse, style=filled, fillcolor=\"#FFB6C1\"];\n");
        w.write("    legend_phi [label=\"Phi\", shape=octagon, style=filled, fillcolor=\"#FFA500\"];\n");
        w.write("    legend_call [label=\"Call\", shape=box, style=filled, fillcolor=\"#DDA0DD\"];\n");
        w.write("    legend_branch [label=\"Branch\", shape=diamond, style=filled, fillcolor=\"#87CEEB\"];\n");
        w.write("    legend_instr [label=\"Instruction\", shape=box, style=filled, fillcolor=\"white\"];\n");
        w.write("\n");

        w.write("    legend_ctrl_true [label=\"\", shape=plaintext];\n");
        w.write("    legend_ctrl_false [label=\"\", shape=plaintext];\n");
        w.write("    legend_data [label=\"\", shape=plaintext];\n");
        w.write("    legend_entry -> legend_ctrl_true [label=\"control (true)\", color=\"#00aa00\", style=dashed];\n");
        w.write("    legend_exit -> legend_ctrl_false [label=\"control (false)\", color=\"#cc0000\", style=dashed];\n");
        w.write("    legend_phi -> legend_data [label=\"data flow\", color=\"#0000cc\", style=solid];\n");

        w.write("  }\n");
    }
}
