package com.tonic.analysis.xref;

import com.tonic.analysis.common.MethodReference;

import java.util.Objects;

/**
 * An immutable cross-reference from a source instruction to the class, method, or field it names.
 */
public class Xref
{

    // Source location (where the reference occurs)
    private final String sourceClass;
    private final String sourceMethod;
    private final String sourceMethodDesc;
    private final int lineNumber;
    private final int instructionIndex;
    private final int bytecodeOffset;

    // Target (what is being referenced)
    private final String targetClass;
    private final String targetMember;      // method/field name, or null for class-only refs
    private final String targetDescriptor;  // method/field descriptor, or null

    // Reference metadata
    private final XrefType type;

    private Xref(Builder builder)
    {
        this.sourceClass = builder.sourceClass;
        this.sourceMethod = builder.sourceMethod;
        this.sourceMethodDesc = builder.sourceMethodDesc;
        this.lineNumber = builder.lineNumber;
        this.instructionIndex = builder.instructionIndex;
        this.bytecodeOffset = builder.bytecodeOffset;
        this.targetClass = builder.targetClass;
        this.targetMember = builder.targetMember;
        this.targetDescriptor = builder.targetDescriptor;
        this.type = builder.type;
    }

    // Getters

    /**
     * @return the source class
     */
    public String getSourceClass()
    {
        return sourceClass;
    }

    /**
     * @return the source method
     */
    public String getSourceMethod()
    {
        return sourceMethod;
    }

    /**
     * @return the source method desc
     */
    public String getSourceMethodDesc()
    {
        return sourceMethodDesc;
    }

    /**
     * @return the line number
     */
    public int getLineNumber()
    {
        return lineNumber;
    }

    /**
     * @return the instruction index
     */
    public int getInstructionIndex()
    {
        return instructionIndex;
    }

    /**
     * @return the bytecode offset
     */
    public int getBytecodeOffset()
    {
        return bytecodeOffset;
    }

    /**
     * @return the target class
     */
    public String getTargetClass()
    {
        return targetClass;
    }

    /**
     * @return the target member
     */
    public String getTargetMember()
    {
        return targetMember;
    }

    /**
     * @return the target descriptor
     */
    public String getTargetDescriptor()
    {
        return targetDescriptor;
    }

    /**
     * @return the type
     */
    public XrefType getType()
    {
        return type;
    }

    // Derived Properties

    /**
     * Tests whether the reference names a specific method.
     *
     * @return true if a member was recorded and the type is a method reference
     */
    public boolean isMethodRef()
    {
        return targetMember != null && type.isMethodRef();
    }

    /**
     * Tests whether the reference names a specific field.
     *
     * @return true if a member was recorded and the type is a field reference
     */
    public boolean isFieldRef()
    {
        return targetMember != null && type.isFieldRef();
    }

    /**
     * Tests whether the reference names a class rather than a specific member.
     *
     * @return true if the type is a type reference or no member was recorded
     */
    public boolean isClassRef()
    {
        return type.isTypeRef() || targetMember == null;
    }

    /**
     * Packages the source class, method name and descriptor as a method reference.
     *
     * @return the enclosing method reference
     */
    public MethodReference getSourceMethodRef()
    {
        return new MethodReference(sourceClass, sourceMethod, sourceMethodDesc);
    }

    /**
     * Packages the target class, member and descriptor as a method reference.
     *
     * @return the target method reference, or null if this is not a method reference
     */
    public MethodReference getTargetMethodRef()
    {
        if (!isMethodRef()) return null;
        return new MethodReference(targetClass, targetMember, targetDescriptor);
    }

    /**
     * Packages the target class, member and descriptor as a field reference.
     *
     * @return the target field reference, or null if this is not a field reference
     */
    public FieldReference getTargetFieldRef()
    {
        if (!isFieldRef()) return null;
        return new FieldReference(targetClass, targetMember, targetDescriptor);
    }

    /**
     * Renders the source as a dotted class name, optionally with method and line suffixes.
     *
     * @return the display string
     */
    public String getSourceDisplay()
    {
        String className = sourceClass.replace('/', '.');
        String methodPart = sourceMethod != null ? "." + sourceMethod + "()" : "";
        String linePart = lineNumber > 0 ? ":" + lineNumber : "";
        return className + methodPart + linePart;
    }

    /**
     * Renders the target as a dotted class name plus member, with "()" appended for method refs.
     *
     * @return the display string
     */
    public String getTargetDisplay()
    {
        String className = targetClass.replace('/', '.');
        if (targetMember == null)
        {
            return className;
        }
        if (type.isMethodRef())
        {
            return className + "." + targetMember + "()";
        }
        else if (type.isFieldRef())
        {
            return className + "." + targetMember;
        }
        return className + "." + targetMember;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Xref xref = (Xref) o;
        return lineNumber == xref.lineNumber &&
               instructionIndex == xref.instructionIndex &&
               bytecodeOffset == xref.bytecodeOffset &&
               Objects.equals(sourceClass, xref.sourceClass) &&
               Objects.equals(sourceMethod, xref.sourceMethod) &&
               Objects.equals(targetClass, xref.targetClass) &&
               Objects.equals(targetMember, xref.targetMember) &&
               type == xref.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(sourceClass, sourceMethod, lineNumber, instructionIndex, bytecodeOffset,
                           targetClass, targetMember, type);
    }

    @Override
    public String toString()
    {
        return String.format("[%s] %s → %s", type.name(), getSourceDisplay(), getTargetDisplay());
    }

    // Builder

    /**
     * @return a fresh builder with an empty source and target and type METHOD_CALL
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Mutable accumulator for the fields of an Xref.
     */
    public static class Builder
    {
        private String sourceClass = "";
        private String sourceMethod;
        private String sourceMethodDesc;
        private int lineNumber = -1;
        private int instructionIndex = -1;
        private int bytecodeOffset = -1;
        private String targetClass = "";
        private String targetMember;
        private String targetDescriptor;
        private XrefType type = XrefType.METHOD_CALL;

        /**
         * Sets the class the reference occurs in.
         * @param sourceClass the referencing class
         * @return this builder
         */
        public Builder sourceClass(String sourceClass)
        {
            this.sourceClass = sourceClass;
            return this;
        }

        /**
         * Sets the method the reference occurs in.
         * @param name the method name
         * @param desc the method descriptor
         * @return this builder
         */
        public Builder sourceMethod(String name, String desc)
        {
            this.sourceMethod = name;
            this.sourceMethodDesc = desc;
            return this;
        }

        /**
         * Sets the source line the reference was attributed to.
         * @param lineNumber the line number, or -1 if unknown
         * @return this builder
         */
        public Builder lineNumber(int lineNumber)
        {
            this.lineNumber = lineNumber;
            return this;
        }

        /**
         * Sets the index of the referencing instruction within the method.
         * @param index the instruction index, or -1 if unknown
         * @return this builder
         */
        public Builder instructionIndex(int index)
        {
            this.instructionIndex = index;
            return this;
        }

        /**
         * Sets the bytecode offset of the referencing instruction.
         * @param offset the offset, or -1 if unknown
         * @return this builder
         */
        public Builder bytecodeOffset(int offset)
        {
            this.bytecodeOffset = offset;
            return this;
        }

        /**
         * Sets the referenced class.
         * @param targetClass the class being referenced
         * @return this builder
         */
        public Builder targetClass(String targetClass)
        {
            this.targetClass = targetClass;
            return this;
        }

        /**
         * Sets the referenced member, leaving the target class untouched.
         * @param name the member name
         * @param desc the member descriptor
         * @return this builder
         */
        public Builder targetMember(String name, String desc)
        {
            this.targetMember = name;
            this.targetDescriptor = desc;
            return this;
        }

        /**
         * Sets the target class and member from a method reference.
         * @param owner the declaring class
         * @param name the method name
         * @param desc the method descriptor
         * @return this builder
         */
        public Builder targetMethod(String owner, String name, String desc)
        {
            this.targetClass = owner;
            this.targetMember = name;
            this.targetDescriptor = desc;
            return this;
        }

        /**
         * Sets the target class and member from a field reference.
         * @param owner the declaring class
         * @param name the field name
         * @param desc the field descriptor
         * @return this builder
         */
        public Builder targetField(String owner, String name, String desc)
        {
            this.targetClass = owner;
            this.targetMember = name;
            this.targetDescriptor = desc;
            return this;
        }

        /**
         * Sets the kind of reference; defaults to METHOD_CALL.
         * @param type the reference kind
         * @return this builder
         */
        public Builder type(XrefType type)
        {
            this.type = type;
            return this;
        }

        /**
         * @return an immutable cross-reference holding the accumulated values
         */
        public Xref build()
        {
            return new Xref(this);
        }
    }
}
