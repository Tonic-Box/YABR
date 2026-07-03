package com.tonic.fixtures;

/**
 * Fixture: string concatenation compiled to invokedynamic makeConcatWithConstants.
 */
public class Concats {

    public static String tag(int i, String s) {
        return i + ":" + s;
    }
}
