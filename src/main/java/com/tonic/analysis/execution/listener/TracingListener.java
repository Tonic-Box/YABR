package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.result.BytecodeResult;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TracingListener extends AbstractBytecodeListener {

    private final List<TraceEvent> events;
    private final boolean includeStackState;
    private final int maxEvents;

    public TracingListener() {
        this(false, Integer.MAX_VALUE);
    }

    public TracingListener(boolean includeStackState) {
        this(includeStackState, Integer.MAX_VALUE);
    }

    public TracingListener(boolean includeStackState, int maxEvents) {
        this.events = new ArrayList<>();
        this.includeStackState = includeStackState;
        this.maxEvents = maxEvents;
    }

    private void recordEvent(TraceEvent event) {
        if (events.size() < maxEvents) {
            events.add(event);
        }
    }

    @Override
    public void onExecutionStart(MethodEntry entryPoint) {
        super.onExecutionStart(entryPoint);
        recordEvent(TraceEvent.executionStart(entryPoint.getOwnerName() + "." + entryPoint.getName() + entryPoint.getDesc()));
    }

    @Override
    public void onExecutionEnd(BytecodeResult result) {
        recordEvent(TraceEvent.executionEnd(result != null ? result.toString() : "null"));
    }

    @Override
    public void onFramePush(StackFrame frame) {
        super.onFramePush(frame);
        recordEvent(TraceEvent.framePush(frame.getMethodSignature()));
    }

    @Override
    public void onFramePop(StackFrame frame, ConcreteValue returnValue) {
        recordEvent(TraceEvent.framePop(frame.getMethodSignature(),
            returnValue != null ? returnValue.toString() : "void"));
    }

    @Override
    public void onFrameException(StackFrame frame, ObjectInstance exception) {
        recordEvent(TraceEvent.frameException(frame.getMethodSignature(), exception.toString()));
    }

    @Override
    public void beforeInstruction(StackFrame frame, Instruction instruction) {
        recordEvent(TraceEvent.instruction(
            frame.getPC(),
            instruction.getOpcode(),
            includeStackState ? frame.getStack().snapshot() : null
        ));
    }

    @Override
    public void onStackPush(StackFrame frame, ConcreteValue value) {
        recordEvent(TraceEvent.stackPush(frame.getPC(), value.toString()));
    }

    @Override
    public void onStackPop(StackFrame frame, ConcreteValue value) {
        recordEvent(TraceEvent.stackPop(frame.getPC(), value.toString()));
    }

    @Override
    public void onLocalLoad(StackFrame frame, int slot, ConcreteValue value) {
        recordEvent(TraceEvent.localLoad(frame.getPC(), slot, value.toString()));
    }

    @Override
    public void onLocalStore(StackFrame frame, int slot, ConcreteValue value) {
        recordEvent(TraceEvent.localStore(frame.getPC(), slot, value.toString()));
    }

    @Override
    public void onObjectAllocation(ObjectInstance instance) {
        recordEvent(TraceEvent.objectAllocation(instance.getClassName(), instance.getId()));
    }

    @Override
    public void onArrayAllocation(ArrayInstance array) {
        recordEvent(TraceEvent.arrayAllocation(
            array.getComponentType(),
            array.getLength(),
            array.getId()
        ));
    }

    @Override
    public void onFieldRead(ObjectInstance instance, String fieldName, ConcreteValue value) {
        recordEvent(TraceEvent.fieldRead(
            instance.toString(),
            fieldName,
            value != null ? value.toString() : "null"
        ));
    }

    @Override
    public void onFieldWrite(ObjectInstance instance, String fieldName, ConcreteValue oldValue, ConcreteValue newValue) {
        recordEvent(TraceEvent.fieldWrite(
            instance.toString(),
            fieldName,
            oldValue != null ? oldValue.toString() : "null",
            newValue != null ? newValue.toString() : "null"
        ));
    }

    @Override
    public void onArrayRead(ArrayInstance array, int index, ConcreteValue value) {
        recordEvent(TraceEvent.arrayRead(
            array.toString(),
            index,
            value != null ? value.toString() : "null"
        ));
    }

    @Override
    public void onArrayWrite(ArrayInstance array, int index, ConcreteValue oldValue, ConcreteValue newValue) {
        recordEvent(TraceEvent.arrayWrite(
            array.toString(),
            index,
            oldValue != null ? oldValue.toString() : "null",
            newValue != null ? newValue.toString() : "null"
        ));
    }

    @Override
    public void onBranch(StackFrame frame, int fromPC, int toPC, boolean taken) {
        recordEvent(TraceEvent.branch(fromPC, toPC, taken));
    }

    @Override
    public void onMethodCall(StackFrame caller, MethodEntry target, ConcreteValue[] args) {
        recordEvent(TraceEvent.methodCall(
            caller != null ? caller.getMethodSignature() : "root",
            target.getOwnerName() + "." + target.getName() + target.getDesc()
        ));
    }

    @Override
    public void onMethodReturn(StackFrame frame, ConcreteValue returnValue) {
        recordEvent(TraceEvent.methodReturn(
            frame.getMethodSignature(),
            returnValue != null ? returnValue.toString() : "void"
        ));
    }

    @Override
    public void onExceptionThrow(StackFrame frame, ObjectInstance exception) {
        recordEvent(TraceEvent.exceptionThrow(frame.getMethodSignature(), exception.toString()));
    }

    @Override
    public void onExceptionCatch(StackFrame frame, ObjectInstance exception, int handlerPC) {
        recordEvent(TraceEvent.exceptionCatch(
            frame.getMethodSignature(),
            exception.toString(),
            handlerPC
        ));
    }

    @Override
    public void onNativeMethodCall(MethodEntry method, ObjectInstance receiver, ConcreteValue[] args) {
        recordEvent(TraceEvent.nativeCall(
            method.getOwnerName() + "." + method.getName() + method.getDesc()
        ));
    }

    @Override
    public void onNativeMethodReturn(MethodEntry method, ConcreteValue result) {
        recordEvent(TraceEvent.nativeReturn(
            method.getOwnerName() + "." + method.getName() + method.getDesc(),
            result != null ? result.toString() : "void"
        ));
    }

    public List<TraceEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void clearEvents() {
        events.clear();
    }

    public String formatTrace() {
        return formatTrace(Integer.MAX_VALUE);
    }

    public String formatTrace(int maxLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execution Trace (").append(events.size()).append(" events):\n");

        int count = 0;
        for (TraceEvent event : events) {
            if (count >= maxLines) {
                sb.append("... (").append(events.size() - count).append(" more events)\n");
                break;
            }
            sb.append("  ").append(event.format()).append("\n");
            count++;
        }

        return sb.toString();
    }

    @Override
    public void reset() {
        super.reset();
        events.clear();
    }
}
