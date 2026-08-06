package com.tonic.analysis.ssa.transform;

import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.util.IRMethodCloner;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * A class-level transform that replaces calls to private, final, and static methods with the callee's body.
 */
public class MethodInlining implements ClassTransform
{

    private static final int MAX_INLINE_SIZE = 35;
    private static final int MAX_INLINE_DEPTH = 5;

    private int inlineCount;
    private int currentDepth;

    @Override
    public String getName()
    {
        return "MethodInlining";
    }

    @Override
    public boolean run(ClassFile classFile, SSA ssa)
    {
        boolean changed = false;
        inlineCount = 0;

        String className = classFile.getClassName();

        Map<String, MethodEntry> methodMap = new HashMap<>();
        for (MethodEntry method : classFile.getMethods())
        {
            String key = method.getName() + method.getDesc();
            methodMap.put(key, method);
        }

        for (MethodEntry method : classFile.getMethods())
        {
            if (method.getCodeAttribute() == null) continue;
            if (method.getName().startsWith("<")) continue;

            currentDepth = 0;
            if (inlineMethodCalls(ssa, method, methodMap, className))
            {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Processes a method to inline eligible call sites.
     */
    private boolean inlineMethodCalls(SSA ssa, MethodEntry caller, Map<String, MethodEntry> methodMap, String className)
    {
        boolean changed = false;
        boolean madeProgress;

        do
        {
            madeProgress = false;

            IRMethod callerIR = ssa.lift(caller);

            List<InlineCandidate> candidates = findInlineCandidates(callerIR, methodMap, className, caller.getName());

            if (candidates.isEmpty())
            {
                ssa.lower(callerIR, caller);
                break;
            }

            for (InlineCandidate candidate : candidates)
            {
                if (inlineCall(ssa, callerIR, candidate))
                {
                    madeProgress = true;
                    changed = true;
                    inlineCount++;
                    break;
                }
            }

            ssa.lower(callerIR, caller);

        } while (madeProgress && currentDepth < MAX_INLINE_DEPTH);

        return changed;
    }

    /**
     * Finds all invoke instructions that are eligible for inlining.
     */
    private List<InlineCandidate> findInlineCandidates(IRMethod callerIR, Map<String, MethodEntry> methodMap, String className, String callerName)
    {
        List<InlineCandidate> candidates = new ArrayList<>();

        for (IRBlock block : callerIR.getBlocks())
        {
            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof InvokeInstruction)
                {
                    InvokeInstruction invoke = (InvokeInstruction) instr;
                    String calleeKey = invoke.getName() + invoke.getDescriptor();
                    MethodEntry callee = methodMap.get(calleeKey);

                    if (shouldInline(invoke, callee, className, callerName))
                    {
                        candidates.add(new InlineCandidate(block, invoke, callee));
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Checks if a call should be inlined.
     */
    private boolean shouldInline(InvokeInstruction invoke, MethodEntry callee, String className, String callerName)
    {
        if (callee == null) return false;
        if (callee.getCodeAttribute() == null) return false;

        String owner = invoke.getOwner();
        if (!owner.equals(className)) return false;

        if (invoke.getName().equals(callerName) && invoke.getDescriptor().equals(callee.getDesc()))
        {
            return false;
        }

        InvokeType type = invoke.getInvokeType();
        if (type == InvokeType.VIRTUAL || type == InvokeType.INTERFACE)
        {
            int access = callee.getAccess();
            if (!Modifier.isFinal(access))
            {
                return false;
            }
        }

        int access = callee.getAccess();
        if (Modifier.isNative(access)) return false;
        if (Modifier.isSynchronized(access)) return false;
        if (Modifier.isAbstract(access)) return false;

        boolean isPrivate = Modifier.isPrivate(access);
        boolean isFinal = Modifier.isFinal(access);
        boolean isStatic = Modifier.isStatic(access);

        if (!isPrivate && !isFinal && !isStatic)
        {
            return false;
        }

        CodeAttribute code = callee.getCodeAttribute();
        if (code.getCode().length > MAX_INLINE_SIZE)
        {
            return false;
        }

        if (!code.getExceptionTable().isEmpty())
        {
            return false;
        }

        return currentDepth < MAX_INLINE_DEPTH;
    }

    /**
     * Performs the actual inlining of a call site.
     */
    private boolean inlineCall(SSA ssa, IRMethod callerIR, InlineCandidate candidate)
    {
        currentDepth++;

        try
        {
            InvokeInstruction invoke = candidate.invoke;
            MethodEntry callee = candidate.callee;
            IRBlock callBlock = candidate.block;

            IRMethod calleeIR = ssa.lift(callee);
            if (calleeIR.getEntryBlock() == null)
            {
                return false;
            }

            IRMethodCloner cloner = new IRMethodCloner("inline_" + inlineCount + "_");
            IRMethod clonedCallee = cloner.clone(calleeIR);

            mapParametersToArguments(clonedCallee, invoke, cloner);

            int invokeIndex = callBlock.getInstructions().indexOf(invoke);
            if (invokeIndex < 0)
            {
                return false;
            }

            IRBlock continuationBlock = splitBlockAtInvoke(callerIR, callBlock, invokeIndex);

            SSAValue resultValue = invoke.getResult();
            handleReturns(clonedCallee, resultValue, continuationBlock);

            for (IRBlock block : clonedCallee.getBlocks())
            {
                callerIR.addBlock(block);
            }

            IRBlock inlinedEntry = clonedCallee.getEntryBlock();
            callBlock.addSuccessor(inlinedEntry);

            callBlock.removeInstruction(invoke);
            callBlock.addInstruction(SimpleInstruction.createGoto(inlinedEntry));

            return true;

        }
        finally
        {
            currentDepth--;
        }
    }

    /**
     * Maps callee parameters to the actual arguments from the call site.
     */
    private void mapParametersToArguments(IRMethod clonedCallee, InvokeInstruction invoke, IRMethodCloner cloner)
    {
        List<Value> arguments = invoke.getArguments();
        List<SSAValue> parameters = clonedCallee.getParameters();
        Map<SSAValue, SSAValue> valueMapping = cloner.getValueMapping();

        Set<SSAValue> replacedParams = new HashSet<>();

        IRBlock entryBlock = clonedCallee.getEntryBlock();
        List<IRInstruction> copies = new ArrayList<>();

        for (int i = 0; i < parameters.size() && i < arguments.size(); i++)
        {
            SSAValue param = parameters.get(i);
            Value arg = arguments.get(i);

            SSAValue clonedParam = valueMapping.get(param);
            if (clonedParam == null)
            {
                clonedParam = param;
            }

            if (arg instanceof SSAValue)
            {
                SSAValue argSSA = (SSAValue) arg;
                clonedParam.replaceAllUsesWith(argSSA);
                replacedParams.add(clonedParam);
            }
            else
            {
                CopyInstruction copy = new CopyInstruction(clonedParam, arg);
                copies.add(copy);
            }
        }

        for (int i = copies.size() - 1; i >= 0; i--)
        {
            entryBlock.insertInstruction(0, copies.get(i));
        }

        removeDeadLocalInstructions(clonedCallee);
    }

    /**
     * Removes ALL LoadLocalInstruction and StoreLocalInstruction from inlined code.
     */
    private void removeDeadLocalInstructions(IRMethod method)
    {
        for (IRBlock block : method.getBlocks())
        {
            List<IRInstruction> toRemove = new ArrayList<>();

            for (IRInstruction instr : block.getInstructions())
            {
                if (instr instanceof LoadLocalInstruction)
                {
                    toRemove.add(instr);
                }
                else if (instr instanceof StoreLocalInstruction)
                {
                    toRemove.add(instr);
                }
            }

            for (IRInstruction instr : toRemove)
            {
                block.removeInstruction(instr);
            }
        }
    }

    /**
     * Splits a block at the invoke instruction, creating a continuation block.
     */
    private IRBlock splitBlockAtInvoke(IRMethod callerIR, IRBlock callBlock, int invokeIndex)
    {
        IRBlock continuationBlock = new IRBlock("continue_" + inlineCount);
        callerIR.addBlock(continuationBlock);

        List<IRInstruction> instructions = callBlock.getInstructions();
        List<IRInstruction> toMove = new ArrayList<>();

        for (int i = invokeIndex + 1; i < instructions.size(); i++)
        {
            toMove.add(instructions.get(i));
        }

        for (IRInstruction instr : toMove)
        {
            callBlock.removeInstruction(instr);
            continuationBlock.addInstruction(instr);
        }

        for (IRBlock succ : new ArrayList<>(callBlock.getSuccessors()))
        {
            continuationBlock.addSuccessor(succ);
            callBlock.removeSuccessor(succ);

            for (PhiInstruction phi : succ.getPhiInstructions())
            {
                Value incoming = phi.getIncoming(callBlock);
                if (incoming != null)
                {
                    phi.removeIncoming(callBlock);
                    phi.addIncoming(incoming, continuationBlock);
                }
            }
        }

        return continuationBlock;
    }

    /**
     * Handles return instructions in the inlined code.
     */
    private void handleReturns(IRMethod clonedCallee, SSAValue resultValue, IRBlock continuationBlock)
    {
        List<ReturnInstruction> returns = new ArrayList<>();
        List<IRBlock> returnBlocks = new ArrayList<>();

        for (IRBlock block : clonedCallee.getBlocks())
        {
            IRInstruction term = block.getTerminator();
            if (term instanceof ReturnInstruction)
            {
                ReturnInstruction ret = (ReturnInstruction) term;
                returns.add(ret);
                returnBlocks.add(block);
            }
        }

        if (returns.isEmpty())
        {
            return;
        }

        if (returns.size() == 1)
        {
            ReturnInstruction ret = returns.get(0);
            IRBlock retBlock = returnBlocks.get(0);

            if (resultValue != null && ret.getReturnValue() != null)
            {
                CopyInstruction copy = new CopyInstruction(resultValue, ret.getReturnValue());
                retBlock.removeInstruction(ret);
                retBlock.addInstruction(copy);
            }
            else
            {
                retBlock.removeInstruction(ret);
            }

            retBlock.addInstruction(SimpleInstruction.createGoto(continuationBlock));
            retBlock.addSuccessor(continuationBlock);

        }
        else
        {
            PhiInstruction phi = null;
            if (resultValue != null)
            {
                phi = new PhiInstruction(resultValue);
                continuationBlock.addPhi(phi);
            }

            for (int i = 0; i < returns.size(); i++)
            {
                ReturnInstruction ret = returns.get(i);
                IRBlock retBlock = returnBlocks.get(i);

                if (phi != null && ret.getReturnValue() != null)
                {
                    phi.addIncoming(ret.getReturnValue(), retBlock);
                }

                retBlock.removeInstruction(ret);
                retBlock.addInstruction(SimpleInstruction.createGoto(continuationBlock));
                retBlock.addSuccessor(continuationBlock);
            }
        }
    }

    /**
     * Represents a candidate call site for inlining.
     */
    private static class InlineCandidate
    {
        final IRBlock block;
        final InvokeInstruction invoke;
        final MethodEntry callee;

        InlineCandidate(IRBlock block, InvokeInstruction invoke, MethodEntry callee)
        {
            this.block = block;
            this.invoke = invoke;
            this.callee = callee;
        }
    }
}
