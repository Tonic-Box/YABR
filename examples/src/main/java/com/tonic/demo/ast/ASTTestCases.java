package com.tonic.demo.ast;

/**
 * Compiled fixture covering the Java constructs exercised by AST recovery tests.
 */
public class ASTTestCases
{

    // Simple field for field access tests
    private int counter = 0;
    private static String staticField = "hello";

    /**
     * Simple arithmetic method for expression recovery.
     *
     * @param a first operand
     * @param b second operand
     * @return the sum plus the product of both operands
     */
    public int simpleArithmetic(int a, int b)
    {
        int sum = a + b;
        int product = a * b;
        return sum + product;
    }

    /**
     * Method with if-else for control flow recovery.
     *
     * @param x the tested value
     * @return twice x when positive, its negation when negative, 0 otherwise
     */
    public int conditionalLogic(int x)
    {
        if (x > 0)
        {
            return x * 2;
        }
        else if (x < 0)
        {
            return x * -1;
        }
        else
        {
            return 0;
        }
    }

    /**
     * Simple while loop.
     *
     * @param n the exclusive upper bound
     * @return the sum of the integers below the bound
     */
    public int whileLoop(int n)
    {
        int sum = 0;
        int i = 0;
        while (i < n)
        {
            sum = sum + i;
            i = i + 1;
        }
        return sum;
    }

    /**
     * For loop with array access.
     *
     * @param arr the array to total
     * @return the sum of every element
     */
    public int sumArray(int[] arr)
    {
        int sum = 0;
        for (int i = 0; i < arr.length; i++)
        {
            sum = sum + arr[i];
        }
        return sum;
    }

    /**
     * Method call chain.
     *
     * @param input the string to upper-case and trim
     * @return the trimmed upper-case text with its length appended
     */
    public String methodCalls(String input)
    {
        String upper = input.toUpperCase();
        String trimmed = upper.trim();
        int len = trimmed.length();
        return trimmed + len;
    }

    /**
     * Field access and modification.
     *
     * @return three times the incremented counter
     */
    public int fieldAccess()
    {
        counter = counter + 1;
        int local = counter * 2;
        return local + counter;
    }

    /**
     * Static field and method access.
     *
     * @return twice the length of the static field
     */
    public static int staticAccess()
    {
        int len = staticField.length();
        return len * 2;
    }

    /**
     * Object creation.
     *
     * @param initial the seed text of the builder
     * @return a builder holding the seed followed by " world"
     */
    public StringBuilder createObject(String initial)
    {
        StringBuilder sb = new StringBuilder(initial);
        sb.append(" world");
        return sb;
    }

    /**
     * Array creation and initialization.
     *
     * @param size the array length
     * @return an array of that length with its first two slots set to 1 and 2
     */
    public int[] createArray(int size)
    {
        int[] arr = new int[size];
        arr[0] = 1;
        arr[1] = 2;
        return arr;
    }

    /**
     * Nested conditionals.
     *
     * @param a the outer tested value
     * @param b the inner tested value
     * @return the sum or difference when a is positive, 0 otherwise
     */
    public int nestedConditions(int a, int b)
    {
        if (a > 0)
        {
            if (b > 0)
            {
                return a + b;
            }
            else
            {
                return a - b;
            }
        }
        return 0;
    }

    /**
     * Switch statement.
     *
     * @param day the dispatched key
     * @return the weekday name for 1 to 3, "Other" otherwise
     */
    public String switchCase(int day)
    {
        String result;
        switch (day)
        {
            case 1:
                result = "Monday";
                break;
            case 2:
                result = "Tuesday";
                break;
            case 3:
                result = "Wednesday";
                break;
            default:
                result = "Other";
                break;
        }
        return result;
    }

    /**
     * Comparison operators.
     *
     * @param a left comparison operand
     * @param b right comparison operand
     * @return the disjunction of the four comparisons
     */
    public boolean comparisons(int a, int b)
    {
        boolean eq = (a == b);
        boolean neq = (a != b);
        boolean lt = (a < b);
        boolean gt = (a > b);
        return eq || neq || lt || gt;
    }

    /**
     * Bitwise operations.
     *
     * @param a left bitwise operand, also the shift-left source
     * @param b right bitwise operand, also the shift-right source
     * @return the sum of the and, or, xor and both shift results
     */
    public int bitwiseOps(int a, int b)
    {
        int and = a & b;
        int or = a | b;
        int xor = a ^ b;
        int shl = a << 2;
        int shr = b >> 1;
        return and + or + xor + shl + shr;
    }

    /**
     * Type casting.
     *
     * @param i widened to long
     * @param d narrowed to int
     * @return the sum of both converted values
     */
    public long typeCasting(int i, double d)
    {
        long l = (long) i;
        int fromDouble = (int) d;
        return l + fromDouble;
    }
}
