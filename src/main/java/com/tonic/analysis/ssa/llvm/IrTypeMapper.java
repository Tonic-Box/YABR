package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.VoidType;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps YABR {@link IRType}s to {@link LlvmType}s for the computational subset.
 *
 * <p>All integral JVM types (boolean/byte/char/short/int) collapse to {@code i32}: the JVM computes
 * them as int on the operand stack, and v1 has no heap/field storage where the narrow widths would
 * matter. Narrow integer widths (i8/i16) are deferred to the future reference/heap layer. Reference
 * and array types route to {@link UnsupportedLowering}.
 */
final class IrTypeMapper {

    private IrTypeMapper() {
    }

    static LlvmType map(IRType type) {
        if (type instanceof VoidType) {
            return LlvmType.VOID;
        }
        if (type instanceof PrimitiveType) {
            PrimitiveType prim = (PrimitiveType) type;
            if (prim == PrimitiveType.LONG) {
                return LlvmType.I64;
            }
            if (prim == PrimitiveType.FLOAT) {
                return LlvmType.FLOAT;
            }
            if (prim == PrimitiveType.DOUBLE) {
                return LlvmType.DOUBLE;
            }
            return LlvmType.I32;
        }
        throw UnsupportedLowering.reject("type " + (type == null ? "null" : type.getDescriptor()));
    }

    /** Parameter LLVM types parsed from a JVM method descriptor, e.g. {@code (IJDF)V}. */
    static List<LlvmType> mapParams(String descriptor) {
        List<LlvmType> params = new ArrayList<>();
        int i = descriptor.indexOf('(') + 1;
        int end = descriptor.indexOf(')');
        while (i < end) {
            char c = descriptor.charAt(i);
            switch (c) {
                case 'J':
                    params.add(LlvmType.I64);
                    i++;
                    break;
                case 'F':
                    params.add(LlvmType.FLOAT);
                    i++;
                    break;
                case 'D':
                    params.add(LlvmType.DOUBLE);
                    i++;
                    break;
                case 'Z':
                case 'B':
                case 'C':
                case 'S':
                case 'I':
                    params.add(LlvmType.I32);
                    i++;
                    break;
                case 'L':
                    throw UnsupportedLowering.reject("reference parameter");
                case '[':
                    throw UnsupportedLowering.reject("array parameter");
                default:
                    throw UnsupportedLowering.reject("descriptor char '" + c + "'");
            }
        }
        return params;
    }

    /** Return LLVM type parsed from a JVM method descriptor. */
    static LlvmType mapReturn(String descriptor) {
        String ret = descriptor.substring(descriptor.indexOf(')') + 1);
        switch (ret) {
            case "V":
                return LlvmType.VOID;
            case "J":
                return LlvmType.I64;
            case "F":
                return LlvmType.FLOAT;
            case "D":
                return LlvmType.DOUBLE;
            case "Z":
            case "B":
            case "C":
            case "S":
            case "I":
                return LlvmType.I32;
            default:
                throw UnsupportedLowering.reject("return type " + ret);
        }
    }
}
