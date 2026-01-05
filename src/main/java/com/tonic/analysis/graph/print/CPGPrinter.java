package com.tonic.analysis.graph.print;

import com.tonic.analysis.cpg.CodePropertyGraph;
import com.tonic.analysis.cpg.edge.CPGEdge;
import com.tonic.analysis.cpg.edge.CPGEdgeType;
import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.source.emit.IndentingWriter;

import java.io.StringWriter;
import java.io.Writer;
import java.util.*;
import java.util.stream.Collectors;

public class CPGPrinter {

    private final GraphPrinterConfig config;

    public CPGPrinter() {
        this(GraphPrinterConfig.defaults());
    }

    public CPGPrinter(GraphPrinterConfig config) {
        this.config = config;
    }

    public String print(CodePropertyGraph cpg) {
        StringWriter sw = new StringWriter();
        print(cpg, sw);
        return sw.toString();
    }

    public void print(CodePropertyGraph cpg, Writer output) {
        IndentingWriter writer = new IndentingWriter(output, config.getIndentString());

        printHeader(writer, cpg);
        writer.newLine();

        if (config.isGroupByMethod()) {
            printByMethod(writer, cpg);
        } else {
            printAllNodes(writer, cpg);
        }

        if (config.getVerbosity().ordinal() >= Verbosity.VERBOSE.ordinal()) {
            writer.newLine();
            printEdges(writer, cpg);
        }

        if (config.isShowStatistics()) {
            writer.newLine();
            printStatistics(writer, cpg);
        }

        writer.flush();
    }

    private void printHeader(IndentingWriter writer, CodePropertyGraph cpg) {
        writer.writeLine("=== Code Property Graph ===");
        writer.writeLine(cpg.toString());
    }

    private void printByMethod(IndentingWriter writer, CodePropertyGraph cpg) {
        writer.writeLine("--- Methods ---");

        List<MethodNode> methods = cpg.nodes(MethodNode.class)
                .sorted(Comparator.comparing(MethodNode::getFullSignature))
                .collect(Collectors.toList());

        for (MethodNode method : methods) {
            writer.newLine();
            printMethod(writer, cpg, method);
        }
    }

    private void printMethod(IndentingWriter writer, CodePropertyGraph cpg, MethodNode method) {
        StringBuilder header = new StringBuilder();
        if (config.isShowNodeIds()) {
            header.append("[").append(method.getId()).append("] ");
        }
        header.append("METHOD: ").append(method.getFullSignature());
        writer.writeLine(header.toString());
        writer.indent();

        List<BlockNode> blocks = method.getOutgoingEdges().stream()
            .filter(e -> e.getType() == CPGEdgeType.CONTAINS)
            .map(CPGEdge::getTarget)
            .filter(n -> n instanceof BlockNode)
            .map(n -> (BlockNode) n)
            .sorted(Comparator.comparingInt(BlockNode::getBlockId))
            .collect(Collectors.toList());

        for (BlockNode block : blocks) {
            printBlock(writer, block);
        }

        List<CallSiteNode> callSites = cpg.nodes(CallSiteNode.class)
            .filter(cs -> isInMethod(cs, method))
            .collect(Collectors.toList());

        if (!callSites.isEmpty() && config.getVerbosity().ordinal() >= Verbosity.VERBOSE.ordinal()) {
            writer.writeLine("Call Sites:");
            writer.indent();
            for (CallSiteNode callSite : callSites) {
                printCallSite(writer, callSite);
            }
            writer.dedent();
        }

        writer.dedent();
    }

    private boolean isInMethod(CallSiteNode callSite, MethodNode method) {
        for (CPGEdge edge : callSite.getIncomingEdges()) {
            if (edge.getType() == CPGEdgeType.CALL) {
                CPGNode source = edge.getSource();
                if (source instanceof InstructionNode) {
                    for (CPGEdge containsEdge : source.getIncomingEdges()) {
                        if (containsEdge.getType() == CPGEdgeType.CONTAINS) {
                            CPGNode block = containsEdge.getSource();
                            for (CPGEdge methodEdge : block.getIncomingEdges()) {
                                if (methodEdge.getSource().equals(method)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void printBlock(IndentingWriter writer, BlockNode block) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(block.getId()).append("] ");
        }
        sb.append("BLOCK ").append(block.getBlockId());
        if (block.isEntryBlock()) sb.append(" (entry)");
        if (block.isExitBlock()) sb.append(" (exit)");
        writer.writeLine(sb.toString());

        if (config.getVerbosity().ordinal() >= Verbosity.NORMAL.ordinal()) {
            writer.indent();

            List<InstructionNode> instructions = block.getOutgoingEdges().stream()
                .filter(e -> e.getType() == CPGEdgeType.CONTAINS)
                .map(CPGEdge::getTarget)
                .filter(n -> n instanceof InstructionNode)
                .map(n -> (InstructionNode) n)
                .sorted(Comparator.comparingInt(InstructionNode::getInstructionIndex))
                .collect(Collectors.toList());

            int printed = 0;
            for (InstructionNode instr : instructions) {
                if (printed >= config.getMaxNodesPerMethod()) {
                    writer.writeLine("... (" + (instructions.size() - printed) + " more instructions)");
                    break;
                }
                printInstruction(writer, instr);
                printed++;
            }

            writer.dedent();
        }
    }

    private void printInstruction(IndentingWriter writer, InstructionNode instr) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(instr.getId()).append("] ");
        }

        String label = instr.getLabel();
        if (config.isTruncateLongLabels() && label.length() > config.getMaxLabelLength()) {
            label = label.substring(0, config.getMaxLabelLength() - 3) + "...";
        }
        sb.append(label);

        writer.writeLine(sb.toString());

        if (config.isShowProperties() && !instr.getProperties().isEmpty()) {
            writer.indent();
            for (Map.Entry<String, Object> prop : instr.getProperties().entrySet()) {
                writer.writeLine(prop.getKey() + ": " + prop.getValue());
            }
            writer.dedent();
        }
    }

    private void printCallSite(IndentingWriter writer, CallSiteNode callSite) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(callSite.getId()).append("] ");
        }
        sb.append(callSite.getInvokeType().name()).append(" ");
        sb.append(callSite.getTargetOwner()).append(".")
          .append(callSite.getTargetName());
        writer.writeLine(sb.toString());
    }

    private void printAllNodes(IndentingWriter writer, CodePropertyGraph cpg) {
        writer.writeLine("--- All Nodes ---");

        Map<CPGNodeType, List<CPGNode>> byType = new EnumMap<>(CPGNodeType.class);
        for (CPGNode node : cpg.getAllNodes()) {
            byType.computeIfAbsent(node.getNodeType(), k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<CPGNodeType, List<CPGNode>> entry : byType.entrySet()) {
            writer.writeLine(entry.getKey().name() + " (" + entry.getValue().size() + "):");
            writer.indent();

            int printed = 0;
            for (CPGNode node : entry.getValue()) {
                if (printed >= config.getMaxNodesPerMethod()) {
                    writer.writeLine("... (" + (entry.getValue().size() - printed) + " more)");
                    break;
                }
                printGenericNode(writer, node);
                printed++;
            }

            writer.dedent();
        }
    }

    private void printGenericNode(IndentingWriter writer, CPGNode node) {
        StringBuilder sb = new StringBuilder();
        if (config.isShowNodeIds()) {
            sb.append("[").append(node.getId()).append("] ");
        }

        String label = node.getLabel();
        if (config.isTruncateLongLabels() && label.length() > config.getMaxLabelLength()) {
            label = label.substring(0, config.getMaxLabelLength() - 3) + "...";
        }
        sb.append(label);

        writer.writeLine(sb.toString());
    }

    private void printEdges(IndentingWriter writer, CodePropertyGraph cpg) {
        writer.writeLine("--- Edges by Type ---");

        Map<CPGEdgeType, Integer> edgeCounts = cpg.getEdgeTypeCounts();

        for (Map.Entry<CPGEdgeType, Integer> entry : edgeCounts.entrySet()) {
            writer.writeLine(entry.getKey().name() + ": " + entry.getValue());

            if (config.getVerbosity() == Verbosity.DEBUG) {
                writer.indent();
                int printed = 0;
                for (CPGEdge edge : cpg.getAllEdges()) {
                    if (edge.getType() != entry.getKey()) continue;
                    if (printed >= 10) {
                        writer.writeLine("...");
                        break;
                    }
                    writer.writeLine(edge.getSource().getId() + " -> " + edge.getTarget().getId());
                    printed++;
                }
                writer.dedent();
            }
        }
    }

    private void printStatistics(IndentingWriter writer, CodePropertyGraph cpg) {
        writer.writeLine("--- Statistics ---");
        writer.writeLine("Total nodes: " + cpg.getNodeCount());
        writer.writeLine("Total edges: " + cpg.getEdgeCount());
        writer.writeLine("Methods: " + cpg.getMethodCount());

        Map<CPGEdgeType, Integer> edgeCounts = cpg.getEdgeTypeCounts();
        writer.writeLine("Edge type breakdown:");
        writer.indent();
        for (Map.Entry<CPGEdgeType, Integer> entry : edgeCounts.entrySet()) {
            writer.writeLine(entry.getKey().name() + ": " + entry.getValue());
        }
        writer.dedent();
    }
}
