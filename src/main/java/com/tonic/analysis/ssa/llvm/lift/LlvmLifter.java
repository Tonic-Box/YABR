package com.tonic.analysis.ssa.llvm.lift;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;

import java.util.List;

/**
 * Public entry point for lifting textual LLVM IR (computational subset, {@code NONE} model) back
 * to YABR SSA IR.
 *
 * <p>The lifter inverts the lowering performed by {@link com.tonic.analysis.ssa.llvm.LlvmLowering}:
 * it parses a {@code define … { … }} block, constructs {@link IRMethod}/{@link IRBlock}/{@link IRInstruction}
 * objects, folds synthesized temporaries (shift masks, narrowing conversions, 3-way compare
 * expansions) back to the originating single IR instructions, and returns an {@link IRMethod} ready
 * for {@code SSA.runTransforms(ir)} or further YABR analysis.
 *
 * <p>Only the computational subset emitted under {@link com.tonic.analysis.ssa.llvm.LlvmLoweringConfig.ObjectModel#NONE}
 * is supported. LLVM IR with {@code jvm_*} ABI calls or other runtime-model constructs is parsed
 * on a best-effort basis; unrecognised lines are silently skipped.
 *
 * <pre>{@code
 *   IRMethod original = new SSA(cp).lift(method);
 *   String ll = new LlvmLowering().lower(original);
 *   // optionally: run opt -O2 on ll
 *   IRMethod lifted = new LlvmLifter().lift(ll);
 * }</pre>
 */
public final class LlvmLifter {

    private final LlvmLifterConfig config;

    public LlvmLifter() {
        this(LlvmLifterConfig.defaults());
    }

    public LlvmLifter(LlvmLifterConfig config) {
        this.config = config != null ? config : LlvmLifterConfig.defaults();
    }

    /**
     * Lifts the first {@code define} function found in {@code llvmModuleText} and returns it as an
     * {@link IRMethod}. Throws {@link LlvmLiftException} if no function is found.
     */
    public IRMethod lift(String llvmModuleText) {
        List<IRMethod> methods = liftModule(llvmModuleText);
        if (methods.isEmpty()) {
            throw new LlvmLiftException("no define found in LLVM module text");
        }
        return methods.get(0);
    }

    /**
     * Lifts all {@code define} functions in {@code llvmModuleText} and returns them in order.
     */
    public List<IRMethod> liftModule(String llvmModuleText) {
        List<ParsedFunction> parsed = LlvmParser.parse(llvmModuleText);
        List<IRMethod> result = new java.util.ArrayList<>(parsed.size());
        for (ParsedFunction pf : parsed) {
            SSAValue.resetIdCounter();
            com.tonic.analysis.ssa.cfg.IRBlock.resetIdCounter();
            IRInstruction.resetIdCounter();
            FunctionLifter fl = new FunctionLifter(pf);
            IRMethod method = fl.lift();
            new TempFoldingPass(fl, fl.vMap).run(method);
            result.add(method);
        }
        return result;
    }
}
