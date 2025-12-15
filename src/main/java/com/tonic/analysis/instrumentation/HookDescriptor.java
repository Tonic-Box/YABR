package com.tonic.analysis.instrumentation;

import com.tonic.analysis.ssa.ir.InvokeType;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a hook method to be called at instrumentation points.
 */
@Getter
@Builder
public class HookDescriptor {

    /**
     * The owner class of the hook method (internal name, e.g., "com/example/Hooks").
     */
    private final String owner;

    /**
     * The name of the hook method.
     */
    private final String name;

    /**
     * The method descriptor.
     */
    private final String descriptor;

    /**
     * The invocation type (typically STATIC).
     */
    @Builder.Default
    private final InvokeType invokeType = InvokeType.STATIC;

    /**
     * The parameters to pass to the hook method.
     */
    @Builder.Default
    private final List<HookParameter> parameters = new ArrayList<>();

    /**
     * Whether the hook method returns a value that should replace the original.
     */
    @Builder.Default
    private final boolean replacesValue = false;

    /**
     * Creates a static hook descriptor.
     */
    public static HookDescriptor staticHook(String owner, String name, String descriptor) {
        return HookDescriptor.builder()
                .owner(owner)
                .name(name)
                .descriptor(descriptor)
                .invokeType(InvokeType.STATIC)
                .build();
    }

    /**
     * Types of parameters that can be passed to hooks.
     */
    public enum HookParameter {
        /** The 'this' reference (null for static methods) */
        THIS,
        /** The method name as a String */
        METHOD_NAME,
        /** The method descriptor as a String */
        METHOD_DESCRIPTOR,
        /** The class name as a String */
        CLASS_NAME,
        /** All method parameters as Object[] */
        ALL_PARAMETERS,
        /** A specific parameter by index */
        PARAMETER,
        /** The return value (for exit hooks) */
        RETURN_VALUE,
        /** The field owner object */
        FIELD_OWNER,
        /** The field name as a String */
        FIELD_NAME,
        /** The new value being assigned (for write hooks) */
        NEW_VALUE,
        /** The old/read value (for read hooks) */
        READ_VALUE,
        /** The array reference */
        ARRAY_REF,
        /** The array index */
        ARRAY_INDEX,
        /** The exception object (for exception hooks) */
        EXCEPTION,
        /** The receiver of a method call */
        CALL_RECEIVER,
        /** Arguments of a method call */
        CALL_ARGUMENTS,
        /** Result of a method call */
        CALL_RESULT
    }
}
