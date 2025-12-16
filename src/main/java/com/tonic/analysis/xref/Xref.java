package com.tonic.analysis.xref;

import java.util.Objects;

/**
 * Represents a cross-reference from a source location to a target symbol.
 *
 * A cross-reference captures:
 * - The source class and method where the reference occurs
 * - The target being referenced (class, method, or field)
 * - The type of reference (call, read, write, instantiate, etc.)
 * - The location within the source (line number, instruction index)
 */
public class Xref {

    // Source location (where the reference occurs)
    private final String sourceClass;
    private final String sourceMethod;
    private final String sourceMethodDesc;
    private final int lineNumber;
    private final int instructionIndex;

    // Target (what is being referenced)
    private final String targetClass;
    private final String targetMember;      // method/field name, or null for class-only refs
    private final String targetDescriptor;  // method/field descriptor, or null

    // Reference metadata
    private final XrefType type;

    private Xref(Builder builder) {
        this.sourceClass = builder.sourceClass;
        this.sourceMethod = builder.sourceMethod;
        this.sourceMethodDesc = builder.sourceMethodDesc;
        this.lineNumber = builder.lineNumber;
        this.instructionIndex = builder.instructionIndex;
        this.targetClass = builder.targetClass;
        this.targetMember = builder.targetMember;
        this.targetDescriptor = builder.targetDescriptor;
        this.type = builder.type;
    }

    // ==================== Getters ====================

    public String getSourceClass() {
        return sourceClass;
    }

    public String getSourceMethod() {
        return sourceMethod;
    }

    public String getSourceMethodDesc() {
        return sourceMethodDesc;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getInstructionIndex() {
        return instructionIndex;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public String getTargetMember() {
        return targetMember;
    }

    public String getTargetDescriptor() {
        return targetDescriptor;
    }

    public XrefType getType() {
        return type;
    }

    // ==================== Derived Properties ====================

    /**
     * Returns true if this references a specific method (not just a class).
     */
    public boolean isMethodRef() {
        return targetMember != null && type.isMethodRef();
    }

    /**
     * Returns true if this references a specific field.
     */
    public boolean isFieldRef() {
        return targetMember != null && type.isFieldRef();
    }

    /**
     * Returns true if this is a class-level reference (not member-specific).
     */
    public boolean isClassRef() {
        return type.isTypeRef() || targetMember == null;
    }

    /**
     * Returns a MethodReference for the source method.
     */
    public MethodReference getSourceMethodRef() {
        return new MethodReference(sourceClass, sourceMethod, sourceMethodDesc);
    }

    /**
     * Returns a MethodReference for the target if this is a method reference.
     */
    public MethodReference getTargetMethodRef() {
        if (!isMethodRef()) return null;
        return new MethodReference(targetClass, targetMember, targetDescriptor);
    }

    /**
     * Returns a FieldReference for the target if this is a field reference.
     */
    public FieldReference getTargetFieldRef() {
        if (!isFieldRef()) return null;
        return new FieldReference(targetClass, targetMember, targetDescriptor);
    }

    /**
     * Get display string for the source location.
     */
    public String getSourceDisplay() {
        String className = sourceClass.replace('/', '.');
        String methodPart = sourceMethod != null ? "." + sourceMethod + "()" : "";
        String linePart = lineNumber > 0 ? ":" + lineNumber : "";
        return className + methodPart + linePart;
    }

    /**
     * Get display string for the target.
     */
    public String getTargetDisplay() {
        String className = targetClass.replace('/', '.');
        if (targetMember == null) {
            return className;
        }
        if (type.isMethodRef()) {
            return className + "." + targetMember + "()";
        } else if (type.isFieldRef()) {
            return className + "." + targetMember;
        }
        return className + "." + targetMember;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Xref xref = (Xref) o;
        return lineNumber == xref.lineNumber &&
               instructionIndex == xref.instructionIndex &&
               Objects.equals(sourceClass, xref.sourceClass) &&
               Objects.equals(sourceMethod, xref.sourceMethod) &&
               Objects.equals(targetClass, xref.targetClass) &&
               Objects.equals(targetMember, xref.targetMember) &&
               type == xref.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceClass, sourceMethod, lineNumber, instructionIndex,
                           targetClass, targetMember, type);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s → %s", type.name(), getSourceDisplay(), getTargetDisplay());
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceClass = "";
        private String sourceMethod;
        private String sourceMethodDesc;
        private int lineNumber = -1;
        private int instructionIndex = -1;
        private String targetClass = "";
        private String targetMember;
        private String targetDescriptor;
        private XrefType type = XrefType.METHOD_CALL;

        public Builder sourceClass(String sourceClass) {
            this.sourceClass = sourceClass;
            return this;
        }

        public Builder sourceMethod(String name, String desc) {
            this.sourceMethod = name;
            this.sourceMethodDesc = desc;
            return this;
        }

        public Builder lineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }

        public Builder instructionIndex(int index) {
            this.instructionIndex = index;
            return this;
        }

        public Builder targetClass(String targetClass) {
            this.targetClass = targetClass;
            return this;
        }

        public Builder targetMember(String name, String desc) {
            this.targetMember = name;
            this.targetDescriptor = desc;
            return this;
        }

        public Builder targetMethod(String owner, String name, String desc) {
            this.targetClass = owner;
            this.targetMember = name;
            this.targetDescriptor = desc;
            return this;
        }

        public Builder targetField(String owner, String name, String desc) {
            this.targetClass = owner;
            this.targetMember = name;
            this.targetDescriptor = desc;
            return this;
        }

        public Builder type(XrefType type) {
            this.type = type;
            return this;
        }

        public Xref build() {
            return new Xref(this);
        }
    }
}
