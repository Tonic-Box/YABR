package com.tonic.analysis.execution.dispatch;

import java.util.ArrayList;
import java.util.List;

/**
 * A CONSTANT_MethodType entry wrapping a method descriptor with parsing helpers.
 */
public final class MethodTypeInfo
{

    private final String descriptor;

    /**
     * Creates a method type wrapper.
     * @param descriptor the method descriptor to parse
     */
    public MethodTypeInfo(String descriptor)
    {
        this.descriptor = descriptor;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
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
     * Checks whether the method type returns void.
     * @return true if the return type is "V"
     */
    public boolean isVoidReturn()
    {
        return "V".equals(getReturnType());
    }

    /**
     * Counts the parameters declared in the descriptor.
     * @return the number of parameters
     */
    public int getParameterCount()
    {
        return getParameterTypes().length;
    }

    /**
     * Parses the parameter type descriptors out of the method descriptor.
     * @return the parameter descriptors in order, empty for a missing or malformed descriptor
     */
    public String[] getParameterTypes()
    {
        if (descriptor == null || !descriptor.startsWith("("))
        {
            return new String[0];
        }

        List<String> types = new ArrayList<>();
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')')
        {
            int start = i;
            char c = descriptor.charAt(i);

            if (c == 'L')
            {
                int end = descriptor.indexOf(';', i);
                if (end > 0)
                {
                    types.add(descriptor.substring(start, end + 1));
                    i = end + 1;
                }
                else
                {
                    break;
                }
            }
            else if (c == '[')
            {
                while (i < descriptor.length() && descriptor.charAt(i) == '[')
                {
                    i++;
                }
                if (i < descriptor.length())
                {
                    if (descriptor.charAt(i) == 'L')
                    {
                        int end = descriptor.indexOf(';', i);
                        if (end > 0)
                        {
                            types.add(descriptor.substring(start, end + 1));
                            i = end + 1;
                        }
                        else
                        {
                            break;
                        }
                    }
                    else
                    {
                        types.add(descriptor.substring(start, i + 1));
                        i++;
                    }
                }
            }
            else
            {
                types.add(String.valueOf(c));
                i++;
            }
        }

        return types.toArray(new String[0]);
    }

    /**
     * Counts the local-variable slots consumed by the parameters.
     * @return the total slot count, with long and double counting as two
     */
    public int getParameterSlots()
    {
        if (descriptor == null || !descriptor.startsWith("("))
        {
            return 0;
        }

        int slots = 0;
        int i = 1;
        while (i < descriptor.length() && descriptor.charAt(i) != ')')
        {
            char c = descriptor.charAt(i);
            switch (c)
            {
                case 'J':
                case 'D':
                    slots += 2;
                    i++;
                    break;
                case 'L':
                    slots++;
                    while (i < descriptor.length() && descriptor.charAt(i) != ';')
                    {
                        i++;
                    }
                    i++;
                    break;
                case '[':
                    slots++;
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
                    slots++;
                    i++;
                    break;
            }
        }
        return slots;
    }

    @Override
    public String toString()
    {
        return "MethodTypeInfo{" +
            "desc='" + descriptor + '\'' +
            ", params=" + getParameterCount() +
            ", slots=" + getParameterSlots() +
            ", return='" + getReturnType() + '\'' +
            '}';
    }
}
