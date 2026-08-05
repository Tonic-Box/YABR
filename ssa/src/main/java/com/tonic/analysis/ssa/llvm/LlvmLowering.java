package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRMethod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Entry point lowering YABR SSA IR to textual LLVM IR; unsupported constructs throw
 * {@link UnsupportedOperationException} via {@link UnsupportedLowering}.
 */
public final class LlvmLowering
{

    private final LlvmLoweringConfig config;

    /**
     * Creates a lowering with the default configuration.
     */
    public LlvmLowering()
    {
        this(LlvmLoweringConfig.defaults());
    }

    /**
     * Creates a lowering.
     * @param config the configuration to use; null selects the defaults
     */
    public LlvmLowering(LlvmLoweringConfig config)
    {
        this.config = config != null ? config : LlvmLoweringConfig.defaults();
    }

    /**
     * Lowers a single method to a complete LLVM IR module (declares + one define).
     * @param method the SSA method to lower
     * @return the module's LLVM IR text
     * @throws UnsupportedOperationException when the method uses a construct outside the lowered subset
     */
    public String lower(IRMethod method)
    {
        return lowerToModule(Collections.singletonList(method));
    }

    /**
     * Lowers several methods into one module; callees defined in the module are not re-declared.
     * @param methods the SSA methods to lower
     * @return the module's LLVM IR text
     * @throws UnsupportedOperationException when a method uses a construct outside the lowered subset
     */
    public String lowerToModule(List<IRMethod> methods)
    {
        LlvmModule module = new LlvmModule(config);
        DeclareCollector declares = new DeclareCollector();
        GlobalCollector globals = new GlobalCollector();
        CStringPool strings = new CStringPool();
        Set<String> definedSymbols = new HashSet<>();
        Set<String> definedOwnerClasses = new HashSet<>();
        List<String> defines = new ArrayList<>();

        for (IRMethod method : methods)
        {
            LlvmFunctionBuilder fb = new LlvmFunctionBuilder();
            SsaToLlvmLowerer lowerer = new SsaToLlvmLowerer(method, fb, declares, globals, strings, config);
            defines.add(lowerer.lowerFunction());
            definedSymbols.add(lowerer.definedSymbol());
            definedOwnerClasses.add(method.getOwnerClass());
        }

        module.addConstants(strings.renderConstants());
        module.addGlobals(globals.renderGlobals(definedOwnerClasses));
        module.addDeclares(declares.renderDeclares(definedSymbols));
        for (String define : defines)
        {
            module.addFunction(define);
        }
        return module.render();
    }
}
