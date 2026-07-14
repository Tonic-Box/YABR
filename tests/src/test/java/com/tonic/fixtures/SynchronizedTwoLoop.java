package com.tonic.fixtures;

public class SynchronizedTwoLoop {
    static final Object LOCK = new Object();

    public static Object find(Class<?> type, Object[] a, Object[] b) {
        synchronized (LOCK) {
            for (int i = 0; i < a.length; i++) {
                Object x = a[i];
                if (type.isInstance(x)) {
                    return x;
                }
            }
            for (int i = 0; i < b.length; i++) {
                Object x = b[i];
                if (type.isInstance(x)) {
                    return x;
                }
            }
        }
        return null;
    }
}
