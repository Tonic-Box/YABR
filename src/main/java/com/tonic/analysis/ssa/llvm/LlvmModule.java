package com.tonic.analysis.ssa.llvm;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a complete textual LLVM IR module: an optional target header (triple / datalayout),
 * the {@code declare} block for external callees, then one or more {@code define} blocks.
 */
final class LlvmModule {

    private final LlvmLoweringConfig config;
    private final List<String> declares = new ArrayList<>();
    private final List<String> functions = new ArrayList<>();

    LlvmModule(LlvmLoweringConfig config) {
        this.config = config;
    }

    void addDeclares(List<String> declareLines) {
        declares.addAll(declareLines);
    }

    void addFunction(String defineText) {
        functions.add(defineText);
    }

    String render() {
        StringBuilder sb = new StringBuilder();
        if (config.getTargetTriple() != null) {
            sb.append("target triple = \"").append(config.getTargetTriple()).append("\"\n");
        }
        if (config.getDataLayout() != null) {
            sb.append("target datalayout = \"").append(config.getDataLayout()).append("\"\n");
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        for (String declare : declares) {
            sb.append(declare).append('\n');
        }
        if (!declares.isEmpty()) {
            sb.append('\n');
        }
        for (int i = 0; i < functions.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(functions.get(i)).append('\n');
        }
        return sb.toString();
    }
}
