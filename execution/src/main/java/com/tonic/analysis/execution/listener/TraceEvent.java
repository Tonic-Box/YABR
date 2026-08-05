package com.tonic.analysis.execution.listener;

import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.Collections;
import java.util.List;

/**
 * Immutable record of a single interpreter event, timestamped at creation and built via static factories.
 */
public final class TraceEvent
{

    /**
     * Categories of interpreter events a trace can record.
     */
    public enum Type
    {
        /**
         * The interpreter began a run, carrying the entry-point signature; the
         * first event in any trace.
         */
        EXECUTION_START,
        /**
         * The interpreter finished a run; the last event in any trace.
         */
        EXECUTION_END,
        /**
         * A frame was pushed onto the call stack for a method about to run.
         */
        FRAME_PUSH,
        /**
         * A frame was popped after its method returned normally.
         */
        FRAME_POP,
        /**
         * A frame was unwound because its method exited by exception.
         */
        FRAME_EXCEPTION,
        /**
         * A single instruction was executed, carrying its offset, opcode, and an
         * optional operand stack snapshot.
         */
        INSTRUCTION,
        /**
         * A value was pushed onto the operand stack.
         */
        STACK_PUSH,
        /**
         * A value was popped off the operand stack.
         */
        STACK_POP,
        /**
         * A local variable was read, carrying its slot number and the value
         * loaded.
         */
        LOCAL_LOAD,
        /**
         * A local variable was written, carrying its slot number and the value stored.
         */
        LOCAL_STORE,
        /**
         * An object was allocated on the interpreter heap, carrying its class
         * and the heap id assigned to it.
         */
        OBJECT_ALLOC,
        /**
         * An array was allocated on the interpreter heap, carrying its element type, length, and
         * the heap id assigned to it.
         */
        ARRAY_ALLOC,
        /**
         * A field was loaded, carrying the owning instance, the field name, and
         * the value read.
         */
        FIELD_READ,
        /**
         * A field was stored, carrying the owning instance, the field name, and the value written.
         */
        FIELD_WRITE,
        /**
         * An array element was loaded, carrying the array, the index, and the
         * value read.
         */
        ARRAY_READ,
        /**
         * An array element was stored, carrying the array, the index, and the value written.
         */
        ARRAY_WRITE,
        /**
         * A branch instruction was evaluated, carrying its source offset, target
         * offset, and whether the branch was taken.
         */
        BRANCH,
        /**
         * A method invocation was dispatched, carrying the resolved target.
         */
        METHOD_CALL,
        /**
         * A method returned to its caller, carrying the returned value where there is one.
         */
        METHOD_RETURN,
        /**
         * A method raised an exception, recorded before any handler search.
         */
        EXCEPTION_THROW,
        /**
         * A handler was selected for a thrown exception, carrying the handler offset.
         */
        EXCEPTION_CATCH,
        /**
         * A native method was reached, so the interpreter handed it to a
         * built-in implementation instead of executing bytecode.
         */
        NATIVE_CALL,
        /**
         * A native method's built-in implementation returned, carrying its result.
         */
        NATIVE_RETURN
    }

    private final Type type;
    private final long timestamp;
    private final int pc;
    private final String description;
    private final List<ConcreteValue> stackState;

    private TraceEvent(Type type, int pc, String description, List<ConcreteValue> stackState)
    {
        this.type = type;
        this.timestamp = System.nanoTime();
        this.pc = pc;
        this.description = description;
        this.stackState = stackState != null ? Collections.unmodifiableList(stackState) : null;
    }

    /**
     * Creates an execution-start event.
     * @param entryPoint the entry-point method signature
     * @return the created event
     */
    public static TraceEvent executionStart(String entryPoint)
    {
        return new TraceEvent(Type.EXECUTION_START, -1, "Start: " + entryPoint, null);
    }

    /**
     * Creates an execution-end event.
     * @param result the final execution result
     * @return the created event
     */
    public static TraceEvent executionEnd(String result)
    {
        return new TraceEvent(Type.EXECUTION_END, -1, "End: " + result, null);
    }

    /**
     * Creates a frame-push event.
     * @param methodSig the signature of the pushed frame's method
     * @return the created event
     */
    public static TraceEvent framePush(String methodSig)
    {
        return new TraceEvent(Type.FRAME_PUSH, -1, "Push frame: " + methodSig, null);
    }

    /**
     * Creates a frame-pop event.
     * @param methodSig the signature of the popped frame's method
     * @param returnValue the value returned by the frame
     * @return the created event
     */
    public static TraceEvent framePop(String methodSig, String returnValue)
    {
        return new TraceEvent(Type.FRAME_POP, -1, "Pop frame: " + methodSig + " -> " + returnValue, null);
    }

    /**
     * Creates a frame-exception event.
     * @param methodSig the signature of the method whose frame threw
     * @param exception the exception that escaped the frame
     * @return the created event
     */
    public static TraceEvent frameException(String methodSig, String exception)
    {
        return new TraceEvent(Type.FRAME_EXCEPTION, -1, "Frame exception: " + methodSig + " threw " + exception, null);
    }

    /**
     * Creates an instruction-execution event.
     * @param pc the bytecode offset
     * @param opcode the instruction opcode
     * @param stack the operand stack snapshot, or null when not captured
     * @return the created event
     */
    public static TraceEvent instruction(int pc, int opcode, List<ConcreteValue> stack)
    {
        return new TraceEvent(Type.INSTRUCTION, pc, String.format("0x%02X", opcode), stack);
    }

    /**
     * Creates a stack-push event.
     * @param pc the bytecode offset
     * @param value the value pushed
     * @return the created event
     */
    public static TraceEvent stackPush(int pc, String value)
    {
        return new TraceEvent(Type.STACK_PUSH, pc, "Push: " + value, null);
    }

    /**
     * Creates a stack-pop event.
     * @param pc the bytecode offset
     * @param value the value popped
     * @return the created event
     */
    public static TraceEvent stackPop(int pc, String value)
    {
        return new TraceEvent(Type.STACK_POP, pc, "Pop: " + value, null);
    }

    /**
     * Creates a local-variable-load event.
     * @param pc the bytecode offset
     * @param slot the local variable slot
     * @param value the value loaded
     * @return the created event
     */
    public static TraceEvent localLoad(int pc, int slot, String value)
    {
        return new TraceEvent(Type.LOCAL_LOAD, pc, "Load local[" + slot + "]: " + value, null);
    }

    /**
     * Creates a local-variable-store event.
     * @param pc the bytecode offset
     * @param slot the local variable slot
     * @param value the value stored
     * @return the created event
     */
    public static TraceEvent localStore(int pc, int slot, String value)
    {
        return new TraceEvent(Type.LOCAL_STORE, pc, "Store local[" + slot + "]: " + value, null);
    }

    /**
     * Creates an object-allocation event.
     * @param className the class of the new object
     * @param id the heap id of the new object
     * @return the created event
     */
    public static TraceEvent objectAllocation(String className, int id)
    {
        return new TraceEvent(Type.OBJECT_ALLOC, -1, "New " + className + "@" + id, null);
    }

    /**
     * Creates an array-allocation event.
     * @param componentType the array component type
     * @param length the array length
     * @param id the heap id of the new array
     * @return the created event
     */
    public static TraceEvent arrayAllocation(String componentType, int length, int id)
    {
        return new TraceEvent(Type.ARRAY_ALLOC, -1, "New " + componentType + "[" + length + "]@" + id, null);
    }

    /**
     * Creates a field-read event.
     * @param instance the owning instance
     * @param fieldName the field name
     * @param value the value read
     * @return the created event
     */
    public static TraceEvent fieldRead(String instance, String fieldName, String value)
    {
        return new TraceEvent(Type.FIELD_READ, -1, instance + "." + fieldName + " -> " + value, null);
    }

    /**
     * Creates a field-write event.
     * @param instance the owning instance
     * @param fieldName the field name
     * @param oldValue the previous field value
     * @param newValue the stored field value
     * @return the created event
     */
    public static TraceEvent fieldWrite(String instance, String fieldName, String oldValue, String newValue)
    {
        return new TraceEvent(Type.FIELD_WRITE, -1,
            instance + "." + fieldName + ": " + oldValue + " -> " + newValue, null);
    }

    /**
     * Creates an array-read event.
     * @param array the array being read
     * @param index the element index
     * @param value the value read
     * @return the created event
     */
    public static TraceEvent arrayRead(String array, int index, String value)
    {
        return new TraceEvent(Type.ARRAY_READ, -1, array + "[" + index + "] -> " + value, null);
    }

    /**
     * Creates an array-write event.
     * @param array the array being written
     * @param index the element index
     * @param oldValue the previous element value
     * @param newValue the stored element value
     * @return the created event
     */
    public static TraceEvent arrayWrite(String array, int index, String oldValue, String newValue)
    {
        return new TraceEvent(Type.ARRAY_WRITE, -1, array + "[" + index + "]: " + oldValue + " -> " + newValue, null);
    }

    /**
     * Creates a branch event.
     * @param fromPC the bytecode offset of the branch instruction
     * @param toPC the branch target offset
     * @param taken whether the branch was taken
     * @return the created event
     */
    public static TraceEvent branch(int fromPC, int toPC, boolean taken)
    {
        return new TraceEvent(Type.BRANCH, fromPC,
            "Branch " + fromPC + " -> " + toPC + (taken ? " (taken)" : " (not taken)"), null);
    }

    /**
     * Creates a method-call event.
     * @param caller the calling method signature
     * @param target the invoked method signature
     * @return the created event
     */
    public static TraceEvent methodCall(String caller, String target)
    {
        return new TraceEvent(Type.METHOD_CALL, -1, caller + " calls " + target, null);
    }

    /**
     * Creates a method-return event.
     * @param method the returning method signature
     * @param returnValue the returned value, or "void"
     * @return the created event
     */
    public static TraceEvent methodReturn(String method, String returnValue)
    {
        return new TraceEvent(Type.METHOD_RETURN, -1, method + " returns " + returnValue, null);
    }

    /**
     * Creates an exception-throw event.
     * @param method the throwing method signature
     * @param exception the thrown exception
     * @return the created event
     */
    public static TraceEvent exceptionThrow(String method, String exception)
    {
        return new TraceEvent(Type.EXCEPTION_THROW, -1, method + " throws " + exception, null);
    }

    /**
     * Creates an exception-catch event.
     * @param method the catching method signature
     * @param exception the caught exception
     * @param handlerPC the bytecode offset of the handler
     * @return the created event
     */
    public static TraceEvent exceptionCatch(String method, String exception, int handlerPC)
    {
        return new TraceEvent(Type.EXCEPTION_CATCH, -1, method + " catches " + exception + " at " + handlerPC, null);
    }

    /**
     * Creates a native-method-call event.
     * @param method the native method signature
     * @return the created event
     */
    public static TraceEvent nativeCall(String method)
    {
        return new TraceEvent(Type.NATIVE_CALL, -1, "Native call: " + method, null);
    }

    /**
     * Creates a native-method-return event.
     * @param method the native method signature
     * @param result the returned value, or "void"
     * @return the created event
     */
    public static TraceEvent nativeReturn(String method, String result)
    {
        return new TraceEvent(Type.NATIVE_RETURN, -1, "Native return: " + method + " -> " + result, null);
    }

    /**
     * @return the type
     */
    public Type getType()
    {
        return type;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp()
    {
        return timestamp;
    }

    /**
     * @return the bytecode offset, or -1 when not applicable
     */
    public int getPC()
    {
        return pc;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @return the stack state
     */
    public List<ConcreteValue> getStackState()
    {
        return stackState;
    }

    /**
     * Renders the event as a single line with type, offset, description, and any captured stack state.
     * @return the formatted line
     */
    public String format()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(type).append("]");
        if (pc >= 0)
        {
            sb.append(" @").append(pc);
        }
        sb.append(" ").append(description);
        if (stackState != null && !stackState.isEmpty())
        {
            sb.append(" | stack=").append(stackState);
        }
        return sb.toString();
    }

    @Override
    public String toString()
    {
        return format();
    }
}
