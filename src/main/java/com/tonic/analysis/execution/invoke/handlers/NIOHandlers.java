package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class NIOHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerMappedByteBufferHandlers(registry);
        registerFileChannelHandlers(registry);
        registerFileDispatcherHandlers(registry);
        registerSocketDispatcherHandlers(registry);
        registerIOUtilHandlers(registry);
        registerNetHandlers(registry);
        registerDatagramChannelHandlers(registry);
        registerServerSocketChannelHandlers(registry);
        registerSocketChannelHandlers(registry);
        registerIocpHandlers(registry);
        registerWindowsSelectorHandlers(registry);
    }

    private void registerMappedByteBufferHandlers(NativeRegistry registry) {
        registry.register("java/nio/MappedByteBuffer", "isLoaded0", "(JJI)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/nio/MappedByteBuffer", "load0", "(JJ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/nio/MappedByteBuffer", "force0", "(Ljava/io/FileDescriptor;JJ)V",
            (receiver, args, ctx) -> null);
    }

    private void registerFileChannelHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/FileChannelImpl", "initIDs", "()J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/FileChannelImpl", "map0", "(IJJ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/FileChannelImpl", "unmap0", "(JJ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/FileChannelImpl", "transferTo0", "(Ljava/io/FileDescriptor;JJLjava/io/FileDescriptor;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));
    }

    private void registerFileDispatcherHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/FileDispatcherImpl", "read0", "(Ljava/io/FileDescriptor;JI)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/FileDispatcherImpl", "readv0", "(Ljava/io/FileDescriptor;JI)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("sun/nio/ch/FileDispatcherImpl", "write0", "(Ljava/io/FileDescriptor;JIZ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/FileDispatcherImpl", "writev0", "(Ljava/io/FileDescriptor;JIZ)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/FileDispatcherImpl", "pread0", "(Ljava/io/FileDescriptor;JIJ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/FileDispatcherImpl", "pwrite0", "(Ljava/io/FileDescriptor;JIJ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/FileDispatcherImpl", "seek0", "(Ljava/io/FileDescriptor;J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/FileDispatcherImpl", "force0", "(Ljava/io/FileDescriptor;Z)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/FileDispatcherImpl", "truncate0", "(Ljava/io/FileDescriptor;J)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/FileDispatcherImpl", "size0", "(Ljava/io/FileDescriptor;)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/FileDispatcherImpl", "lock0", "(Ljava/io/FileDescriptor;ZJJZ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/FileDispatcherImpl", "release0", "(Ljava/io/FileDescriptor;JJ)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/FileDispatcherImpl", "close0", "(Ljava/io/FileDescriptor;)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/FileDispatcherImpl", "duplicateHandle", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("sun/nio/ch/FileDispatcherImpl", "setDirect0", "(Ljava/io/FileDescriptor;Ljava/nio/CharBuffer;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerSocketDispatcherHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/SocketDispatcher", "read0", "(Ljava/io/FileDescriptor;JI)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/SocketDispatcher", "readv0", "(Ljava/io/FileDescriptor;JI)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("sun/nio/ch/SocketDispatcher", "write0", "(Ljava/io/FileDescriptor;JI)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/SocketDispatcher", "writev0", "(Ljava/io/FileDescriptor;JI)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/SocketDispatcher", "close0", "(Ljava/io/FileDescriptor;)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/SocketDispatcher", "preClose0", "(Ljava/io/FileDescriptor;)V",
            (receiver, args, ctx) -> null);
    }

    private void registerIOUtilHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/IOUtil", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/IOUtil", "iovMax", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(16));

        registry.register("sun/nio/ch/IOUtil", "fdLimit", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1024));

        registry.register("sun/nio/ch/IOUtil", "fdVal", "(Ljava/io/FileDescriptor;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/IOUtil", "setfdVal", "(Ljava/io/FileDescriptor;I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/IOUtil", "configureBlocking", "(Ljava/io/FileDescriptor;Z)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/IOUtil", "makePipe", "(Z)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/IOUtil", "drain", "(I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("sun/nio/ch/IOUtil", "drain1", "(I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/IOUtil", "write1", "(IB)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("sun/nio/ch/IOUtil", "randomBytes", "([B)Z",
            (receiver, args, ctx) -> {
                if (args != null && args.length > 0 && !args[0].isNull()) {
                    ArrayInstance arr = (ArrayInstance) args[0].asReference();
                    java.util.Random rnd = new java.util.Random();
                    for (int i = 0; i < arr.getLength(); i++) {
                        arr.setByte(i, (byte) rnd.nextInt(256));
                    }
                }
                return ConcreteValue.intValue(1);
            });

        registry.register("sun/nio/ch/FileKey", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/FileKey", "init", "(Ljava/io/FileDescriptor;)V",
            (receiver, args, ctx) -> null);
    }

    private void registerNetHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/Net", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "isIPv6Available0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("sun/nio/ch/Net", "isReusePortAvailable0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "isExclusiveBindAvailable", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("sun/nio/ch/Net", "canIPv6SocketJoinIPv4Group0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "canJoin6WithIPv4Group0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "socket0", "(ZZZZ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/Net", "bind0", "(Ljava/io/FileDescriptor;ZZLjava/net/InetAddress;I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "listen", "(Ljava/io/FileDescriptor;I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "connect0", "(ZLjava/io/FileDescriptor;Ljava/net/InetAddress;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/Net", "shutdown", "(Ljava/io/FileDescriptor;I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "localPort", "(Ljava/io/FileDescriptor;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "localInetAddress", "(Ljava/io/FileDescriptor;)Ljava/net/InetAddress;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("sun/nio/ch/Net", "remotePort", "(Ljava/io/FileDescriptor;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "remoteInetAddress", "(Ljava/io/FileDescriptor;)Ljava/net/InetAddress;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("sun/nio/ch/Net", "getIntOption0", "(Ljava/io/FileDescriptor;ZII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "setIntOption0", "(Ljava/io/FileDescriptor;ZIIIZ)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "getInterface4", "(Ljava/io/FileDescriptor;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "setInterface4", "(Ljava/io/FileDescriptor;I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "getInterface6", "(Ljava/io/FileDescriptor;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "setInterface6", "(Ljava/io/FileDescriptor;I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Net", "joinOrDrop4", "(ZLjava/io/FileDescriptor;III)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "joinOrDrop6", "(ZLjava/io/FileDescriptor;[BI[B)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "blockOrUnblock4", "(ZLjava/io/FileDescriptor;III)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "blockOrUnblock6", "(ZLjava/io/FileDescriptor;[BI[B)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "poll", "(Ljava/io/FileDescriptor;IJ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/Net", "pollinValue", "()S",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("sun/nio/ch/Net", "polloutValue", "()S",
            (receiver, args, ctx) -> ConcreteValue.intValue(4));

        registry.register("sun/nio/ch/Net", "pollerrValue", "()S",
            (receiver, args, ctx) -> ConcreteValue.intValue(8));

        registry.register("sun/nio/ch/Net", "pollhupValue", "()S",
            (receiver, args, ctx) -> ConcreteValue.intValue(16));

        registry.register("sun/nio/ch/Net", "pollnvalValue", "()S",
            (receiver, args, ctx) -> ConcreteValue.intValue(32));

        registry.register("sun/nio/ch/Net", "pollconnValue", "()S",
            (receiver, args, ctx) -> ConcreteValue.intValue(2));
    }

    private void registerDatagramChannelHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/DatagramChannelImpl", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/DatagramChannelImpl", "disconnect0", "(Ljava/io/FileDescriptor;Z)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/DatagramChannelImpl", "receive0", "(Ljava/io/FileDescriptor;JIZ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/DatagramChannelImpl", "send0", "(ZLjava/io/FileDescriptor;JILjava/net/InetAddress;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/DatagramDispatcher", "read0", "(Ljava/io/FileDescriptor;JI)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/nio/ch/DatagramDispatcher", "readv0", "(Ljava/io/FileDescriptor;JI)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(-1L));

        registry.register("sun/nio/ch/DatagramDispatcher", "write0", "(Ljava/io/FileDescriptor;JI)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/DatagramDispatcher", "writev0", "(Ljava/io/FileDescriptor;JI)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));
    }

    private void registerServerSocketChannelHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/ServerSocketChannelImpl", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/ServerSocketChannelImpl", "accept0", "(Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;[Ljava/net/InetSocketAddress;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));
    }

    private void registerSocketChannelHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/SocketChannelImpl", "checkConnect", "(Ljava/io/FileDescriptor;Z)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/SocketChannelImpl", "sendOutOfBandData", "(Ljava/io/FileDescriptor;B)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerIocpHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/Iocp", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Iocp", "createIoCompletionPort", "(JJII)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));

        registry.register("sun/nio/ch/Iocp", "close0", "(J)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Iocp", "getQueuedCompletionStatus", "(JLsun/nio/ch/Iocp$CompletionStatus;)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Iocp", "postQueuedCompletionStatus", "(JI)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/Iocp", "getErrorMessage", "(I)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("Unknown error")));
    }

    private void registerWindowsSelectorHandlers(NativeRegistry registry) {
        registry.register("sun/nio/ch/WindowsSelectorImpl$SubSelector", "poll0", "(JI[I[I[IJJ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/nio/ch/WindowsSelectorImpl", "setWakeupSocket0", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/WindowsSelectorImpl", "resetWakeupSocket0", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("sun/nio/ch/WindowsSelectorImpl", "discardUrgentData", "(I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }
}
