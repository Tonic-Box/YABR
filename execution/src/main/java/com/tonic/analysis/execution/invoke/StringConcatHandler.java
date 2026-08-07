package com.tonic.analysis.execution.invoke;

import com.tonic.analysis.execution.dispatch.InvokeDynamicInfo;
import com.tonic.analysis.execution.state.ConcreteValue;

/**
 * Interpreter-side implementation of StringConcatFactory call sites, expanding recipe strings into concatenated results.
 */
public final class StringConcatHandler
{

    public static final char TAG_ARG = '\u0001';
    public static final char TAG_CONST = '\u0002';

    /**
     * Builds the concatenated string, expanding the recipe's argument and constant markers in order.
     * @param stackArgs the dynamic argument values popped from the stack
     * @param recipe the concat recipe, or null or empty for plain concatenation
     * @param constants the bootstrap constant arguments, may be null
     * @return the concatenated string
     */
    public String executeConcat(ConcreteValue[] stackArgs, String recipe, Object[] constants)
    {
        if (recipe == null || recipe.isEmpty())
        {
            return buildSimpleConcat(stackArgs);
        }

        StringBuilder result = new StringBuilder();
        int dynamicIndex = 0;
        int constantIndex = 0;

        for (int i = 0; i < recipe.length(); i++)
        {
            char c = recipe.charAt(i);
            if (c == TAG_ARG)
            {
                if (dynamicIndex < stackArgs.length)
                {
                    result.append(valueToString(stackArgs[dynamicIndex++]));
                }
            }
            else if (c == TAG_CONST)
            {
                if (constants != null && constantIndex < constants.length)
                {
                    result.append(constants[constantIndex++]);
                }
            }
            else
            {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Concatenates the stack arguments in order for a recipe-less call site.
     * @param stackArgs the dynamic argument values popped from the stack
     * @return the concatenated string
     */
    public String executeConcat(ConcreteValue[] stackArgs)
    {
        return buildSimpleConcat(stackArgs);
    }

    /**
     * Checks whether the call site's bootstrap method is a StringConcatFactory entry point.
     * @param info the invokedynamic call site information, may be null
     * @return true if the bootstrap is makeConcat or makeConcatWithConstants
     */
    public boolean isStringConcat(InvokeDynamicInfo info)
    {
        if (info == null)
        {
            return false;
        }
        String name = info.getMethodName();
        return "makeConcatWithConstants".equals(name) || "makeConcat".equals(name);
    }

    /**
     * Checks whether the call site variant carries a recipe constant.
     * @param info the invokedynamic call site information
     * @return true if the bootstrap is makeConcatWithConstants
     */
    public boolean hasRecipe(InvokeDynamicInfo info)
    {
        return "makeConcatWithConstants".equals(info.getMethodName());
    }

    /**
     * Counts the parameters declared in a call site descriptor.
     * @param descriptor the call site method descriptor, may be null
     * @return the parameter count, or 0 for a malformed descriptor
     */
    public int getExpectedArgCount(String descriptor)
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
     * Renders a recipe readably, replacing tag characters with {arg} and {const} markers.
     * @param recipe the concat recipe, may be null
     * @return the readable form, or an empty string for null
     */
    public String parseRecipe(String recipe)
    {
        if (recipe == null)
        {
            return "";
        }
        StringBuilder readable = new StringBuilder();
        for (int i = 0; i < recipe.length(); i++)
        {
            char c = recipe.charAt(i);
            if (c == TAG_ARG)
            {
                readable.append("{arg}");
            }
            else if (c == TAG_CONST)
            {
                readable.append("{const}");
            }
            else
            {
                readable.append(c);
            }
        }
        return readable.toString();
    }

    /**
     * Counts the dynamic-argument markers in a recipe.
     * @param recipe the concat recipe, may be null
     * @return the number of argument slots
     */
    public int countDynamicArgs(String recipe)
    {
        if (recipe == null)
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < recipe.length(); i++)
        {
            if (recipe.charAt(i) == TAG_ARG)
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts the constant markers in a recipe.
     * @param recipe the concat recipe, may be null
     * @return the number of constant slots
     */
    public int countConstants(String recipe)
    {
        if (recipe == null)
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < recipe.length(); i++)
        {
            if (recipe.charAt(i) == TAG_CONST)
            {
                count++;
            }
        }
        return count;
    }

    private String buildSimpleConcat(ConcreteValue[] stackArgs)
    {
        StringBuilder sb = new StringBuilder();
        for (ConcreteValue arg : stackArgs)
        {
            sb.append(valueToString(arg));
        }
        return sb.toString();
    }

    private String valueToString(ConcreteValue value)
    {
        if (value == null || value.isNull())
        {
            return "null";
        }
        switch (value.getTag())
        {
            case INT:
                return String.valueOf(value.asInt());
            case LONG:
                return String.valueOf(value.asLong());
            case FLOAT:
                return String.valueOf(value.asFloat());
            case DOUBLE:
                return String.valueOf(value.asDouble());
            case REFERENCE:
                return "<object>";
            default:
                return value.toString();
        }
    }
}
