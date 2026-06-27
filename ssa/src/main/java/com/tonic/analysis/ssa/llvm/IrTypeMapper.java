package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.VoidType;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps YABR {@link IRType}s to {@link LlvmType}s.
 *
 * <p>All integral JVM types (boolean/byte/char/short/int) collapse to {@code i32}: the JVM computes
 * them as int on the operand stack. {@code long}→{@code i64}, {@code float}/{@code double} direct.
 * Reference and array types map to the opaque pointer {@code ptr} — the lowering is type-lattice
 * agnostic; whether object operations are actually emitted (vs rejected) is gated by the
 * {@link LlvmLoweringConfig.ObjectModel} in {@code SsaToLlvmLowerer}.
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
        if (type != null && type.isReference()) {
            return LlvmType.PTR;
        }
        throw UnsupportedLowering.reject("type " + (type == null ? "null" : type.getDescriptor()));
    }

    /** Maps a single JVM type descriptor (field type / array element), e.g. {@code I}, {@code Ljava/lang/String;}, {@code [I}. */
    static LlvmType mapDescriptor(String descriptor) {
        switch (descriptor.charAt(0)) {
            case 'V':
                return LlvmType.VOID;
            case 'J':
                return LlvmType.I64;
            case 'F':
                return LlvmType.FLOAT;
            case 'D':
                return LlvmType.DOUBLE;
            case 'Z':
            case 'B':
            case 'C':
            case 'S':
            case 'I':
                return LlvmType.I32;
            case 'L':
            case '[':
                return LlvmType.PTR;
            default:
                throw UnsupportedLowering.reject("descriptor '" + descriptor + "'");
        }
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
                    params.add(LlvmType.PTR);
                    i = descriptor.indexOf(';', i) + 1;
                    break;
                case '[':
                    params.add(LlvmType.PTR);
                    i++;
                    while (i < end && descriptor.charAt(i) == '[') {
                        i++;
                    }
                    if (i < end && descriptor.charAt(i) == 'L') {
                        i = descriptor.indexOf(';', i) + 1;
                    } else {
                        i++;
                    }
                    break;
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
                if (ret.startsWith("L") || ret.startsWith("[")) {
                    return LlvmType.PTR;
                }
                throw UnsupportedLowering.reject("return type " + ret);
        }
    }
}
