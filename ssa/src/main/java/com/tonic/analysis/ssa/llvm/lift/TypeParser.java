package com.tonic.analysis.ssa.llvm.lift;

import com.tonic.analysis.ssa.type.*;

/**
 * Maps LLVM IR type strings back to {@link IRType}. Inverse of {@link com.tonic.analysis.ssa.llvm.LlvmType#render()}.
 */
final class TypeParser {

    private TypeParser() {
    }

    static IRType parse(String llvmType) {
        switch (llvmType.trim()) {
            case "i32":    return PrimitiveType.INT;
            case "i64":    return PrimitiveType.LONG;
            case "float":  return PrimitiveType.FLOAT;
            case "double": return PrimitiveType.DOUBLE;
            case "void":   return VoidType.INSTANCE;
            case "ptr":    return ReferenceType.OBJECT;
            case "i1":     return PrimitiveType.BOOLEAN;
            case "i8":     return PrimitiveType.BYTE;
            case "i16":    return PrimitiveType.SHORT;
            default:
                throw new LlvmLiftException("unknown LLVM type: " + llvmType);
        }
    }
}
