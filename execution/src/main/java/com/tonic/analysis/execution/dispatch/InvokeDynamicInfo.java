package com.tonic.analysis.execution.dispatch;

import com.tonic.util.DescriptorUtil;

/**
 * An invokedynamic call site: bootstrap method index plus the invoked name and descriptor.
 */
public final class InvokeDynamicInfo
{

    private final int bootstrapMethodIndex;
    private final String methodName;
    private final String descriptor;
    private final int constantPoolIndex;

    /**
     * Creates an invokedynamic call site descriptor.
     * @param bootstrapMethodIndex index into the BootstrapMethods attribute
     * @param methodName the invoked name of the call site
     * @param descriptor the call site's method descriptor
     * @param constantPoolIndex index of the entry in the constant pool
     */
    public InvokeDynamicInfo(int bootstrapMethodIndex, String methodName, String descriptor, int constantPoolIndex)
    {
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.constantPoolIndex = constantPoolIndex;
    }

    /**
     * @return the bootstrap method index
     */
    public int getBootstrapMethodIndex()
    {
        return bootstrapMethodIndex;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return the constant pool index
     */
    public int getConstantPoolIndex()
    {
        return constantPoolIndex;
    }

    /**
     * Counts the local-variable slots consumed by the parameters.
     * @return the total slot count, with long and double counting as two
     */
    public int getParameterSlots()
    {
        return DescriptorUtil.countParameterSlots(descriptor);
    }

    /**
     * Counts the parameters declared in the descriptor.
     * @return the number of parameters, or 0 for a missing or malformed descriptor
     */
    public int getParameterCount()
    {
        if (descriptor == null || !descriptor.startsWith("("))
        {
            return 0;
        }

        int count = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')')
        {
            char c = descriptor.charAt(i);
            switch (c)
            {
                case 'L':
                    count++;
                    while (i < descriptor.length() && descriptor.charAt(i) != ';')
                    {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    count++;
                    while (i < descriptor.length() && descriptor.charAt(i) == '[')
                    {
                        i++;
                    }
                    if (i < descriptor.length() && descriptor.charAt(i) == 'L')
                    {
                        while (i < descriptor.length() && descriptor.charAt(i) != ';')
                        {
                            i++;
                        }
                    }
                    i++;
                    break;
                default:
                    count++;
                    i++;
                    break;
            }
        }
        return count;
    }

    /**
     * Extracts the return type from the descriptor.
     * @return the return type descriptor, or "V" if it cannot be parsed
     */
    public String getReturnType()
    {
        if (descriptor == null)
        {
            return "V";
        }
        int parenIndex = descriptor.indexOf(')');
        if (parenIndex >= 0 && parenIndex < descriptor.length() - 1)
        {
            return descriptor.substring(parenIndex + 1);
        }
        return "V";
    }

    /**
     * Checks whether the call site returns void.
     * @return true if the return type is "V"
     */
    public boolean isVoidReturn()
    {
        return "V".equals(getReturnType());
    }

    /**
     * Checks whether the invoked name matches a common functional-interface method,
     * indicating a LambdaMetafactory call site.
     * @return true if the name is a known functional-interface method name
     */
    public boolean isLambdaMetafactory()
    {
        return "run".equals(methodName) || "apply".equals(methodName) ||
               "accept".equals(methodName) || "test".equals(methodName) ||
               "get".equals(methodName) || "getAsInt".equals(methodName) ||
               "getAsLong".equals(methodName) || "getAsDouble".equals(methodName);
    }

    /**
     * Checks whether this is a StringConcatFactory call site.
     * @return true if the invoked name is makeConcat or makeConcatWithConstants
     */
    public boolean isStringConcat()
    {
        return "makeConcatWithConstants".equals(methodName) || "makeConcat".equals(methodName);
    }

    @Override
    public String toString()
    {
        return "InvokeDynamicInfo{" +
            "bsm=" + bootstrapMethodIndex +
            ", name='" + methodName + '\'' +
            ", desc='" + descriptor + '\'' +
            ", cpIndex=" + constantPoolIndex +
            '}';
    }
}
