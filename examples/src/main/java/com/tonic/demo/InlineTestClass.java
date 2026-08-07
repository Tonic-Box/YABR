package com.tonic.demo;

/**
 * Demo fixture whose public methods call private helpers, used as input for method inlining.
 */
public class InlineTestClass
{

    /**
     * Doubles the input via a private helper and adds ten.
     * @param x the input value
     * @return x * 2 + 10
     */
    public int compute(int x)
    {
        return double_value(x) + 10;
    }

    // Private static helper - should be inlined
    private static int double_value(int y)
    {
        return y * 2;
    }

    /**
     * Squares the input via a private multiply helper.
     * @param x the input value
     * @return x * x
     */
    public int computeSquare(int x)
    {
        return multiply(x, x);
    }

    // Private static helper for multiplication
    private static int multiply(int a, int b)
    {
        return a * b;
    }

    /**
     * Triples the input then adds ten through a chain of private helpers.
     * @param x the input value
     * @return x * 3 + 10
     */
    public int computeComplex(int x)
    {
        return add_ten(triple(x));
    }

    private static int triple(int x)
    {
        return x * 3;
    }

    private static int add_ten(int x)
    {
        return x + 10;
    }

    /**
     * Increments the input via a private instance helper.
     * @param x the input value
     * @return x + 1
     */
    public int instanceMethod(int x)
    {
        return instanceHelper(x);
    }

    private int instanceHelper(int x)
    {
        return x + 1;
    }

    /**
     * Doubles both inputs via the same private helper and sums the results.
     * @param a the first value
     * @param b the second value
     * @return a * 2 + b * 2
     */
    public int multiCall(int a, int b)
    {
        return double_value(a) + double_value(b);
    }
}
