package com.tonic.analysis.ssa;

import com.tonic.analysis.ssa.analysis.*;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.lift.*;
import com.tonic.analysis.ssa.lower.BytecodeLowerer;
import com.tonic.analysis.ssa.transform.*;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for SSA-form IR operations.
 * Provides methods for lifting bytecode to SSA and lowering back to bytecode.
 */
public class SSA {

    private final ConstPool constPool;
    private final List<IRTransform> transforms;

    /**
     * Creates a new SSA processor.
     *
     * @param constPool the constant pool for the class being processed
     */
    public SSA(ConstPool constPool) {
        this.constPool = constPool;
        this.transforms = new ArrayList<>();
    }

    /**
     * Lifts a method from bytecode to SSA-form IR.
     *
     * @param method the method to lift
     * @return the SSA-form IR representation
     */
    public IRMethod lift(MethodEntry method) {
        BytecodeLifter lifter = new BytecodeLifter(constPool);
        IRMethod irMethod = lifter.lift(method);

        if (irMethod.getEntryBlock() != null) {
            DominatorTree domTree = new DominatorTree(irMethod);
            domTree.compute();

            PhiInserter phiInserter = new PhiInserter(domTree);
            phiInserter.insertPhis(irMethod);

            VariableRenamer renamer = new VariableRenamer(domTree);
            renamer.rename(irMethod);
        }

        return irMethod;
    }

    /**
     * Lowers an SSA-form IR method back to bytecode.
     *
     * @param irMethod the IR method to lower
     * @param targetMethod the target method to write bytecode into
     */
    public void lower(IRMethod irMethod, MethodEntry targetMethod) {
        BytecodeLowerer lowerer = new BytecodeLowerer(constPool);
        lowerer.lower(irMethod, targetMethod);
    }

    /**
     * Adds an optimization transform to be applied.
     *
     * @param transform the transform to add
     * @return this SSA instance for chaining
     */
    public SSA addTransform(IRTransform transform) {
        transforms.add(transform);
        return this;
    }

    /**
     * Enables dead code elimination optimization.
     *
     * @return this SSA instance for chaining
     */
    public SSA withDeadCodeElimination() {
        return addTransform(new DeadCodeElimination());
    }

    /**
     * Enables copy propagation optimization.
     *
     * @return this SSA instance for chaining
     */
    public SSA withCopyPropagation() {
        return addTransform(new CopyPropagation());
    }

    /**
     * Enables constant folding optimization.
     *
     * @return this SSA instance for chaining
     */
    public SSA withConstantFolding() {
        return addTransform(new ConstantFolding());
    }

    /**
     * Enables strength reduction optimization.
     * Replaces expensive operations with cheaper equivalents:
     * - x * 2^n -> x << n
     * - x / 2^n -> x >> n
     * - x % 2^n -> x & (2^n - 1)
     *
     * @return this SSA instance for chaining
     */
    public SSA withStrengthReduction() {
        return addTransform(new StrengthReduction());
    }

    /**
     * Enables algebraic simplification optimization.
     * Applies mathematical identities:
     * - x + 0 -> x, x * 1 -> x, x * 0 -> 0
     * - x - x -> 0, x ^ x -> 0
     * - x & 0 -> 0, x | 0 -> x
     *
     * @return this SSA instance for chaining
     */
    public SSA withAlgebraicSimplification() {
        return addTransform(new AlgebraicSimplification());
    }

    /**
     * Enables phi constant propagation optimization.
     * Simplifies phi nodes when all incoming values are identical.
     *
     * @return this SSA instance for chaining
     */
    public SSA withPhiConstantPropagation() {
        return addTransform(new PhiConstantPropagation());
    }

    /**
     * Enables peephole optimizations.
     * Applies small pattern-based optimizations like double negation removal.
     *
     * @return this SSA instance for chaining
     */
    public SSA withPeepholeOptimizations() {
        return addTransform(new PeepholeOptimizations());
    }

    /**
     * Enables common subexpression elimination.
     * Identifies identical expressions and reuses the first computed result.
     *
     * @return this SSA instance for chaining
     */
    public SSA withCommonSubexpressionElimination() {
        return addTransform(new CommonSubexpressionElimination());
    }

    /**
     * Enables null check elimination optimization.
     * Removes redundant null checks when an object is provably non-null.
     *
     * @return this SSA instance for chaining
     */
    public SSA withNullCheckElimination() {
        return addTransform(new NullCheckElimination());
    }

    /**
     * Enables conditional constant propagation optimization.
     * Propagates constants through conditional branches, eliminating unreachable code.
     *
     * @return this SSA instance for chaining
     */
    public SSA withConditionalConstantPropagation() {
        return addTransform(new ConditionalConstantPropagation());
    }

    /**
     * Enables loop-invariant code motion optimization.
     * Moves loop-invariant computations outside the loop.
     *
     * @return this SSA instance for chaining
     */
    public SSA withLoopInvariantCodeMotion() {
        return addTransform(new LoopInvariantCodeMotion());
    }

    /**
     * Enables induction variable simplification optimization.
     * Simplifies loop counters and derived induction variables.
     *
     * @return this SSA instance for chaining
     */
    public SSA withInductionVariableSimplification() {
        return addTransform(new InductionVariableSimplification());
    }

    /**
     * Enables the standard set of optimizations.
     *
     * @return this SSA instance for chaining
     */
    public SSA withStandardOptimizations() {
        return withConstantFolding()
                .withCopyPropagation()
                .withDeadCodeElimination();
    }

    /**
     * Enables all available optimizations.
     * Transforms are applied in optimal order for best results.
     *
     * @return this SSA instance for chaining
     */
    public SSA withAllOptimizations() {
        return withConstantFolding()
                .withPhiConstantPropagation()
                .withConditionalConstantPropagation()
                .withAlgebraicSimplification()
                .withPeepholeOptimizations()
                .withStrengthReduction()
                .withCommonSubexpressionElimination()
                .withCopyPropagation()
                .withNullCheckElimination()
                .withLoopInvariantCodeMotion()
                .withInductionVariableSimplification()
                .withDeadCodeElimination();
    }

    /**
     * Runs all registered transforms on a method until a fixed point is reached.
     *
     * @param method the method to optimize
     */
    public void runTransforms(IRMethod method) {
        boolean changed = true;
        int iterations = 0;
        int maxIterations = 10;

        while (changed && iterations < maxIterations) {
            changed = false;
            for (IRTransform transform : transforms) {
                if (transform.run(method)) {
                    changed = true;
                }
            }
            iterations++;
        }
    }

    /**
     * Computes the dominator tree for a method.
     *
     * @param method the method to analyze
     * @return the computed dominator tree
     */
    public DominatorTree computeDominators(IRMethod method) {
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();
        return domTree;
    }

    /**
     * Computes liveness information for a method.
     *
     * @param method the method to analyze
     * @return the computed liveness analysis
     */
    public LivenessAnalysis computeLiveness(IRMethod method) {
        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();
        return liveness;
    }

    /**
     * Computes def-use chains for a method.
     *
     * @param method the method to analyze
     * @return the computed def-use chains
     */
    public DefUseChains computeDefUse(IRMethod method) {
        DefUseChains defUse = new DefUseChains(method);
        defUse.compute();
        return defUse;
    }

    /**
     * Computes loop information for a method.
     *
     * @param method the method to analyze
     * @return the computed loop analysis
     */
    public LoopAnalysis computeLoops(IRMethod method) {
        DominatorTree domTree = computeDominators(method);
        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();
        return loops;
    }

    /**
     * Lifts a method to SSA form and applies all registered optimizations.
     *
     * @param method the method to lift and optimize
     * @return the optimized IR method
     */
    public IRMethod liftAndOptimize(MethodEntry method) {
        IRMethod irMethod = lift(method);
        runTransforms(irMethod);
        return irMethod;
    }

    /**
     * Optimizes an IR method and lowers it back to bytecode.
     *
     * @param irMethod the IR method to optimize
     * @param targetMethod the target method to write bytecode into
     */
    public void optimizeAndLower(IRMethod irMethod, MethodEntry targetMethod) {
        runTransforms(irMethod);
        lower(irMethod, targetMethod);
    }

    /**
     * Performs a complete transformation: lift, optimize, and lower.
     *
     * @param method the method to transform
     */
    public void transform(MethodEntry method) {
        IRMethod irMethod = liftAndOptimize(method);
        lower(irMethod, method);
    }
}
