package com.tonic.demo;

/**
 * Sample methods, one per SSA IR optimization opportunity.
 */
public class SSAShowcase
{

    // Constant Folding Examples

    /**
     * Folds a chain of integer constants down to one value.
     *
     * @return 50
     */
    public int constantFolding()
    {
        int a = 10;
        int b = 20;
        int c = a + b;        // Should fold to 30
        int d = c * 2;        // Should fold to 60
        int e = d - 10;       // Should fold to 50
        return e;             // Should just return 50
    }

    /**
     * Mixes foldable constant arithmetic with an add that depends on a parameter.
     *
     * @param x addend that blocks folding
     * @return x plus 20
     */
    public int partialConstantFolding(int x)
    {
        int a = 5 + 5;        // Folds to 10
        int b = a * 2;        // Folds to 20
        int c = x + b;        // Cannot fold - depends on x
        return c;
    }

    // Copy Propagation Examples

    /**
     * Chains three copies ahead of the only real computation.
     *
     * @param input value copied through each intermediate local
     * @return input plus 1
     */
    public int copyPropagation(int input)
    {
        int a = input;
        int b = a;            // Copy of a
        int c = b;            // Copy of b (transitively a)
        int d = c + 1;        // Should use input directly
        return d;
    }

    /**
     * Reads one copy twice so propagation has to handle multiple uses.
     *
     * @param x value copied into the reused local
     * @param y addend
     * @return x + y + x * 2
     */
    public int multiUseCopyPropagation(int x, int y)
    {
        int temp = x;
        int result = temp + y;
        int doubled = temp * 2;
        return result + doubled;
    }

    // Dead Code Elimination Examples

    /**
     * Surrounds one live computation with three unused ones.
     *
     * @param x source of both the live and the dead values
     * @return x plus 1
     */
    public int deadCodeElimination(int x)
    {
        int unused1 = x * 100;     // Dead - never used
        int unused2 = unused1 + 5; // Dead - never used
        int used = x + 1;          // Live - returned
        int unused3 = used * 2;    // Dead - computed but not used
        return used;
    }

    /**
     * Overwrites a local twice before the store that survives.
     *
     * @param x addend of the live store
     * @return x plus 5
     */
    public int deadStoreElimination(int x)
    {
        int a = 10;           // Dead store - overwritten
        a = 20;               // Dead store - overwritten
        a = x + 5;            // Live - used in return
        return a;
    }

    // Control Flow Examples

    /**
     * Assigns the result from both arms of an if-else.
     *
     * @param x value tested against zero and scaled
     * @return x doubled when positive, otherwise x negated
     */
    public int simpleConditional(int x)
    {
        int result;
        if (x > 0)
        {
            result = x * 2;
        }
        else
        {
            result = x * -1;
        }
        return result;
    }

    /**
     * Nests an if-else inside an if-else to widen the control flow graph.
     *
     * @param x outer test value
     * @param y inner test value
     * @return x + y, x - y or y depending on the two signs
     */
    public int nestedConditional(int x, int y)
    {
        int result = 0;
        if (x > 0)
        {
            if (y > 0)
            {
                result = x + y;
            }
            else
            {
                result = x - y;
            }
        }
        else
        {
            result = y;
        }
        return result;
    }

    // Loop Examples

    /**
     * Sums the counter of a counted for loop.
     *
     * @param n exclusive upper bound
     * @return sum of 0 through n - 1
     */
    public int simpleLoop(int n)
    {
        int sum = 0;
        for (int i = 0; i < n; i++)
        {
            sum = sum + i;
        }
        return sum;
    }

    /**
     * Counts down a value in a while loop that also breaks after 100 iterations.
     *
     * @param x starting count
     * @return the number of iterations run, at most 101
     */
    public int whileLoopWithBreak(int x)
    {
        int count = 0;
        while (x > 0)
        {
            count++;
            x = x - 1;
            if (count > 100)
            {
                break;
            }
        }
        return count;
    }

    // Method Invocation Examples

    /**
     * Chains virtual calls on a string receiver.
     *
     * @param s receiver of length, toUpperCase and trim
     * @return s uppercased and trimmed
     */
    public String virtualCalls(String s)
    {
        int len = s.length();
        String upper = s.toUpperCase();
        String trimmed = upper.trim();
        return trimmed;
    }

    /**
     * Chains static Math calls over a primitive.
     *
     * @param x value passed to Math.abs
     * @return the magnitude of x clamped to the range 10 to 100
     */
    public int staticCalls(int x)
    {
        int abs = Math.abs(x);
        int max = Math.max(abs, 10);
        int min = Math.min(max, 100);
        return min;
    }

    // Field Access Examples

    private int instanceField = 42;
    private static int staticField = 100;

    /**
     * Reads an instance field, updates it, then reads it back.
     *
     * @return the field value after adding 10
     */
    public int fieldAccess()
    {
        int a = this.instanceField;
        int b = a + 10;
        this.instanceField = b;
        return this.instanceField;
    }

    /**
     * Reads a static field, updates it, then reads it back.
     *
     * @return the field value after doubling
     */
    public static int staticFieldAccess()
    {
        int a = staticField;
        int b = a * 2;
        staticField = b;
        return staticField;
    }

    // Array Examples

    /**
     * Loads two array elements and stores their sum back into the array.
     *
     * @param arr array read at indexes 0 and 1 and written at index 2
     * @return the stored sum
     */
    public int arrayOperations(int[] arr)
    {
        int first = arr[0];
        int second = arr[1];
        arr[2] = first + second;
        return arr[2];
    }

    /**
     * Allocates an int array and seeds its first two slots.
     *
     * @param size length of the new array
     * @return the array holding 1 and 2 at indexes 0 and 1
     */
    public int[] createArray(int size)
    {
        int[] result = new int[size];
        result[0] = 1;
        result[1] = 2;
        return result;
    }

    // Combined Optimization Example

    /**
     * Combines constant folding, copy propagation and dead code in one body.
     *
     * @param input value copied twice and added to the folded constant
     * @return input plus 30
     */
    public int combinedOptimizations(int input)
    {
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

    // Object Creation Example

    /**
     * Allocates a second showcase instance.
     *
     * @return the newly constructed instance
     */
    public SSAShowcase createInstance()
    {
        SSAShowcase obj = new SSAShowcase();
        return obj;
    }

    // Simple Arithmetic Example

    /**
     * Multiplies the sum of two operands by their difference.
     *
     * @param a left operand
     * @param b right operand
     * @return (a + b) * (a - b)
     */
    public int arithmetic(int a, int b)
    {
        int sum = a + b;
        int diff = a - b;
        int product = sum * diff;
        return product;
    }

    // Strength Reduction Examples

    /**
     * Multiplies by 2, 4 and 8 so each product can become a left shift.
     *
     * @param x multiplicand
     * @return x times 14
     */
    public int strengthReductionMul(int x)
    {
        int a = x * 2;    // Should become x << 1
        int b = x * 4;    // Should become x << 2
        int c = x * 8;    // Should become x << 3
        return a + b + c; // x*2 + x*4 + x*8 = x*14
    }

    /**
     * Divides by 2 and 4 so each quotient can become a right shift.
     *
     * @param x dividend
     * @return x / 2 + x / 4
     */
    public int strengthReductionDiv(int x)
    {
        int a = x / 2;    // Should become x >> 1
        int b = x / 4;    // Should become x >> 2
        return a + b;
    }

    /**
     * Takes the remainder by 2 and 8 so each can become a bitwise and.
     *
     * @param x dividend
     * @return x % 2 + x % 8
     */
    public int strengthReductionMod(int x)
    {
        int a = x % 2;    // Should become x & 1
        int b = x % 8;    // Should become x & 7
        return a + b;
    }

    // Algebraic Simplification Examples

    /**
     * Adds then subtracts zero so both operations can be dropped.
     *
     * @param x value passed through unchanged
     * @return x
     */
    public int algebraicAddSub(int x)
    {
        int a = x + 0;    // Should become x
        int b = a - 0;    // Should become a (which is x)
        return b;
    }

    /**
     * Multiplies by one and by zero so both products fold away.
     *
     * @param x multiplied by 1
     * @param y multiplied by 0
     * @return x
     */
    public int algebraicMul(int x, int y)
    {
        int a = x * 1;    // Should become x
        int b = y * 0;    // Should become 0
        return a + b;     // Should simplify to just x
    }

    /**
     * Ands, ors and xors against zero so two of the three collapse to x.
     *
     * @param x operand of every bitwise operation
     * @return x doubled
     */
    public int algebraicBitwise(int x)
    {
        int a = x & 0;    // Should become 0
        int b = x | 0;    // Should become x
        int c = x ^ 0;    // Should become x
        return a + b + c; // Should become 0 + x + x = 2*x
    }

    /**
     * Subtracts, xors, ands and ors a value against itself.
     *
     * @param x both operands of every operation
     * @return x doubled
     */
    public int algebraicSelfOps(int x)
    {
        int a = x - x;    // Should become 0
        int b = x ^ x;    // Should become 0
        int c = x & x;    // Should become x
        int d = x | x;    // Should become x
        return a + b + c + d; // Should become 0 + 0 + x + x = 2*x
    }

    /**
     * Chains strength reduction and algebraic identities in one body.
     *
     * @param x value shifted up, run through the identities, then halved
     * @return x times 4
     */
    public int combinedNewOptimizations(int x)
    {
        int a = x * 8;      // Strength reduction: x << 3
        int b = a + 0;      // Algebraic: a
        int c = b * 1;      // Algebraic: b
        int d = c / 2;      // Strength reduction: c >> 1
        int e = x - x;      // Algebraic: 0
        return d + e;       // Should be (x << 3) >> 1 + 0 = x * 4
    }

    // Phi Constant Propagation Tests

    /**
     * Assigns the same constant on both arms so the merge phi collapses.
     *
     * @param x operand of the branch condition
     * @return 42
     */
    public int phiConstantProp(int x)
    {
        int result;
        if (x > 0)
        {
            result = 42;
        }
        else
        {
            result = 42;  // Same value as true branch
        }
        // phi(42, 42) -> 42
        return result;
    }

    // Peephole Optimization Tests

    /**
     * Pairs a double negation with a shift whose count masks to zero.
     *
     * @param x operand of both patterns
     * @return x doubled
     */
    public int peepholeOpt(int x)
    {
        int a = -(-x);        // Double negation -> x
        int b = x << 32;      // Shift by 32 -> x (masked to 0)
        return a + b;         // Should be x + x = 2*x
    }

    // Common Subexpression Elimination Tests

    /**
     * Repeats an add and a multiply so each pair can share one computation.
     *
     * @param x left operand of both expressions
     * @param y right operand of both expressions
     * @return 2 * (x + y) + 2 * (x * y)
     */
    public int commonSubexpr(int x, int y)
    {
        int a = x + y;
        int b = x + y;        // Same as 'a' - reuse
        int c = x * y;
        int d = x * y;        // Same as 'c' - reuse
        return a + b + c + d; // = 2*(x+y) + 2*(x*y)
    }

    // Null Check Elimination Tests

    /**
     * Nests a redundant always-true guard inside an identical outer guard.
     *
     * @param flag value incremented on the guarded path
     * @return flag plus 1
     */
    public int nullCheckTest(int flag)
    {
        int isValid = 1;      // Simulate "not null" state
        if (isValid != 0) {   // Check that's always true
            if (isValid != 0) { // Redundant check - can be eliminated
                return flag + 1;
            }
        }
        return 0;             // Dead code - never reached
    }

    // Conditional Constant Propagation Tests

    /**
     * Branches on a comparison between two equal constants.
     *
     * @param x value adjusted on the taken arm
     * @return x plus 1
     */
    public int conditionalConst(int x)
    {
        int a = 5;
        int b = 5;
        if (a == b) {         // Always true (5 == 5)
            return x + 1;
        }
        else
        {
            return x - 1;     // Dead code
        }
    }

    // Loop-Invariant Code Motion Tests

    /**
     * Accumulates a loop-invariant constant on every iteration.
     *
     * @param n iteration count
     * @return n times 12, or 0 when n is not positive
     */
    public int loopInvariant(int n)
    {
        int sum = 0;
        for (int i = 0; i < n; i++)
        {
            sum += 12;        // Constant addition per iteration
        }
        return sum;           // = n * 12
    }

    // Induction Variable Tests

    /**
     * Accumulates a constant multiple of the loop counter each iteration.
     *
     * @param n exclusive upper bound
     * @return 4 times the sum of 0 through n - 1
     */
    public int inductionVar(int n)
    {
        int sum = 0;
        for (int i = 0; i < n; i++)
        {
            sum += i * 4;     // i*4 can be optimized with accumulator
        }
        return sum;           // = 4 * (0 + 1 + 2 + ... + (n-1)) = 4 * n*(n-1)/2
    }

    // Reassociation Tests

    /**
     * Adds two constants in separate steps so they can be regrouped.
     *
     * @param x base operand
     * @return x plus 15
     */
    public int reassociateConstants(int x)
    {
        int a = x + 5;
        int b = a + 10;       // Should become x + 15 after reassociate + fold
        return b;
    }

    /**
     * Multiplies by two constants in separate steps so they can be regrouped.
     *
     * @param x base operand
     * @return x times 8
     */
    public int reassociateMul(int x)
    {
        int a = x * 2;
        int b = a * 4;        // Should become x * 8 after reassociate + fold
        return b;
    }

    /**
     * Separates two constant addends with a variable addend.
     *
     * @param x base operand
     * @param y addend sitting between the two constants
     * @return x + y + 10
     */
    public int reassociateMultiVar(int x, int y)
    {
        int a = x + 3;
        int b = a + y;
        int c = b + 7;        // Constants 3 and 7 should group to 10
        return c;
    }

    // Loop Predication Tests

    /**
     * Guards the loop body with a comparison against a constant limit.
     *
     * @param n exclusive loop bound
     * @return sum of the counter values below 100
     */
    public int loopPredicationSimple(int n)
    {
        int sum = 0;
        for (int i = 0; i < n; i++)
        {
            if (i < 100) {    // Guard: can be predicated if n <= 100
                sum += i;
            }
        }
        return sum;
    }

    /**
     * Guards the loop body with the loop bound itself, so the guard always holds.
     *
     * @param n exclusive loop bound and guard limit
     * @return twice the sum of 0 through n - 1
     */
    public int loopPredicationRedundant(int n)
    {
        int sum = 0;
        for (int i = 0; i < n; i++)
        {
            if (i < n) {      // Redundant guard - always true
                sum += i * 2;
            }
        }
        return sum;
    }

    /**
     * Guards the loop body with a limit independent of the loop bound.
     *
     * @param n exclusive loop bound
     * @param limit guard threshold on the counter
     * @return sum of the counter values below limit
     */
    public int loopPredicationLimit(int n, int limit)
    {
        int sum = 0;
        for (int i = 0; i < n; i++)
        {
            if (i < limit) {  // Can predicate if n <= limit
                sum += i;
            }
        }
        return sum;
    }

    // Bit-Tracking DCE Tests

    /**
     * Masks a full-width multiply down to its low byte.
     *
     * @param a multiplicand
     * @return the low 8 bits of a * 1000
     */
    public int bdceMaskDead(int a)
    {
        int x = a * 1000;      // Full 32-bit multiply
        return x & 0xFF;       // Only low 8 bits used
    }

    /**
     * Shifts left and back with sign extension before masking to a byte.
     *
     * @param a value shifted
     * @return the low 8 bits of a
     */
    public int bdceShiftMask(int a)
    {
        int x = a << 24;       // Shift to high bits
        int y = x >> 24;       // Shift back (sign extend)
        return y & 0xFF;       // Mask to byte - only low 8 bits matter
    }

    /**
     * Ors in a high-shifted operand that the trailing mask discards.
     *
     * @param a operand kept by the mask
     * @param b operand shifted entirely above the mask
     * @return the low 16 bits of a
     */
    public int bdceCascade(int a, int b)
    {
        int x = a | (b << 16); // Combine values
        return x & 0xFFFF;     // Only low 16 bits used - b's contribution dead
    }

    /**
     * Returns a doubled value with every bit still live.
     *
     * @param a multiplicand
     * @return a times 2
     */
    public int bdceAllLive(int a)
    {
        int x = a * 2;
        return x;              // All bits returned - nothing dead
    }

    // Correlated Value Propagation Tests

    /**
     * Nests a comparison that the enclosing range check already proves true.
     *
     * @param x tested against 10 and then against 20
     * @return x plus 1 below 10, otherwise x
     */
    public int cvpRedundantCheck(int x)
    {
        if (x < 10)
        {
            if (x < 20) {     // Always true - x is [MIN, 9]
                return x + 1;
            }
            return 0;         // Dead code
        }
        return x;
    }

    /**
     * Nests a comparison that the enclosing range check rules out.
     *
     * @param x tested against 10 and then against 5
     * @return x doubled from 10 up, otherwise x
     */
    public int cvpImpossibleCheck(int x)
    {
        if (x >= 10)
        {
            if (x < 5) {      // Always false - x is [10, MAX]
                return 0;     // Dead code
            }
            return x * 2;
        }
        return x;
    }

    /**
     * Narrows a range over two checks so the third one is already implied.
     *
     * @param x tested against 0, then 100, then 1
     * @return x when it falls between 1 and 99, otherwise 0
     */
    public int cvpNestedRange(int x)
    {
        if (x > 0) {          // x is [1, MAX]
            if (x < 100) {    // x is [1, 99]
                if (x >= 1) { // Always true - x is [1, 99]
                    return x;
                }
            }
        }
        return 0;
    }

    /**
     * Nests a comparison that the enclosing range check leaves undecided.
     *
     * @param x tested against 50 and then against 25
     * @return 1 below 25, 2 from 25 through 49, otherwise 0
     */
    public int cvpNoOptimization(int x)
    {
        if (x < 50)
        {
            if (x < 25) {     // Cannot prove - x is [MIN, 49]
                return 1;
            }
            return 2;
        }
        return 0;
    }

    // Main Method for Testing

    /**
     * Runs every showcase method and prints its result next to the expected value.
     * @param args unused
     */
    public static void main(String[] args)
    {
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

        System.out.println("=== Bit-Tracking DCE ===");
        System.out.println("bdceMaskDead(100) = " + showcase.bdceMaskDead(100) + " (expected: 160)");
        System.out.println("bdceShiftMask(255) = " + showcase.bdceShiftMask(255) + " (expected: 255)");
        System.out.println("bdceCascade(255, 1000) = " + showcase.bdceCascade(255, 1000) + " (expected: 255)");
        System.out.println("bdceAllLive(50) = " + showcase.bdceAllLive(50) + " (expected: 100)");
        System.out.println();

        System.out.println("=== Correlated Value Propagation ===");
        System.out.println("cvpRedundantCheck(5) = " + showcase.cvpRedundantCheck(5) + " (expected: 6)");
        System.out.println("cvpRedundantCheck(15) = " + showcase.cvpRedundantCheck(15) + " (expected: 15)");
        System.out.println("cvpImpossibleCheck(5) = " + showcase.cvpImpossibleCheck(5) + " (expected: 5)");
        System.out.println("cvpImpossibleCheck(15) = " + showcase.cvpImpossibleCheck(15) + " (expected: 30)");
        System.out.println("cvpNestedRange(50) = " + showcase.cvpNestedRange(50) + " (expected: 50)");
        System.out.println("cvpNestedRange(0) = " + showcase.cvpNestedRange(0) + " (expected: 0)");
        System.out.println("cvpNoOptimization(10) = " + showcase.cvpNoOptimization(10) + " (expected: 1)");
        System.out.println("cvpNoOptimization(30) = " + showcase.cvpNoOptimization(30) + " (expected: 2)");
        System.out.println();

        System.out.println("=== All tests complete ===");
    }
}
