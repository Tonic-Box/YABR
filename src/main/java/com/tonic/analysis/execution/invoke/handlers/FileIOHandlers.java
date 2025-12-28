package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeException;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FileIOHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerFileDescriptorHandlers(registry);
        registerFileInputStreamHandlers(registry);
        registerFileOutputStreamHandlers(registry);
        registerRandomAccessFileHandlers(registry);
        registerWinNTFileSystemHandlers(registry);
        registerObjectStreamHandlers(registry);
        registerConsoleHandlers(registry);
    }

    private void registerFileDescriptorHandlers(NativeRegistry registry) {
        registry.register("java/io/FileDescriptor", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileDescriptor", "sync", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileDescriptor", "close0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileDescriptor", "getHandle", "(I)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("java/io/FileDescriptor", "getAppend", "(I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/FileCleanable", "cleanupClose0", "(IJ)V",
            (receiver, args, ctx) -> null);
    }

    private void registerFileInputStreamHandlers(NativeRegistry registry) {
        registry.register("java/io/FileInputStream", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileInputStream", "open0", "(Ljava/lang/String;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileInputStream", "read0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/io/FileInputStream", "readBytes", "([BII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/io/FileInputStream", "skip0", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/io/FileInputStream", "available0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerFileOutputStreamHandlers(NativeRegistry registry) {
        registry.register("java/io/FileOutputStream", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileOutputStream", "open0", "(Ljava/lang/String;Z)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileOutputStream", "write", "(IZ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/FileOutputStream", "writeBytes", "([BIIZ)V",
            (receiver, args, ctx) -> null);
    }

    private void registerRandomAccessFileHandlers(NativeRegistry registry) {
        registry.register("java/io/RandomAccessFile", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/RandomAccessFile", "open0", "(Ljava/lang/String;I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/RandomAccessFile", "read0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/io/RandomAccessFile", "readBytes", "([BII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/io/RandomAccessFile", "write0", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/RandomAccessFile", "writeBytes", "([BII)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/RandomAccessFile", "seek0", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/RandomAccessFile", "getFilePointer", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/io/RandomAccessFile", "length", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/io/RandomAccessFile", "setLength", "(J)V",
            (receiver, args, ctx) -> null);
    }

    private void registerWinNTFileSystemHandlers(NativeRegistry registry) {
        registry.register("java/io/WinNTFileSystem", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/WinNTFileSystem", "canonicalize0", "(Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (args == null || args.length == 0 || args[0].isNull()) {
                    return ConcreteValue.nullRef();
                }
                String path = ctx.getHeapManager().extractString(args[0].asReference());
                if (path == null) return ConcreteValue.nullRef();
                try {
                    String canonical = new File(path).getCanonicalPath();
                    return ConcreteValue.reference(ctx.getHeapManager().internString(canonical));
                } catch (Exception e) {
                    return ConcreteValue.reference(args[0].asReference());
                }
            });

        registry.register("java/io/WinNTFileSystem", "canonicalizeWithPrefix0", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                if (args == null || args.length < 2) return ConcreteValue.nullRef();
                if (!args[1].isNull()) {
                    return ConcreteValue.reference(args[1].asReference());
                }
                return ConcreteValue.nullRef();
            });

        registry.register("java/io/WinNTFileSystem", "getBooleanAttributes", "(Ljava/io/File;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "checkAccess", "(Ljava/io/File;I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "getLastModifiedTime", "(Ljava/io/File;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/io/WinNTFileSystem", "getLength", "(Ljava/io/File;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/io/WinNTFileSystem", "setPermission", "(Ljava/io/File;IZZ)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "createFileExclusively", "(Ljava/lang/String;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "delete0", "(Ljava/io/File;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "list", "(Ljava/io/File;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/lang/String;", 0);
                return ConcreteValue.reference(empty);
            });

        registry.register("java/io/WinNTFileSystem", "createDirectory", "(Ljava/io/File;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "rename0", "(Ljava/io/File;Ljava/io/File;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "setLastModifiedTime", "(Ljava/io/File;J)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "setReadOnly", "(Ljava/io/File;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/WinNTFileSystem", "getDriveDirectory", "(I)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                int drive = args != null && args.length > 0 ? args[0].asInt() : 0;
                char driveLetter = (char) ('A' + drive);
                return ConcreteValue.reference(ctx.getHeapManager().internString(driveLetter + ":\\"));
            });

        registry.register("java/io/WinNTFileSystem", "getSpace0", "(Ljava/io/File;I)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("java/io/WinNTFileSystem", "getNameMax0", "(Ljava/lang/String;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(255));

        registry.register("java/io/WinNTFileSystem", "listRoots0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(4));
    }

    private void registerObjectStreamHandlers(NativeRegistry registry) {
        registry.register("java/io/ObjectInputStream", "bytesToFloats", "([BI[FII)V",
            (receiver, args, ctx) -> {
                if (args == null || args.length < 5) return null;
                if (args[0].isNull() || args[2].isNull()) return null;
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcPos = args[1].asInt();
                ArrayInstance dst = (ArrayInstance) args[2].asReference();
                int dstPos = args[3].asInt();
                int count = args[4].asInt();
                for (int i = 0; i < count; i++) {
                    int bits = 0;
                    for (int j = 0; j < 4; j++) {
                        bits = (bits << 8) | (src.getByte(srcPos + i * 4 + j) & 0xFF);
                    }
                    dst.set(dstPos + i, Float.intBitsToFloat(bits));
                }
                return null;
            });

        registry.register("java/io/ObjectInputStream", "bytesToDoubles", "([BI[DII)V",
            (receiver, args, ctx) -> {
                if (args == null || args.length < 5) return null;
                if (args[0].isNull() || args[2].isNull()) return null;
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcPos = args[1].asInt();
                ArrayInstance dst = (ArrayInstance) args[2].asReference();
                int dstPos = args[3].asInt();
                int count = args[4].asInt();
                for (int i = 0; i < count; i++) {
                    long bits = 0;
                    for (int j = 0; j < 8; j++) {
                        bits = (bits << 8) | (src.getByte(srcPos + i * 8 + j) & 0xFF);
                    }
                    dst.set(dstPos + i, Double.longBitsToDouble(bits));
                }
                return null;
            });

        registry.register("java/io/ObjectOutputStream", "floatsToBytes", "([FI[BII)V",
            (receiver, args, ctx) -> {
                if (args == null || args.length < 5) return null;
                if (args[0].isNull() || args[2].isNull()) return null;
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcPos = args[1].asInt();
                ArrayInstance dst = (ArrayInstance) args[2].asReference();
                int dstPos = args[3].asInt();
                int count = args[4].asInt();
                for (int i = 0; i < count; i++) {
                    Object val = src.get(srcPos + i);
                    float f = val instanceof Float ? (Float) val : 0.0f;
                    int bits = Float.floatToIntBits(f);
                    for (int j = 0; j < 4; j++) {
                        dst.setByte(dstPos + i * 4 + (3 - j), (byte) (bits & 0xFF));
                        bits >>= 8;
                    }
                }
                return null;
            });

        registry.register("java/io/ObjectOutputStream", "doublesToBytes", "([DI[BII)V",
            (receiver, args, ctx) -> {
                if (args == null || args.length < 5) return null;
                if (args[0].isNull() || args[2].isNull()) return null;
                ArrayInstance src = (ArrayInstance) args[0].asReference();
                int srcPos = args[1].asInt();
                ArrayInstance dst = (ArrayInstance) args[2].asReference();
                int dstPos = args[3].asInt();
                int count = args[4].asInt();
                for (int i = 0; i < count; i++) {
                    Object val = src.get(srcPos + i);
                    double d = val instanceof Double ? (Double) val : 0.0;
                    long bits = Double.doubleToLongBits(d);
                    for (int j = 0; j < 8; j++) {
                        dst.setByte(dstPos + i * 8 + (7 - j), (byte) (bits & 0xFF));
                        bits >>= 8;
                    }
                }
                return null;
            });

        registry.register("java/io/ObjectStreamClass", "initNative", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/io/ObjectStreamClass", "hasStaticInitializer", "(Ljava/lang/Class;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerConsoleHandlers(NativeRegistry registry) {
        registry.register("java/io/Console", "istty", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/io/Console", "echo", "(Z)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/io/Console", "encoding", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("UTF-8")));
    }
}
