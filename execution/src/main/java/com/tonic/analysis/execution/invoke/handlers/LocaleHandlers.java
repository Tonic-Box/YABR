package com.tonic.analysis.execution.invoke.handlers;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.invoke.NativeHandlerProvider;
import com.tonic.analysis.execution.invoke.NativeRegistry;
import com.tonic.analysis.execution.state.ConcreteValue;

import java.util.TimeZone;

public final class LocaleHandlers implements NativeHandlerProvider {

    @Override
    public void register(NativeRegistry registry) {
        registerTimeZoneHandlers(registry);
        registerHostLocaleProviderHandlers(registry);
        registerDnsResolverHandlers(registry);
        registerProxySelectorHandlers(registry);
        registerWin32ErrorModeHandlers(registry);
    }

    private void registerTimeZoneHandlers(NativeRegistry registry) {
        registry.register("java/util/TimeZone", "getSystemTimeZoneID", "(Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String id = TimeZone.getDefault().getID();
                return ConcreteValue.reference(ctx.getHeapManager().internString(id));
            });

        registry.register("java/util/TimeZone", "getSystemGMTOffsetID", "()Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("GMT")));
    }

    private void registerHostLocaleProviderHandlers(NativeRegistry registry) {
        String provider = "sun/util/locale/provider/HostLocaleProviderAdapterImpl";

        registry.register(provider, "initialize", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register(provider, "getDefaultLocale", "(I)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("en_US")));

        registry.register(provider, "getDateTimePattern", "(IILjava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("yyyy-MM-dd HH:mm:ss")));

        registry.register(provider, "getNumberPattern", "(ILjava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("#,##0.###")));

        registry.register(provider, "getCurrencySymbol", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("$")));

        registry.register(provider, "getInternationalCurrencySymbol", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("USD")));

        registry.register(provider, "getDecimalSeparator", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue('.'));

        registry.register(provider, "getGroupingSeparator", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue(','));

        registry.register(provider, "getMonetaryDecimalSeparator", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue('.'));

        registry.register(provider, "getMinusSign", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue('-'));

        registry.register(provider, "getPercent", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue('%'));

        registry.register(provider, "getPerMill", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue('\u2030'));

        registry.register(provider, "getZeroDigit", "(Ljava/lang/String;C)C",
            (receiver, args, ctx) -> ConcreteValue.intValue('0'));

        registry.register(provider, "getInfinity", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("\u221E")));

        registry.register(provider, "getNaN", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("NaN")));

        registry.register(provider, "isNativeDigit", "(Ljava/lang/String;)Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register(provider, "getCalendarID", "(Ljava/lang/String;)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register(provider, "getCalendarDataValue", "(Ljava/lang/String;I)I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register(provider, "getDisplayString", "(Ljava/lang/String;ILjava/lang/String;)Ljava/lang/String;",
            (receiver, args, ctx) -> ConcreteValue.reference(ctx.getHeapManager().internString("")));

        registry.register(provider, "getAmPmStrings", "(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", 2);
                arr.set(0, ctx.getHeapManager().internString("AM"));
                arr.set(1, ctx.getHeapManager().internString("PM"));
                return ConcreteValue.reference(arr);
            });

        registry.register(provider, "getEras", "(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", 2);
                arr.set(0, ctx.getHeapManager().internString("BC"));
                arr.set(1, ctx.getHeapManager().internString("AD"));
                return ConcreteValue.reference(arr);
            });

        registry.register(provider, "getMonths", "(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String[] months = {"January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December", ""};
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", months.length);
                for (int i = 0; i < months.length; i++) {
                    arr.set(i, ctx.getHeapManager().internString(months[i]));
                }
                return ConcreteValue.reference(arr);
            });

        registry.register(provider, "getShortMonths", "(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", ""};
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", months.length);
                for (int i = 0; i < months.length; i++) {
                    arr.set(i, ctx.getHeapManager().internString(months[i]));
                }
                return ConcreteValue.reference(arr);
            });

        registry.register(provider, "getWeekdays", "(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String[] days = {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", days.length);
                for (int i = 0; i < days.length; i++) {
                    arr.set(i, ctx.getHeapManager().internString(days[i]));
                }
                return ConcreteValue.reference(arr);
            });

        registry.register(provider, "getShortWeekdays", "(Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                String[] days = {"", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", days.length);
                for (int i = 0; i < days.length; i++) {
                    arr.set(i, ctx.getHeapManager().internString(days[i]));
                }
                return ConcreteValue.reference(arr);
            });

        registry.register(provider, "getCalendarDisplayStrings", "(Ljava/lang/String;III)[Ljava/lang/String;",
            (receiver, args, ctx) -> {
                ArrayInstance arr = ctx.getHeapManager().newArray("[Ljava/lang/String;", 0);
                return ConcreteValue.reference(arr);
            });
    }

    private void registerDnsResolverHandlers(NativeRegistry registry) {
        registry.register("sun/net/dns/ResolverConfigurationImpl", "init0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/net/dns/ResolverConfigurationImpl", "loadDNSconfig0", "()V",
            (receiver, args, ctx) -> null);

        registry.register("sun/net/dns/ResolverConfigurationImpl", "notifyAddrChange0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(0));

        registry.register("sun/net/sdp/SdpSupport", "create0", "()I",
            (receiver, args, ctx) -> ConcreteValue.intValue(-1));

        registry.register("sun/net/sdp/SdpSupport", "convert0", "(I)V",
            (receiver, args, ctx) -> null);
    }

    private void registerProxySelectorHandlers(NativeRegistry registry) {
        registry.register("sun/net/spi/DefaultProxySelector", "init", "()Z",
            (receiver, args, ctx) -> ConcreteValue.intValue(1));

        registry.register("sun/net/spi/DefaultProxySelector", "getSystemProxies", "(Ljava/lang/String;Ljava/lang/String;)[Ljava/net/Proxy;",
            (receiver, args, ctx) -> {
                ArrayInstance empty = ctx.getHeapManager().newArray("[Ljava/net/Proxy;", 0);
                return ConcreteValue.reference(empty);
            });
    }

    private void registerWin32ErrorModeHandlers(NativeRegistry registry) {
        registry.register("sun/io/Win32ErrorMode", "setErrorMode", "(J)J",
            (receiver, args, ctx) -> ConcreteValue.longValue(0L));
    }
}
