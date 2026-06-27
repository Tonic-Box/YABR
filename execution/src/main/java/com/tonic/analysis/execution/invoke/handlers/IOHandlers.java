package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeMethodHandler;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class IOHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        NativeMethodHandler voidNoOp = (receiver, args, ctx) -> null;

        String[] printStreamMethods = {
            "println", "()V",
            "println", "(Z)V",
            "println", "(C)V",
            "println", "(I)V",
            "println", "(J)V",
            "println", "(F)V",
            "println", "(D)V",
            "println", "(Ljava/lang/String;)V",
            "println", "(Ljava/lang/Object;)V",
            "println", "([C)V",
            "print", "(Z)V",
            "print", "(C)V",
            "print", "(I)V",
            "print", "(J)V",
            "print", "(F)V",
            "print", "(D)V",
            "print", "(Ljava/lang/String;)V",
            "print", "(Ljava/lang/Object;)V",
            "print", "([C)V",
            "write", "(I)V",
            "write", "([B)V",
            "write", "([BII)V",
            "write", "(Ljava/lang/String;)V",
            "flush", "()V",
            "close", "()V",
            "checkError", "()Z",
            "append", "(C)Ljava/io/PrintStream;",
            "append", "(Ljava/lang/CharSequence;)Ljava/io/PrintStream;",
            "append", "(Ljava/lang/CharSequence;II)Ljava/io/PrintStream;"
        };

        for (int i = 0; i < printStreamMethods.length; i += 2) {
            String name = printStreamMethods[i];
            String desc = printStreamMethods[i + 1];

            if (desc.endsWith(")Z")) {
                registry.register("java/io/PrintStream", name, desc,
                    (receiver, args, ctx) -> ConcreteValue.intValue(0));
            } else if (desc.endsWith(")Ljava/io/PrintStream;")) {
                registry.register("java/io/PrintStream", name, desc,
                    (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
            } else {
                registry.register("java/io/PrintStream", name, desc, voidNoOp);
            }
        }

        registry.register("java/io/PrintStream", "printf", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
        registry.register("java/io/PrintStream", "printf", "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
        registry.register("java/io/PrintStream", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
        registry.register("java/io/PrintStream", "format", "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintStream;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());

        String[] printWriterMethods = {
            "println", "()V",
            "println", "(Z)V",
            "println", "(C)V",
            "println", "(I)V",
            "println", "(J)V",
            "println", "(F)V",
            "println", "(D)V",
            "println", "(Ljava/lang/String;)V",
            "println", "(Ljava/lang/Object;)V",
            "println", "([C)V",
            "print", "(Z)V",
            "print", "(C)V",
            "print", "(I)V",
            "print", "(J)V",
            "print", "(F)V",
            "print", "(D)V",
            "print", "(Ljava/lang/String;)V",
            "print", "(Ljava/lang/Object;)V",
            "print", "([C)V",
            "write", "(I)V",
            "write", "([C)V",
            "write", "([CII)V",
            "write", "(Ljava/lang/String;)V",
            "write", "(Ljava/lang/String;II)V",
            "flush", "()V",
            "close", "()V",
            "checkError", "()Z",
            "append", "(C)Ljava/io/PrintWriter;",
            "append", "(Ljava/lang/CharSequence;)Ljava/io/PrintWriter;",
            "append", "(Ljava/lang/CharSequence;II)Ljava/io/PrintWriter;"
        };

        for (int i = 0; i < printWriterMethods.length; i += 2) {
            String name = printWriterMethods[i];
            String desc = printWriterMethods[i + 1];

            if (desc.endsWith(")Z")) {
                registry.register("java/io/PrintWriter", name, desc,
                    (receiver, args, ctx) -> ConcreteValue.intValue(0));
            } else if (desc.endsWith(")Ljava/io/PrintWriter;")) {
                registry.register("java/io/PrintWriter", name, desc,
                    (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
            } else {
                registry.register("java/io/PrintWriter", name, desc, voidNoOp);
            }
        }

        registry.register("java/io/PrintWriter", "printf", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintWriter;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
        registry.register("java/io/PrintWriter", "printf", "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintWriter;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
        registry.register("java/io/PrintWriter", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintWriter;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());
        registry.register("java/io/PrintWriter", "format", "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/io/PrintWriter;",
            (receiver, args, ctx) -> receiver != null ? ConcreteValue.reference(receiver) : ConcreteValue.nullRef());

        registry.register("java/io/OutputStream", "write", "(I)V", voidNoOp);
        registry.register("java/io/OutputStream", "write", "([B)V", voidNoOp);
        registry.register("java/io/OutputStream", "write", "([BII)V", voidNoOp);
        registry.register("java/io/OutputStream", "flush", "()V", voidNoOp);
        registry.register("java/io/OutputStream", "close", "()V", voidNoOp);

        registry.register("java/io/Writer", "write", "(I)V", voidNoOp);
        registry.register("java/io/Writer", "write", "([C)V", voidNoOp);
        registry.register("java/io/Writer", "write", "([CII)V", voidNoOp);
        registry.register("java/io/Writer", "write", "(Ljava/lang/String;)V", voidNoOp);
        registry.register("java/io/Writer", "write", "(Ljava/lang/String;II)V", voidNoOp);
        registry.register("java/io/Writer", "flush", "()V", voidNoOp);
        registry.register("java/io/Writer", "close", "()V", voidNoOp);

        registry.register("java/io/BufferedOutputStream", "write", "(I)V", voidNoOp);
        registry.register("java/io/BufferedOutputStream", "write", "([BII)V", voidNoOp);
        registry.register("java/io/BufferedOutputStream", "flush", "()V", voidNoOp);

        registry.register("java/io/BufferedWriter", "write", "(I)V", voidNoOp);
        registry.register("java/io/BufferedWriter", "write", "([CII)V", voidNoOp);
        registry.register("java/io/BufferedWriter", "write", "(Ljava/lang/String;II)V", voidNoOp);
        registry.register("java/io/BufferedWriter", "newLine", "()V", voidNoOp);
        registry.register("java/io/BufferedWriter", "flush", "()V", voidNoOp);
    }
}
