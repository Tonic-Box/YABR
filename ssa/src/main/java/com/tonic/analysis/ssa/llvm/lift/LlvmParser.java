package com.tonic.analysis.ssa.llvm.lift;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a textual LLVM IR module into {@link ParsedFunction} value objects. The module format
 * produced by the YABR lowerer is strictly regular — no nested braces, no continuation lines.
 */
final class LlvmParser {

    // define <ret> @"sym"(<params>) [personality ptr @jvm_personality] {
    private static final Pattern DEFINE = Pattern.compile(
        "^define\\s+(\\S+)\\s+(@\"[^\"]*\"|@\\S+)\\s*\\(([^)]*)\\)");

    // Label line: "B3:" or "Lpad0:" — no leading whitespace, ends with colon
    private static final Pattern LABEL = Pattern.compile("^([A-Za-z0-9_]+):$");

    private LlvmParser() {
    }

    /** Returns one {@link ParsedFunction} per {@code define} block found in the module text. */
    static List<ParsedFunction> parse(String moduleText) {
        List<ParsedFunction> functions = new ArrayList<>();
        String[] lines = moduleText.split("\n", -1);

        int i = 0;
        while (i < lines.length) {
            String line = lines[i].stripTrailing();
            Matcher m = DEFINE.matcher(line);
            if (m.find()) {
                String retType = m.group(1);
                String symbol = m.group(2);
                String paramStr = m.group(3).trim();
                List<String> params = parseParams(paramStr);

                // Collect body lines until matching '}'
                List<String> body = new ArrayList<>();
                i++;
                while (i < lines.length) {
                    String bodyLine = lines[i].stripTrailing();
                    if (bodyLine.equals("}")) {
                        break;
                    }
                    body.add(bodyLine);
                    i++;
                }
                functions.add(new ParsedFunction(symbol, retType, params, parseBlocks(body)));
            }
            i++;
        }
        return functions;
    }

    /** Parses the parameter list string {@code "i32 %v0, ptr %v1, ..."} into individual param strings. */
    private static List<String> parseParams(String paramStr) {
        if (paramStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(paramStr.split(",\\s*"));
    }

    private static List<ParsedFunction.ParsedBlock> parseBlocks(List<String> bodyLines) {
        List<ParsedFunction.ParsedBlock> blocks = new ArrayList<>();
        String currentLabel = null;
        List<String> currentLines = null;

        for (String raw : bodyLines) {
            // Strip exactly 2-space indent that LlvmFunctionBuilder.emit() adds
            String line = raw.startsWith("  ") ? raw.substring(2) : raw;

            Matcher lm = LABEL.matcher(line.stripTrailing());
            if (lm.matches()) {
                if (currentLabel != null) {
                    blocks.add(new ParsedFunction.ParsedBlock(currentLabel, currentLines));
                }
                currentLabel = lm.group(1);
                currentLines = new ArrayList<>();
            } else if (currentLabel != null && !line.isBlank()) {
                currentLines.add(line.stripTrailing());
            }
        }
        if (currentLabel != null) {
            blocks.add(new ParsedFunction.ParsedBlock(currentLabel, currentLines));
        }
        return blocks;
    }
}
