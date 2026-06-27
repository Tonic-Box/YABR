package com.tonic.analysis.source.ast.type;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods for type operations including boxing, unboxing,
 * type checking, and common supertype computation.
 */
public final class TypeUtils {

    private static final Map<PrimitiveSourceType, ReferenceSourceType> BOXING_MAP = new HashMap<>();
    private static final Map<String, PrimitiveSourceType> UNBOXING_MAP = new HashMap<>();

    static {
        BOXING_MAP.put(PrimitiveSourceType.BOOLEAN, new ReferenceSourceType("java/lang/Boolean"));
        BOXING_MAP.put(PrimitiveSourceType.BYTE, new ReferenceSourceType("java/lang/Byte"));
        BOXING_MAP.put(PrimitiveSourceType.CHAR, new ReferenceSourceType("java/lang/Character"));
        BOXING_MAP.put(PrimitiveSourceType.SHORT, new ReferenceSourceType("java/lang/Short"));
        BOXING_MAP.put(PrimitiveSourceType.INT, new ReferenceSourceType("java/lang/Integer"));
        BOXING_MAP.put(PrimitiveSourceType.LONG, new ReferenceSourceType("java/lang/Long"));
        BOXING_MAP.put(PrimitiveSourceType.FLOAT, new ReferenceSourceType("java/lang/Float"));
        BOXING_MAP.put(PrimitiveSourceType.DOUBLE, new ReferenceSourceType("java/lang/Double"));

        UNBOXING_MAP.put("java/lang/Boolean", PrimitiveSourceType.BOOLEAN);
        UNBOXING_MAP.put("java/lang/Byte", PrimitiveSourceType.BYTE);
        UNBOXING_MAP.put("java/lang/Character", PrimitiveSourceType.CHAR);
        UNBOXING_MAP.put("java/lang/Short", PrimitiveSourceType.SHORT);
        UNBOXING_MAP.put("java/lang/Integer", PrimitiveSourceType.INT);
        UNBOXING_MAP.put("java/lang/Long", PrimitiveSourceType.LONG);
        UNBOXING_MAP.put("java/lang/Float", PrimitiveSourceType.FLOAT);
        UNBOXING_MAP.put("java/lang/Double", PrimitiveSourceType.DOUBLE);
    }

    private TypeUtils() {}

    public static boolean isNumeric(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType prim = (PrimitiveSourceType) type;
            return prim == PrimitiveSourceType.BYTE ||
                   prim == PrimitiveSourceType.SHORT ||
                   prim == PrimitiveSourceType.INT ||
                   prim == PrimitiveSourceType.LONG ||
                   prim == PrimitiveSourceType.FLOAT ||
                   prim == PrimitiveSourceType.DOUBLE ||
                   prim == PrimitiveSourceType.CHAR;
        }
        return false;
    }

    public static boolean isIntegral(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType prim = (PrimitiveSourceType) type;
            return prim == PrimitiveSourceType.BYTE ||
                   prim == PrimitiveSourceType.SHORT ||
                   prim == PrimitiveSourceType.INT ||
                   prim == PrimitiveSourceType.LONG ||
                   prim == PrimitiveSourceType.CHAR;
        }
        return false;
    }

    public static boolean isFloatingPoint(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            PrimitiveSourceType prim = (PrimitiveSourceType) type;
            return prim == PrimitiveSourceType.FLOAT ||
                   prim == PrimitiveSourceType.DOUBLE;
        }
        return false;
    }

    public static boolean isPrimitive(SourceType type) {
        return type instanceof PrimitiveSourceType;
    }

    public static boolean isReference(SourceType type) {
        return type instanceof ReferenceSourceType ||
               type instanceof ArraySourceType ||
               type instanceof GenericSourceType;
    }

    public static boolean isArray(SourceType type) {
        return type instanceof ArraySourceType;
    }

    public static boolean isVoid(SourceType type) {
        return type instanceof VoidSourceType;
    }

    public static boolean isBoolean(SourceType type) {
        return type instanceof PrimitiveSourceType &&
               ((PrimitiveSourceType) type) == PrimitiveSourceType.BOOLEAN;
    }

    public static boolean isBoxedType(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            return UNBOXING_MAP.containsKey(((ReferenceSourceType) type).getInternalName());
        }
        return false;
    }

    public static SourceType box(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            ReferenceSourceType boxed = BOXING_MAP.get(type);
            if (boxed != null) {
                return boxed;
            }
        }
        return type;
    }

    public static SourceType unbox(SourceType type) {
        if (type instanceof ReferenceSourceType) {
            PrimitiveSourceType unboxed = UNBOXING_MAP.get(((ReferenceSourceType) type).getInternalName());
            if (unboxed != null) {
                return unboxed;
            }
        }
        return type;
    }

    public static SourceType getElementType(SourceType type) {
        if (type instanceof ArraySourceType) {
            return ((ArraySourceType) type).getElementType();
        }
        return null;
    }

    public static SourceType getRawType(SourceType type) {
        if (type instanceof GenericSourceType) {
            return ((GenericSourceType) type).getRawType();
        }
        return type;
    }

    public static SourceType commonSupertype(SourceType a, SourceType b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.equals(b)) return a;

        if (a instanceof PrimitiveSourceType && b instanceof PrimitiveSourceType) {
            return numericPromotion((PrimitiveSourceType) a, (PrimitiveSourceType) b);
        }

        if (isReference(a) && isReference(b)) {
            return ReferenceSourceType.OBJECT;
        }

        return ReferenceSourceType.OBJECT;
    }

    private static SourceType numericPromotion(PrimitiveSourceType a, PrimitiveSourceType b) {
        if (a == PrimitiveSourceType.DOUBLE || b == PrimitiveSourceType.DOUBLE) {
            return PrimitiveSourceType.DOUBLE;
        }
        if (a == PrimitiveSourceType.FLOAT || b == PrimitiveSourceType.FLOAT) {
            return PrimitiveSourceType.FLOAT;
        }
        if (a == PrimitiveSourceType.LONG || b == PrimitiveSourceType.LONG) {
            return PrimitiveSourceType.LONG;
        }
        return PrimitiveSourceType.INT;
    }

    public static boolean isAssignableTo(SourceType from, SourceType to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;

        if (from instanceof PrimitiveSourceType && to instanceof PrimitiveSourceType) {
            return isPrimitiveWidening((PrimitiveSourceType) from, (PrimitiveSourceType) to);
        }

        if (isReference(from) && to instanceof ReferenceSourceType) {
            ReferenceSourceType refTo = (ReferenceSourceType) to;
            if ("java/lang/Object".equals(refTo.getInternalName())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isPrimitiveWidening(PrimitiveSourceType from, PrimitiveSourceType to) {
        int fromRank = getNumericRank(from);
        int toRank = getNumericRank(to);
        if (fromRank < 0 || toRank < 0) return false;
        return fromRank <= toRank;
    }

    private static int getNumericRank(PrimitiveSourceType type) {
        switch (type.getKind()) {
            case BYTE: return 1;
            case SHORT: return 2;
            case CHAR: return 2;
            case INT: return 3;
            case LONG: return 4;
            case FLOAT: return 5;
            case DOUBLE: return 6;
            default: return -1;
        }
    }

    public static boolean isGeneric(SourceType type) {
        return type instanceof GenericSourceType;
    }

    public static boolean isWildcard(SourceType type) {
        return type instanceof WildcardSourceType;
    }

    public static boolean isIntersection(SourceType type) {
        return type instanceof IntersectionSourceType;
    }

    public static boolean isUnion(SourceType type) {
        return type instanceof UnionSourceType;
    }

    public static String getSimpleName(SourceType type) {
        if (type instanceof PrimitiveSourceType) {
            return type.toJavaSource();
        }
        if (type instanceof ReferenceSourceType) {
            return ((ReferenceSourceType) type).getSimpleName();
        }
        if (type instanceof ArraySourceType) {
            return getSimpleName(((ArraySourceType) type).getElementType()) + "[]";
        }
        if (type instanceof GenericSourceType) {
            return getSimpleName(((GenericSourceType) type).getRawType());
        }
        return type.toJavaSource();
    }
}
