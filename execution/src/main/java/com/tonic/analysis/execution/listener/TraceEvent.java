package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.Collections;
import java.util.List;

public final class TraceEvent {

    public enum Type {
        EXECUTION_START, EXECUTION_END,
        FRAME_PUSH, FRAME_POP, FRAME_EXCEPTION,
        INSTRUCTION,
        STACK_PUSH, STACK_POP,
        LOCAL_LOAD, LOCAL_STORE,
        OBJECT_ALLOC, ARRAY_ALLOC,
        FIELD_READ, FIELD_WRITE,
        ARRAY_READ, ARRAY_WRITE,
        BRANCH, METHOD_CALL, METHOD_RETURN,
        EXCEPTION_THROW, EXCEPTION_CATCH,
        NATIVE_CALL, NATIVE_RETURN
    }

    private final Type type;
    private final long timestamp;
    private final int pc;
    private final String description;
    private final List<ConcreteValue> stackState;

    private TraceEvent(Type type, int pc, String description, List<ConcreteValue> stackState) {
        this.type = type;
        this.timestamp = System.nanoTime();
        this.pc = pc;
        this.description = description;
        this.stackState = stackState != null ? Collections.unmodifiableList(stackState) : null;
    }

    public static TraceEvent executionStart(String entryPoint) {
        return new TraceEvent(Type.EXECUTION_START, -1, "Start: " + entryPoint, null);
    }

    public static TraceEvent executionEnd(String result) {
        return new TraceEvent(Type.EXECUTION_END, -1, "End: " + result, null);
    }

    public static TraceEvent framePush(String methodSig) {
        return new TraceEvent(Type.FRAME_PUSH, -1, "Push frame: " + methodSig, null);
    }

    public static TraceEvent framePop(String methodSig, String returnValue) {
        return new TraceEvent(Type.FRAME_POP, -1, "Pop frame: " + methodSig + " -> " + returnValue, null);
    }

    public static TraceEvent frameException(String methodSig, String exception) {
        return new TraceEvent(Type.FRAME_EXCEPTION, -1, "Frame exception: " + methodSig + " threw " + exception, null);
    }

    public static TraceEvent instruction(int pc, int opcode, List<ConcreteValue> stack) {
        return new TraceEvent(Type.INSTRUCTION, pc, String.format("0x%02X", opcode), stack);
    }

    public static TraceEvent stackPush(int pc, String value) {
        return new TraceEvent(Type.STACK_PUSH, pc, "Push: " + value, null);
    }

    public static TraceEvent stackPop(int pc, String value) {
        return new TraceEvent(Type.STACK_POP, pc, "Pop: " + value, null);
    }

    public static TraceEvent localLoad(int pc, int slot, String value) {
        return new TraceEvent(Type.LOCAL_LOAD, pc, "Load local[" + slot + "]: " + value, null);
    }

    public static TraceEvent localStore(int pc, int slot, String value) {
        return new TraceEvent(Type.LOCAL_STORE, pc, "Store local[" + slot + "]: " + value, null);
    }

    public static TraceEvent objectAllocation(String className, int id) {
        return new TraceEvent(Type.OBJECT_ALLOC, -1, "New " + className + "@" + id, null);
    }

    public static TraceEvent arrayAllocation(String componentType, int length, int id) {
        return new TraceEvent(Type.ARRAY_ALLOC, -1, "New " + componentType + "[" + length + "]@" + id, null);
    }

    public static TraceEvent fieldRead(String instance, String fieldName, String value) {
        return new TraceEvent(Type.FIELD_READ, -1, instance + "." + fieldName + " -> " + value, null);
    }

    public static TraceEvent fieldWrite(String instance, String fieldName, String oldValue, String newValue) {
        return new TraceEvent(Type.FIELD_WRITE, -1,
            instance + "." + fieldName + ": " + oldValue + " -> " + newValue, null);
    }

    public static TraceEvent arrayRead(String array, int index, String value) {
        return new TraceEvent(Type.ARRAY_READ, -1, array + "[" + index + "] -> " + value, null);
    }

    public static TraceEvent arrayWrite(String array, int index, String oldValue, String newValue) {
        return new TraceEvent(Type.ARRAY_WRITE, -1,
            array + "[" + index + "]: " + oldValue + " -> " + newValue, null);
    }

    public static TraceEvent branch(int fromPC, int toPC, boolean taken) {
        return new TraceEvent(Type.BRANCH, fromPC,
            "Branch " + fromPC + " -> " + toPC + (taken ? " (taken)" : " (not taken)"), null);
    }

    public static TraceEvent methodCall(String caller, String target) {
        return new TraceEvent(Type.METHOD_CALL, -1, caller + " calls " + target, null);
    }

    public static TraceEvent methodReturn(String method, String returnValue) {
        return new TraceEvent(Type.METHOD_RETURN, -1, method + " returns " + returnValue, null);
    }

    public static TraceEvent exceptionThrow(String method, String exception) {
        return new TraceEvent(Type.EXCEPTION_THROW, -1, method + " throws " + exception, null);
    }

    public static TraceEvent exceptionCatch(String method, String exception, int handlerPC) {
        return new TraceEvent(Type.EXCEPTION_CATCH, -1,
            method + " catches " + exception + " at " + handlerPC, null);
    }

    public static TraceEvent nativeCall(String method) {
        return new TraceEvent(Type.NATIVE_CALL, -1, "Native call: " + method, null);
    }

    public static TraceEvent nativeReturn(String method, String result) {
        return new TraceEvent(Type.NATIVE_RETURN, -1, "Native return: " + method + " -> " + result, null);
    }

    public Type getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getPC() {
        return pc;
    }

    public String getDescription() {
        return description;
    }

    public List<ConcreteValue> getStackState() {
        return stackState;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append("]");
        if (pc >= 0) {
            sb.append(" @").append(pc);
        }
        sb.append(" ").append(description);
        if (stackState != null && !stackState.isEmpty()) {
            sb.append(" | stack=").append(stackState);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
