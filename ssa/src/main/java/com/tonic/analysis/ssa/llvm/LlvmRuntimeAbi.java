package com.tonic.analysis.ssa.llvm;

import java.util.Arrays;

/**
 * Single source of truth for the {@code jvm_*} runtime ABI that the {@code RUNTIME_ABI} object model
 * lowers object operations onto. Each method records the extern in the {@link DeclareCollector} (so
 * it appears once, sorted, in the module's {@code declare} block) and returns the callee symbol; the
 * caller composes the {@code call}/{@code invoke} line with its typed operands.
 *
 * <p>This is the contract a future runtime library must implement to link/run the emitted IR. The
 * ABI is intentionally layout-agnostic: references are opaque {@code ptr}, and class/field/method
 * identities are passed as interned C-string names rather than resolved offsets/vtables.
 */
final class LlvmRuntimeAbi {

    static final String PERSONALITY = "@jvm_personality";

    private LlvmRuntimeAbi() {
    }

    // ---- allocation --------------------------------------------------------

    /** {@code ptr jvm_new(ptr className)}. */
    static String newObject(DeclareCollector d) {
        return decl(d, "@jvm_new", LlvmType.PTR, LlvmType.PTR);
    }

    /** {@code ptr jvm_newarray_<kind>(i32 length)} for a primitive element kind. */
    static String newPrimitiveArray(DeclareCollector d, char kind) {
        return decl(d, "@jvm_newarray_" + kind, LlvmType.PTR, LlvmType.I32);
    }

    /** {@code ptr jvm_anewarray(ptr elementClass, i32 length)}. */
    static String newRefArray(DeclareCollector d) {
        return decl(d, "@jvm_anewarray", LlvmType.PTR, LlvmType.PTR, LlvmType.I32);
    }

    /** {@code ptr jvm_multianewarray(ptr arrayType, i32 ndims, ptr dims)}. */
    static String newMultiArray(DeclareCollector d) {
        return decl(d, "@jvm_multianewarray", LlvmType.PTR, LlvmType.PTR, LlvmType.I32, LlvmType.PTR);
    }

    /** {@code i32 jvm_arraylength(ptr array)}. */
    static String arrayLength(DeclareCollector d) {
        return decl(d, "@jvm_arraylength", LlvmType.I32, LlvmType.PTR);
    }

    // ---- array element access ---------------------------------------------

    /** {@code <elemLlvm> jvm_aload_<kind>(ptr array, i32 index)}. */
    static String arrayLoad(DeclareCollector d, char kind, LlvmType elem) {
        return decl(d, "@jvm_aload_" + kind, elem, LlvmType.PTR, LlvmType.I32);
    }

    /** {@code void jvm_astore_<kind>(ptr array, i32 index, <elemLlvm> value)}. */
    static String arrayStore(DeclareCollector d, char kind, LlvmType elem) {
        return decl(d, "@jvm_astore_" + kind, LlvmType.VOID, LlvmType.PTR, LlvmType.I32, elem);
    }

    // ---- dispatch ----------------------------------------------------------

    /** {@code ptr jvm_vtable_lookup(ptr receiver, ptr methodId)} -> resolved function pointer. */
    static String vtableLookup(DeclareCollector d) {
        return decl(d, "@jvm_vtable_lookup", LlvmType.PTR, LlvmType.PTR, LlvmType.PTR);
    }

    /** {@code ptr jvm_itable_lookup(ptr receiver, ptr methodId)} -> resolved function pointer. */
    static String itableLookup(DeclareCollector d) {
        return decl(d, "@jvm_itable_lookup", LlvmType.PTR, LlvmType.PTR, LlvmType.PTR);
    }

    // ---- casts -------------------------------------------------------------

    /** {@code ptr jvm_checkcast(ptr obj, ptr className)} (returns obj or throws). */
    static String checkCast(DeclareCollector d) {
        return decl(d, "@jvm_checkcast", LlvmType.PTR, LlvmType.PTR, LlvmType.PTR);
    }

    /** {@code i32 jvm_instanceof(ptr obj, ptr className)} -> 0/1. */
    static String instanceOf(DeclareCollector d) {
        return decl(d, "@jvm_instanceof", LlvmType.I32, LlvmType.PTR, LlvmType.PTR);
    }

    // ---- constants ---------------------------------------------------------

    /** {@code ptr jvm_intern_string(ptr utf8Bytes, i32 length)}. */
    static String internString(DeclareCollector d) {
        return decl(d, "@jvm_intern_string", LlvmType.PTR, LlvmType.PTR, LlvmType.I32);
    }

    /** {@code ptr jvm_class_object(ptr className)}. */
    static String classObject(DeclareCollector d) {
        return decl(d, "@jvm_class_object", LlvmType.PTR, LlvmType.PTR);
    }

    // ---- exceptions / monitors --------------------------------------------

    /** {@code void jvm_throw(ptr exception)}. */
    static String throwException(DeclareCollector d) {
        return decl(d, "@jvm_throw", LlvmType.VOID, LlvmType.PTR);
    }

    /** {@code i1 jvm_match_catch(ptr exception, ptr typeName)} -> handler matches. */
    static String matchCatch(DeclareCollector d) {
        return decl(d, "@jvm_match_catch", LlvmType.I1, LlvmType.PTR, LlvmType.PTR);
    }

    /** {@code ptr jvm_current_exception()} -> the in-flight exception (read at the top of a handler block). */
    static String currentException(DeclareCollector d) {
        return decl(d, "@jvm_current_exception", LlvmType.PTR);
    }

    /** {@code void jvm_monitor_enter(ptr obj)}. */
    static String monitorEnter(DeclareCollector d) {
        return decl(d, "@jvm_monitor_enter", LlvmType.VOID, LlvmType.PTR);
    }

    /** {@code void jvm_monitor_exit(ptr obj)}. */
    static String monitorExit(DeclareCollector d) {
        return decl(d, "@jvm_monitor_exit", LlvmType.VOID, LlvmType.PTR);
    }

    /** {@code i32 jvm_personality(...)} — the EH personality referenced by {@code define ... personality}. */
    static String personality(DeclareCollector d) {
        d.note(PERSONALITY, LlvmType.I32, Arrays.asList(LlvmType.I32, LlvmType.I32, LlvmType.I64,
            LlvmType.PTR, LlvmType.PTR));
        return PERSONALITY;
    }

    private static String decl(DeclareCollector d, String symbol, LlvmType ret, LlvmType... params) {
        d.note(symbol, ret, Arrays.asList(params));
        return symbol;
    }
}
