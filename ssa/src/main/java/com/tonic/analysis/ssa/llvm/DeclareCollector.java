package com.tonic.analysis.ssa.llvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Collects the external static callees encountered during lowering and renders module-level
 * {@code declare} lines for them. Keyed by mangled symbol (deduped); rendered in sorted order for
 * deterministic module output. Symbols also {@code define}d in the module are excluded at render.
 */
final class DeclareCollector {

    private final TreeMap<String, String> declares = new TreeMap<>();

    void note(String mangledName, LlvmType returnType, List<LlvmType> paramTypes) {
        String params = paramTypes.stream().map(LlvmType::render).collect(Collectors.joining(", "));
        declares.put(mangledName, "declare " + returnType.render() + " " + mangledName + "(" + params + ")");
    }

    List<String> renderDeclares(Set<String> definedSymbols) {
        List<String> out = new ArrayList<>();
        for (var entry : declares.entrySet()) {
            if (!definedSymbols.contains(entry.getKey())) {
                out.add(entry.getValue());
            }
        }
        return out;
    }
}
