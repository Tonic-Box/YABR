package com.tonic.analysis.frame;

import com.tonic.parser.attribute.stack.VerificationTypeInfo;
import lombok.Getter;

import java.util.Objects;

/**
 * Represents a verification type used in StackMapTable frames.
 * This is a sealed hierarchy allowing for both primitive types (as enum constants)
 * and reference types (as subclasses with additional data).
 */
@Getter
public abstract sealed class VerificationType permits
        VerificationType.PrimitiveType,
        VerificationType.ObjectType,
        VerificationType.UninitializedType {

    public static final int TAG_TOP = 0;
    public static final int TAG_INTEGER = 1;
    public static final int TAG_FLOAT = 2;
    public static final int TAG_DOUBLE = 3;
    public static final int TAG_LONG = 4;
    public static final int TAG_NULL = 5;
    public static final int TAG_UNINITIALIZED_THIS = 6;
    public static final int TAG_OBJECT = 7;
    public static final int TAG_UNINITIALIZED = 8;

    public static final VerificationType TOP = new PrimitiveType(TAG_TOP, "top");
    public static final VerificationType INTEGER = new PrimitiveType(TAG_INTEGER, "int");
    public static final VerificationType FLOAT = new PrimitiveType(TAG_FLOAT, "float");
    public static final VerificationType DOUBLE = new PrimitiveType(TAG_DOUBLE, "double");
    public static final VerificationType LONG = new PrimitiveType(TAG_LONG, "long");
    public static final VerificationType NULL = new PrimitiveType(TAG_NULL, "null");
    public static final VerificationType UNINITIALIZED_THIS = new PrimitiveType(TAG_UNINITIALIZED_THIS, "uninitializedThis");

    protected final int tag;

    /**
     * Constructs a VerificationType with the given tag.
     *
     * @param tag the JVM verification type tag
     */
    protected VerificationType(int tag) {
        this.tag = tag;
    }

    /**
     * Creates an Object verification type with the given constant pool index.
     *
     * @param classIndex the constant pool index
     * @return Object verification type
     */
    public static VerificationType object(int classIndex) {
        return new ObjectType(classIndex);
    }

    /**
     * Creates an Object verification type for a class name.
     *
     * @param className the class name
     * @param classIndex the constant pool index
     * @return Object verification type
     */
    public static VerificationType object(String className, int classIndex) {
        return new ObjectType(classIndex, className);
    }

    /**
     * Creates an Uninitialized verification type for a NEW instruction.
     *
     * @param newInstructionOffset the bytecode offset of the NEW instruction
     * @return Uninitialized verification type
     */
    public static VerificationType uninitialized(int newInstructionOffset) {
        return new UninitializedType(newInstructionOffset);
    }

    /**
     * Converts this VerificationType to a VerificationTypeInfo for writing to class files.
     *
     * @return VerificationTypeInfo instance
     */
    public abstract VerificationTypeInfo toVerificationTypeInfo();

    /**
     * Returns true if this type takes two slots.
     *
     * @return true for long or double
     */
    public boolean isTwoSlot() {
        return tag == TAG_LONG || tag == TAG_DOUBLE;
    }

    /**
     * Gets a verification type from a type descriptor character.
     *
     * @param c the descriptor character
     * @return corresponding VerificationType
     */
    public static VerificationType fromDescriptor(char c) {
        return switch (c) {
            case 'B', 'C', 'I', 'S', 'Z' -> INTEGER;
            case 'F' -> FLOAT;
            case 'D' -> DOUBLE;
            case 'J' -> LONG;
            default -> throw new IllegalArgumentException("Unknown primitive descriptor: " + c);
        };
    }

    /**
     * Gets a verification type from a full type descriptor.
     *
     * @param descriptor the type descriptor
     * @param classIndex the constant pool index for object types
     * @return corresponding VerificationType
     */
    public static VerificationType fromDescriptor(String descriptor, int classIndex) {
        if (descriptor.isEmpty()) {
            throw new IllegalArgumentException("Empty descriptor");
        }
        char first = descriptor.charAt(0);
        return switch (first) {
            case 'B', 'C', 'I', 'S', 'Z' -> INTEGER;
            case 'F' -> FLOAT;
            case 'D' -> DOUBLE;
            case 'J' -> LONG;
            case 'L', '[' -> object(classIndex);
            default -> throw new IllegalArgumentException("Unknown descriptor: " + descriptor);
        };
    }

    /**
     * Primitive verification type implementation.
     */
    @Getter
    public static final class PrimitiveType extends VerificationType {
        private final String name;

        private PrimitiveType(int tag, String name) {
            super(tag);
            this.name = name;
        }

        @Override
        public VerificationTypeInfo toVerificationTypeInfo() {
            return new VerificationTypeInfo(tag, null);
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PrimitiveType that)) return false;
            return tag == that.tag;
        }

        @Override
        public int hashCode() {
            return tag;
        }
    }

    /**
     * Object verification type implementation.
     */
    @Getter
    public static final class ObjectType extends VerificationType {
        private final int classIndex;
        private final String className;

        public ObjectType(int classIndex) {
            super(TAG_OBJECT);
            this.classIndex = classIndex;
            this.className = null;
        }

        public ObjectType(int classIndex, String className) {
            super(TAG_OBJECT);
            this.classIndex = classIndex;
            this.className = className;
        }

        @Override
        public VerificationTypeInfo toVerificationTypeInfo() {
            return new VerificationTypeInfo(TAG_OBJECT, classIndex);
        }

        @Override
        public String toString() {
            return className != null ? "Object(" + className + ")" : "Object(#" + classIndex + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ObjectType that)) return false;
            return classIndex == that.classIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(TAG_OBJECT, classIndex);
        }
    }

    /**
     * Uninitialized verification type implementation.
     */
    @Getter
    public static final class UninitializedType extends VerificationType {
        private final int newInstructionOffset;

        public UninitializedType(int newInstructionOffset) {
            super(TAG_UNINITIALIZED);
            this.newInstructionOffset = newInstructionOffset;
        }

        @Override
        public VerificationTypeInfo toVerificationTypeInfo() {
            return new VerificationTypeInfo(TAG_UNINITIALIZED, newInstructionOffset);
        }

        @Override
        public String toString() {
            return "Uninitialized(@" + newInstructionOffset + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UninitializedType that)) return false;
            return newInstructionOffset == that.newInstructionOffset;
        }

        @Override
        public int hashCode() {
            return Objects.hash(TAG_UNINITIALIZED, newInstructionOffset);
        }
    }
}
