package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.value.SSAValue;

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
