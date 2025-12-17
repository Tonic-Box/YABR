package com.tonic.parser.constpool.structure;

import com.tonic.utill.DescriptorUtil;

/**
 * Utility for parsing method descriptors in invoke operations.
 * Delegates to DescriptorUtil for core parsing logic.
 */
public class InvokeParameterUtil
{
    /**
     * Parses the method descriptor to count the number of parameters.
     *
     * @param descriptor The method descriptor string.
     * @return The number of parameters.
     */
    public static int parseDescriptorParameters(String descriptor) {
        if (descriptor == null || !descriptor.contains("(") || !descriptor.contains(")")) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
        return DescriptorUtil.countParameters(descriptor);
    }

    /**
     * Parses the method descriptor to extract the return type.
     *
     * @param descriptor The method descriptor string.
     * @return The return type as a string.
     */
    public static String parseDescriptorReturnType(String descriptor) {
        String returnType = DescriptorUtil.parseReturnDescriptor(descriptor);
        if (returnType == null) {
            throw new IllegalArgumentException("Invalid method descriptor: " + descriptor);
        }
        return returnType;
    }

    /**
     * Determines the number of slots based on the return type.
     *
     * @param returnType The return type descriptor.
     * @return The number of slots (0 for void, 1 for most types, 2 for long and double).
     */
    public static int determineTypeSlots(String returnType) {
        return DescriptorUtil.getTypeSlots(returnType);
    }
}
