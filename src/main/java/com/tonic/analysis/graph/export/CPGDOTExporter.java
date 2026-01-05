package com.tonic.analysis.graph.export;

import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

public class CPGDOTExporter extends DOTExporter<CodePropertyGraph> {

    private Set<CPGEdgeType> includedEdgeTypes;

    public CPGDOTExporter() {
        this(DOTExporterConfig.defaults());
    }

    public CPGDOTExporter(DOTExporterConfig config) {
        super(config);
        this.includedEdgeTypes = EnumSet.allOf(CPGEdgeType.class);
    }

    public CPGDOTExporter includeEdgeTypes(CPGEdgeType... types) {
        this.includedEdgeTypes = EnumSet.copyOf(Arrays.asList(types));
        return this;
    }

    public CPGDOTExporter cfgOnly() {
        this.includedEdgeTypes = EnumSet.of(
            CPGEdgeType.CFG_NEXT,
            CPGEdgeType.CFG_TRUE,
            CPGEdgeType.CFG_FALSE,
            CPGEdgeType.CFG_EXCEPTION,
            CPGEdgeType.CFG_BACK,
            CPGEdgeType.CONTAINS
        );
        return this;
    }

    public CPGDOTExporter dataFlowOnly() {
        this.includedEdgeTypes = EnumSet.of(
            CPGEdgeType.DATA_DEF,
            CPGEdgeType.DATA_USE,
            CPGEdgeType.REACHING_DEF,
            CPGEdgeType.CONTAINS
        );
        return this;
    }

    public CPGDOTExporter callGraphOnly() {
        this.includedEdgeTypes = EnumSet.of(
            CPGEdgeType.CALL,
            CPGEdgeType.PARAM_IN,
            CPGEdgeType.PARAM_OUT,
            CPGEdgeType.RETURN_VALUE
        );
        return this;
    }

    @Override
    public void export(CodePropertyGraph cpg, Writer output) {
        try {
            writeHeader(output);

            if (config.isClusterByMethod()) {
                exportClustered(cpg, output);
            } else {
                exportFlat(cpg, output);
            }

            output.write("\n");
            exportEdges(cpg, output);

            if (config.isIncludeLegend()) {
                output.write("\n");
                writeCPGLegend(output);
            }

            writeFooter(output);
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to export CPG to DOT", e);
        }
    }

    private void exportClustered(CodePropertyGraph cpg, Writer w) throws IOException {
        List<MethodNode> methods = cpg.nodes(MethodNode.class).collect(Collectors.toList());

        int clusterIdx = 0;
        for (MethodNode method : methods) {
            String clusterName = "method_" + clusterIdx++;
            String clusterLabel = method.getName() + method.getDescriptor();

            startCluster(w, clusterName, clusterLabel);
            writeNodeDOT(w, method, "    ");

            List<BlockNode> blocks = method.getOutgoingEdges().stream()
                .filter(e -> e.getType() == CPGEdgeType.CONTAINS)
                .map(CPGEdge::getTarget)
                .filter(n -> n instanceof BlockNode)
                .map(n -> (BlockNode) n)
                .collect(Collectors.toList());

            for (BlockNode block : blocks) {
                writeNodeDOT(w, block, "    ");

                List<InstructionNode> instrs = block.getOutgoingEdges().stream()
                    .filter(e -> e.getType() == CPGEdgeType.CONTAINS)
                    .map(CPGEdge::getTarget)
                    .filter(n -> n instanceof InstructionNode)
                    .map(n -> (InstructionNode) n)
                    .collect(Collectors.toList());

                for (InstructionNode instr : instrs) {
                    writeNodeDOT(w, instr, "    ");
                }
            }

            endCluster(w);
        }

        for (CallSiteNode callSite : cpg.nodes(CallSiteNode.class).collect(Collectors.toList())) {
            writeNodeDOT(w, callSite, "  ");
        }
    }

    private void exportFlat(CodePropertyGraph cpg, Writer w) throws IOException {
        for (CPGNode node : cpg.getAllNodes()) {
            writeNodeDOT(w, node, "  ");
        }
    }

    private void writeNodeDOT(Writer w, CPGNode node, String indent) throws IOException {
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

    private String getNodeLabel(CPGNode node) {
        StringBuilder sb = new StringBuilder();

        if (config.isShowNodeIds()) {
            sb.append(node.getId()).append(": ");
        }

        if (node instanceof MethodNode) {
            MethodNode method = (MethodNode) node;
            sb.append(method.getName());
        } else if (node instanceof BlockNode) {
            BlockNode block = (BlockNode) node;
            sb.append("B").append(block.getBlockId());
            if (block.isEntryBlock()) sb.append(" (entry)");
            if (block.isExitBlock()) sb.append(" (exit)");
        } else if (node instanceof InstructionNode) {
            InstructionNode instr = (InstructionNode) node;
            sb.append(instr.getLabel());
        } else if (node instanceof CallSiteNode) {
            CallSiteNode call = (CallSiteNode) node;
            sb.append("CALL: ").append(call.getTargetName());
        } else {
            sb.append(node.getLabel());
        }

        return sb.toString();
    }

    private String getNodeShape(CPGNode node) {
        switch (node.getNodeType()) {
            case METHOD:
                return "house";
            case BLOCK:
                return "box";
            case INSTRUCTION:
                return "box";
            case CALL_SITE:
                return "ellipse";
            default:
                return "box";
        }
    }

    private String getNodeFillColor(CPGNode node) {
        switch (node.getNodeType()) {
            case METHOD:
                return "#90EE90";
            case BLOCK:
                return "#E0E0E0";
            case INSTRUCTION:
                return "white";
            case CALL_SITE:
                return "#DDA0DD";
            default:
                return "white";
        }
    }

    private void exportEdges(CodePropertyGraph cpg, Writer w) throws IOException {
        Map<CPGEdgeType, List<CPGEdge>> edgesByType = new EnumMap<>(CPGEdgeType.class);

        for (CPGEdge edge : cpg.getAllEdges()) {
            if (includedEdgeTypes.contains(edge.getType())) {
                edgesByType.computeIfAbsent(edge.getType(), k -> new ArrayList<>()).add(edge);
            }
        }

        for (Map.Entry<CPGEdgeType, List<CPGEdge>> entry : edgesByType.entrySet()) {
            CPGEdgeType type = entry.getKey();
            w.write("  // " + type.name() + " edges\n");

            for (CPGEdge edge : entry.getValue()) {
                writeEdgeDOT(w, edge);
            }
            w.write("\n");
        }
    }

    private void writeEdgeDOT(Writer w, CPGEdge edge) throws IOException {
        String source = "n" + edge.getSource().getId();
        String target = "n" + edge.getTarget().getId();

        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("color", getEdgeColor(edge.getType()));
        attrs.put("style", getEdgeStyle(edge.getType()));

        if (edge.getType().isCFGEdge()) {
            attrs.put("weight", "10");
        }

        if (edge.getType() == CPGEdgeType.CFG_BACK) {
            attrs.put("constraint", "false");
        }

        writeEdge(w, source, target, attrs);
    }

    private String getEdgeColor(CPGEdgeType type) {
        if (type.isCFGEdge()) {
            switch (type) {
                case CFG_TRUE:
                    return "#00aa00";
                case CFG_FALSE:
                    return "#cc0000";
                case CFG_EXCEPTION:
                    return "#ff6600";
                case CFG_BACK:
                    return "#666666";
                default:
                    return "black";
            }
        }

        if (type.isDataFlowEdge()) {
            return "#0000cc";
        }

        if (type.isControlDependenceEdge()) {
            return "#006600";
        }

        switch (type) {
            case CALL:
                return "#9932CC";
            case PARAM_IN:
            case PARAM_OUT:
                return "#0066cc";
            case RETURN_VALUE:
                return "#cc6600";
            case TAINT:
                return "#cc0000";
            case CONTAINS:
                return "#999999";
            default:
                return "black";
        }
    }

    private String getEdgeStyle(CPGEdgeType type) {
        if (type.isControlDependenceEdge()) {
            return "dashed";
        }
        if (type == CPGEdgeType.CONTAINS) {
            return "dotted";
        }
        if (type == CPGEdgeType.CFG_BACK) {
            return "dashed";
        }
        return "solid";
    }

    private void writeCPGLegend(Writer w) throws IOException {
        w.write("  subgraph cluster_legend {\n");
        w.write("    label=\"Legend\";\n");
        w.write("    style=rounded;\n");
        w.write("    bgcolor=\"#fffff0\";\n");
        w.write("\n");

        w.write("    legend_method [label=\"Method\", shape=house, style=filled, fillcolor=\"#90EE90\"];\n");
        w.write("    legend_block [label=\"Block\", shape=box, style=filled, fillcolor=\"#E0E0E0\"];\n");
        w.write("    legend_instr [label=\"Instruction\", shape=box, style=filled, fillcolor=\"white\"];\n");
        w.write("    legend_call [label=\"Call Site\", shape=ellipse, style=filled, fillcolor=\"#DDA0DD\"];\n");
        w.write("\n");

        w.write("    legend_cfg [label=\"\", shape=plaintext];\n");
        w.write("    legend_data [label=\"\", shape=plaintext];\n");
        w.write("    legend_true [label=\"\", shape=plaintext];\n");
        w.write("    legend_false [label=\"\", shape=plaintext];\n");

        w.write("    legend_method -> legend_cfg [label=\"CFG\", color=\"black\"];\n");
        w.write("    legend_block -> legend_data [label=\"Data Flow\", color=\"#0000cc\"];\n");
        w.write("    legend_instr -> legend_true [label=\"True\", color=\"#00aa00\"];\n");
        w.write("    legend_call -> legend_false [label=\"False\", color=\"#cc0000\"];\n");

        w.write("  }\n");
    }
}
