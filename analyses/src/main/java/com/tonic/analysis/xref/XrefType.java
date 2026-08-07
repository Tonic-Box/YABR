package com.tonic.analysis.xref;

/**
 * Kinds of cross-reference tracked in the xref database.
 */
public enum XrefType
{
    // Method references
    /**
     * A method named at an invoke instruction in the referencing method's body.
     */
    METHOD_CALL("Method Call", "Direct method invocation"),
    /**
     * A supertype method that the referencing method redeclares, recorded from
     * the class hierarchy rather than from a call site.
     */
    METHOD_OVERRIDE("Method Override", "Method that overrides a parent method"),
    /**
     * A method invoked by the JVM at link time to resolve an invokedynamic call
     * site or a dynamic constant.
     */
    BOOTSTRAP_METHOD("Bootstrap Method", "Bootstrap method for invokedynamic/condy"),
    /**
     * A method named by a method handle passed as a static argument to a
     * bootstrap method, such as the lambda body behind an invokedynamic.
     */
    BOOTSTRAP_ARG_METHOD("Bootstrap Arg Method", "Method referenced in bootstrap arguments"),
    /**
     * A field named by a field method handle passed as a static argument to a
     * bootstrap method.
     */
    BOOTSTRAP_ARG_FIELD("Bootstrap Arg Field", "Field referenced in bootstrap arguments"),

    // Field references
    /**
     * A field whose value the referencing code loads.
     */
    FIELD_READ("Field Read", "Read access to a field"),
    /**
     * A field whose value the referencing code stores to.
     */
    FIELD_WRITE("Field Write", "Write access to a field"),

    // Class instantiation and type usage
    /**
     * A class allocated by the referencing code, from a {@code new} expression.
     */
    CLASS_INSTANTIATE("Class Instantiation", "new Object() calls"),
    /**
     * A type a value is checked-cast to, from a cast expression.
     */
    CLASS_CAST("Type Cast", "Cast expressions (Type) obj"),
    /**
     * A type tested against a value by an {@code instanceof} check.
     */
    CLASS_INSTANCEOF("Instanceof Check", "instanceof type checks"),

    // Inheritance and implementation
    /**
     * A class named as the direct superclass of the referencing class.
     */
    CLASS_EXTENDS("Class Extension", "extends relationship"),
    /**
     * An interface listed among the referencing class's direct interfaces.
     */
    CLASS_IMPLEMENTS("Interface Implementation", "implements relationship"),
    /**
     * An annotation type applied to a class, method, field, or parameter.
     */
    CLASS_ANNOTATION("Annotation Usage", "Annotation applied to element"),

    // Type references in declarations
    /**
     * A class named as a generic type argument or type-parameter bound in a
     * signature.
     */
    TYPE_PARAMETER("Type Parameter", "Generic type parameter usage"),
    /**
     * A class named as the declared type of a local variable, recorded from the
     * local variable table rather than from an instruction.
     */
    TYPE_LOCAL_VAR("Local Variable Type", "Type used in local variable declaration");

    private final String displayName;
    private final String description;

    XrefType(String displayName, String description)
    {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * @return the display name
     */
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Check if this is a method-related reference.
     *
     * @return true for the call, override, and bootstrap-method kinds
     */
    public boolean isMethodRef()
    {
        return this == METHOD_CALL || this == METHOD_OVERRIDE ||
               this == BOOTSTRAP_METHOD || this == BOOTSTRAP_ARG_METHOD;
    }

    /**
     * Check if this is a field-related reference.
     *
     * @return true for the field read and write kinds
     */
    public boolean isFieldRef()
    {
        return this == FIELD_READ || this == FIELD_WRITE;
    }

    /**
     * Check if this is a class/type-related reference.
     *
     * @return true for instantiation, cast, instanceof, inheritance, annotation, and type-usage kinds
     */
    public boolean isTypeRef()
    {
        return this == CLASS_INSTANTIATE || this == CLASS_CAST ||
               this == CLASS_INSTANCEOF || this == CLASS_EXTENDS ||
               this == CLASS_IMPLEMENTS || this == CLASS_ANNOTATION ||
               this == TYPE_PARAMETER || this == TYPE_LOCAL_VAR;
    }

    /**
     * Check if this reference indicates inheritance/implementation.
     *
     * @return true for the extends and implements kinds
     */
    public boolean isInheritanceRef()
    {
        return this == CLASS_EXTENDS || this == CLASS_IMPLEMENTS;
    }
}
