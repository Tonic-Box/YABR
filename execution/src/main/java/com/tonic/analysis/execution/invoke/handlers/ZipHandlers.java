package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;


/**
 * Native handlers for java.util.zip.
 */
public final class ZipHandlers implements NativeHandlerProvider
{

    private static final int ADLER_BASE = 65521;
    private static final int[] CRC32_TABLE = buildCrc32Table();

    private static int[] buildCrc32Table()
    {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++)
        {
            int c = i;
            for (int bit = 0; bit < 8; bit++)
            {
                c = (c & 1) != 0 ? 0xEDB88320 ^ c >>> 1 : c >>> 1;
            }
            table[i] = c;
        }
        return table;
    }

    @Override
    public void register(NativeRegistry registry)
    {
        registerCRC32Handlers(registry);
        registerAdler32Handlers(registry);
        registerInflaterHandlers(registry);
        registerDeflaterHandlers(registry);
    }

    private void registerCRC32Handlers(NativeRegistry registry)
    {
        registry.register("java/util/zip/CRC32", "update", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null)
                {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.update on null");
                }
                Object crcObj = receiver.getField("java/util/zip/CRC32", "crc", "J");
                long crc = crcObj instanceof Long ? (Long) crcObj : 0L;
                byte[] one = {(byte) args[0].asInt()};
                receiver.setField("java/util/zip/CRC32", "crc", "J", crc32(crc, one, 0, 1));
                return null;
            });

        registry.register("java/util/zip/CRC32", "updateBytes", "(J[BII)I",
            (receiver, args, ctx) -> {
                long crc = args[0].asLong();
                if (args[1].isNull())
                {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.updateBytes null array");
                }
                ArrayInstance arr = (ArrayInstance) args[1].asReference();
                int off = args[2].asInt();
                int len = args[3].asInt();
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++)
                {
                    bytes[i] = arr.getByte(off + i);
                }
                return ConcreteValue.intValue((int) crc32(crc, bytes, 0, len));
            });

        registry.register("java/util/zip/CRC32", "updateByteBuffer", "(JJII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/util/zip/CRC32", "getValue", "()J",
            (receiver, args, ctx) -> {
                if (receiver == null)
                {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.getValue on null");
                }
                Object crcObj = receiver.getField("java/util/zip/CRC32", "crc", "J");
                return ConcreteValue.longValue(crcObj instanceof Long ? (Long) crcObj : 0L);
            });

        registry.register("java/util/zip/CRC32", "reset", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null)
                {
                    throw new NativeException("java/lang/NullPointerException", "CRC32.reset on null");
                }
                receiver.setField("java/util/zip/CRC32", "crc", "J", 0L);
                return null;
            });
    }

    private void registerAdler32Handlers(NativeRegistry registry)
    {
        registry.register("java/util/zip/Adler32", "update", "(I)V",
            (receiver, args, ctx) -> {
                if (receiver == null)
                {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.update on null");
                }
                Object adlerObj = receiver.getField("java/util/zip/Adler32", "adler", "J");
                long adler = adlerObj instanceof Long ? (Long) adlerObj : 1L;
                byte[] one = {(byte) args[0].asInt()};
                receiver.setField("java/util/zip/Adler32", "adler", "J", adler32(adler, one, 0, 1));
                return null;
            });

        registry.register("java/util/zip/Adler32", "updateBytes", "(J[BII)I",
            (receiver, args, ctx) -> {
                if (args[1].isNull())
                {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.updateBytes null array");
                }
                ArrayInstance arr = (ArrayInstance) args[1].asReference();
                int off = args[2].asInt();
                int len = args[3].asInt();
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++)
                {
                    bytes[i] = arr.getByte(off + i);
                }
                return ConcreteValue.intValue((int) adler32(args[0].asLong(), bytes, 0, len));
            });

        registry.register("java/util/zip/Adler32", "updateByteBuffer", "(JJII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/util/zip/Adler32", "getValue", "()J",
            (receiver, args, ctx) -> {
                if (receiver == null)
                {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.getValue on null");
                }
                Object adlerObj = receiver.getField("java/util/zip/Adler32", "adler", "J");
                return ConcreteValue.longValue(adlerObj instanceof Long ? (Long) adlerObj : 1L);
            });

        registry.register("java/util/zip/Adler32", "reset", "()V",
            (receiver, args, ctx) -> {
                if (receiver == null)
                {
                    throw new NativeException("java/lang/NullPointerException", "Adler32.reset on null");
                }
                receiver.setField("java/util/zip/Adler32", "adler", "J", 1L);
                return null;
            });
    }

    /**
     * Continues a CRC-32 over {@code len} bytes.
     */
    private static long crc32(long crc, byte[] bytes, int off, int len)
    {
        int c = (int) ~crc;
        for (int i = 0; i < len; i++)
        {
            c = CRC32_TABLE[(c ^ bytes[off + i]) & 0xFF] ^ (c >>> 8);
        }
        return ~c & 0xFFFFFFFFL;
    }

    /**
     * Continues an Adler-32 over {@code len} bytes.
     */
    private static long adler32(long adler, byte[] bytes, int off, int len)
    {
        long a = adler & 0xFFFF;
        long b = adler >>> 16 & 0xFFFF;
        for (int i = 0; i < len; i++)
        {
            a = (a + (bytes[off + i] & 0xFF)) % ADLER_BASE;
            b = (b + a) % ADLER_BASE;
        }
        return b << 16 | a;
    }

    private void registerInflaterHandlers(NativeRegistry registry)
    {
        registry.register("java/util/zip/Inflater", "init", "(Z)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.nanoTime()));

        registry.register("java/util/zip/Inflater", "initBytes", "(J[BII)V", (receiver, args, ctx) -> null);

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

        registry.register("java/util/zip/Inflater", "reset", "(J)V", (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Inflater", "end", "(J)V", (receiver, args, ctx) -> null);
    }

    private void registerDeflaterHandlers(NativeRegistry registry)
    {
        registry.register("java/util/zip/Deflater", "init", "(IIZ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(System.nanoTime()));

        registry.register("java/util/zip/Deflater", "initBytes", "(J[BII)V", (receiver, args, ctx) -> null);

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

        registry.register("java/util/zip/Deflater", "reset", "(J)V", (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "end", "(J)V", (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "setDictionary", "(J[BII)V", (receiver, args, ctx) -> null);

        registry.register("java/util/zip/Deflater", "setDictionaryBuffer", "(JJI)V", (receiver, args, ctx) -> null);
    }
}
