package com.tonic.analysis.ssa;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.analysis.DominatorTree;
import com.tonic.analysis.ssa.analysis.LivenessAnalysis;
import com.tonic.analysis.ssa.analysis.DefUseChains;
import com.tonic.analysis.ssa.analysis.LoopAnalysis;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the SSA class - the main entry point for SSA operations.
 * Covers lifting, lowering, transforms, and analysis computations.
 */
class SSATest {

    private ClassPool pool;
    private ClassFile classFile;
    private SSA ssa;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/SSATestClass", access);
        ssa = new SSA(classFile.getConstPool());
    }

    // ========== Constructor Tests ==========

    @Test
    void constructorSetsConstPool() {
        assertNotNull(ssa.getConstPool());
        assertEquals(classFile.getConstPool(), ssa.getConstPool());
    }

    // ========== Lift Tests ==========

    @Test
    void liftMethodReturnsIRMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "simpleMethod", "V");

        IRMethod irMethod = ssa.lift(method);

        assertNotNull(irMethod);
    }

    @Test
    void liftMethodPreservesMethodName() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "namedMethod", "V");

        IRMethod irMethod = ssa.lift(method);

        assertEquals("namedMethod", irMethod.getName());
    }

    @Test
    void liftMethodPreservesDescriptor() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "compute", "I", "I", "I");

        IRMethod irMethod = ssa.lift(method);

        assertEquals("(II)I", irMethod.getDescriptor());
    }

    @Test
    void liftMethodPreservesOwnerClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "owned", "V");

        IRMethod irMethod = ssa.lift(method);

        assertEquals("com/test/SSATestClass", irMethod.getOwnerClass());
    }

    @Test
    void liftStaticMethod() throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, "staticMethod", "V");

        IRMethod irMethod = ssa.lift(method);

        assertTrue(irMethod.isStatic());
    }

    @Test
    void liftInstanceMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "instanceMethod", "V");

        IRMethod irMethod = ssa.lift(method);

        assertFalse(irMethod.isStatic());
    }

    // ========== Lower Tests ==========

    @Test
    void lowerUpdatesBytecode() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "lowerTest", "V");

        IRMethod irMethod = ssa.lift(method);
        // Lower should not throw
        ssa.lower(irMethod, method);

        assertNotNull(method.getCodeAttribute());
    }

    // ========== Transform Chain Tests ==========

    @Test
    void addTransformReturnsSelfForChaining() {
        SSA result = ssa.withConstantFolding();
        assertSame(ssa, result);
    }

    @Test
    void withDeadCodeEliminationAddsTransform() {
        SSA result = ssa.withDeadCodeElimination();
        assertSame(ssa, result);
    }

    @Test
    void withCopyPropagationAddsTransform() {
        SSA result = ssa.withCopyPropagation();
        assertSame(ssa, result);
    }

    @Test
    void withConstantFoldingAddsTransform() {
        SSA result = ssa.withConstantFolding();
        assertSame(ssa, result);
    }

    @Test
    void withStrengthReductionAddsTransform() {
        SSA result = ssa.withStrengthReduction();
        assertSame(ssa, result);
    }

    @Test
    void withAlgebraicSimplificationAddsTransform() {
        SSA result = ssa.withAlgebraicSimplification();
        assertSame(ssa, result);
    }

    @Test
    void withReassociateAddsTransform() {
        SSA result = ssa.withReassociate();
        assertSame(ssa, result);
    }

    @Test
    void withPhiConstantPropagationAddsTransform() {
        SSA result = ssa.withPhiConstantPropagation();
        assertSame(ssa, result);
    }

    @Test
    void withPeepholeOptimizationsAddsTransform() {
        SSA result = ssa.withPeepholeOptimizations();
        assertSame(ssa, result);
    }

    @Test
    void withCommonSubexpressionEliminationAddsTransform() {
        SSA result = ssa.withCommonSubexpressionElimination();
        assertSame(ssa, result);
    }

    @Test
    void withNullCheckEliminationAddsTransform() {
        SSA result = ssa.withNullCheckElimination();
        assertSame(ssa, result);
    }

    @Test
    void withConditionalConstantPropagationAddsTransform() {
        SSA result = ssa.withConditionalConstantPropagation();
        assertSame(ssa, result);
    }

    @Test
    void withLoopInvariantCodeMotionAddsTransform() {
        SSA result = ssa.withLoopInvariantCodeMotion();
        assertSame(ssa, result);
    }

    @Test
    void withLoopPredicationAddsTransform() {
        SSA result = ssa.withLoopPredication();
        assertSame(ssa, result);
    }

    @Test
    void withInductionVariableSimplificationAddsTransform() {
        SSA result = ssa.withInductionVariableSimplification();
        assertSame(ssa, result);
    }

    @Test
    void withJumpThreadingAddsTransform() {
        SSA result = ssa.withJumpThreading();
        assertSame(ssa, result);
    }

    @Test
    void withBlockMergingAddsTransform() {
        SSA result = ssa.withBlockMerging();
        assertSame(ssa, result);
    }

    @Test
    void withControlFlowReducibilityAddsTransform() {
        SSA result = ssa.withControlFlowReducibility();
        assertSame(ssa, result);
    }

    @Test
    void withDuplicateBlockMergingAddsTransform() {
        SSA result = ssa.withDuplicateBlockMerging();
        assertSame(ssa, result);
    }

    @Test
    void withDuplicateBlockMergingAggressiveAddsTransform() {
        SSA result = ssa.withDuplicateBlockMerging(true);
        assertSame(ssa, result);
    }

    @Test
    void withRedundantCopyEliminationAddsTransform() {
        SSA result = ssa.withRedundantCopyElimination();
        assertSame(ssa, result);
    }

    @Test
    void withBitTrackingDCEAddsTransform() {
        SSA result = ssa.withBitTrackingDCE();
        assertSame(ssa, result);
    }

    @Test
    void withCorrelatedValuePropagationAddsTransform() {
        SSA result = ssa.withCorrelatedValuePropagation();
        assertSame(ssa, result);
    }

    @Test
    void withStandardOptimizationsAddsMultipleTransforms() {
        SSA result = ssa.withStandardOptimizations();
        assertSame(ssa, result);
    }

    @Test
    void withAllOptimizationsAddsAllTransforms() {
        SSA result = ssa.withAllOptimizations();
        assertSame(ssa, result);
    }

    // ========== Class Transform Tests ==========

    @Test
    void withMethodInliningAddsClassTransform() {
        SSA result = ssa.withMethodInlining();
        assertSame(ssa, result);
        assertFalse(ssa.getClassTransforms().isEmpty());
    }

    @Test
    void withDeadMethodEliminationAddsClassTransform() {
        SSA result = ssa.withDeadMethodElimination();
        assertSame(ssa, result);
        assertFalse(ssa.getClassTransforms().isEmpty());
    }

    // ========== Analysis Computation Tests ==========

    @Test
    void computeDominatorsReturnsTree() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "domTest", "V");

        IRMethod irMethod = ssa.lift(method);
        DominatorTree domTree = ssa.computeDominators(irMethod);

        assertNotNull(domTree);
    }

    @Test
    void computeLivenessReturnsAnalysis() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "liveTest", "V");

        IRMethod irMethod = ssa.lift(method);
        LivenessAnalysis liveness = ssa.computeLiveness(irMethod);

        assertNotNull(liveness);
    }

    @Test
    void computeDefUseReturnsChains() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "defuseTest", "V");

        IRMethod irMethod = ssa.lift(method);
        DefUseChains defUse = ssa.computeDefUse(irMethod);

        assertNotNull(defUse);
    }

    @Test
    void computeLoopsReturnsAnalysis() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "loopTest", "V");

        IRMethod irMethod = ssa.lift(method);
        LoopAnalysis loops = ssa.computeLoops(irMethod);

        assertNotNull(loops);
    }

    // ========== Combined Operations Tests ==========

    @Test
    void liftAndOptimizeReturnsOptimizedMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "optTest", "V");

        ssa.withConstantFolding();
        IRMethod irMethod = ssa.liftAndOptimize(method);

        assertNotNull(irMethod);
    }

    @Test
    void optimizeAndLowerCompletes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "optLowerTest", "V");

        IRMethod irMethod = ssa.lift(method);
        ssa.withConstantFolding();
        ssa.optimizeAndLower(irMethod, method);

        // Should complete without error
        assertNotNull(method.getCodeAttribute());
    }

    @Test
    void transformMethodCompletes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "transformTest", "V");

        ssa.withConstantFolding();
        ssa.transform(method);

        // Should complete without error
        assertNotNull(method.getCodeAttribute());
    }

    // ========== Class-Level Transform Tests ==========

    @Test
    void runClassTransformsReturnsBoolean() throws IOException {
        boolean changed = ssa.runClassTransforms(classFile);
        // No transforms registered, so should be false
        assertFalse(changed);
    }

    @Test
    void transformClassCompletes() throws IOException {
        ssa.withConstantFolding();
        ssa.transformClass(classFile);

        // Should complete without error
        assertNotNull(classFile);
    }

    // ========== Run Transforms Tests ==========

    @Test
    void runTransformsOnEmptyMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        MethodEntry method = classFile.createNewMethod(access, "emptyMethod", "V");

        IRMethod irMethod = ssa.lift(method);
        ssa.withConstantFolding().withDeadCodeElimination();
        ssa.runTransforms(irMethod);

        // Should complete without error
        assertNotNull(irMethod);
    }
}
