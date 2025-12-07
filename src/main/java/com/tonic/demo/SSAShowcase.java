package com.tonic.demo;

/**
 * A showcase class designed to demonstrate SSA IR transformations.
 * Each method targets specific optimization opportunities.
 */
public class SSAShowcase {

    // ========================================
    // Constant Folding Examples
    // ========================================

    /**
     * Demonstrates constant folding - all arithmetic can be computed at compile time.
     * Expected: Constants folded to final value, dead stores eliminated.
     */
    public int constantFolding() {
        int a = 10;
        int b = 20;
        int c = a + b;        // Should fold to 30
        int d = c * 2;        // Should fold to 60
        int e = d - 10;       // Should fold to 50
        return e;             // Should just return 50
    }

    /**
     * Demonstrates partial constant folding with a parameter.
     * Expected: Constants folded where possible, parameter operations preserved.
     */
    public int partialConstantFolding(int x) {
        int a = 5 + 5;        // Folds to 10
        int b = a * 2;        // Folds to 20
        int c = x + b;        // Cannot fold - depends on x
        return c;
    }

    // ========================================
    // Copy Propagation Examples
    // ========================================

    /**
     * Demonstrates copy propagation - intermediate copies should be eliminated.
     * Expected: Direct use of original values, copy assignments removed.
     */
    public int copyPropagation(int input) {
        int a = input;
        int b = a;            // Copy of a
        int c = b;            // Copy of b (transitively a)
        int d = c + 1;        // Should use input directly
        return d;
    }

    /**
     * Demonstrates copy propagation with multiple uses.
     */
    public int multiUseCopyPropagation(int x, int y) {
        int temp = x;
        int result = temp + y;
        int doubled = temp * 2;
        return result + doubled;
    }

    // ========================================
    // Dead Code Elimination Examples
    // ========================================

    /**
     * Demonstrates dead code elimination - unused computations removed.
     * Expected: Only the return value computation should remain.
     */
    public int deadCodeElimination(int x) {
        int unused1 = x * 100;     // Dead - never used
        int unused2 = unused1 + 5; // Dead - never used
        int used = x + 1;          // Live - returned
        int unused3 = used * 2;    // Dead - computed but not used
        return used;
    }

    /**
     * Demonstrates dead store elimination.
     */
    public int deadStoreElimination(int x) {
        int a = 10;           // Dead store - overwritten
        a = 20;               // Dead store - overwritten
        a = x + 5;            // Live - used in return
        return a;
    }

    // ========================================
    // Control Flow Examples
    // ========================================

    /**
     * Simple if-else demonstrating basic block structure.
     */
    public int simpleConditional(int x) {
        int result;
        if (x > 0) {
            result = x * 2;
        } else {
            result = x * -1;
        }
        return result;
    }

    /**
     * Nested conditionals demonstrating complex CFG.
     */
    public int nestedConditional(int x, int y) {
        int result = 0;
        if (x > 0) {
            if (y > 0) {
                result = x + y;
            } else {
                result = x - y;
            }
        } else {
            result = y;
        }
        return result;
    }

    // ========================================
    // Loop Examples
    // ========================================

    /**
     * Simple counted loop demonstrating phi functions and loop analysis.
     */
    public int simpleLoop(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum = sum + i;
        }
        return sum;
    }

    /**
     * While loop with early exit.
     */
    public int whileLoopWithBreak(int x) {
        int count = 0;
        while (x > 0) {
            count++;
            x = x - 1;
            if (count > 100) {
                break;
            }
        }
        return count;
    }

    // ========================================
    // Method Invocation Examples
    // ========================================

    /**
     * Virtual method calls.
     */
    public String virtualCalls(String s) {
        int len = s.length();
        String upper = s.toUpperCase();
        String trimmed = upper.trim();
        return trimmed;
    }

    /**
     * Static method calls with primitive operations.
     */
    public int staticCalls(int x) {
        int abs = Math.abs(x);
        int max = Math.max(abs, 10);
        int min = Math.min(max, 100);
        return min;
    }

    // ========================================
    // Field Access Examples
    // ========================================

    private int instanceField = 42;
    private static int staticField = 100;

    /**
     * Instance field access.
     */
    public int fieldAccess() {
        int a = this.instanceField;
        int b = a + 10;
        this.instanceField = b;
        return this.instanceField;
    }

    /**
     * Static field access.
     */
    public static int staticFieldAccess() {
        int a = staticField;
        int b = a * 2;
        staticField = b;
        return staticField;
    }

    // ========================================
    // Array Examples
    // ========================================

    /**
     * Array operations.
     */
    public int arrayOperations(int[] arr) {
        int first = arr[0];
        int second = arr[1];
        arr[2] = first + second;
        return arr[2];
    }

    /**
     * Array creation and initialization.
     */
    public int[] createArray(int size) {
        int[] result = new int[size];
        result[0] = 1;
        result[1] = 2;
        return result;
    }

    // ========================================
    // Combined Optimization Example
    // ========================================

    /**
     * A method combining multiple optimization opportunities.
     * This should showcase constant folding, copy propagation, and dead code elimination together.
     */
    public int combinedOptimizations(int input) {
        // Constant folding opportunity
        int constant = 10 + 20;       // Folds to 30

        // Copy propagation opportunity
        int copy1 = input;
        int copy2 = copy1;

        // Dead code
        int dead = copy2 * 100;       // Not used

        // Actual computation using propagated copy
        int result = copy2 + constant; // Should become: input + 30

        // More dead code
        int alsoDead = result * 2;    // Not used

        return result;
    }

    // ========================================
    // Object Creation Example
    // ========================================

    /**
     * Object instantiation and constructor calls.
     */
    public SSAShowcase createInstance() {
        SSAShowcase obj = new SSAShowcase();
        return obj;
    }

    // ========================================
    // Simple Arithmetic Example
    // ========================================

    /**
     * Basic arithmetic operations for clean SSA demonstration.
     */
    public int arithmetic(int a, int b) {
        int sum = a + b;
        int diff = a - b;
        int product = sum * diff;
        return product;
    }

    // ========================================
    // Main Method for Testing
    // ========================================

    public static void main(String[] args) {
        SSAShowcase showcase = new SSAShowcase();

        System.out.println("=== SSAShowcase Test Results ===");
        System.out.println();

        // Constant folding
        System.out.println("constantFolding() = " + showcase.constantFolding() + " (expected: 50)");
        System.out.println("partialConstantFolding(5) = " + showcase.partialConstantFolding(5) + " (expected: 25)");
        System.out.println();

        // Copy propagation
        System.out.println("copyPropagation(10) = " + showcase.copyPropagation(10) + " (expected: 11)");
        System.out.println("multiUseCopyPropagation(3, 4) = " + showcase.multiUseCopyPropagation(3, 4) + " (expected: 13)");
        System.out.println();

        // Dead code elimination
        System.out.println("deadCodeElimination(5) = " + showcase.deadCodeElimination(5) + " (expected: 6)");
        System.out.println("deadStoreElimination(7) = " + showcase.deadStoreElimination(7) + " (expected: 12)");
        System.out.println();

        // Control flow
        System.out.println("simpleConditional(5) = " + showcase.simpleConditional(5) + " (expected: 10)");
        System.out.println("simpleConditional(-3) = " + showcase.simpleConditional(-3) + " (expected: 3)");
        System.out.println("nestedConditional(5, 3) = " + showcase.nestedConditional(5, 3) + " (expected: 8)");
        System.out.println();

        // Loops
        System.out.println("simpleLoop(5) = " + showcase.simpleLoop(5) + " (expected: 10)");
        System.out.println("simpleLoop(10) = " + showcase.simpleLoop(10) + " (expected: 45)");
        System.out.println("whileLoopWithBreak(5) = " + showcase.whileLoopWithBreak(5) + " (expected: 5)");
        System.out.println();

        // Arithmetic
        System.out.println("arithmetic(7, 3) = " + showcase.arithmetic(7, 3) + " (expected: 40)");
        System.out.println();

        // Static calls
        System.out.println("staticCalls(-15) = " + showcase.staticCalls(-15) + " (expected: 15)");
        System.out.println("staticFieldAccess() = " + SSAShowcase.staticFieldAccess() + " (expected: 200)");
        System.out.println();

        System.out.println("=== All tests complete ===");
    }
}
