package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.zip.CRC32;
import java.util.zip.Adler32;

public final class ZipHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerCRC32Handlers(registry);
        registerAdler32Handlers(registry);
        registerInflaterHandlers(registry);
        registerDeflaterHandlers(registry);
    }

    private void registerCRC32Handlers(NativeRegistry registry) {
        registry.register("java/util/zip/CRC32", "update", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.update on null");
                }
                Object crcObj = receiver.getField("java/util/zip/CRC32", "crc", "J");
                long crc = crcObj instanceof Long ? (Long) crcObj : 0L;
                CRC32 temp = new CRC32();
                temp.update(args[0].asInt());
                receiver.setField("java/util/zip/CRC32", "crc", "J", temp.getValue());
                return null;
            });

        registry.register("java/util/zip/CRC32", "updateBytes", "(J[BII)I",
            (receiver, args, ctx) -> {
                long crc = args[0].asLong();
                if (args[1].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.updateBytes null array");
                }
                ArrayInstance arr = (ArrayInstance) args[1].asReference();
                int off = args[2].asInt();
                int len = args[3].asInt();
                CRC32 temp = new CRC32();
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++) {
                    bytes[i] = arr.getByte(off + i);
                }
                temp.update(bytes);
                return ConcreteValue.intValue((int) temp.getValue());
            });

        registry.register("java/util/zip/CRC32", "updateByteBuffer", "(JJII)I",
            (receiver, args, ctx) -> {
                return ConcreteValue.intValue(0);
            });

        registry.register("java/util/zip/CRC32", "getValue", "()J",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.getValue on null");
                }
                Object crcObj = receiver.getField("java/util/zip/CRC32", "crc", "J");
                return ConcreteValue.longValue(crcObj instanceof Long ? (Long) crcObj : 0L);
            });

        registry.register("java/util/zip/CRC32", "reset", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.reset on null");
                }
                receiver.setField("java/util/zip/CRC32", "crc", "J", 0L);
                return null;
            });
    }

    private void registerAdler32Handlers(NativeRegistry registry) {
        registry.register("java/util/zip/Adler32", "update", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.update on null");
                }
                Object adlerObj = receiver.getField("java/util/zip/Adler32", "adler", "J");
                Adler32 temp = new Adler32();
                temp.update(args[0].asInt());
                receiver.setField("java/util/zip/Adler32", "adler", "J", temp.getValue());
                return null;
            });

        registry.register("java/util/zip/Adler32", "updateBytes", "(J[BII)I",
            (receiver, args, ctx) -> {
                if (args[1].isNull()) {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.updateBytes null array");
                }
                ArrayInstance arr = (ArrayInstance) args[1].asReference();
                int off = args[2].asInt();
                int len = args[3].asInt();
                Adler32 temp = new Adler32();
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++) {
                    bytes[i] = arr.getByte(off + i);
                }
                temp.update(bytes);
                return ConcreteValue.intValue((int) temp.getValue());
            });

        registry.register("java/util/zip/Adler32", "updateByteBuffer", "(JJII)I",
            (receiver, args, ctx) -> {
                return ConcreteValue.intValue(1);
            });

        registry.register("java/util/zip/Adler32", "getValue", "()J",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.getValue on null");
                }
                Object adlerObj = receiver.getField("java/util/zip/Adler32", "adler", "J");
                return ConcreteValue.longValue(adlerObj instanceof Long ? (Long) adlerObj : 1L);
            });

        registry.register("java/util/zip/Adler32", "reset", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null) {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.reset on null");
                }
                receiver.setField("java/util/zip/Adler32", "adler", "J", 1L);
                return null;
            });
    }

    private void registerInflaterHandlers(NativeRegistry registry) {
        registry.register("java/util/zip/Inflater", "init", "(Z)J",
            (receiver, args, ctx) -> {
                return ConcreteValue.longValue(System.nanoTime());
            });

        registry.register("java/util/zip/Inflater", "initBytes", "(J[BII)V",
            (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Inflater", "inflateBytes", "(J[BII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/util/zip/Inflater", "inflateBufferBytes", "(JJI[BII)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/util/zip/Inflater", "getAdler", "(J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/util/zip/Inflater", "getBytesRead", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/util/zip/Inflater", "getBytesWritten", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/util/zip/Inflater", "reset", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Inflater", "end", "(J)V",
            (receiver, args, ctx) -> null);
    }

    private void registerDeflaterHandlers(NativeRegistry registry) {
        registry.register("java/util/zip/Deflater", "init", "(IIZ)J",
            (receiver, args, ctx) -> {
                return ConcreteValue.longValue(System.nanoTime());
            });

        registry.register("java/util/zip/Deflater", "initBytes", "(J[BII)V",
            (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "deflateBytes", "(J[BIII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/util/zip/Deflater", "deflateBufferBytes", "(JJI[BIII)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/util/zip/Deflater", "getAdler", "(J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/util/zip/Deflater", "getBytesRead", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/util/zip/Deflater", "getBytesWritten", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/util/zip/Deflater", "reset", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "end", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "setDictionary", "(J[BII)V",
            (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "setDictionaryBuffer", "(JJI)V",
            (receiver, args, ctx) -> null);
    }
}
