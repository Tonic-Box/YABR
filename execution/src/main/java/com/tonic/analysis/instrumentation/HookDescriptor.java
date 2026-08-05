package com.tonic.analysis.instrumentation;

import com.tonic.analysis.ssa.ir.InvokeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a hook method to be called at instrumentation points.
 */
public class HookDescriptor
{

    private final String owner;
    private final String name;
    private final String descriptor;
    private final InvokeType invokeType;
    private final List<HookParameter> parameters;
    private final boolean replacesValue;

    private HookDescriptor(Builder builder)
    {
        this.owner = builder.owner;
        this.name = builder.name;
        this.descriptor = builder.descriptor;
        this.invokeType = builder.invokeType;
        this.parameters = builder.parameters;
        this.replacesValue = builder.replacesValue;
    }

    /**
     * @return the owner class of the hook method (internal name, e.g. {@code "com/example/Hooks"})
     */
    public String getOwner()
    {
        return owner;
    }

    /**
     * @return the name of the hook method
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the hook method descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the invocation type (typically STATIC)
     */
    public InvokeType getInvokeType()
    {
        return invokeType;
    }

    /**
     * @return the parameters to pass to the hook method
     */
    public List<HookParameter> getParameters()
    {
        return parameters;
    }

    /**
     * @return whether the hook's return value should replace the original value
     */
    public boolean isReplacesValue()
    {
        return replacesValue;
    }

    /**
     * Creates a descriptor for a static hook method.
     * @param owner the hook class internal name
     * @param name the hook method name
     * @param descriptor the hook method descriptor
     * @return the descriptor
     */
    public static HookDescriptor staticHook(String owner, String name, String descriptor)
    {
        return HookDescriptor.builder()
                .owner(owner)
                .name(name)
                .descriptor(descriptor)
                .invokeType(InvokeType.STATIC)
                .build();
    }

    /**
     * Creates a new builder.
     * @return a new Builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Builder for HookDescriptor instances.
     */
    public static class Builder
    {
        private String owner;
        private String name;
        private String descriptor;
        private InvokeType invokeType = InvokeType.STATIC;
        private List<HookParameter> parameters = new ArrayList<>();
        private boolean replacesValue = false;

        /**
         * Sets the hook class internal name.
         * @param owner the owner class internal name
         * @return this builder
         */
        public Builder owner(String owner)
        {
            this.owner = owner;
            return this;
        }

        /**
         * Sets the hook method name.
         * @param name the hook method name
         * @return this builder
         */
        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        /**
         * Sets the hook method descriptor.
         * @param descriptor the hook method descriptor
         * @return this builder
         */
        public Builder descriptor(String descriptor)
        {
            this.descriptor = descriptor;
            return this;
        }

        /**
         * Sets the invocation type used to call the hook.
         * @param invokeType the invocation type
         * @return this builder
         */
        public Builder invokeType(InvokeType invokeType)
        {
            this.invokeType = invokeType;
            return this;
        }

        /**
         * Sets the parameters to pass to the hook method.
         * @param parameters the hook parameters
         * @return this builder
         */
        public Builder parameters(List<HookParameter> parameters)
        {
            this.parameters = parameters;
            return this;
        }

        /**
         * Sets whether the hook's return value replaces the original value.
         * @param replacesValue true if the hook result replaces the value
         * @return this builder
         */
        public Builder replacesValue(boolean replacesValue)
        {
            this.replacesValue = replacesValue;
            return this;
        }

        /**
         * Builds the hook descriptor.
         * @return the built HookDescriptor
         */
        public HookDescriptor build()
        {
            return new HookDescriptor(this);
        }
    }

    /**
     * Types of parameters that can be passed to hooks.
     */
    public enum HookParameter
    {
        /**
         * The 'this' reference (null for static methods)
         */
        THIS,
        /**
         * The method name as a String
         */
        METHOD_NAME,
        /**
         * The method descriptor as a String
         */
        METHOD_DESCRIPTOR,
        /**
         * The class name as a String
         */
        CLASS_NAME,
        /**
         * All method parameters as Object[]
         */
        ALL_PARAMETERS,
        /**
         * A specific parameter by index
         */
        PARAMETER,
        /**
         * The return value (for exit hooks)
         */
        RETURN_VALUE,
        /**
         * The field owner object
         */
        FIELD_OWNER,
        /**
         * The field name as a String
         */
        FIELD_NAME,
        /**
         * The new value being assigned (for write hooks)
         */
        NEW_VALUE,
        /**
         * The old/read value (for read hooks)
         */
        READ_VALUE,
        /**
         * The array reference
         */
        ARRAY_REF,
        /**
         * The array index
         */
        ARRAY_INDEX,
        /**
         * The exception object (for exception hooks)
         */
        EXCEPTION,
        /**
         * The receiver of a method call
         */
        CALL_RECEIVER,
        /**
         * Arguments of a method call
         */
        CALL_ARGUMENTS,
        /**
         * Result of a method call
         */
        CALL_RESULT
    }
}
