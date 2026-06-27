package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

public final class NetworkHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerInetAddressHandlers(registry);
        registerPlainSocketImplHandlers(registry);
        registerDatagramSocketHandlers(registry);
        registerNetworkInterfaceHandlers(registry);
        registerSocketStreamHandlers(registry);
    }

    private void registerInetAddressHandlers(NativeRegistry registry) {
        registry.register("java/net/InetAddress", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/Inet4Address", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/Inet6Address", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/InetAddressImplFactory", "isIPv6Supported", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("java/net/Inet4AddressImpl", "getLocalHostName", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("localhost")));

        registry.register("java/net/Inet4AddressImpl", "lookupAllHostAddr", "(Ljava/lang/String;)[Ljava/net/InetAddress;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/net/InetAddress;", 1);
                ObjectInstance addr = ctx.getHeapManager().newObject("java/net/Inet4Address");
                arr.set(0, ConcreteValue.reference(addr));
                return ConcreteValue.reference(arr);
            });

        registry.register("java/net/Inet4AddressImpl", "getHostByAddr", "([B)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("localhost")));

        registry.register("java/net/Inet4AddressImpl", "isReachable0", "([BI[BI)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/Inet6AddressImpl", "getLocalHostName", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("localhost")));

        registry.register("java/net/Inet6AddressImpl", "lookupAllHostAddr", "(Ljava/lang/String;)[Ljava/net/InetAddress;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/net/InetAddress;", 1);
                ObjectInstance addr = ctx.getHeapManager().newObject("java/net/Inet6Address");
                arr.set(0, ConcreteValue.reference(addr));
                return ConcreteValue.reference(arr);
            });

        registry.register("java/net/Inet6AddressImpl", "getHostByAddr", "([B)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("localhost")));

        registry.register("java/net/Inet6AddressImpl", "isReachable0", "([BII[BII)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));
    }

    private void registerPlainSocketImplHandlers(NativeRegistry registry) {
        registry.register("java/net/PlainSocketImpl", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "socket0", "(Z)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/PlainSocketImpl", "bind0", "(ILjava/net/InetAddress;IZ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "listen0", "(II)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "connect0", "(ILjava/net/InetAddress;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/PlainSocketImpl", "accept0", "(I[Ljava/net/InetSocketAddress;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/PlainSocketImpl", "waitForConnect", "(II)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "waitForNewConnection", "(II)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "available0", "(I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/PlainSocketImpl", "close0", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "shutdown0", "(II)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "setIntOption", "(III)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "getIntOption", "(II)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/PlainSocketImpl", "setSoTimeout0", "(II)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "sendOOB", "(II)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "configureBlocking", "(IZ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/PlainSocketImpl", "localPort0", "(I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/PlainSocketImpl", "localAddress", "(ILjava/net/InetAddressContainer;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/AbstractPlainSocketImpl", "isReusePortAvailable0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/AbstractPlainDatagramSocketImpl", "isReusePortAvailable0", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/SocketCleanable", "cleanupClose0", "(I)V",
            (receiver, args, ctx) -> null);
    }

    private void registerDatagramSocketHandlers(NativeRegistry registry) {
        registry.register("java/net/DatagramPacket", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "initIDs", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketCreate", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketBind", "(ILjava/net/InetAddress;IZ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketConnect", "(ILjava/net/InetAddress;I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketDisconnect", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketClose", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketLocalPort", "(I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketLocalAddress", "(I)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketReceiveOrPeekData", "(ILjava/net/DatagramPacket;IZZ)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketSend", "(I[BIILjava/net/InetAddress;IZ)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketSetIntOption", "(III)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "socketGetIntOption", "(II)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/DualStackPlainDatagramSocketImpl", "dataAvailable", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registerTwoStacksDatagramHandlers(registry);
    }

    private void registerTwoStacksDatagramHandlers(NativeRegistry registry) {
        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "datagramSocketCreate", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "datagramSocketClose", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "bind0", "(ILjava/net/InetAddress;Z)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "connect0", "(Ljava/net/InetAddress;I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "disconnect0", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "send0", "(Ljava/net/DatagramPacket;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "receive0", "(Ljava/net/DatagramPacket;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "peek", "(Ljava/net/InetAddress;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "peekData", "(Ljava/net/DatagramPacket;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "getTTL", "()B",
            (receiver, args, ctx) -> ConcreteValue.intValue(64));

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "setTTL", "(B)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "getTimeToLive", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(64));

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "setTimeToLive", "(I)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "join", "(Ljava/net/InetAddress;Ljava/net/NetworkInterface;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "leave", "(Ljava/net/InetAddress;Ljava/net/NetworkInterface;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "dataAvailable", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "socketGetOption", "(I)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "socketNativeSetOption", "(ILjava/lang/Object;)V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/TwoStacksPlainDatagramSocketImpl", "socketLocalAddress", "(I)Ljava/lang/Object;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());
    }

    private void registerNetworkInterfaceHandlers(NativeRegistry registry) {
        registry.register("java/net/NetworkInterface", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/NetworkInterface", "getByName0", "(Ljava/lang/String;)Ljava/net/NetworkInterface;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/net/NetworkInterface", "getByIndex0", "(I)Ljava/net/NetworkInterface;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/net/NetworkInterface", "getByInetAddress0", "(Ljava/net/InetAddress;)Ljava/net/NetworkInterface;",
            (receiver, args, ctx) -> ConcreteValue.nullRef());

        registry.register("java/net/NetworkInterface", "getAll", "()[Ljava/net/NetworkInterface;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/net/NetworkInterface;", 0);
                return ConcreteValue.reference(empty);
            });

        registry.register("java/net/NetworkInterface", "isUp0", "(Ljava/lang/String;I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/NetworkInterface", "isLoopback0", "(Ljava/lang/String;I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/NetworkInterface", "isP2P0", "(Ljava/lang/String;I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/NetworkInterface", "supportsMulticast0", "(Ljava/lang/String;I)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("java/net/NetworkInterface", "getMTU0", "(Ljava/lang/String;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1500));

        registry.register("java/net/NetworkInterface", "getMacAddr0", "([BLjava/lang/String;I)[B",
            (receiver, args, ctx) -> {
                ArrayInstance mac = ctx.getHeapManager().newArray("[B", 6);
                return ConcreteValue.reference(mac);
            });
    }

    private void registerSocketStreamHandlers(NativeRegistry registry) {
        registry.register("java/net/SocketInputStream", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/SocketInputStream", "socketRead0", "(Ljava/io/FileDescriptor;[BIII)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("java/net/SocketOutputStream", "init", "()V",
            (receiver, args, ctx) -> null);

        registry.register("java/net/SocketOutputStream", "socketWrite0", "(Ljava/io/FileDescriptor;[BII)V",
            (receiver, args, ctx) -> null);
    }
}
