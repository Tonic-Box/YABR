package com.tonic.analysis.ssa;

import com.tonic.analysis.ssa.analysis.*;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.lift.*;
import com.tonic.analysis.ssa.lower.BytecodeLowerer;
import com.tonic.analysis.ssa.transform.*;
import com.tonic.parser.ClassFile;
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
    private final List<ClassTransform> classTransforms;

    /**
     * Creates a new SSA processor.
     *
     * @param constPool the constant pool for the class being processed
     */
    public SSA(ConstPool constPool) {
        this.constPool = constPool;
        this.transforms = new ArrayList<>();
        this.classTransforms = new ArrayList<>();
    }

    /**
     * Gets the constant pool associated with this SSA processor.
     *
     * @return the constant pool
     */
    public ConstPool getConstPool() {
        return constPool;
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
     * Enables jump threading optimization.
     * Eliminates redundant jump chains by threading through empty goto blocks.
     * For example: goto A; A: goto B -> goto B
     *
     * @return this SSA instance for chaining
     */
    public SSA withJumpThreading() {
        return addTransform(new JumpThreading());
    }

    /**
     * Enables block merging optimization.
     * Merges blocks with a single predecessor/successor relationship
     * where no phi instructions are present.
     *
     * @return this SSA instance for chaining
     */
    public SSA withBlockMerging() {
        return addTransform(new BlockMerging());
    }

    /**
     * Adds a class-level transform to be applied.
     *
     * @param transform the class transform to add
     * @return this SSA instance for chaining
     */
    public SSA addClassTransform(ClassTransform transform) {
        classTransforms.add(transform);
        return this;
    }

    /**
     * Enables method inlining optimization.
     * Replaces method calls with the body of the called method for
     * private, final, and static methods within the same class.
     *
     * @return this SSA instance for chaining
     */
    public SSA withMethodInlining() {
        return addClassTransform(new MethodInlining());
    }

    /**
     * Enables dead method elimination.
     * Removes private methods that are never called after inlining.
     *
     * @return this SSA instance for chaining
     */
    public SSA withDeadMethodElimination() {
        return addClassTransform(new DeadMethodElimination());
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
                .withJumpThreading()
                .withBlockMerging()
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

    /**
     * Runs all registered class-level transforms on a class file.
     * Class transforms (like method inlining) have access to the entire class
     * and can perform cross-method optimizations.
     *
     * @param classFile the class file to transform
     * @return true if any transform modified the class
     */
    public boolean runClassTransforms(ClassFile classFile) {
        boolean changed = false;
        for (ClassTransform transform : classTransforms) {
            if (transform.run(classFile, this)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Transforms an entire class file with all registered transforms.
     * First runs class-level transforms (like inlining), then runs
     * method-level transforms on each method.
     *
     * @param classFile the class file to transform
     */
    public void transformClass(ClassFile classFile) {
        // Run class-level transforms first (e.g., method inlining)
        runClassTransforms(classFile);

        // Then run method-level transforms on each method
        for (MethodEntry method : classFile.getMethods()) {
            if (method.getCodeAttribute() == null) continue;
            if (method.getName().startsWith("<")) continue; // Skip init methods

            IRMethod irMethod = lift(method);
            runTransforms(irMethod);
            lower(irMethod, method);
        }
    }

    /**
     * Gets the list of registered class-level transforms.
     *
     * @return unmodifiable list of class transforms
     */
    public List<ClassTransform> getClassTransforms() {
        return java.util.Collections.unmodifiableList(classTransforms);
    }
}
