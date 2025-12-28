package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class TimeHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registry.register("java/time/LocalDateTime", "now", "()Ljava/time/LocalDateTime;",
            (receiver, args, ctx) -> {
                ObjectInstance ldt = ctx.getHeapManager().newObject("java/time/LocalDateTime");
                return ConcreteValue.reference(ldt);
            });

        registry.register("java/time/LocalDateTime", "format", "(Ljava/time/format/DateTimeFormatter;)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                String formatted = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return ConcreteValue.reference(ctx.getHeapManager().internString(formatted));
            });

        registry.register("java/time/format/DateTimeFormatter", "ofPattern", "(Ljava/lang/String;)Ljava/time/format/DateTimeFormatter;",
            (receiver, args, ctx) -> {
                ObjectInstance formatter = ctx.getHeapManager().newObject("java/time/format/DateTimeFormatter");
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    ObjectInstance patternObj = args[0].asReference();
                    String pattern = ctx.getHeapManager().extractString(patternObj);
                    if (pattern != null) {
                        formatter.setField("java/time/format/DateTimeFormatter", "pattern", "Ljava/lang/String;", patternObj);
                    }
                }
                return ConcreteValue.reference(formatter);
            });

        registry.register("java/time/Clock", "systemDefaultZone", "()Ljava/time/Clock;",
            (receiver, args, ctx) -> {
                ObjectInstance clock = ctx.getHeapManager().newObject("java/time/Clock$SystemClock");
                return ConcreteValue.reference(clock);
            });

        registry.register("java/time/Clock$SystemClock", "instant", "()Ljava/time/Instant;",
            (receiver, args, ctx) -> {
                ObjectInstance instant = ctx.getHeapManager().newObject("java/time/Instant");
                instant.setField("java/time/Instant", "seconds", "J", System.currentTimeMillis() / 1000L);
                return ConcreteValue.reference(instant);
            });

        registry.register("java/time/Clock$SystemClock", "getZone", "()Ljava/time/ZoneId;",
            (receiver, args, ctx) -> {
                ObjectInstance zone = ctx.getHeapManager().newObject("java/time/ZoneId");
                return ConcreteValue.reference(zone);
            });

        registry.register("java/time/Instant", "now", "()Ljava/time/Instant;",
            (receiver, args, ctx) -> {
                ObjectInstance instant = ctx.getHeapManager().newObject("java/time/Instant");
                instant.setField("java/time/Instant", "seconds", "J", System.currentTimeMillis() / 1000L);
                return ConcreteValue.reference(instant);
            });

        registry.register("java/time/ZoneId", "systemDefault", "()Ljava/time/ZoneId;",
            (receiver, args, ctx) -> {
                ObjectInstance zone = ctx.getHeapManager().newObject("java/time/ZoneId");
                return ConcreteValue.reference(zone);
            });
    }
}
