package com.tonic.analysis.graph.print;

import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGInstructionNode;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGRegionNode;
import com.tonic.analysis.source.emit.IndentingWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

public class PDGPrinter {

    private final GraphPrinterConfig config;

    public PDGPrinter() {
        this(GraphPrinterConfig.defaults());
    }

    public PDGPrinter(GraphPrinterConfig config) {
        this.config = config;
    }

    public String print(PDG pdg) {
        StringWriter sw = new StringWriter();
        print(pdg, sw);
        return sw.toString();
    }

    public void print(PDG pdg, Writer output) {
        IndentingWriter writer = new IndentingWriter(output, config.getIndentString());

        printHeader(writer, pdg);
        writer.newLine();
        printNodes(writer, pdg);
        writer.newLine();
        printEdges(writer, pdg);

        if (config.isShowStatistics()) {
            writer.newLine();
            printStatistics(writer, pdg);
        }

        writer.flush();
    }

    private void printHeader(IndentingWriter writer, PDG pdg) {
        writer.writeLine("=== Program Dependence Graph ===");
        if (pdg.getMethodName() != null) {
            writer.writeLine("Method: " + pdg.getMethodName());
        }
    }

    private void printNodes(IndentingWriter writer, PDG pdg) {
        writer.writeLine("--- Nodes ---");

        List<PDGNode> nodes = new ArrayList<>(pdg.getNodes());
        nodes.sort(Comparator.comparingInt(PDGNode::getId));

        int printed = 0;
        for (PDGNode node : nodes) {
            if (printed >= config.getMaxNodesPerMethod()) {
                writer.writeLine("... (" + (nodes.size() - printed) + " more nodes)");
                break;
            }

            printNode(writer, node);
            printed++;
        }
    }

    private void printNode(IndentingWriter writer, PDGNode node) {
        StringBuilder sb = new StringBuilder();

        if (config.isShowNodeIds()) {
            sb.append("[").append(node.getId()).append("] ");
        }

        sb.append(node.getType().name()).append(": ");

        String label = getNodeLabel(node);
        if (config.isTruncateLongLabels() && label.length() > config.getMaxLabelLength()) {
            label = label.substring(0, config.getMaxLabelLength() - 3) + "...";
        }
        sb.append(label);

        if (config.getVerbosity() == Verbosity.DEBUG && node.isTainted()) {
            sb.append(" [TAINTED]");
        }

        writer.writeLine(sb.toString());

        if (config.isShowProperties() && config.getVerbosity().ordinal() >= Verbosity.VERBOSE.ordinal()) {
            writer.indent();
            if (node instanceof PDGInstructionNode) {
                PDGInstructionNode instrNode = (PDGInstructionNode) node;
                if (instrNode.getBlock() != null) {
                    writer.writeLine("Block: " + instrNode.getBlock().getId());
                }
            }
            writer.dedent();
        }
    }

    private String getNodeLabel(PDGNode node) {
        if (node instanceof PDGRegionNode) {
            PDGRegionNode region = (PDGRegionNode) node;
            return region.getType().name();
        } else if (node instanceof PDGInstructionNode) {
            PDGInstructionNode instrNode = (PDGInstructionNode) node;
            if (instrNode.getInstruction() != null) {
                return instrNode.getInstruction().toString();
            }
            return "instruction";
        }
        return node.getType().name();
    }

    private void printEdges(IndentingWriter writer, PDG pdg) {
        writer.writeLine("--- Edges ---");

        Map<String, List<PDGEdge>> edgesByType = new LinkedHashMap<>();
        for (PDGEdge edge : pdg.getEdges()) {
            String typeName = edge.getType().name();
            edgesByType.computeIfAbsent(typeName, k -> new ArrayList<>()).add(edge);
        }

        for (Map.Entry<String, List<PDGEdge>> entry : edgesByType.entrySet()) {
            if (config.getVerbosity().ordinal() >= Verbosity.NORMAL.ordinal()) {
                writer.writeLine(entry.getKey() + ":");
                writer.indent();
            }

            for (PDGEdge edge : entry.getValue()) {
                printEdge(writer, edge);
            }

            if (config.getVerbosity().ordinal() >= Verbosity.NORMAL.ordinal()) {
                writer.dedent();
            }
        }
    }

    private void printEdge(IndentingWriter writer, PDGEdge edge) {
        StringBuilder sb = new StringBuilder();

        sb.append(edge.getSource().getId());
        sb.append(" -> ");
        sb.append(edge.getTarget().getId());

        if (config.isShowEdgeLabels() && config.getVerbosity() == Verbosity.MINIMAL) {
            sb.append(" (").append(edge.getType().name()).append(")");
        }

        if (edge.getVariable() != null && config.getVerbosity().ordinal() >= Verbosity.VERBOSE.ordinal()) {
            sb.append(" [var: ").append(edge.getVariable()).append("]");
        }

        writer.writeLine(sb.toString());
    }

    private void printStatistics(IndentingWriter writer, PDG pdg) {
        writer.writeLine("--- Statistics ---");
        writer.writeLine("Total nodes: " + pdg.getNodes().size());
        writer.writeLine("Total edges: " + pdg.getEdges().size());

        Map<String, Integer> edgeCounts = new LinkedHashMap<>();
        for (PDGEdge edge : pdg.getEdges()) {
            edgeCounts.merge(edge.getType().name(), 1, Integer::sum);
        }

        if (!edgeCounts.isEmpty()) {
            writer.writeLine("Edge counts:");
            writer.indent();
            for (Map.Entry<String, Integer> entry : edgeCounts.entrySet()) {
                writer.writeLine(entry.getKey() + ": " + entry.getValue());
            }
            writer.dedent();
        }
    }
}
