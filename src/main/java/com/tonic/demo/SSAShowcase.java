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
    // Strength Reduction Examples
    // ========================================

    /**
     * Demonstrates strength reduction for multiplication by powers of 2.
     * x * 2 -> x << 1
     * x * 4 -> x << 2
     * x * 8 -> x << 3
     */
    public int strengthReductionMul(int x) {
        int a = x * 2;    // Should become x << 1
        int b = x * 4;    // Should become x << 2
        int c = x * 8;    // Should become x << 3
        return a + b + c; // x*2 + x*4 + x*8 = x*14
    }

    /**
     * Demonstrates strength reduction for division by powers of 2.
     * x / 2 -> x >> 1
     * x / 4 -> x >> 2
     */
    public int strengthReductionDiv(int x) {
        int a = x / 2;    // Should become x >> 1
        int b = x / 4;    // Should become x >> 2
        return a + b;
    }

    /**
     * Demonstrates strength reduction for modulo by powers of 2.
     * x % 2 -> x & 1
     * x % 8 -> x & 7
     */
    public int strengthReductionMod(int x) {
        int a = x % 2;    // Should become x & 1
        int b = x % 8;    // Should become x & 7
        return a + b;
    }

    // ========================================
    // Algebraic Simplification Examples
    // ========================================

    /**
     * Demonstrates algebraic simplification with addition/subtraction.
     * x + 0 -> x
     * x - 0 -> x
     */
    public int algebraicAddSub(int x) {
        int a = x + 0;    // Should become x
        int b = a - 0;    // Should become a (which is x)
        return b;
    }

    /**
     * Demonstrates algebraic simplification with multiplication.
     * x * 1 -> x
     * x * 0 -> 0
     */
    public int algebraicMul(int x, int y) {
        int a = x * 1;    // Should become x
        int b = y * 0;    // Should become 0
        return a + b;     // Should simplify to just x
    }

    /**
     * Demonstrates algebraic simplification with bitwise operations.
     * x & 0 -> 0
     * x | 0 -> x
     * x ^ 0 -> x
     */
    public int algebraicBitwise(int x) {
        int a = x & 0;    // Should become 0
        int b = x | 0;    // Should become x
        int c = x ^ 0;    // Should become x
        return a + b + c; // Should become 0 + x + x = 2*x
    }

    /**
     * Demonstrates algebraic simplification with self-operations.
     * x - x -> 0
     * x ^ x -> 0
     * x & x -> x
     * x | x -> x
     */
    public int algebraicSelfOps(int x) {
        int a = x - x;    // Should become 0
        int b = x ^ x;    // Should become 0
        int c = x & x;    // Should become x
        int d = x | x;    // Should become x
        return a + b + c + d; // Should become 0 + 0 + x + x = 2*x
    }

    /**
     * Combined strength reduction and algebraic simplification.
     */
    public int combinedNewOptimizations(int x) {
        int a = x * 8;      // Strength reduction: x << 3
        int b = a + 0;      // Algebraic: a
        int c = b * 1;      // Algebraic: b
        int d = c / 2;      // Strength reduction: c >> 1
        int e = x - x;      // Algebraic: 0
        return d + e;       // Should be (x << 3) >> 1 + 0 = x * 4
    }

    // ========================================
    // Phi Constant Propagation Tests
    // ========================================

    /**
     * Demonstrates phi constant propagation.
     * When all branches assign the same value, phi can be simplified.
     */
    public int phiConstantProp(int x) {
        int result;
        if (x > 0) {
            result = 42;
        } else {
            result = 42;  // Same value as true branch
        }
        // phi(42, 42) -> 42
        return result;
    }

    // ========================================
    // Peephole Optimization Tests
    // ========================================

    /**
     * Demonstrates peephole optimizations.
     * Double negation and shift normalization.
     */
    public int peepholeOpt(int x) {
        int a = -(-x);        // Double negation -> x
        int b = x << 32;      // Shift by 32 -> x (masked to 0)
        return a + b;         // Should be x + x = 2*x
    }

    // ========================================
    // Common Subexpression Elimination Tests
    // ========================================

    /**
     * Demonstrates common subexpression elimination.
     * Identical expressions are computed only once.
     */
    public int commonSubexpr(int x, int y) {
        int a = x + y;
        int b = x + y;        // Same as 'a' - reuse
        int c = x * y;
        int d = x * y;        // Same as 'c' - reuse
        return a + b + c + d; // = 2*(x+y) + 2*(x*y)
    }

    // ========================================
    // Null Check Elimination Tests
    // ========================================

    /**
     * Demonstrates null check elimination concept using integer comparison.
     * Uses pattern similar to null check but with primitives for lifter compatibility.
     * Tests redundant check elimination after confirmed state.
     */
    public int nullCheckTest(int flag) {
        int isValid = 1;      // Simulate "not null" state
        if (isValid != 0) {   // Check that's always true
            if (isValid != 0) { // Redundant check - can be eliminated
                return flag + 1;
            }
        }
        return 0;             // Dead code - never reached
    }

    // ========================================
    // Conditional Constant Propagation Tests
    // ========================================

    /**
     * Demonstrates conditional constant propagation.
     * When condition is known constant, branch can be eliminated.
     */
    public int conditionalConst(int x) {
        int a = 5;
        int b = 5;
        if (a == b) {         // Always true (5 == 5)
            return x + 1;
        } else {
            return x - 1;     // Dead code
        }
    }

    // ========================================
    // Loop-Invariant Code Motion Tests
    // ========================================

    /**
     * Demonstrates loop-invariant concept using simple addition.
     * This pattern works correctly through lift/lower.
     */
    public int loopInvariant(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += 12;        // Constant addition per iteration
        }
        return sum;           // = n * 12
    }

    // ========================================
    // Induction Variable Tests
    // ========================================

    /**
     * Demonstrates induction variable simplification.
     * Multiplication by constant in loop can be strength-reduced.
     */
    public int inductionVar(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i * 4;     // i*4 can be optimized with accumulator
        }
        return sum;           // = 4 * (0 + 1 + 2 + ... + (n-1)) = 4 * n*(n-1)/2
    }

    // ========================================
    // Reassociation Tests
    // ========================================

    /**
     * Demonstrates reassociation for constant grouping.
     * (x + 5) + 10 should reassociate to x + (5 + 10) = x + 15
     */
    public int reassociateConstants(int x) {
        int a = x + 5;
        int b = a + 10;       // Should become x + 15 after reassociate + fold
        return b;
    }

    /**
     * Demonstrates reassociation with multiplication.
     * (x * 2) * 4 should reassociate to x * (2 * 4) = x * 8
     */
    public int reassociateMul(int x) {
        int a = x * 2;
        int b = a * 4;        // Should become x * 8 after reassociate + fold
        return b;
    }

    /**
     * Demonstrates reassociation with multiple variables.
     * Groups constants together: (x + 3) + y + 7 -> x + y + 10
     */
    public int reassociateMultiVar(int x, int y) {
        int a = x + 3;
        int b = a + y;
        int c = b + 7;        // Constants 3 and 7 should group to 10
        return c;
    }

    // ========================================
    // Loop Predication Tests
    // ========================================

    /**
     * Demonstrates loop predication with constant limit.
     * Guard i < 100 is always true when n <= 100.
     */
    public int loopPredicationSimple(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            if (i < 100) {    // Guard: can be predicated if n <= 100
                sum += i;
            }
        }
        return sum;
    }

    /**
     * Demonstrates loop predication with redundant guard.
     * Guard i < len is always true when loop bound is i < len.
     */
    public int loopPredicationRedundant(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            if (i < n) {      // Redundant guard - always true
                sum += i * 2;
            }
        }
        return sum;
    }

    /**
     * Demonstrates loop predication with separate limit.
     * Guard can be predicated when n <= limit.
     */
    public int loopPredicationLimit(int n, int limit) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            if (i < limit) {  // Can predicate if n <= limit
                sum += i;
            }
        }
        return sum;
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

        // Strength Reduction tests
        System.out.println("=== Strength Reduction Tests ===");
        System.out.println("strengthReductionMul(10) = " + showcase.strengthReductionMul(10) + " (expected: 140)");
        System.out.println("strengthReductionDiv(100) = " + showcase.strengthReductionDiv(100) + " (expected: 75)");
        System.out.println("strengthReductionMod(100) = " + showcase.strengthReductionMod(100) + " (expected: 4)");
        System.out.println();

        // Algebraic Simplification tests
        System.out.println("=== Algebraic Simplification Tests ===");
        System.out.println("algebraicAddSub(42) = " + showcase.algebraicAddSub(42) + " (expected: 42)");
        System.out.println("algebraicMul(42, 99) = " + showcase.algebraicMul(42, 99) + " (expected: 42)");
        System.out.println("algebraicBitwise(10) = " + showcase.algebraicBitwise(10) + " (expected: 20)");
        System.out.println("algebraicSelfOps(15) = " + showcase.algebraicSelfOps(15) + " (expected: 30)");
        System.out.println();

        // Combined new optimizations
        System.out.println("=== Combined New Optimizations ===");
        System.out.println("combinedNewOptimizations(10) = " + showcase.combinedNewOptimizations(10) + " (expected: 40)");
        System.out.println();

        // New optimization tests
        System.out.println("=== Phi Constant Propagation ===");
        System.out.println("phiConstantProp(5) = " + showcase.phiConstantProp(5) + " (expected: 42)");
        System.out.println("phiConstantProp(-3) = " + showcase.phiConstantProp(-3) + " (expected: 42)");
        System.out.println();

        System.out.println("=== Peephole Optimizations ===");
        System.out.println("peepholeOpt(10) = " + showcase.peepholeOpt(10) + " (expected: 20)");
        System.out.println();

        System.out.println("=== Common Subexpression Elimination ===");
        System.out.println("commonSubexpr(3, 4) = " + showcase.commonSubexpr(3, 4) + " (expected: 38)");
        System.out.println();

        System.out.println("=== Null Check Elimination ===");
        System.out.println("nullCheckTest(5) = " + showcase.nullCheckTest(5) + " (expected: 6)");
        System.out.println();

        System.out.println("=== Conditional Constant Propagation ===");
        System.out.println("conditionalConst(10) = " + showcase.conditionalConst(10) + " (expected: 11)");
        System.out.println();

        System.out.println("=== Loop-Invariant Code Motion ===");
        System.out.println("loopInvariant(5) = " + showcase.loopInvariant(5) + " (expected: 60)");
        System.out.println();

        System.out.println("=== Induction Variable Simplification ===");
        System.out.println("inductionVar(5) = " + showcase.inductionVar(5) + " (expected: 40)");
        System.out.println();

        System.out.println("=== Reassociation ===");
        System.out.println("reassociateConstants(5) = " + showcase.reassociateConstants(5) + " (expected: 20)");
        System.out.println("reassociateMul(3) = " + showcase.reassociateMul(3) + " (expected: 24)");
        System.out.println("reassociateMultiVar(2, 3) = " + showcase.reassociateMultiVar(2, 3) + " (expected: 15)");
        System.out.println();

        System.out.println("=== Loop Predication ===");
        System.out.println("loopPredicationSimple(10) = " + showcase.loopPredicationSimple(10) + " (expected: 45)");
        System.out.println("loopPredicationRedundant(5) = " + showcase.loopPredicationRedundant(5) + " (expected: 20)");
        System.out.println("loopPredicationLimit(5, 10) = " + showcase.loopPredicationLimit(5, 10) + " (expected: 10)");
        System.out.println();

        System.out.println("=== All tests complete ===");
    }
}
