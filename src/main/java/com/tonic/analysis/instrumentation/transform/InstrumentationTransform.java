package com.tonic.analysis.instrumentation.transform;

import com.tonic.analysis.instrumentation.InstrumentationConfig;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.factory.InstrumentationFactory;
import com.tonic.analysis.instrumentation.filter.InstrumentationFilter;
import com.tonic.analysis.instrumentation.hook.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Transform that applies instrumentation hooks to IR methods.
 * Handles method entry/exit, field access, and array operations.
 */
public class InstrumentationTransform {

    private final List<Hook> hooks;
    private final InstrumentationConfig config;
    private final InstrumentationFactory factory;

    @Getter
    private int lastInstrumentationCount;

    public InstrumentationTransform(List<Hook> hooks, InstrumentationConfig config) {
        this.hooks = hooks;
        this.config = config;
        this.factory = new InstrumentationFactory();
    }

    /**
     * Instruments a single method.
     *
     * @param irMethod the IR method to instrument
     * @param sourceMethod the source method entry
     * @param classFile the class file
     * @return number of instrumentation points applied
     */
    public int instrumentMethod(IRMethod irMethod, MethodEntry sourceMethod, ClassFile classFile) {
        if (!shouldInstrumentMethod(sourceMethod)) {
            return 0;
        }

        lastInstrumentationCount = 0;
        String className = classFile.getClassName();

        // Sort hooks by priority (lower = first)
        List<Hook> sortedHooks = new ArrayList<>(hooks);
        sortedHooks.sort(Comparator.comparingInt(Hook::getPriority));

        for (Hook hook : sortedHooks) {
            if (!hook.isEnabled()) continue;
            if (!matchesFilters(hook.getFilters(), classFile, sourceMethod)) continue;

            try {
                switch (hook.getTarget()) {
                    case METHOD_ENTRY:
                        lastInstrumentationCount += instrumentMethodEntry(irMethod, (MethodEntryHook) hook, sourceMethod, className);
                        break;
                    case METHOD_EXIT:
                        lastInstrumentationCount += instrumentMethodExit(irMethod, (MethodExitHook) hook, sourceMethod, className);
                        break;
                    case FIELD_WRITE:
                        lastInstrumentationCount += instrumentFieldWrites(irMethod, (FieldWriteHook) hook, className);
                        break;
                    case FIELD_READ:
                        lastInstrumentationCount += instrumentFieldReads(irMethod, (FieldReadHook) hook, className);
                        break;
                    case ARRAY_STORE:
                        lastInstrumentationCount += instrumentArrayStores(irMethod, (ArrayStoreHook) hook);
                        break;
                    case ARRAY_LOAD:
                        lastInstrumentationCount += instrumentArrayLoads(irMethod, (ArrayLoadHook) hook);
                        break;
                    case METHOD_CALL_BEFORE:
                    case METHOD_CALL_AFTER:
                        lastInstrumentationCount += instrumentMethodCalls(irMethod, (MethodCallHook) hook, className);
                        break;
                    case EXCEPTION_HANDLER:
                        lastInstrumentationCount += instrumentExceptionHandlers(irMethod, (ExceptionHook) hook, className);
                        break;
                }
            } catch (Exception e) {
                if (config.isFailOnError()) {
                    throw new RuntimeException("Failed to apply hook " + hook.getTarget() +
                            " to method " + sourceMethod.getName(), e);
                }
                if (config.isVerbose()) {
                    System.err.println("Warning: Failed to apply hook to " + sourceMethod.getName() + ": " + e.getMessage());
                }
            }
        }

        return lastInstrumentationCount;
    }

    private boolean shouldInstrumentMethod(MethodEntry method) {
        int access = method.getAccess();

        if (config.isSkipAbstract() && Modifier.isAbstract(access)) return false;
        if (config.isSkipNative() && Modifier.isNative(access)) return false;
        if (config.isSkipSynthetic() && (access & 0x1000) != 0) return false;
        if (config.isSkipBridge() && (access & 0x0040) != 0) return false;
        if (config.isSkipConstructors() && method.getName().equals("<init>")) return false;
        if (config.isSkipStaticInitializers() && method.getName().equals("<clinit>")) return false;

        return method.getCodeAttribute() != null;
    }

    private boolean matchesFilters(List<InstrumentationFilter> filters, ClassFile classFile, MethodEntry method) {
        for (InstrumentationFilter filter : filters) {
            if (!filter.matchesClass(classFile)) return false;
            if (!filter.matchesMethod(method)) return false;
        }
        return true;
    }

    private int instrumentMethodEntry(IRMethod irMethod, MethodEntryHook hook, MethodEntry sourceMethod, String className) {
        IRBlock entryBlock = irMethod.getEntryBlock();
        if (entryBlock == null) return 0;

        List<IRInstruction> hookInstructions = factory.createMethodEntryHook(hook, irMethod, sourceMethod, className);

        // Insert at the beginning of entry block (after phi instructions would go)
        for (int i = 0; i < hookInstructions.size(); i++) {
            entryBlock.insertInstruction(i, hookInstructions.get(i));
        }

        return 1;
    }

    private int instrumentMethodExit(IRMethod irMethod, MethodExitHook hook, MethodEntry sourceMethod, String className) {
        int count = 0;

        for (IRBlock block : irMethod.getBlocks()) {
            List<IRInstruction> instructions = block.getInstructions();
            if (instructions.isEmpty()) continue;

            IRInstruction last = instructions.get(instructions.size() - 1);
            if (last instanceof ReturnInstruction) {
                ReturnInstruction ret = (ReturnInstruction) last;

                List<IRInstruction> hookInstructions = factory.createMethodExitHook(
                        hook, irMethod, ret, sourceMethod, className);

                // Insert before the return
                int insertIdx = instructions.size() - 1;
                for (int i = 0; i < hookInstructions.size(); i++) {
                    block.insertInstruction(insertIdx + i, hookInstructions.get(i));
                }

                count++;
            }
        }

        return count;
    }

    private int instrumentFieldWrites(IRMethod irMethod, FieldWriteHook hook, String className) {
        int count = 0;

        for (IRBlock block : irMethod.getBlocks()) {
            // Work with a copy to avoid concurrent modification
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
            int offset = 0;

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof PutFieldInstruction) {
                    PutFieldInstruction putField = (PutFieldInstruction) instr;

                    // Check field-specific filters
                    if (!matchesFieldFilters(hook.getFilters(), putField)) continue;

                    // Check static/instance filter
                    if (putField.isStatic() && !hook.isInstrumentStatic()) continue;
                    if (!putField.isStatic() && !hook.isInstrumentInstance()) continue;

                    List<IRInstruction> hookInstructions = factory.createFieldWriteHook(
                            hook, putField, irMethod, className);

                    // Insert before the putfield
                    int insertIdx = i + offset;
                    for (int j = 0; j < hookInstructions.size(); j++) {
                        block.insertInstruction(insertIdx + j, hookInstructions.get(j));
                    }
                    offset += hookInstructions.size();

                    // Handle value modification if enabled
                    if (hook.isCanModifyValue() && !hookInstructions.isEmpty()) {
                        IRInstruction lastHook = hookInstructions.get(hookInstructions.size() - 1);
                        if (lastHook instanceof InvokeInstruction) {
                            InvokeInstruction hookInvoke = (InvokeInstruction) lastHook;
                            if (hookInvoke.getResult() != null) {
                                // Unbox if needed and replace the value
                                // This is simplified - full impl would handle unboxing
                                putField.replaceOperand(putField.getValue(), hookInvoke.getResult());
                            }
                        }
                    }

                    count++;
                }
            }
        }

        return count;
    }

    private int instrumentFieldReads(IRMethod irMethod, FieldReadHook hook, String className) {
        int count = 0;

        for (IRBlock block : irMethod.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
            int offset = 0;

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof GetFieldInstruction) {
                    GetFieldInstruction getField = (GetFieldInstruction) instr;

                    if (!matchesFieldFilters(hook.getFilters(), getField)) continue;
                    if (getField.isStatic() && !hook.isInstrumentStatic()) continue;
                    if (!getField.isStatic() && !hook.isInstrumentInstance()) continue;

                    List<IRInstruction> hookInstructions = factory.createFieldReadHook(
                            hook, getField, irMethod, className);

                    // Insert after the getfield
                    int insertIdx = i + offset + 1;
                    for (int j = 0; j < hookInstructions.size(); j++) {
                        block.insertInstruction(insertIdx + j, hookInstructions.get(j));
                    }
                    offset += hookInstructions.size();

                    count++;
                }
            }
        }

        return count;
    }

    private int instrumentArrayStores(IRMethod irMethod, ArrayStoreHook hook) {
        int count = 0;

        for (IRBlock block : irMethod.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
            int offset = 0;

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof ArrayStoreInstruction) {
                    ArrayStoreInstruction arrayStore = (ArrayStoreInstruction) instr;

                    // Check array type filter if specified
                    if (hook.getArrayTypeFilter() != null) {
                        // Would need type analysis to check this properly
                        // For now, skip filter check
                    }

                    List<IRInstruction> hookInstructions = factory.createArrayStoreHook(
                            hook, arrayStore, irMethod);

                    // Insert before the array store
                    int insertIdx = i + offset;
                    for (int j = 0; j < hookInstructions.size(); j++) {
                        block.insertInstruction(insertIdx + j, hookInstructions.get(j));
                    }
                    offset += hookInstructions.size();

                    // Handle value modification if enabled
                    if (hook.isCanModifyValue() && !hookInstructions.isEmpty()) {
                        IRInstruction lastHook = hookInstructions.get(hookInstructions.size() - 1);
                        if (lastHook instanceof InvokeInstruction) {
                            InvokeInstruction hookInvoke = (InvokeInstruction) lastHook;
                            if (hookInvoke.getResult() != null) {
                                arrayStore.replaceOperand(arrayStore.getValue(), hookInvoke.getResult());
                            }
                        }
                    }

                    count++;
                }
            }
        }

        return count;
    }

    private int instrumentArrayLoads(IRMethod irMethod, ArrayLoadHook hook) {
        int count = 0;

        for (IRBlock block : irMethod.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
            int offset = 0;

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof ArrayLoadInstruction) {
                    ArrayLoadInstruction arrayLoad = (ArrayLoadInstruction) instr;

                    List<IRInstruction> hookInstructions = factory.createArrayLoadHook(
                            hook, arrayLoad, irMethod);

                    // Insert after the array load
                    int insertIdx = i + offset + 1;
                    for (int j = 0; j < hookInstructions.size(); j++) {
                        block.insertInstruction(insertIdx + j, hookInstructions.get(j));
                    }
                    offset += hookInstructions.size();

                    count++;
                }
            }
        }

        return count;
    }

    private int instrumentMethodCalls(IRMethod irMethod, MethodCallHook hook, String className) {
        int count = 0;

        for (IRBlock block : irMethod.getBlocks()) {
            List<IRInstruction> instructions = new ArrayList<>(block.getInstructions());
            int offset = 0;

            for (int i = 0; i < instructions.size(); i++) {
                IRInstruction instr = instructions.get(i);

                if (instr instanceof InvokeInstruction) {
                    InvokeInstruction invoke = (InvokeInstruction) instr;

                    // Check if this call matches the target
                    if (!matchesCallTarget(hook, invoke)) continue;

                    boolean isBefore = hook.getTarget() == InstrumentationTarget.METHOD_CALL_BEFORE;
                    List<IRInstruction> hookInstructions = factory.createMethodCallHook(
                            hook, invoke, irMethod, className, isBefore);

                    if (isBefore) {
                        // Insert before the call
                        int insertIdx = i + offset;
                        for (int j = 0; j < hookInstructions.size(); j++) {
                            block.insertInstruction(insertIdx + j, hookInstructions.get(j));
                        }
                    } else {
                        // Insert after the call
                        int insertIdx = i + offset + 1;
                        for (int j = 0; j < hookInstructions.size(); j++) {
                            block.insertInstruction(insertIdx + j, hookInstructions.get(j));
                        }
                    }
                    offset += hookInstructions.size();

                    count++;
                }
            }
        }

        return count;
    }

    private int instrumentExceptionHandlers(IRMethod irMethod, ExceptionHook hook, String className) {
        // Exception handlers are more complex - would need to modify exception handler table
        // For now, return 0 - this is a placeholder for future implementation
        return 0;
    }

    private boolean matchesFieldFilters(List<InstrumentationFilter> filters, PutFieldInstruction put) {
        for (InstrumentationFilter filter : filters) {
            if (!filter.matchesField(put.getOwner(), put.getName(), put.getDescriptor())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesFieldFilters(List<InstrumentationFilter> filters, GetFieldInstruction get) {
        for (InstrumentationFilter filter : filters) {
            if (!filter.matchesField(get.getOwner(), get.getName(), get.getDescriptor())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCallTarget(MethodCallHook hook, InvokeInstruction invoke) {
        if (hook.getTargetClass() != null && !hook.getTargetClass().equals(invoke.getOwner())) {
            return false;
        }
        if (hook.getTargetMethod() != null && !hook.getTargetMethod().equals(invoke.getName())) {
            return false;
        }
        if (hook.getTargetDescriptor() != null && !hook.getTargetDescriptor().equals(invoke.getDescriptor())) {
            return false;
        }
        return true;
    }
}
