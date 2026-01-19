package com.tonic.analysis.xref;

import lombok.Getter;

/**
 * Types of cross-references tracked in the xref database.
 * Each type represents a different kind of reference relationship.
 */
@Getter
public enum XrefType {
    // Method references
    METHOD_CALL("Method Call", "Direct method invocation"),
    METHOD_OVERRIDE("Method Override", "Method that overrides a parent method"),
    BOOTSTRAP_METHOD("Bootstrap Method", "Bootstrap method for invokedynamic/condy"),
    BOOTSTRAP_ARG_METHOD("Bootstrap Arg Method", "Method referenced in bootstrap arguments"),
    BOOTSTRAP_ARG_FIELD("Bootstrap Arg Field", "Field referenced in bootstrap arguments"),

    // Field references
    FIELD_READ("Field Read", "Read access to a field"),
    FIELD_WRITE("Field Write", "Write access to a field"),

    // Class instantiation and type usage
    CLASS_INSTANTIATE("Class Instantiation", "new Object() calls"),
    CLASS_CAST("Type Cast", "Cast expressions (Type) obj"),
    CLASS_INSTANCEOF("Instanceof Check", "instanceof type checks"),

    // Inheritance and implementation
    CLASS_EXTENDS("Class Extension", "extends relationship"),
    CLASS_IMPLEMENTS("Interface Implementation", "implements relationship"),
    CLASS_ANNOTATION("Annotation Usage", "Annotation applied to element"),

    // Type references in declarations
    TYPE_PARAMETER("Type Parameter", "Generic type parameter usage"),
    TYPE_LOCAL_VAR("Local Variable Type", "Type used in local variable declaration");

    private final String displayName;
    private final String description;

    XrefType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Check if this is a method-related reference.
     */
    public boolean isMethodRef() {
        return this == METHOD_CALL || this == METHOD_OVERRIDE ||
               this == BOOTSTRAP_METHOD || this == BOOTSTRAP_ARG_METHOD;
    }

    /**
     * Check if this is a field-related reference.
     */
    public boolean isFieldRef() {
        return this == FIELD_READ || this == FIELD_WRITE;
    }

    /**
     * Check if this is a class/type-related reference.
     */
    public boolean isTypeRef() {
        return this == CLASS_INSTANTIATE || this == CLASS_CAST ||
               this == CLASS_INSTANCEOF || this == CLASS_EXTENDS ||
               this == CLASS_IMPLEMENTS || this == CLASS_ANNOTATION ||
               this == TYPE_PARAMETER || this == TYPE_LOCAL_VAR;
    }

    /**
     * Check if this reference indicates inheritance/implementation.
     */
    public boolean isInheritanceRef() {
        return this == CLASS_EXTENDS || this == CLASS_IMPLEMENTS;
    }
}
