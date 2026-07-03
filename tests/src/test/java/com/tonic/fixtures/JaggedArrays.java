package com.tonic.fixtures;

/**
 * Fixture: reference array loads whose precise element type matters (jagged int[][] and an
 * object array). Regression shape for aaload results being typed as bare Object.
 */
public class JaggedArrays {

    public static int sum(int[][] m) {
        return m[0][1] + m[1].length;
    }

    public static String first(String[] names) {
        return names[0];
    }
}
