package com.tonic.fixtures;

/**
 * Control flow test fixture for CFG analysis and decompilation tests.
 * Used for: control flow analysis, loop detection, switch handling, dominator tests.
 */
public class ControlFlow {

    // ========== Simple Conditionals ==========

    public static int abs(int x) {
        if (x < 0) {
            return -x;
        } else {
            return x;
        }
    }

    public static int max(int a, int b) {
        if (a > b) {
            return a;
        }
        return b;
    }

    public static int min(int a, int b) {
        if (a < b) {
            return a;
        }
        return b;
    }

    public static int sign(int x) {
        if (x > 0) {
            return 1;
        } else if (x < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    // ========== Nested Conditionals ==========

    public static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        } else {
            return value;
        }
    }

    public static boolean isInRange(int value, int low, int high) {
        if (value >= low) {
            if (value <= high) {
                return true;
            }
        }
        return false;
    }

    // ========== For Loops ==========

    public static int sumToN(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum = sum + i;
        }
        return sum;
    }

    public static int countDown(int n) {
        int count = 0;
        for (int i = n; i > 0; i--) {
            count++;
        }
        return count;
    }

    public static int nestedLoop(int n, int m) {
        int total = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                total++;
            }
        }
        return total;
    }

    // ========== While Loops ==========

    public static int whileSum(int n) {
        int sum = 0;
        int i = 1;
        while (i <= n) {
            sum = sum + i;
            i++;
        }
        return sum;
    }

    public static int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    // ========== Do-While Loops ==========

    public static int doWhileSum(int n) {
        int sum = 0;
        int i = 1;
        do {
            sum = sum + i;
            i++;
        } while (i <= n);
        return sum;
    }

    // ========== Loop Control ==========

    public static int findFirst(int[] arr, int target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public static int sumEvenOnly(int n) {
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            if (i % 2 != 0) {
                continue;
            }
            sum = sum + i;
        }
        return sum;
    }

    public static int sumUntilThreshold(int[] arr, int threshold) {
        int sum = 0;
        for (int value : arr) {
            sum = sum + value;
            if (sum > threshold) {
                break;
            }
        }
        return sum;
    }

    // ========== Switch Statements ==========

    public static String getGrade(int score) {
        switch (score / 10) {
            case 10:
            case 9:
                return "A";
            case 8:
                return "B";
            case 7:
                return "C";
            case 6:
                return "D";
            default:
                return "F";
        }
    }

    public static int dayOfWeek(int day) {
        switch (day) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            case 6:
                return 6;
            case 7:
                return 7;
            default:
                return -1;
        }
    }

    // ========== Complex Control Flow ==========

    public static boolean isPrime(int n) {
        if (n <= 1) {
            return false;
        }
        if (n <= 3) {
            return true;
        }
        if (n % 2 == 0 || n % 3 == 0) {
            return false;
        }
        int i = 5;
        while (i * i <= n) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
            i = i + 6;
        }
        return true;
    }

    public static int fibonacci(int n) {
        if (n <= 0) {
            return 0;
        }
        if (n == 1) {
            return 1;
        }
        int a = 0;
        int b = 1;
        for (int i = 2; i <= n; i++) {
            int temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    // ========== Ternary Operator ==========

    public static int ternaryMax(int a, int b) {
        return a > b ? a : b;
    }

    public static int ternaryAbs(int x) {
        return x < 0 ? -x : x;
    }

    // ========== Short-Circuit Evaluation ==========

    public static boolean andShortCircuit(int a, int b) {
        return a > 0 && b > 0;
    }

    public static boolean orShortCircuit(int a, int b) {
        return a > 0 || b > 0;
    }

    public static boolean complexCondition(int a, int b, int c) {
        return (a > 0 && b > 0) || (c > 0);
    }
}
