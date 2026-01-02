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
        return recoverTypeWithInstructionContext(value);
    }

    /**
     * Recovers a source type from any Value (SSAValue or Constant).
     */
    public SourceType recoverType(Value value) {
        if (value == null) {
            return VoidSourceType.INSTANCE;
        }
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            return recoverTypeWithInstructionContext(ssa);
        }
        if (value instanceof Constant) {
            Constant c = (Constant) value;
            IRType type = c.getType();
            if (type == null) {
                return VoidSourceType.INSTANCE;
            }
            return SourceType.fromIRType(type);
        }
        return VoidSourceType.INSTANCE;
    }

    /**
     * Recovers a source type from an SSAValue, considering its defining instruction.
     * This handles cases where the IR type is INT but the actual semantic type is BOOLEAN.
     */
    public SourceType recoverTypeWithInstructionContext(SSAValue ssa) {
        if (ssa == null || ssa.getType() == null) {
            return VoidSourceType.INSTANCE;
        }

        com.tonic.analysis.ssa.ir.IRInstruction def = ssa.getDefinition();

        if (def instanceof com.tonic.analysis.ssa.ir.TypeCheckInstruction) {
            com.tonic.analysis.ssa.ir.TypeCheckInstruction typeCheck =
                (com.tonic.analysis.ssa.ir.TypeCheckInstruction) def;
            if (typeCheck.isInstanceOf()) {
                return PrimitiveSourceType.BOOLEAN;
            }
        }

        if (def instanceof com.tonic.analysis.ssa.ir.BinaryOpInstruction) {
            com.tonic.analysis.ssa.ir.BinaryOpInstruction binOp = (com.tonic.analysis.ssa.ir.BinaryOpInstruction) def;
            com.tonic.analysis.ssa.ir.BinaryOp op = binOp.getOp();
            if (op == com.tonic.analysis.ssa.ir.BinaryOp.AND ||
                op == com.tonic.analysis.ssa.ir.BinaryOp.OR ||
                op == com.tonic.analysis.ssa.ir.BinaryOp.XOR) {
                SourceType leftType = recoverTypeWithInstructionContext(binOp.getLeft());
                SourceType rightType = recoverTypeWithInstructionContext(binOp.getRight());
                if (leftType == PrimitiveSourceType.BOOLEAN || rightType == PrimitiveSourceType.BOOLEAN) {
                    return PrimitiveSourceType.BOOLEAN;
                }
            }
        }

        return SourceType.fromIRType(ssa.getType());
    }

    /**
     * Recovers type from a Value operand of a binary operation.
     */
    private SourceType recoverTypeWithInstructionContext(com.tonic.analysis.ssa.value.Value value) {
        if (value instanceof SSAValue) {
            SSAValue ssa = (SSAValue) value;
            return recoverTypeWithInstructionContext(ssa);
        }
        if (value instanceof Constant) {
            Constant c = (Constant) value;
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

        SourceType first = uniqueTypes.iterator().next();
        boolean allSame = uniqueTypes.stream().allMatch(t -> t.equals(first));
        if (allSame) {
            return first;
        }

        boolean allPrimitives = uniqueTypes.stream().allMatch(SourceType::isPrimitive);
        if (allPrimitives) {
            return computeWidestPrimitive(uniqueTypes);
        }

        boolean allArrays = uniqueTypes.stream().allMatch(SourceType::isArray);
        if (allArrays) {
            return computeCommonArrayType(uniqueTypes);
        }

        boolean allReferences = uniqueTypes.stream().allMatch(SourceType::isReference);
        if (allReferences) {
            return computeCommonReferenceType(uniqueTypes);
        }

        return ReferenceSourceType.OBJECT;
    }

    /**
     * Computes the widest primitive type using Java numeric promotion rules.
     */
    private SourceType computeWidestPrimitive(Set<SourceType> types) {
        boolean hasBoolean = types.stream()
                .anyMatch(t -> t == PrimitiveSourceType.BOOLEAN);
        boolean hasNumeric = types.stream()
                .anyMatch(t -> t != PrimitiveSourceType.BOOLEAN);

        if (hasBoolean && hasNumeric) {
            return ReferenceSourceType.OBJECT;
        }

        if (hasBoolean) {
            return PrimitiveSourceType.BOOLEAN;
        }

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
        return 0;
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
        Set<SourceType> elementTypes = new HashSet<>();
        int dimensions = -1;

        for (SourceType type : types) {
            if (type instanceof ArraySourceType) {
                ArraySourceType arr = (ArraySourceType) type;
                if (dimensions == -1) {
                    dimensions = arr.getDimensions();
                } else if (dimensions != arr.getDimensions()) {
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
        Set<String> classNames = new HashSet<>();
        for (SourceType type : types) {
            if (type instanceof ReferenceSourceType) {
                ReferenceSourceType ref = (ReferenceSourceType) type;
                classNames.add(ref.getInternalName());
            }
        }

        if (classNames.size() == 1) {
            return types.iterator().next();
        }

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

        switch (descriptor.charAt(0)) {
            case 'V':
                return VoidSourceType.INSTANCE;
            case 'Z':
                return PrimitiveSourceType.BOOLEAN;
            case 'B':
                return PrimitiveSourceType.BYTE;
            case 'C':
                return PrimitiveSourceType.CHAR;
            case 'S':
                return PrimitiveSourceType.SHORT;
            case 'I':
                return PrimitiveSourceType.INT;
            case 'J':
                return PrimitiveSourceType.LONG;
            case 'F':
                return PrimitiveSourceType.FLOAT;
            case 'D':
                return PrimitiveSourceType.DOUBLE;
            case 'L': {
                int end = descriptor.indexOf(';');
                String internalName = end > 0 ? descriptor.substring(1, end) : descriptor.substring(1);
                return new ReferenceSourceType(internalName, java.util.Collections.emptyList());
            }
            case '[': {
                SourceType component = recoverType(descriptor.substring(1));
                return new ArraySourceType(component);
            }
            default:
                return VoidSourceType.INSTANCE;
        }
    }
}
