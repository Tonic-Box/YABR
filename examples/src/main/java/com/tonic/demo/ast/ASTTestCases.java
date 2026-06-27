package com.tonic.demo.ast;

/**
 * Test cases class that will be compiled and used for AST recovery testing.
 * Contains various Java constructs to test the source-level AST layer.
 */
public class ASTTestCases {

    // Simple field for field access tests
    private int counter = 0;
    private static String staticField = "hello";

    /**
     * Simple arithmetic method for expression recovery.
     */
    public int simpleArithmetic(int a, int b) {
        int sum = a + b;
        int product = a * b;
        return sum + product;
    }

    /**
     * Method with if-else for control flow recovery.
     */
    public int conditionalLogic(int x) {
        if (x > 0) {
            return x * 2;
        } else if (x < 0) {
            return x * -1;
        } else {
            return 0;
        }
    }

    /**
     * Simple while loop.
     */
    public int whileLoop(int n) {
        int sum = 0;
        int i = 0;
        while (i < n) {
            sum = sum + i;
            i = i + 1;
        }
        return sum;
    }

    /**
     * For loop with array access.
     */
    public int sumArray(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum = sum + arr[i];
        }
        return sum;
    }

    /**
     * Method call chain.
     */
    public String methodCalls(String input) {
        String upper = input.toUpperCase();
        String trimmed = upper.trim();
        int len = trimmed.length();
        return trimmed + len;
    }

    /**
     * Field access and modification.
     */
    public int fieldAccess() {
        counter = counter + 1;
        int local = counter * 2;
        return local + counter;
    }

    /**
     * Static field and method access.
     */
    public static int staticAccess() {
        int len = staticField.length();
        return len * 2;
    }

    /**
     * Object creation.
     */
    public StringBuilder createObject(String initial) {
        StringBuilder sb = new StringBuilder(initial);
        sb.append(" world");
        return sb;
    }

    /**
     * Array creation and initialization.
     */
    public int[] createArray(int size) {
        int[] arr = new int[size];
        arr[0] = 1;
        arr[1] = 2;
        return arr;
    }

    /**
     * Nested conditionals.
     */
    public int nestedConditions(int a, int b) {
        if (a > 0) {
            if (b > 0) {
                return a + b;
            } else {
                return a - b;
            }
        }
        return 0;
    }

    /**
     * Switch statement.
     */
    public String switchCase(int day) {
        String result;
        switch (day) {
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
     */
    public boolean comparisons(int a, int b) {
        boolean eq = (a == b);
        boolean neq = (a != b);
        boolean lt = (a < b);
        boolean gt = (a > b);
        return eq || neq || lt || gt;
    }

    /**
     * Bitwise operations.
     */
    public int bitwiseOps(int a, int b) {
        int and = a & b;
        int or = a | b;
        int xor = a ^ b;
        int shl = a << 2;
        int shr = b >> 1;
        return and + or + xor + shl + shr;
    }

    /**
     * Type casting.
     */
    public long typeCasting(int i, double d) {
        long l = (long) i;
        int fromDouble = (int) d;
        return l + fromDouble;
    }
}
