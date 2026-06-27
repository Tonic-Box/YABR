package com.tonic.analysis.graph.print;

import com.tonic.analysis.pdg.edge.PDGEdge;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.sdg.SDG;
import com.tonic.analysis.pdg.sdg.node.*;
import com.tonic.analysis.source.emit.IndentingWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.util.*;

public class SDGPrinter {

    private final GraphPrinterConfig config;

    public SDGPrinter() {
        this(GraphPrinterConfig.defaults());
    }

    public SDGPrinter(GraphPrinterConfig config) {
        this.config = config;
    }

    public String print(SDG sdg) {
        StringWriter sw = new StringWriter();
        print(sdg, sw);
        return sw.toString();
    }

    public void print(SDG sdg, Writer output) {
        IndentingWriter writer = new IndentingWriter(output, config.getIndentString());

        printHeader(writer, sdg);
        writer.newLine();

        if (config.isGroupByMethod()) {
            printByProcedure(writer, sdg);
        } else {
            printAllNodes(writer, sdg);
        }

        writer.newLine();
        printInterproceduralEdges(writer, sdg);

        if (config.isShowStatistics()) {
            writer.newLine();
            printStatistics(writer, sdg);
        }

        writer.flush();
    }

    private void printHeader(IndentingWriter writer, SDG sdg) {
        writer.writeLine("=== System Dependence Graph ===");
        writer.writeLine("Procedures: " + sdg.getEntryNodes().size());
    }

    private void printByProcedure(IndentingWriter writer, SDG sdg) {
        writer.writeLine("--- Procedures ---");

        for (SDGEntryNode entry : sdg.getEntryNodes()) {
            writer.newLine();
            printProcedure(writer, sdg, entry);
        }
    }

    private void printProcedure(IndentingWriter writer, SDG sdg, SDGEntryNode entry) {
        writer.writeLine("PROCEDURE: " + entry.getMethodName());
        writer.indent();

        if (config.isShowNodeIds()) {
            writer.writeLine("Entry Node: " + entry.getId());
        }

        List<SDGFormalInNode> formalIns = sdg.getFormalIns(entry);
        if (!formalIns.isEmpty()) {
            writer.writeLine("Formal Parameters:");
            writer.indent();
            for (SDGFormalInNode formal : formalIns) {
                printFormalNode(writer, formal);
            }
            writer.dedent();
        }

        List<SDGFormalOutNode> formalOuts = sdg.getFormalOuts(entry);
        if (!formalOuts.isEmpty()) {
            writer.writeLine("Formal Returns:");
            writer.indent();
            for (SDGFormalOutNode formal : formalOuts) {
                printFormalNode(writer, formal);
            }
            writer.dedent();
        }

        List<SDGCallNode> callNodes = sdg.getCallNodes(entry);
        if (!callNodes.isEmpty()) {
            writer.writeLine("Call Sites:");
            writer.indent();
            for (SDGCallNode call : callNodes) {
                printCallNode(writer, sdg, call);
            }
            writer.dedent();
        }

        writer.dedent();
    }

    private void printFormalNode(IndentingWriter writer, PDGNode node) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(node.getId()).append("] ");
        }

        if (node instanceof SDGFormalInNode) {
            SDGFormalInNode formal = (SDGFormalInNode) node;
            sb.append("IN: ").append(formal.getParameterName());
            sb.append(" (index: ").append(formal.getParameterIndex()).append(")");
        } else if (node instanceof SDGFormalOutNode) {
            SDGFormalOutNode formal = (SDGFormalOutNode) node;
            sb.append("OUT: ").append(formal.getParameterName());
        }

        writer.writeLine(sb.toString());
    }

    private void printCallNode(IndentingWriter writer, SDG sdg, SDGCallNode call) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(call.getId()).append("] ");
        }

        sb.append("CALL: ").append(call.getTargetOwner()).append(".")
          .append(call.getTargetName());

        writer.writeLine(sb.toString());

        if (config.getVerbosity().ordinal() >= Verbosity.VERBOSE.ordinal()) {
            writer.indent();

            List<SDGActualInNode> actualIns = sdg.getActualIns(call);
            for (SDGActualInNode actual : actualIns) {
                StringBuilder actualSb = new StringBuilder();
                if (config.isShowNodeIds()) {
                    actualSb.append("[").append(actual.getId()).append("] ");
                }
                actualSb.append("ACTUAL_IN: ").append(actual.getParameterName());
                writer.writeLine(actualSb.toString());
            }

            List<SDGActualOutNode> actualOuts = sdg.getActualOuts(call);
            for (SDGActualOutNode actual : actualOuts) {
                StringBuilder actualSb = new StringBuilder();
                if (config.isShowNodeIds()) {
                    actualSb.append("[").append(actual.getId()).append("] ");
                }
                actualSb.append("ACTUAL_OUT: ").append(actual.getParameterName());
                writer.writeLine(actualSb.toString());
            }

            writer.dedent();
        }
    }

    private void printAllNodes(IndentingWriter writer, SDG sdg) {
        writer.writeLine("--- All Nodes ---");

        List<PDGNode> nodes = new ArrayList<>(sdg.getAllNodes());
        nodes.sort(Comparator.comparingInt(PDGNode::getId));

        for (PDGNode node : nodes) {
            printGenericNode(writer, node);
        }
    }

    private void printGenericNode(IndentingWriter writer, PDGNode node) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(node.getId()).append("] ");
        }
        sb.append(node.getType().name());

        if (node instanceof SDGEntryNode) {
            sb.append(": ").append(((SDGEntryNode) node).getMethodName());
        } else if (node instanceof SDGCallNode) {
            SDGCallNode call = (SDGCallNode) node;
            sb.append(": ").append(call.getTargetOwner()).append(".").append(call.getTargetName());
        } else if (node instanceof SDGFormalInNode) {
            sb.append(": ").append(((SDGFormalInNode) node).getParameterName());
        } else if (node instanceof SDGFormalOutNode) {
            sb.append(": ").append(((SDGFormalOutNode) node).getParameterName());
        } else if (node instanceof SDGActualInNode) {
            sb.append(": ").append(((SDGActualInNode) node).getParameterName());
        } else if (node instanceof SDGActualOutNode) {
            sb.append(": ").append(((SDGActualOutNode) node).getParameterName());
        }

        writer.writeLine(sb.toString());
    }

    private void printInterproceduralEdges(IndentingWriter writer, SDG sdg) {
        writer.writeLine("--- Interprocedural Edges ---");

        writer.writeLine("Parameter Edges:");
        writer.indent();
        for (PDGEdge edge : sdg.getParameterEdges()) {
            printEdge(writer, edge);
        }
        writer.dedent();

        if (!sdg.getSummaryEdges().isEmpty()) {
            writer.writeLine("Summary Edges:");
            writer.indent();
            for (PDGEdge edge : sdg.getSummaryEdges()) {
                printEdge(writer, edge);
            }
            writer.dedent();
        }
    }

    private void printEdge(IndentingWriter writer, PDGEdge edge) {
        StringBuilder sb = new StringBuilder();
        sb.append(edge.getSource().getId());
        sb.append(" -> ");
        sb.append(edge.getTarget().getId());

        if (config.isShowEdgeLabels()) {
            sb.append(" (").append(edge.getType().name()).append(")");
        }

        writer.writeLine(sb.toString());
    }

    private void printStatistics(IndentingWriter writer, SDG sdg) {
        writer.writeLine("--- Statistics ---");
        writer.writeLine("Entry nodes: " + sdg.getEntryNodes().size());
        writer.writeLine("Call nodes: " + sdg.getCallNodesCount());
        writer.writeLine("Total nodes: " + sdg.getAllNodes().size());
        writer.writeLine("Parameter edges: " + sdg.getParameterEdges().size());
        writer.writeLine("Summary edges: " + sdg.getSummaryEdges().size());
    }
}
