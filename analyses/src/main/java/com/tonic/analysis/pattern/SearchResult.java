package com.tonic.analysis.pattern;

import com.tonic.analysis.ssa.ir.IRInstruction;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.Objects;

/**
 * Represents a search result from pattern matching.
 */
public class SearchResult {

    private final ClassFile classFile;
    private final MethodEntry method;
    private final IRInstruction instruction;
    private final int bytecodeOffset;
    private final String description;

    public SearchResult(ClassFile classFile, MethodEntry method, IRInstruction instruction,
                        int bytecodeOffset, String description) {
        this.classFile = classFile;
        this.method = method;
        this.instruction = instruction;
        this.bytecodeOffset = bytecodeOffset;
        this.description = description;
    }

    public SearchResult(ClassFile classFile, MethodEntry method, String description) {
        this(classFile, method, null, -1, description);
    }

    public SearchResult(ClassFile classFile, String description) {
        this(classFile, null, null, -1, description);
    }

    public ClassFile getClassFile() {
        return classFile;
    }

    public MethodEntry getMethod() {
        return method;
    }

    public IRInstruction getInstruction() {
        return instruction;
    }

    public int getBytecodeOffset() {
        return bytecodeOffset;
    }

    public String getDescription() {
        return description;
    }

    public String getClassName() {
        return classFile != null ? classFile.getClassName() : null;
    }

    public String getMethodName() {
        return method != null ? method.getName() : null;
    }

    public String getMethodDescriptor() {
        return method != null ? method.getDesc() : null;
    }

    public String getLocation() {
        StringBuilder sb = new StringBuilder();
        if (classFile != null) {
            sb.append(classFile.getClassName());
        }
        if (method != null) {
            sb.append(".").append(method.getName()).append(method.getDesc());
        }
        if (bytecodeOffset >= 0) {
            sb.append(" @ ").append(bytecodeOffset);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchResult)) return false;
        SearchResult that = (SearchResult) o;
        return bytecodeOffset == that.bytecodeOffset &&
               Objects.equals(classFile, that.classFile) &&
               Objects.equals(method, that.method) &&
               Objects.equals(instruction, that.instruction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classFile, method, instruction, bytecodeOffset);
    }

    @Override
    public String toString() {
        return getLocation() + ": " + description;
    }
}
