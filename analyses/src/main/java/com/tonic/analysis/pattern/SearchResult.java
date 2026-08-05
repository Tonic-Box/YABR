package com.tonic.analysis.pattern;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.Objects;

/**
 * A single pattern-search hit, locating a class, method, or IR instruction with a description.
 */
public class SearchResult
{

    private final ClassFile classFile;
    private final MethodEntry method;
    private final IRInstruction instruction;
    private final int bytecodeOffset;
    private final String description;

    /**
     * Creates an instruction-level result.
     * @param classFile the class the result refers to
     * @param method the matched method, may be null
     * @param instruction the matched IR instruction, may be null
     * @param bytecodeOffset the bytecode offset, or -1 if unknown
     * @param description what was matched
     */
    public SearchResult(ClassFile classFile, MethodEntry method, IRInstruction instruction, int bytecodeOffset, String description)
    {
        this.classFile = classFile;
        this.method = method;
        this.instruction = instruction;
        this.bytecodeOffset = bytecodeOffset;
        this.description = description;
    }

    /**
     * Creates a method-level result with no instruction.
     * @param classFile the class the result refers to
     * @param method the matched method
     * @param description what was matched
     */
    public SearchResult(ClassFile classFile, MethodEntry method, String description)
    {
        this(classFile, method, null, -1, description);
    }

    /**
     * Creates a class-level result with no method or instruction.
     * @param classFile the class the result refers to
     * @param description what was matched
     */
    public SearchResult(ClassFile classFile, String description)
    {
        this(classFile, null, null, -1, description);
    }

    /**
     * @return the class file
     */
    public ClassFile getClassFile()
    {
        return classFile;
    }

    /**
     * @return the method
     */
    public MethodEntry getMethod()
    {
        return method;
    }

    /**
     * @return the instruction
     */
    public IRInstruction getInstruction()
    {
        return instruction;
    }

    /**
     * @return the bytecode offset
     */
    public int getBytecodeOffset()
    {
        return bytecodeOffset;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @return the class name, or null if no class is attached
     */
    public String getClassName()
    {
        return classFile != null ? classFile.getClassName() : null;
    }

    /**
     * @return the method name, or null if no method is attached
     */
    public String getMethodName()
    {
        return method != null ? method.getName() : null;
    }

    /**
     * @return the method descriptor, or null if no method is attached
     */
    public String getMethodDescriptor()
    {
        return method != null ? method.getDesc() : null;
    }

    /**
     * Formats the result location as class, optional method signature, and optional bytecode offset.
     * @return the human-readable location string
     */
    public String getLocation()
    {
        StringBuilder sb = new StringBuilder();
        if (classFile != null)
        {
            sb.append(classFile.getClassName());
        }
        if (method != null)
        {
            sb.append(".").append(method.getName()).append(method.getDesc());
        }
        if (bytecodeOffset >= 0)
        {
            sb.append(" @ ").append(bytecodeOffset);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof SearchResult)) return false;
        SearchResult that = (SearchResult) o;
        return bytecodeOffset == that.bytecodeOffset &&
               Objects.equals(classFile, that.classFile) &&
               Objects.equals(method, that.method) &&
               Objects.equals(instruction, that.instruction);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(classFile, method, instruction, bytecodeOffset);
    }

    @Override
    public String toString()
    {
        return getLocation() + ": " + description;
    }
}
