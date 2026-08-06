package com.tonic.analysis.ssa;

import com.tonic.analysis.ssa.analysis.*;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.lift.*;
import com.tonic.analysis.ssa.llvm.LlvmLowering;
import com.tonic.analysis.ssa.lower.BytecodeLowerer;
import com.tonic.analysis.ssa.transform.*;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point for SSA-form IR operations.
 */
public class SSA
{

    private static final int EXPENSIVE_TRANSFORM_THRESHOLD = 200;

    private final ConstPool constPool;
    private final List<IRTransform> transforms;
    private final List<ClassTransform> classTransforms;
    private boolean resolveExceptionLocals = false;
    private boolean emitLocalVariableTable = true;

    /**
     * Creates a new SSA processor.
     * @param constPool the constant pool for the class being processed
     */
    public SSA(ConstPool constPool)
    {
        this.constPool = constPool;
        this.transforms = new ArrayList<>();
        this.classTransforms = new ArrayList<>();
    }

    /**
     * Gets the constant pool associated with this SSA processor.
     * @return the constant pool
     */
    public ConstPool getConstPool()
    {
        return constPool;
    }

    /**
     * Enables exception-local resolution during lifting.
     * @return this for fluent chaining
     */
    public SSA withExceptionLocalResolution()
    {
        this.resolveExceptionLocals = true;
        return this;
    }

    /**
     * Disables LocalVariableTable emission when lowering back to bytecode.
     * @return this for fluent chaining
     */
    public SSA withoutLocalVariableTable()
    {
        this.emitLocalVariableTable = false;
        return this;
    }

    /**
     * Lifts a method from bytecode to SSA-form IR.
     * @param method the method to lift
     * @return the SSA-form IR representation
     */
    public IRMethod lift(MethodEntry method)
    {
        BytecodeLifter lifter = new BytecodeLifter(constPool);
        IRMethod irMethod = lifter.lift(method);

        if (irMethod.getEntryBlock() != null)
        {
            // Connect protected blocks to their handlers ONLY for SSA construction (opt-in): this makes handlers
            // reachable in the dominator tree so phi insertion + renaming propagate locals that are live across
            // the exception edge (params and try-body defs) into the handler. The edges are removed below so the
            // final CFG carries only real control flow, and the exception table is rebuilt from
            // ExceptionHandler.tryBlocks regardless. Gated by withExceptionLocalResolution() because the handler
            // phis it introduces change control-flow shape that the source decompiler's finally recovery rejects.
            java.util.List<IRBlock[]> exceptionEdges =
                    resolveExceptionLocals ? BytecodeLifter.addExceptionEdges(irMethod) : null;

            DominatorTree domTree = new DominatorTree(irMethod);
            domTree.compute();

            PhiInserter phiInserter = new PhiInserter(domTree);
            phiInserter.insertPhis(irMethod);

            VariableRenamer renamer = new VariableRenamer(domTree);
            renamer.rename(irMethod);

            BytecodeLifter.refinePhiTypes(irMethod);

            if (exceptionEdges != null)
            {
                BytecodeLifter.removeExceptionEdges(exceptionEdges);
            }
        }

        return irMethod;
    }

    /**
     * Lowers an SSA-form IR method back to bytecode.
     * @param irMethod the IR method to lower
     * @param targetMethod the target method to write bytecode into
     */
    public void lower(IRMethod irMethod, MethodEntry targetMethod)
    {
        BytecodeLowerer lowerer = new BytecodeLowerer(constPool, emitLocalVariableTable);
        lowerer.lower(irMethod, targetMethod);
    }

    /**
     * Lowers an SSA-form IR method to textual LLVM IR (computational subset).
     * @param irMethod the IR method to lower
     * @return the LLVM IR module text
     */
    public String toLlvm(IRMethod irMethod)
    {
        return new LlvmLowering().lower(irMethod);
    }

    /**
     * Adds an optimization transform to be applied.
     * @param transform the transform to add
     * @return this SSA instance for chaining
     */
    public SSA addTransform(IRTransform transform)
    {
        transforms.add(transform);
        return this;
    }

    /**
     * Enables dead code elimination optimization.
     * @return this SSA instance for chaining
     */
    public SSA withDeadCodeElimination()
    {
        return addTransform(new DeadCodeElimination());
    }

    /**
     * Enables copy propagation optimization.
     * @return this SSA instance for chaining
     */
    public SSA withCopyPropagation()
    {
        return addTransform(new CopyPropagation());
    }

    /**
     * Enables constant folding optimization.
     * @return this SSA instance for chaining
     */
    public SSA withConstantFolding()
    {
        return addTransform(new ConstantFolding());
    }

    /**
     * Enables strength reduction optimization.
     * @return this SSA instance for chaining
     */
    public SSA withStrengthReduction()
    {
        return addTransform(new StrengthReduction());
    }

    /**
     * Enables algebraic simplification optimization.
     * @return this SSA instance for chaining
     */
    public SSA withAlgebraicSimplification()
    {
        return addTransform(new AlgebraicSimplification());
    }

    /**
     * Enables reassociation optimization.
     * @return this SSA instance for chaining
     */
    public SSA withReassociate()
    {
        return addTransform(new Reassociate());
    }

    /**
     * Enables phi constant propagation optimization.
     * @return this SSA instance for chaining
     */
    public SSA withPhiConstantPropagation()
    {
        return addTransform(new PhiConstantPropagation());
    }

    /**
     * Enables peephole optimizations.
     * @return this SSA instance for chaining
     */
    public SSA withPeepholeOptimizations()
    {
        return addTransform(new PeepholeOptimizations());
    }

    /**
     * Enables common subexpression elimination.
     * @return this SSA instance for chaining
     */
    public SSA withCommonSubexpressionElimination()
    {
        return addTransform(new CommonSubexpressionElimination());
    }

    /**
     * Enables null check elimination optimization.
     * @return this SSA instance for chaining
     */
    public SSA withNullCheckElimination()
    {
        return addTransform(new NullCheckElimination());
    }

    /**
     * Enables conditional constant propagation optimization.
     * @return this SSA instance for chaining
     */
    public SSA withConditionalConstantPropagation()
    {
        return addTransform(new ConditionalConstantPropagation());
    }

    /**
     * Enables loop-invariant code motion optimization.
     * @return this SSA instance for chaining
     */
    public SSA withLoopInvariantCodeMotion()
    {
        return addTransform(new LoopInvariantCodeMotion());
    }

    /**
     * Enables loop predication optimization.
     * @return this SSA instance for chaining
     */
    public SSA withLoopPredication()
    {
        return addTransform(new LoopPredication());
    }

    /**
     * Enables jump threading optimization.
     * @return this SSA instance for chaining
     */
    public SSA withJumpThreading()
    {
        return addTransform(new JumpThreading());
    }

    /**
     * Enables block merging optimization.
     * @return this SSA instance for chaining
     */
    public SSA withBlockMerging()
    {
        return addTransform(new BlockMerging());
    }

    /**
     * Enables control flow reducibility transformation.
     * @return this SSA instance for chaining
     */
    public SSA withControlFlowReducibility()
    {
        return addTransform(new ControlFlowReducibility());
    }

    /**
     * Enables duplicate block merging optimization.
     * @return this SSA instance for chaining
     */
    public SSA withDuplicateBlockMerging()
    {
        return addTransform(new DuplicateBlockMerging());
    }

    /**
     * Enables duplicate block merging optimization with configurable aggression.
     * @param aggressive true for aggressive merging, false for conservative
     * @return this SSA instance for chaining
     */
    public SSA withDuplicateBlockMerging(boolean aggressive)
    {
        return addTransform(new DuplicateBlockMerging(aggressive));
    }

    /**
     * Enables redundant copy elimination optimization.
     * @return this SSA instance for chaining
     */
    public SSA withRedundantCopyElimination()
    {
        return addTransform(new RedundantCopyElimination());
    }

    /**
     * Enables bit-tracking dead code elimination.
     * @return this SSA instance for chaining
     */
    public SSA withBitTrackingDCE()
    {
        return addTransform(new BitTrackingDCE());
    }

    /**
     * Enables correlated value propagation optimization.
     * @return this SSA instance for chaining
     */
    public SSA withCorrelatedValuePropagation()
    {
        return addTransform(new CorrelatedValuePropagation());
    }

    /**
     * Adds a class-level transform to be applied.
     * @param transform the class transform to add
     * @return this SSA instance for chaining
     */
    public SSA addClassTransform(ClassTransform transform)
    {
        classTransforms.add(transform);
        return this;
    }

    /**
     * Enables method inlining optimization.
     * @return this SSA instance for chaining
     */
    public SSA withMethodInlining()
    {
        return addClassTransform(new MethodInlining());
    }

    /**
     * Enables dead method elimination.
     * @return this SSA instance for chaining
     */
    public SSA withDeadMethodElimination()
    {
        return addClassTransform(new DeadMethodElimination());
    }

    /**
     * Enables the standard set of optimizations.
     * @return this SSA instance for chaining
     */
    public SSA withStandardOptimizations()
    {
        return withConstantFolding()
                .withCopyPropagation()
                .withDeadCodeElimination();
    }

    /**
     * Enables all available optimizations.
     * @return this SSA instance for chaining
     */
    public SSA withAllOptimizations()
    {
        return withReassociate()
                .withConstantFolding()
                .withPhiConstantPropagation()
                .withConditionalConstantPropagation()
                .withCorrelatedValuePropagation()
                .withAlgebraicSimplification()
                .withPeepholeOptimizations()
                .withStrengthReduction()
                .withCommonSubexpressionElimination()
                .withCopyPropagation()
                .withRedundantCopyElimination()
                .withBitTrackingDCE()
                .withNullCheckElimination()
                .withLoopInvariantCodeMotion()
                .withLoopPredication()
                .withJumpThreading()
                .withBlockMerging()
                .withDeadCodeElimination();
    }

    /**
     * Runs all registered transforms on a method until a fixed point is reached.
     * @param method the method to optimize
     */
    public void runTransforms(IRMethod method)
    {
        boolean changed = true;
        int iterations = 0;
        int maxIterations = 3;

        int blockCount = method.getBlocks().size();
        boolean skipExpensive = blockCount > EXPENSIVE_TRANSFORM_THRESHOLD;

        while (changed && iterations < maxIterations)
        {
            changed = false;
            for (IRTransform transform : transforms)
            {
                if (skipExpensive && isExpensiveTransform(transform))
                {
                    continue;
                }
                if (transform.run(method))
                {
                    changed = true;
                }
            }
            iterations++;
        }
    }

    private boolean isExpensiveTransform(IRTransform transform)
    {
        String name = transform.getName();
        return "DuplicateBlockMerging".equals(name) || "ControlFlowReducibility".equals(name);
    }

    /**
     * Computes the dominator tree for a method.
     * @param method the method to analyze
     * @return the computed dominator tree
     */
    public DominatorTree computeDominators(IRMethod method)
    {
        DominatorTree domTree = new DominatorTree(method);
        domTree.compute();
        return domTree;
    }

    /**
     * Computes liveness information for a method.
     * @param method the method to analyze
     * @return the computed liveness analysis
     */
    public LivenessAnalysis computeLiveness(IRMethod method)
    {
        LivenessAnalysis liveness = new LivenessAnalysis(method);
        liveness.compute();
        return liveness;
    }

    /**
     * Computes def-use chains for a method.
     * @param method the method to analyze
     * @return the computed def-use chains
     */
    public DefUseChains computeDefUse(IRMethod method)
    {
        DefUseChains defUse = new DefUseChains(method);
        defUse.compute();
        return defUse;
    }

    /**
     * Computes loop information for a method.
     * @param method the method to analyze
     * @return the computed loop analysis
     */
    public LoopAnalysis computeLoops(IRMethod method)
    {
        DominatorTree domTree = computeDominators(method);
        LoopAnalysis loops = new LoopAnalysis(method, domTree);
        loops.compute();
        return loops;
    }

    /**
     * Lifts a method to SSA form and applies all registered optimizations.
     * @param method the method to lift and optimize
     * @return the optimized IR method
     */
    public IRMethod liftAndOptimize(MethodEntry method)
    {
        IRMethod irMethod = lift(method);
        runTransforms(irMethod);
        return irMethod;
    }

    /**
     * Optimizes an IR method and lowers it back to bytecode.
     * @param irMethod the IR method to optimize
     * @param targetMethod the target method to write bytecode into
     */
    public void optimizeAndLower(IRMethod irMethod, MethodEntry targetMethod)
    {
        runTransforms(irMethod);
        lower(irMethod, targetMethod);
    }

    /**
     * Performs a complete transformation: lift, optimize, and lower.
     * @param method the method to transform
     */
    public void transform(MethodEntry method)
    {
        IRMethod irMethod = liftAndOptimize(method);
        lower(irMethod, method);
    }

    /**
     * Runs all registered class-level transforms on a class file.
     * @param classFile the class file to transform
     * @return true if any transform modified the class
     */
    public boolean runClassTransforms(ClassFile classFile)
    {
        boolean changed = false;
        for (ClassTransform transform : classTransforms)
        {
            if (transform.run(classFile, this))
            {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Transforms an entire class file with all registered transforms.
     * @param classFile the class file to transform
     */
    public void transformClass(ClassFile classFile)
    {
        runClassTransforms(classFile);

        for (MethodEntry method : classFile.getMethods())
        {
            if (method.getCodeAttribute() == null) continue;
            if (method.getName().startsWith("<")) continue;

            IRMethod irMethod = lift(method);
            runTransforms(irMethod);
            lower(irMethod, method);
        }
    }

    /**
     * Gets the list of registered class-level transforms.
     * @return unmodifiable list of class transforms
     */
    public List<ClassTransform> getClassTransforms()
    {
        return java.util.Collections.unmodifiableList(classTransforms);
    }
}
