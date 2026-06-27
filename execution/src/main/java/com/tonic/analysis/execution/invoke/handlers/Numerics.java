package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

/**
 * Typed registration helpers for native methods whose body is a pure numeric function. Each helper
 * owns argument indexing and result boxing, so a handler body cannot mis-index {@code args}.
 */
final class Numerics {

    private Numerics() {
    }

    static void unaryInt(NativeRegistry r, String owner, String name, String desc, IntUnaryOperator fn) {
        r.register(owner, name, desc, (recv, args, ctx) -> ConcreteValue.intValue(fn.applyAsInt(args[0].asInt())));
    }

    static void binaryInt(NativeRegistry r, String owner, String name, String desc, IntBinaryOperator fn) {
        r.register(owner, name, desc, (recv, args, ctx) -> ConcreteValue.intValue(fn.applyAsInt(args[0].asInt(), args[1].asInt())));
    }

    static void unaryLong(NativeRegistry r, String owner, String name, String desc, LongUnaryOperator fn) {
        r.register(owner, name, desc, (recv, args, ctx) -> ConcreteValue.longValue(fn.applyAsLong(args[0].asLong())));
    }

    static void binaryLong(NativeRegistry r, String owner, String name, String desc, LongBinaryOperator fn) {
        r.register(owner, name, desc, (recv, args, ctx) -> ConcreteValue.longValue(fn.applyAsLong(args[0].asLong(), args[1].asLong())));
    }

    static void unaryDouble(NativeRegistry r, String owner, String name, String desc, DoubleUnaryOperator fn) {
        r.register(owner, name, desc, (recv, args, ctx) -> ConcreteValue.doubleValue(fn.applyAsDouble(args[0].asDouble())));
    }

    static void binaryDouble(NativeRegistry r, String owner, String name, String desc, DoubleBinaryOperator fn) {
        r.register(owner, name, desc, (recv, args, ctx) -> ConcreteValue.doubleValue(fn.applyAsDouble(args[0].asDouble(), args[1].asDouble())));
    }
}
