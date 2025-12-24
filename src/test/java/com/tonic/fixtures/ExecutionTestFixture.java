package com.tonic.fixtures;

public class ExecutionTestFixture {

    // ========== 1. Simple Recursion ==========

    public static int factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }

    public static int fibonacci(int n) {
        if (n <= 1) return n;
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    public static int sumDigits(int n) {
        if (n == 0) return 0;
        return (n % 10) + sumDigits(n / 10);
    }

    // ========== 2. Mutual Recursion ==========

    public static boolean isEven(int n) {
        if (n == 0) return true;
        return isOdd(n - 1);
    }

    public static boolean isOdd(int n) {
        if (n == 0) return false;
        return isEven(n - 1);
    }

    public static int maleSequence(int n) {
        if (n == 0) return 0;
        return n - femaleSequence(maleSequence(n - 1));
    }

    public static int femaleSequence(int n) {
        if (n == 0) return 1;
        return n - maleSequence(femaleSequence(n - 1));
    }

    // ========== 3. Multi-Method Chains ==========

    public static int chainedComputation(int a, int b) {
        return processStep1(a, b);
    }

    private static int processStep1(int a, int b) {
        return processStep2(a + b, a * b);
    }

    private static int processStep2(int sum, int product) {
        return processStep3(sum, product, sum - product);
    }

    private static int processStep3(int x, int y, int z) {
        return x + y + z;
    }

    public static long mixedChain(int a, long b, double c) {
        return step1Long(a, b) + (long) step2Double(c);
    }

    private static long step1Long(int a, long b) {
        return a + b;
    }

    private static double step2Double(double c) {
        return c * 2.0;
    }

    // ========== 4. Complex Primitive Operations ==========

    public static double primitiveOrchestra(int i, long l, float f, double d) {
        int sum = i + (int) l;
        long product = l * i;
        float fResult = f + (float) sum;
        double dResult = d + fResult + product;
        return dResult;
    }

    public static int bitwiseMagic(int a, int b) {
        int and = a & b;
        int or = a | b;
        int xor = a ^ b;
        int shifted = (a << 2) | (b >> 1);
        return and + or + xor + shifted;
    }

    public static int compareChain(int a, int b, int c) {
        if (a > b) {
            if (b > c) return a + b + c;
            else return a - b + c;
        } else {
            if (a > c) return a * 2;
            else return c * 2;
        }
    }

    // ========== 5. Loop + Recursion Hybrid ==========

    public static int factorialIterative(int n) {
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    public static int ackermann(int m, int n) {
        if (m == 0) return n + 1;
        if (n == 0) return ackermann(m - 1, 1);
        return ackermann(m - 1, ackermann(m, n - 1));
    }

    // ========== 6. Array Operations ==========

    public static int sumArray(int[] arr) {
        int sum = 0;
        for (int i = 0; i < arr.length; i++) {
            sum += arr[i];
        }
        return sum;
    }

    public static int createAndSum(int a, int b, int c) {
        int[] arr = new int[]{a, b, c};
        return sumArray(arr);
    }

    public static int findMax(int[] arr) {
        if (arr.length == 0) return Integer.MIN_VALUE;
        int max = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) max = arr[i];
        }
        return max;
    }

    public static int sum2DArray(int[][] matrix) {
        int sum = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                sum += matrix[i][j];
            }
        }
        return sum;
    }

    public static int binarySearch(int[] arr, int target, int low, int high) {
        if (low > high) return -1;
        int mid = (low + high) / 2;
        if (arr[mid] == target) return mid;
        if (arr[mid] > target) return binarySearch(arr, target, low, mid - 1);
        return binarySearch(arr, target, mid + 1, high);
    }

    // ========== 7. Exception Handling ==========

    public static int safeDivide(int a, int b) {
        try {
            return divideOrThrow(a, b);
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    public static int divideOrThrow(int a, int b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        return a / b;
    }

    public static int tryFinallyCounter(int n) {
        int result = 0;
        try {
            result = n * 2;
            return result;
        } finally {
            result += 1;
        }
    }

    public static int nestedTryCatch(int a, int b, int c) {
        try {
            try {
                return divideOrThrow(a, divideOrThrow(b, c));
            } catch (ArithmeticException inner) {
                return divideOrThrow(a, 1);
            }
        } catch (ArithmeticException outer) {
            return 0;
        }
    }

    public static int recursiveWithException(int n, int limit) {
        if (n > limit) throw new IllegalArgumentException("Exceeded limit");
        if (n <= 0) return 0;
        return n + recursiveWithException(n - 1, limit);
    }

    public static int safeRecursiveSum(int n, int limit) {
        try {
            return recursiveWithException(n, limit);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }
}
