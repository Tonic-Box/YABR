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
public final class EnumConstants {

    private static final int ACC_ENUM = 0x4000;

    private EnumConstants() {
    }

    /** The constant declared at {@code ordinal} in {@code enumInternalName}, or null when unresolvable. */
    public static String nameByOrdinal(ClassPool pool, String enumInternalName, int ordinal) {
        List<String> constants = constantsOf(pool, enumInternalName);
        if (constants == null || ordinal < 0 || ordinal >= constants.size()) {
            return null;
        }
        return constants.get(ordinal);
    }

    /** The ordinal of {@code constantName} in {@code enumInternalName}, or null when unresolvable. */
    public static Integer ordinalByName(ClassPool pool, String enumInternalName, String constantName) {
        List<String> constants = constantsOf(pool, enumInternalName);
        if (constants == null) {
            return null;
        }
        int index = constants.indexOf(constantName);
        return index >= 0 ? index : null;
    }

    private static List<String> constantsOf(ClassPool pool, String enumInternalName) {
        if (enumInternalName == null) {
            return null;
        }
        String internal = enumInternalName.replace('.', '/');
        ClassFile cf = pool != null ? pool.get(internal) : null;
        if (cf != null) {
            List<String> constants = new ArrayList<>();
            for (FieldEntry field : cf.getFields()) {
                if ((field.getAccess() & ACC_ENUM) != 0) {
                    constants.add(field.getName());
                }
            }
            if (!constants.isEmpty()) {
                return constants;
            }
        }
        try {
            Class<?> clazz = Class.forName(internal.replace('/', '.'), false, EnumConstants.class.getClassLoader());
            Object[] values = clazz.getEnumConstants();
            if (values == null) {
                return null;
            }
            List<String> constants = new ArrayList<>();
            for (Object value : values) {
                constants.add(((Enum<?>) value).name());
            }
            return constants;
        } catch (Throwable t) {
            return null;
        }
    }
}
