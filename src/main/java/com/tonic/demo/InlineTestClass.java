package com.tonic.demo;

/**
 * Test class with private helper methods for inlining.
 */
public class InlineTestClass {

    // Public entry point that calls private helper
    public int compute(int x) {
        return double_value(x) + 10;
    }

    // Private static helper - should be inlined
    private static int double_value(int y) {
        return y * 2;
    }

    // Another public method using a different helper
    public int computeSquare(int x) {
        return multiply(x, x);
    }

    // Private static helper for multiplication
    private static int multiply(int a, int b) {
        return a * b;
    }

    // Chain of calls: public -> private -> private
    public int computeComplex(int x) {
        return add_ten(triple(x));
    }

    private static int triple(int x) {
        return x * 3;
    }

    private static int add_ten(int x) {
        return x + 10;
    }

    // Non-static private method (instance)
    public int instanceMethod(int x) {
        return instanceHelper(x);
    }

    private int instanceHelper(int x) {
        return x + 1;
    }

    // Method with multiple calls to same helper
    public int multiCall(int a, int b) {
        return double_value(a) + double_value(b);
    }
}
