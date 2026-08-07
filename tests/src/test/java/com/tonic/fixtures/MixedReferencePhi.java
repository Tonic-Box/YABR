package com.tonic.fixtures;

public class MixedReferencePhi
{
    public static Object pick(Object o, boolean flag)
    {
        return flag ? "literal" : o;
    }
}
