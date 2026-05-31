package com.tonic.analysis.ssa.llvm.lift;

import java.util.List;

/**
 * Raw parsed representation of one LLVM {@code define} block before IR objects are constructed.
 * Each field is a string excerpt from the original text, parsed by {@link LlvmParser}.
 */
final class ParsedFunction {

    /** The mangled symbol, e.g. {@code @"T.add(II)I"}. */
    final String symbol;
    /** LLVM return type string, e.g. {@code i32}. */
    final String returnType;
    /** Ordered list of {@code "<type> %v<id>"} param strings. */
    final List<String> params;
    /** Basic blocks in textual order. */
    final List<ParsedBlock> blocks;

    ParsedFunction(String symbol, String returnType, List<String> params, List<ParsedBlock> blocks) {
        this.symbol = symbol;
        this.returnType = returnType;
        this.params = params;
        this.blocks = blocks;
    }

    static final class ParsedBlock {
        /** Block label without colon, e.g. {@code B3}. */
        final String label;
        /** All instruction lines (trimmed, 2-space indent removed). */
        final List<String> lines;

        ParsedBlock(String label, List<String> lines) {
            this.label = label;
            this.lines = lines;
        }
    }
}
