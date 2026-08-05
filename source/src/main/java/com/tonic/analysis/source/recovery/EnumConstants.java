package com.tonic.analysis.source.recovery;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves an enum's constants by ordinal and by name. The JVM defines an enum's ordinal as its position
 * among the class's enum-flagged static fields in declaration order, so the class file alone answers both
 * directions; reflection covers platform enums whose class files are not in the pool.
 */
public final class EnumConstants
{

    private static final int ACC_ENUM = 0x4000;

    private EnumConstants()
    {
    }

    /**
     * Looks up the enum constant declared at a given position.
     *
     * @param pool the class pool to resolve the enum from, may be null to force the reflection path
     * @param enumInternalName the enum class, in dotted or internal form
     * @param ordinal the declaration position
     * @return the constant name, or null if the enum could not be resolved or the ordinal is out of range
     */
    public static String nameByOrdinal(ClassPool pool, String enumInternalName, int ordinal)
    {
        List<String> constants = constantsOf(pool, enumInternalName);
        if (constants == null || ordinal < 0 || ordinal >= constants.size())
        {
            return null;
        }
        return constants.get(ordinal);
    }

    /**
     * Looks up the declaration position of a named enum constant.
     *
     * @param pool the class pool to resolve the enum from, may be null to force the reflection path
     * @param enumInternalName the enum class, in dotted or internal form
     * @param constantName the constant to find
     * @return the ordinal, or null if the enum or the constant could not be resolved
     */
    public static Integer ordinalByName(ClassPool pool, String enumInternalName, String constantName)
    {
        List<String> constants = constantsOf(pool, enumInternalName);
        if (constants == null)
        {
            return null;
        }
        int index = constants.indexOf(constantName);
        return index >= 0 ? index : null;
    }

    private static List<String> constantsOf(ClassPool pool, String enumInternalName)
    {
        if (enumInternalName == null)
        {
            return null;
        }
        String internal = enumInternalName.replace('.', '/');
        ClassFile cf = pool != null ? pool.get(internal) : null;
        if (cf != null)
        {
            List<String> constants = new ArrayList<>();
            for (FieldEntry field : cf.getFields())
            {
                if ((field.getAccess() & ACC_ENUM) != 0)
                {
                    constants.add(field.getName());
                }
            }
            if (!constants.isEmpty())
            {
                return constants;
            }
        }
        try
        {
            Class<?> clazz = Class.forName(internal.replace('/', '.'), false, EnumConstants.class.getClassLoader());
            Object[] values = clazz.getEnumConstants();
            if (values == null)
            {
                return null;
            }
            List<String> constants = new ArrayList<>();
            for (Object value : values)
            {
                constants.add(((Enum<?>) value).name());
            }
            return constants;
        }
        catch (Throwable t)
        {
            return null;
        }
    }
}
