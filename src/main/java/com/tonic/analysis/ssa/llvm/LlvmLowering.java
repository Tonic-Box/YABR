package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Public entry point for lowering YABR SSA IR to textual LLVM IR.
 *
 * <p>v1 supports the computational subset (arithmetic, conversions, control flow, phis, static
 * calls); constructs requiring the object/runtime model throw {@link UnsupportedOperationException}
 * via {@link UnsupportedLowering}. Output is pure text — no native dependency.
 *
 * <pre>{@code
 *   IRMethod ir = new SSA(cf.getConstPool()).lift(method);
 *   String ll = new LlvmLowering().lower(ir);
 * }</pre>
 */
public final class LlvmLowering {

    private final LlvmLoweringConfig config;

    public LlvmLowering() {
        this(LlvmLoweringConfig.defaults());
    }

    public LlvmLowering(LlvmLoweringConfig config) {
        this.config = config != null ? config : LlvmLoweringConfig.defaults();
    }

    /** Lowers a single method to a complete LLVM IR module (declares + one define). */
    public String lower(IRMethod method) {
        return lowerToModule(Collections.singletonList(method));
    }

    /** Lowers several methods into one module; callees defined in the module are not re-declared. */
    public String lowerToModule(List<IRMethod> methods) {
        LlvmModule module = new LlvmModule(config);
        DeclareCollector declares = new DeclareCollector();
        Set<String> definedSymbols = new HashSet<>();
        List<String> defines = new ArrayList<>();

        for (IRMethod method : methods) {
            LlvmFunctionBuilder fb = new LlvmFunctionBuilder();
            SsaToLlvmLowerer lowerer = new SsaToLlvmLowerer(method, fb, declares, config);
            defines.add(lowerer.lowerFunction());
            definedSymbols.add(lowerer.definedSymbol());
        }

        module.addDeclares(declares.renderDeclares(definedSymbols));
        for (String define : defines) {
            module.addFunction(define);
        }
        return module.render();
    }
}
