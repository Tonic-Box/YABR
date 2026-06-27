package com.tonic.fixtures;

/**
 * Math operations test fixture for SSA optimization tests.
 * Used for: constant folding, strength reduction, algebraic simplification, loop optimization.
 */
public class MathOperations {

    // ========== Basic Arithmetic ==========

    public static int add(int a, int b) {
        return a + b;
    }

    public static int subtract(int a, int b) {
        return a - b;
    }

    public static int multiply(int a, int b) {
        return a * b;
    }

    public static int divide(int a, int b) {
        return a / b;
    }

    public static int remainder(int a, int b) {
        return a % b;
    }

    public static int negate(int a) {
        return -a;
    }

    // ========== Constant Folding Candidates ==========

    public static int constantAdd() {
        return 10 + 20;
    }

    public static int constantMultiply() {
        return 5 * 6;
    }

    public static int nestedConstants() {
        int x = 2 + 3;
        int y = x * 4;
        return y + 5;
    }

    // ========== Algebraic Simplification Candidates ==========

    public static int addZero(int x) {
        return x + 0;
    }

    public static int subtractZero(int x) {
        return x - 0;
    }

    public static int multiplyOne(int x) {
        return x * 1;
    }

    public static int multiplyZero(int x) {
        return x * 0;
    }

    public static int subtractSelf(int x) {
        return x - x;
    }

    public static int xorSelf(int x) {
        return x ^ x;
    }

    public static int andZero(int x) {
        return x & 0;
    }

    public static int orZero(int x) {
        return x | 0;
    }

    // ========== Strength Reduction Candidates ==========

    public static int multiplyByTwo(int x) {
        return x * 2;
    }

    public static int multiplyByFour(int x) {
        return x * 4;
    }

    public static int multiplyByEight(int x) {
        return x * 8;
    }

    public static int divideByTwo(int x) {
        return x / 2;
    }

    public static int divideByFour(int x) {
        return x / 4;
    }

    public static int moduloEight(int x) {
        return x % 8;
    }

    // ========== Loop Optimization Candidates ==========

    public static int factorial(int n) {
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result = result * i;
        }
        return result;
    }

    public static int sumToN(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum = sum + i;
        }
        return sum;
    }

    public static int loopWithInvariant(int n, int k) {
        int sum = 0;
        int invariant = k * 2;
        for (int i = 0; i < n; i++) {
            sum = sum + invariant;
        }
        return sum;
    }

    // ========== Long Operations ==========

    public static long addLong(long a, long b) {
        return a + b;
    }

    public static long multiplyLong(long a, long b) {
        return a * b;
    }

    // ========== Bitwise Operations ==========

    public static int bitwiseAnd(int a, int b) {
        return a & b;
    }

    public static int bitwiseOr(int a, int b) {
        return a | b;
    }

    public static int bitwiseXor(int a, int b) {
        return a ^ b;
    }

    public static int shiftLeft(int x, int n) {
        return x << n;
    }

    public static int shiftRight(int x, int n) {
        return x >> n;
    }

    public static int unsignedShiftRight(int x, int n) {
        return x >>> n;
    }

    // ========== Combined Operations ==========

    public static int quadratic(int x, int a, int b, int c) {
        return a * x * x + b * x + c;
    }

    public static int complexExpression(int a, int b, int c) {
        int t1 = a + b;
        int t2 = t1 * c;
        int t3 = t2 - a;
        return t3 / b;
    }

    // ========== Common Subexpression Candidates ==========

    public static int commonSubexpression(int a, int b) {
        int x = a + b;
        int y = a + b;
        return x * y;
    }

    public static int repeatedComputation(int a, int b, int c) {
        int x = (a * b) + c;
        int y = (a * b) - c;
        return x + y;
    }
}
