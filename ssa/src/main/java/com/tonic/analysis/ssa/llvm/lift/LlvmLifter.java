package com.tonic.analysis.ssa.llvm.lift;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.analysis.ssa.value.SSAValue;
import java.util.List;

/**
 * Entry point for lifting textual LLVM IR back to YABR SSA IR, inverting {@link
 * com.tonic.analysis.ssa.llvm.LlvmLowering}.
 */
public final class LlvmLifter
{

    /**
     * Lifts the first {@code define} function found in {@code llvmModuleText}.
     *
     * @param llvmModuleText the textual LLVM IR module
     * @return the first function as an IRMethod
     * @throws LlvmLiftException if the module defines no function
     */
    public IRMethod lift(String llvmModuleText)
    {
        List<IRMethod> methods = liftModule(llvmModuleText);
        if (methods.isEmpty())
        {
            throw new LlvmLiftException("no define found in LLVM module text");
        }
        return methods.get(0);
    }

    /**
     * Lifts all {@code define} functions in {@code llvmModuleText} and returns them in order.
     *
     * @param llvmModuleText the textual LLVM IR module
     * @return one IRMethod per function, empty if the module defines none
     */
    public List<IRMethod> liftModule(String llvmModuleText)
    {
        List<ParsedFunction> parsed = LlvmParser.parse(llvmModuleText);
        List<IRMethod> result = new java.util.ArrayList<>(parsed.size());
        for (ParsedFunction pf : parsed)
        {
            SSAValue.resetIdCounter();
            IRBlock.resetIdCounter();
            IRInstruction.resetIdCounter();
            FunctionLifter fl = new FunctionLifter(pf);
            IRMethod method = fl.lift();
            new TempFoldingPass(fl, fl.vMap).run(method);
            result.add(method);
        }
        return result;
    }
}
