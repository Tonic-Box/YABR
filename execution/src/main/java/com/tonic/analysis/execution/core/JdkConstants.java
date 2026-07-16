package com.tonic.analysis.execution.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Values of the well-known {@code public static final} primitive constants in {@code java.lang}. The
 * engine cannot run a JDK class's {@code <clinit>} (those classes are not in the pool), so a
 * {@code getstatic} of e.g. {@code Integer.MAX_VALUE} would otherwise read as the 0 default. These are
 * compile-time constants with fixed values, so the engine can supply them directly. Consulted only
 * when the heap has no value for the field, so a genuinely-modeled static still wins.
 */
final class JdkConstants {

    private static final Map<String, Object> VALUES = new HashMap<>();

    private JdkConstants() {
    }

    static {
        put("java/lang/Integer", "MAX_VALUE", Integer.MAX_VALUE);
        put("java/lang/Integer", "MIN_VALUE", Integer.MIN_VALUE);
        put("java/lang/Integer", "SIZE", Integer.SIZE);
        put("java/lang/Integer", "BYTES", Integer.BYTES);

        put("java/lang/Long", "MAX_VALUE", Long.MAX_VALUE);
        put("java/lang/Long", "MIN_VALUE", Long.MIN_VALUE);
        put("java/lang/Long", "SIZE", Long.SIZE);
        put("java/lang/Long", "BYTES", Long.BYTES);

        put("java/lang/Short", "MAX_VALUE", Short.MAX_VALUE);
        put("java/lang/Short", "MIN_VALUE", Short.MIN_VALUE);
        put("java/lang/Short", "SIZE", Short.SIZE);
        put("java/lang/Short", "BYTES", Short.BYTES);

        put("java/lang/Byte", "MAX_VALUE", Byte.MAX_VALUE);
        put("java/lang/Byte", "MIN_VALUE", Byte.MIN_VALUE);
        put("java/lang/Byte", "SIZE", Byte.SIZE);
        put("java/lang/Byte", "BYTES", Byte.BYTES);

        put("java/lang/Character", "MAX_VALUE", Character.MAX_VALUE);
        put("java/lang/Character", "MIN_VALUE", Character.MIN_VALUE);
        put("java/lang/Character", "SIZE", Character.SIZE);
        put("java/lang/Character", "BYTES", Character.BYTES);

        put("java/lang/Double", "MAX_VALUE", Double.MAX_VALUE);
        put("java/lang/Double", "MIN_VALUE", Double.MIN_VALUE);
        put("java/lang/Double", "POSITIVE_INFINITY", Double.POSITIVE_INFINITY);
        put("java/lang/Double", "NEGATIVE_INFINITY", Double.NEGATIVE_INFINITY);
        put("java/lang/Double", "NaN", Double.NaN);
        put("java/lang/Double", "MAX_EXPONENT", Double.MAX_EXPONENT);
        put("java/lang/Double", "MIN_EXPONENT", Double.MIN_EXPONENT);
        put("java/lang/Double", "SIZE", Double.SIZE);
        put("java/lang/Double", "BYTES", Double.BYTES);

        put("java/lang/Float", "MAX_VALUE", Float.MAX_VALUE);
        put("java/lang/Float", "MIN_VALUE", Float.MIN_VALUE);
        put("java/lang/Float", "POSITIVE_INFINITY", Float.POSITIVE_INFINITY);
        put("java/lang/Float", "NEGATIVE_INFINITY", Float.NEGATIVE_INFINITY);
        put("java/lang/Float", "NaN", Float.NaN);
        put("java/lang/Float", "MAX_EXPONENT", Float.MAX_EXPONENT);
        put("java/lang/Float", "MIN_EXPONENT", Float.MIN_EXPONENT);
        put("java/lang/Float", "SIZE", Float.SIZE);
        put("java/lang/Float", "BYTES", Float.BYTES);

        put("java/lang/Math", "PI", Math.PI);
        put("java/lang/Math", "E", Math.E);
        put("java/lang/StrictMath", "PI", Math.PI);
        put("java/lang/StrictMath", "E", Math.E);
    }

    private static void put(String owner, String name, Object value) {
        VALUES.put(owner + "." + name, value);
    }

    /** The constant value for {@code owner.name}, or {@code null} if not a known JDK constant. */
    static Object lookup(String owner, String name) {
        return VALUES.get(owner + "." + name);
    }
}
