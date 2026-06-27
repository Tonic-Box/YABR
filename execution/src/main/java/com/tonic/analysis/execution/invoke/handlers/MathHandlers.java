package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

import static com.tonic.analysis.execution.invoke.handlers.Numerics.binaryDouble;
import static com.tonic.analysis.execution.invoke.handlers.Numerics.binaryInt;
import static com.tonic.analysis.execution.invoke.handlers.Numerics.binaryLong;
import static com.tonic.analysis.execution.invoke.handlers.Numerics.unaryDouble;
import static com.tonic.analysis.execution.invoke.handlers.Numerics.unaryInt;
import static com.tonic.analysis.execution.invoke.handlers.Numerics.unaryLong;

public final class MathHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerMathHandlers(registry);
        registerStrictMathHandlers(registry);
        registerFloatHandlers(registry);
        registerDoubleHandlers(registry);
    }

    private void registerMathHandlers(NativeRegistry registry) {
        unaryInt(registry, "java/lang/Math", "abs", "(I)I", Math::abs);
        unaryLong(registry, "java/lang/Math", "abs", "(J)J", Math::abs);
        registry.register("java/lang/Math", "abs", "(F)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(Math.abs(args[0].asFloat())));
        unaryDouble(registry, "java/lang/Math", "abs", "(D)D", Math::abs);

        binaryInt(registry, "java/lang/Math", "max", "(II)I", Math::max);
        binaryLong(registry, "java/lang/Math", "max", "(JJ)J", Math::max);
        registry.register("java/lang/Math", "max", "(FF)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(Math.max(args[0].asFloat(), args[1].asFloat())));
        binaryDouble(registry, "java/lang/Math", "max", "(DD)D", Math::max);

        binaryInt(registry, "java/lang/Math", "min", "(II)I", Math::min);
        binaryLong(registry, "java/lang/Math", "min", "(JJ)J", Math::min);
        registry.register("java/lang/Math", "min", "(FF)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(Math.min(args[0].asFloat(), args[1].asFloat())));
        binaryDouble(registry, "java/lang/Math", "min", "(DD)D", Math::min);

        unaryDouble(registry, "java/lang/Math", "sqrt", "(D)D", Math::sqrt);
        unaryDouble(registry, "java/lang/Math", "sin", "(D)D", Math::sin);
        unaryDouble(registry, "java/lang/Math", "cos", "(D)D", Math::cos);
        unaryDouble(registry, "java/lang/Math", "tan", "(D)D", Math::tan);
        unaryDouble(registry, "java/lang/Math", "log", "(D)D", Math::log);
        unaryDouble(registry, "java/lang/Math", "log10", "(D)D", Math::log10);
        unaryDouble(registry, "java/lang/Math", "exp", "(D)D", Math::exp);
        binaryDouble(registry, "java/lang/Math", "pow", "(DD)D", Math::pow);
        unaryDouble(registry, "java/lang/Math", "floor", "(D)D", Math::floor);
        unaryDouble(registry, "java/lang/Math", "ceil", "(D)D", Math::ceil);

        registry.register("java/lang/Math", "round", "(D)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Math.round(args[0].asDouble())));
        registry.register("java/lang/Math", "round", "(F)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(Math.round(args[0].asFloat())));
    }

    private void registerStrictMathHandlers(NativeRegistry registry) {
        unaryDouble(registry, "java/lang/StrictMath", "sin", "(D)D", StrictMath::sin);
        unaryDouble(registry, "java/lang/StrictMath", "cos", "(D)D", StrictMath::cos);
        unaryDouble(registry, "java/lang/StrictMath", "tan", "(D)D", StrictMath::tan);
        unaryDouble(registry, "java/lang/StrictMath", "asin", "(D)D", StrictMath::asin);
        unaryDouble(registry, "java/lang/StrictMath", "acos", "(D)D", StrictMath::acos);
        unaryDouble(registry, "java/lang/StrictMath", "atan", "(D)D", StrictMath::atan);
        binaryDouble(registry, "java/lang/StrictMath", "atan2", "(DD)D", StrictMath::atan2);
        unaryDouble(registry, "java/lang/StrictMath", "sinh", "(D)D", StrictMath::sinh);
        unaryDouble(registry, "java/lang/StrictMath", "cosh", "(D)D", StrictMath::cosh);
        unaryDouble(registry, "java/lang/StrictMath", "tanh", "(D)D", StrictMath::tanh);
        unaryDouble(registry, "java/lang/StrictMath", "log", "(D)D", StrictMath::log);
        unaryDouble(registry, "java/lang/StrictMath", "log10", "(D)D", StrictMath::log10);
        unaryDouble(registry, "java/lang/StrictMath", "log1p", "(D)D", StrictMath::log1p);
        unaryDouble(registry, "java/lang/StrictMath", "expm1", "(D)D", StrictMath::expm1);
        binaryDouble(registry, "java/lang/StrictMath", "IEEEremainder", "(DD)D", StrictMath::IEEEremainder);
    }

    private void registerFloatHandlers(NativeRegistry registry) {
        registry.register("java/lang/Float", "floatToRawIntBits", "(F)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(Float.floatToRawIntBits(args[0].asFloat())));
        registry.register("java/lang/Float", "intBitsToFloat", "(I)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(Float.intBitsToFloat(args[0].asInt())));
    }

    private void registerDoubleHandlers(NativeRegistry registry) {
        registry.register("java/lang/Double", "doubleToRawLongBits", "(D)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(Double.doubleToRawLongBits(args[0].asDouble())));
        registry.register("java/lang/Double", "longBitsToDouble", "(J)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(Double.longBitsToDouble(args[0].asLong())));
    }
}
