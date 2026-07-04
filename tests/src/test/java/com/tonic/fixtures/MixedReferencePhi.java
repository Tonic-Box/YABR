package com.tonic.fixtures;

public class MixedReferencePhi {
    public static Object pick(Object o, boolean flag) {
        Object r = flag ? "literal" : o;
        return r;
    }
}
