package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class VMHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerVMHandlers(registry);
        registerBootLoaderHandlers(registry);
        registerPerfHandlers(registry);
        registerVMSupportHandlers(registry);
        registerSignalHandlers(registry);
        registerConstantPoolHandlers(registry);
    }

    private void registerVMHandlers(NativeRegistry registry) {
        registry.register("jdk/internal/misc/VM", "initialize", "()V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/VM", "initializeFromArchive", "(Ljava/lang/Class;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/misc/VM", "getNanoTimeAdjustment", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("jdk/internal/misc/VM", "getRuntimeArguments", "()[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/lang/String;", 0);
                return ConcreteValue.reference(empty);
            });

        registry.register("jdk/internal/misc/VM", "latestUserDefinedLoader0", "()Ljava/lang/ClassLoader;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/misc/VM", "getuid", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(1000L));

        registry.register("jdk/internal/misc/VM", "geteuid", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(1000L));

        registry.register("jdk/internal/misc/VM", "getgid", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(1000L));

        registry.register("jdk/internal/misc/VM", "getegid", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(1000L));
    }

    private void registerBootLoaderHandlers(NativeRegistry registry) {
        registry.register("jdk/internal/loader/BootLoader", "getSystemPackageLocation", "(Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/loader/BootLoader", "getSystemPackageNames", "()[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/lang/String;", 0);
                return ConcreteValue.reference(empty);
            });

        registry.register("jdk/internal/loader/BootLoader", "setBootLoaderUnnamedModule0", "(Ljava/lang/Module;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/jimage/NativeImageBuffer", "getNativeMap", "(Ljava/lang/String;)Ljava/nio/ByteBuffer;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());
    }

    private void registerPerfHandlers(NativeRegistry registry) {
        registry.register("jdk/internal/perf/Perf", "registerNatives", "()V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/perf/Perf", "createLong", "(Ljava/lang/String;IIJ)Ljava/nio/ByteBuffer;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/perf/Perf", "createByteArray", "(Ljava/lang/String;II[BI)Ljava/nio/ByteBuffer;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/perf/Perf", "attach", "(Ljava/lang/String;II)Ljava/nio/ByteBuffer;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/perf/Perf", "detach", "(Ljava/nio/ByteBuffer;)V",
            (receiver, args, ctx) -> null);

        registry.register("jdk/internal/perf/Perf", "highResCounter", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.nanoTime()));

        registry.register("jdk/internal/perf/Perf", "highResFrequency", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(1000000000L));
    }

    private void registerVMSupportHandlers(NativeRegistry registry) {
        registry.register("jdk/internal/vm/VMSupport", "initAgentProperties", "(Ljava/util/Properties;)Ljava/util/Properties;",
            (receiver, args, ctx) -> {
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    return ConcreteValue.reference(args[0].asReference());
                }
                return ConcreteValue.nullRef();
            });

        registry.register("jdk/internal/vm/VMSupport", "getVMTemporaryDirectory", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString(System.getProperty("java.io.tmpdir", "/tmp"))));
    }

    private void registerSignalHandlers(NativeRegistry registry) {
        registry.register("jdk/internal/misc/Signal", "findSignal0", "(Ljava/lang/String;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("jdk/internal/misc/Signal", "handle0", "(IJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("jdk/internal/misc/Signal", "raise0", "(I)V",
            (receiver, args, ctx) -> null);
    }

    private void registerConstantPoolHandlers(NativeRegistry registry) {
        registry.register("jdk/internal/reflect/ConstantPool", "getSize0", "(Ljava/lang/Object;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/reflect/ConstantPool", "getClassAt0", "(Ljava/lang/Object;I)Ljava/lang/Class;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/ConstantPool", "getClassAtIfLoaded0", "(Ljava/lang/Object;I)Ljava/lang/Class;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/ConstantPool", "getClassRefIndexAt0", "(Ljava/lang/Object;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/reflect/ConstantPool", "getMethodAt0", "(Ljava/lang/Object;I)Ljava/lang/reflect/Member;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/ConstantPool", "getMethodAtIfLoaded0", "(Ljava/lang/Object;I)Ljava/lang/reflect/Member;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/ConstantPool", "getFieldAt0", "(Ljava/lang/Object;I)Ljava/lang/reflect/Field;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/ConstantPool", "getFieldAtIfLoaded0", "(Ljava/lang/Object;I)Ljava/lang/reflect/Field;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("jdk/internal/reflect/ConstantPool", "getMemberRefInfoAt0", "(Ljava/lang/Object;I)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", 3);
                return ConcreteValue.reference(arr);
            });

        registry.register("jdk/internal/reflect/ConstantPool", "getNameAndTypeRefIndexAt0", "(Ljava/lang/Object;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/reflect/ConstantPool", "getNameAndTypeRefInfoAt0", "(Ljava/lang/Object;I)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", 2);
                return ConcreteValue.reference(arr);
            });

        registry.register("jdk/internal/reflect/ConstantPool", "getIntAt0", "(Ljava/lang/Object;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/reflect/ConstantPool", "getLongAt0", "(Ljava/lang/Object;I)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("jdk/internal/reflect/ConstantPool", "getFloatAt0", "(Ljava/lang/Object;I)F",
            (receiver, args, ctx) -> ConcreteValue.floatValue(0.0f));

        registry.register("jdk/internal/reflect/ConstantPool", "getDoubleAt0", "(Ljava/lang/Object;I)D",
            (receiver, args, ctx) -> ConcreteValue.doubleValue(0.0));

        registry.register("jdk/internal/reflect/ConstantPool", "getStringAt0", "(Ljava/lang/Object;I)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("")));

        registry.register("jdk/internal/reflect/ConstantPool", "getUTF8At0", "(Ljava/lang/Object;I)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("")));

        registry.register("jdk/internal/reflect/ConstantPool", "getTagAt0", "(Ljava/lang/Object;I)B",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("jdk/internal/reflect/Reflection", "areNestMates", "(Ljava/lang/Class;Ljava/lang/Class;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));
    }
}
