package com.tonic.analysis.execution.frame;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteLocals;
import com.tonic.analysis.execution.state.ConcreteStack;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.LineNumberTableAttribute;
import com.tonic.parser.attribute.table.LineNumberTableEntry;
import com.tonic.util.Logger;

/**
 * One interpreter activation: a method's operand stack, locals, program counter, and completion state.
 */
public final class StackFrame
{

    private final MethodEntry method;
    private final CodeWriter code;
    private final ConcreteStack stack;
    private final ConcreteLocals locals;
    private int pc;
    private boolean completed;
    private ConcreteValue returnValue;
    private ObjectInstance exception;

    /**
     * Creates a frame for a method invocation with the given arguments bound to locals.
     * @param method the method to execute
     * @param args argument values placed into the leading local slots
     * @throws IllegalArgumentException if the method is null or has no code attribute
     */
    public StackFrame(MethodEntry method, ConcreteValue[] args)
    {
        if (method == null)
        {
            throw new IllegalArgumentException("Method cannot be null");
        }

        this.method = method;

        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null)
        {
            throw new IllegalArgumentException("Method has no code attribute (abstract/native): " +
                method.getOwnerName() + "." + method.getName() + method.getDesc());
        }

        this.code = new CodeWriter(method);
        int stackSize = Math.max(codeAttr.getMaxStack(), 8);
        this.stack = new ConcreteStack(stackSize);
        this.locals = ConcreteLocals.forMethod(method, args);
        this.pc = 0;
        this.completed = false;
        this.returnValue = null;
        this.exception = null;
    }

    /**
     * @return the method
     */
    public MethodEntry getMethod()
    {
        return method;
    }

    /**
     * @return the code
     */
    public CodeWriter getCode()
    {
        return code;
    }

    /**
     * @return the stack
     */
    public ConcreteStack getStack()
    {
        return stack;
    }

    /**
     * @return the locals
     */
    public ConcreteLocals getLocals()
    {
        return locals;
    }

    /**
     * @return whether completed
     */
    public boolean isCompleted()
    {
        return completed;
    }

    /**
     * @return the exception
     */
    public ObjectInstance getException()
    {
        return exception;
    }

    /**
     * @return the current program counter
     */
    public int getPC()
    {
        return pc;
    }

    /**
     * Moves the program counter to an absolute bytecode offset.
     * @param pc the target offset
     * @throws IllegalArgumentException if the offset is negative or beyond the bytecode length
     */
    public void setPC(int pc)
    {
        if (pc < 0)
        {
            throw new IllegalArgumentException("PC cannot be negative: " + pc);
        }
        int codeLength = code.getBytecodeSize();
        if (pc > codeLength)
        {
            throw new IllegalArgumentException("PC exceeds bytecode length: " + pc + " > " + codeLength);
        }
        this.pc = pc;
    }

    /**
     * Advances the program counter past the current instruction.
     * @param delta the number of bytes to advance
     * @throws IllegalArgumentException if the delta is negative
     */
    public void advancePC(int delta)
    {
        if (delta < 0)
        {
            throw new IllegalArgumentException("PC delta cannot be negative: " + delta);
        }
        this.pc += delta;
    }

    /**
     * Looks up the instruction at the current program counter.
     * @return the instruction at the PC, or null if none exists there
     */
    public Instruction getCurrentInstruction()
    {
        return getInstructionAt(pc);
    }

    /**
     * Looks up the instruction starting at a bytecode offset.
     * @param offset the bytecode offset to find
     * @return the instruction at that offset, or null if none starts there
     */
    public Instruction getInstructionAt(int offset)
    {
        return code.getInstructions().spliterator().tryAdvance(instr -> {})
            ? findInstructionAtOffset(offset)
            : null;
    }

    private Instruction findInstructionAtOffset(int offset)
    {
        for (Instruction instr : code.getInstructions())
        {
            if (instr.getOffset() == offset)
            {
                return instr;
            }
        }
        Logger.error("No instruction found at offset " + offset + " in method " +
                     method.getOwnerName() + "." + method.getName() + method.getDesc());
        return null;
    }

    /**
     * Checks whether execution can continue in this frame.
     * @return true if an instruction exists at the PC and the frame is not completed
     */
    public boolean hasMoreInstructions()
    {
        return getCurrentInstruction() != null && !completed;
    }

    /**
     * Marks the frame as normally completed.
     * @param returnValue the method's return value, or null for void
     * @throws IllegalStateException if the frame is already completed
     */
    public void complete(ConcreteValue returnValue)
    {
        if (completed)
        {
            throw new IllegalStateException("Frame already completed");
        }
        this.completed = true;
        this.returnValue = returnValue;
    }

    /**
     * Marks the frame as completed by a thrown exception.
     * @param exception the exception object terminating the frame
     * @throws IllegalStateException if the frame is already completed
     * @throws IllegalArgumentException if the exception is null
     */
    public void completeExceptionally(ObjectInstance exception)
    {
        if (completed)
        {
            throw new IllegalStateException("Frame already completed");
        }
        if (exception == null)
        {
            throw new IllegalArgumentException("Exception cannot be null");
        }
        this.completed = true;
        this.exception = exception;
    }

    /**
     * Retrieves the return value of a completed frame.
     * @return the return value, or null for void
     * @throws IllegalStateException if the frame has not completed
     */
    public ConcreteValue getReturnValue()
    {
        if (!completed)
        {
            throw new IllegalStateException("Frame not yet completed");
        }
        return returnValue;
    }

    /**
     * @return the owner, name, and descriptor as one signature string
     */
    public String getMethodSignature()
    {
        return method.getOwnerName() + "." + method.getName() + method.getDesc();
    }

    /**
     * Maps the current PC to a source line via the LineNumberTable attribute.
     * @return the source line for the PC, or -1 if no table entry covers it
     */
    public int getLineNumber()
    {
        CodeAttribute codeAttr = method.getCodeAttribute();
        if (codeAttr == null)
        {
            return -1;
        }

        LineNumberTableAttribute lineTable = null;
        for (var attr : codeAttr.getAttributes())
        {
            if (attr instanceof LineNumberTableAttribute)
            {
                lineTable = (LineNumberTableAttribute) attr;
                break;
            }
        }

        if (lineTable == null || lineTable.getLineNumberTable().isEmpty())
        {
            return -1;
        }

        int bestLine = -1;
        int bestPc = -1;

        for (LineNumberTableEntry entry : lineTable.getLineNumberTable())
        {
            int startPc = entry.getStartPc();
            if (startPc <= pc && startPc > bestPc)
            {
                bestPc = startPc;
                bestLine = entry.getLineNumber();
            }
        }

        return bestLine;
    }

    @Override
    public String toString()
    {
        return "StackFrame{" +
                "method=" + getMethodSignature() +
                ", pc=" + pc +
                ", line=" + getLineNumber() +
                ", completed=" + completed +
                '}';
    }
}
