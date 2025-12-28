package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class MathHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerMathHandlers(registry);
        registerFloatHandlers(registry);
        registerDoubleHandlers(registry);
    }

    private void registerMathHandlers(NativeRegistry registry) {
        registry.register("java/lang/Math", "abs", "(I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(Math.abs(args[0].asInt())));

        registry.register("java/lang/Math", "abs", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Math.abs(args[0].asLong())));

        registry.register("java/lang/Math", "abs", "(F)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(Math.abs(args[0].asFloat())));

        registry.register("java/lang/Math", "abs", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.abs(args[0].asDouble())));

        registry.register("java/lang/Math", "max", "(II)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(
                Math.max(args[0].asInt(), args[1].asInt())));

        registry.register("java/lang/Math", "max", "(JJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(
                Math.max(args[0].asLong(), args[2].asLong())));

        registry.register("java/lang/Math", "max", "(FF)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(
                Math.max(args[0].asFloat(), args[1].asFloat())));

        registry.register("java/lang/Math", "max", "(DD)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.max(args[0].asDouble(), args[2].asDouble())));

        registry.register("java/lang/Math", "min", "(II)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(
                Math.min(args[0].asInt(), args[1].asInt())));

        registry.register("java/lang/Math", "min", "(JJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(
                Math.min(args[0].asLong(), args[2].asLong())));

        registry.register("java/lang/Math", "min", "(FF)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(
                Math.min(args[0].asFloat(), args[1].asFloat())));

        registry.register("java/lang/Math", "min", "(DD)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Math.min(args[0].asDouble(), args[2].asDouble())));

        registry.register("java/lang/Math", "sqrt", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.sqrt(args[0].asDouble())));

        registry.register("java/lang/Math", "sin", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.sin(args[0].asDouble())));

        registry.register("java/lang/Math", "cos", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.cos(args[0].asDouble())));

        registry.register("java/lang/Math", "tan", "(D)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Math.tan(args[0].asDouble())));
    }

    private void registerFloatHandlers(NativeRegistry registry) {
        registry.register("java/lang/Float", "floatToRawIntBits", "(F)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(
                Float.floatToRawIntBits(args[0].asFloat())));

        registry.register("java/lang/Float", "intBitsToFloat", "(I)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(
                Float.intBitsToFloat(args[0].asInt())));
    }

    private void registerDoubleHandlers(NativeRegistry registry) {
        registry.register("java/lang/Double", "doubleToRawLongBits", "(D)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(
                Double.doubleToRawLongBits(args[0].asDouble())));

        registry.register("java/lang/Double", "longBitsToDouble", "(J)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(
                Double.longBitsToDouble(args[0].asLong())));
    }
}
