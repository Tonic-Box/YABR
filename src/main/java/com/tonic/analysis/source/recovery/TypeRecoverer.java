package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.Constant;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Recovers source types from IR types.
 */
public class TypeRecoverer {

    /**
     * Recovers a source type from an SSA value.
     */
    public SourceType recoverType(SSAValue value) {
        if (value == null || value.getType() == null) {
            return VoidSourceType.INSTANCE;
        }
        return SourceType.fromIRType(value.getType());
    }

    /**
     * Recovers a source type from any Value (SSAValue or Constant).
     */
    public SourceType recoverType(Value value) {
        if (value == null) {
            return VoidSourceType.INSTANCE;
        }
        if (value instanceof SSAValue ssa) {
            return recoverType(ssa);
        }
        if (value instanceof Constant c) {
            IRType type = c.getType();
            if (type == null) {
                return VoidSourceType.INSTANCE;
            }
            return SourceType.fromIRType(type);
        }
        return VoidSourceType.INSTANCE;
    }

    /**
     * Computes a common supertype for a collection of types.
     * For incompatible reference types, returns Object.
     * For primitives, returns the widest type in the numeric promotion hierarchy.
     * For identical types, returns that type.
     *
     * @param types the collection of types to unify
     * @return the common supertype
     */
    public SourceType computeCommonType(Collection<SourceType> types) {
        if (types == null || types.isEmpty()) {
            return VoidSourceType.INSTANCE;
        }

        // Filter out void/null types and collect unique types
        Set<SourceType> uniqueTypes = new HashSet<>();
        for (SourceType type : types) {
            if (type != null && !type.isVoid()) {
                uniqueTypes.add(type);
            }
        }

        if (uniqueTypes.isEmpty()) {
            return VoidSourceType.INSTANCE;
        }

        if (uniqueTypes.size() == 1) {
            return uniqueTypes.iterator().next();
        }

        // Check if all types are the same
        SourceType first = uniqueTypes.iterator().next();
        boolean allSame = uniqueTypes.stream().allMatch(t -> t.equals(first));
        if (allSame) {
            return first;
        }

        // Check if all are primitives - use widening conversion
        boolean allPrimitives = uniqueTypes.stream().allMatch(SourceType::isPrimitive);
        if (allPrimitives) {
            return computeWidestPrimitive(uniqueTypes);
        }

        // Check if all are arrays with same dimensions
        boolean allArrays = uniqueTypes.stream().allMatch(SourceType::isArray);
        if (allArrays) {
            return computeCommonArrayType(uniqueTypes);
        }

        // Check if all are reference types - find common supertype
        boolean allReferences = uniqueTypes.stream().allMatch(SourceType::isReference);
        if (allReferences) {
            return computeCommonReferenceType(uniqueTypes);
        }

        // Mixed types (primitive + reference, etc.) - use Object
        return ReferenceSourceType.OBJECT;
    }

    /**
     * Computes the widest primitive type using Java numeric promotion rules.
     */
    private SourceType computeWidestPrimitive(Set<SourceType> types) {
        // Hierarchy: boolean < byte < short < char < int < long < float < double
        // But boolean is not compatible with numeric types
        boolean hasBoolean = types.stream()
                .anyMatch(t -> t == PrimitiveSourceType.BOOLEAN);
        boolean hasNumeric = types.stream()
                .anyMatch(t -> t != PrimitiveSourceType.BOOLEAN);

        if (hasBoolean && hasNumeric) {
            // Incompatible - fallback to Object (boxed)
            return ReferenceSourceType.OBJECT;
        }

        if (hasBoolean) {
            return PrimitiveSourceType.BOOLEAN;
        }

        // Find widest numeric type
        int maxRank = 0;
        for (SourceType type : types) {
            maxRank = Math.max(maxRank, getPrimitiveRank((PrimitiveSourceType) type));
        }
        return getPrimitiveByRank(maxRank);
    }

    private int getPrimitiveRank(PrimitiveSourceType type) {
        if (type == PrimitiveSourceType.BYTE) return 1;
        if (type == PrimitiveSourceType.SHORT) return 2;
        if (type == PrimitiveSourceType.CHAR) return 3;
        if (type == PrimitiveSourceType.INT) return 4;
        if (type == PrimitiveSourceType.LONG) return 5;
        if (type == PrimitiveSourceType.FLOAT) return 6;
        if (type == PrimitiveSourceType.DOUBLE) return 7;
        return 0; // BOOLEAN
    }

    private PrimitiveSourceType getPrimitiveByRank(int rank) {
        if (rank == 1) return PrimitiveSourceType.BYTE;
        if (rank == 2) return PrimitiveSourceType.SHORT;
        if (rank == 3) return PrimitiveSourceType.CHAR;
        if (rank == 4) return PrimitiveSourceType.INT;
        if (rank == 5) return PrimitiveSourceType.LONG;
        if (rank == 6) return PrimitiveSourceType.FLOAT;
        if (rank == 7) return PrimitiveSourceType.DOUBLE;
        return PrimitiveSourceType.INT;
    }

    /**
     * Computes common type for array types.
     * Returns Object[] if element types are incompatible.
     */
    private SourceType computeCommonArrayType(Set<SourceType> types) {
        // Extract element types and recursively compute common type
        Set<SourceType> elementTypes = new HashSet<>();
        int dimensions = -1;

        for (SourceType type : types) {
            if (type instanceof ArraySourceType arr) {
                if (dimensions == -1) {
                    dimensions = arr.getDimensions();
                } else if (dimensions != arr.getDimensions()) {
                    // Different dimensions - return Object
                    return ReferenceSourceType.OBJECT;
                }
                elementTypes.add(arr.getElementType());
            }
        }

        SourceType commonElement = computeCommonType(elementTypes);
        return new ArraySourceType(commonElement, dimensions);
    }

    /**
     * Computes common type for reference types.
     * Without full class hierarchy, returns Object for incompatible types.
     */
    private SourceType computeCommonReferenceType(Set<SourceType> types) {
        // Check if all reference types are the same class
        Set<String> classNames = new HashSet<>();
        for (SourceType type : types) {
            if (type instanceof ReferenceSourceType ref) {
                classNames.add(ref.getInternalName());
            }
        }

        if (classNames.size() == 1) {
            // All same class
            return types.iterator().next();
        }

        // Different classes - without class hierarchy info, fall back to Object
        // In a more sophisticated implementation, we'd compute LCA in class hierarchy
        return ReferenceSourceType.OBJECT;
    }

    /**
     * Recovers a source type from an IR type.
     */
    public SourceType recoverType(IRType irType) {
        return SourceType.fromIRType(irType);
    }

    /**
     * Recovers a source type from a JVM type descriptor.
     */
    public SourceType recoverType(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return VoidSourceType.INSTANCE;
        }

        return switch (descriptor.charAt(0)) {
            case 'V' -> VoidSourceType.INSTANCE;
            case 'Z' -> PrimitiveSourceType.BOOLEAN;
            case 'B' -> PrimitiveSourceType.BYTE;
            case 'C' -> PrimitiveSourceType.CHAR;
            case 'S' -> PrimitiveSourceType.SHORT;
            case 'I' -> PrimitiveSourceType.INT;
            case 'J' -> PrimitiveSourceType.LONG;
            case 'F' -> PrimitiveSourceType.FLOAT;
            case 'D' -> PrimitiveSourceType.DOUBLE;
            case 'L' -> {
                // Reference type: Ljava/lang/String;
                int end = descriptor.indexOf(';');
                String internalName = end > 0 ? descriptor.substring(1, end) : descriptor.substring(1);
                yield new ReferenceSourceType(internalName, java.util.Collections.emptyList());
            }
            case '[' -> {
                // Array type
                SourceType component = recoverType(descriptor.substring(1));
                yield new ArraySourceType(component);
            }
            default -> VoidSourceType.INSTANCE;
        };
    }
}
