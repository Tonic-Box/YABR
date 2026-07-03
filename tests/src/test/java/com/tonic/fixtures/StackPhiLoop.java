package com.tonic.fixtures;

/**
 * Fixture: a ternary whose value merges on the operand stack inside a do-while loop.
 * Regression shape for stack-phi incoming corruption during lift fixup.
 */
public class StackPhiLoop {

    public static int alternatingSum(int n) {
        int k = 0;
        int acc = 0;
        do {
            acc += (k % 2 == 0) ? k : -k;
            k++;
        } while (k < n);
        return acc;
    }
}
