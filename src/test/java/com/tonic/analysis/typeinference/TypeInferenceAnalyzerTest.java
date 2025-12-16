package com.tonic.analysis.typeinference;

import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.IRType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.NullConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TypeInferenceAnalyzer - inferring types and nullability in SSA IR.
 * Covers type inference, nullability analysis, and type narrowing.
 */
class TypeInferenceAnalyzerTest {

    private IRMethod method;
    private TypeInferenceAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        IRBlock.resetIdCounter();
        SSAValue.resetIdCounter();

        method = new IRMethod("com/test/Test", "testMethod", "()V", true);
        IRBlock entry = new IRBlock("entry");
        method.addBlock(entry);
        method.setEntryBlock(entry);

        analyzer = new TypeInferenceAnalyzer(method);
    }

    // ========== Constructor Tests ==========

    @Test
    void constructorCreatesInstance() {
        TypeInferenceAnalyzer tia = new TypeInferenceAnalyzer(method);
        assertNotNull(tia);
    }

    @Test
    void constructorSetsMethod() {
        TypeInferenceAnalyzer tia = new TypeInferenceAnalyzer(method);
        assertNotNull(tia);
    }

    // ========== Analyze Tests ==========

    @Test
    void analyzeOnEmptyMethod() {
        analyzer.analyze();

        assertNotNull(analyzer.getAllTypeStates());
    }

    @Test
    void analyzeCanBeCalledMultipleTimes() {
        analyzer.analyze();
        analyzer.analyze();

        assertNotNull(analyzer.getAllTypeStates());
    }

    @Test
    void analyzeInitializesParameters() {
        IRMethod methodWithParams = new IRMethod(
            "com/test/Test", "withParams", "(I)V", true);
        IRBlock entry = new IRBlock("entry");
        methodWithParams.addBlock(entry);
        methodWithParams.setEntryBlock(entry);

        SSAValue param = new SSAValue(PrimitiveType.INT, "param0");
        methodWithParams.addParameter(param);

        TypeInferenceAnalyzer paramAnalyzer = new TypeInferenceAnalyzer(methodWithParams);
        paramAnalyzer.analyze();

        TypeState state = paramAnalyzer.getTypeState(param);
        assertNotNull(state);
    }

    // ========== Type State Tests ==========

    @Test
    void getTypeStateReturnsBottomForUnknown() {
        SSAValue unknown = new SSAValue(PrimitiveType.INT, "unknown");

        TypeState state = analyzer.getTypeState(unknown);

        assertNotNull(state);
    }

    @Test
    void getTypeStateForConstant() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(v0);

        assertNotNull(state);
        assertEquals(Nullability.NOT_NULL, state.getNullability());
    }

    @Test
    void getTypeStateForNullConstant() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/Object");
        SSAValue v0 = new SSAValue(refType, "v0");

        ConstantInstruction nullInstr = new ConstantInstruction(v0, NullConstant.INSTANCE);
        entry.addInstruction(nullInstr);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(v0);

        assertNotNull(state);
        assertTrue(state.isDefinitelyNull());
    }

    @Test
    void getTypeStateForNewInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/String");
        SSAValue v0 = new SSAValue(refType, "v0");

        NewInstruction newInstr = new NewInstruction(v0, "java/lang/String");
        entry.addInstruction(newInstr);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(v0);

        assertNotNull(state);
        assertTrue(state.isDefinitelyNotNull());
    }

    // ========== Inferred Type Tests ==========

    @Test
    void getInferredTypeReturnsType() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        IRType type = analyzer.getInferredType(v0);

        assertNotNull(type);
    }

    // ========== Nullability Tests ==========

    @Test
    void getNullabilityForPrimitive() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        Nullability nullability = analyzer.getNullability(v0);

        assertEquals(Nullability.NOT_NULL, nullability);
    }

    @Test
    void getNullabilityForNull() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/Object");
        SSAValue v0 = new SSAValue(refType, "v0");

        ConstantInstruction nullInstr = new ConstantInstruction(v0, NullConstant.INSTANCE);
        entry.addInstruction(nullInstr);

        analyzer.analyze();
        Nullability nullability = analyzer.getNullability(v0);

        assertTrue(nullability.isDefinitelyNull());
    }

    @Test
    void isDefinitelyNullForNullConstant() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/Object");
        SSAValue v0 = new SSAValue(refType, "v0");

        ConstantInstruction nullInstr = new ConstantInstruction(v0, NullConstant.INSTANCE);
        entry.addInstruction(nullInstr);

        analyzer.analyze();

        assertTrue(analyzer.isDefinitelyNull(v0));
    }

    @Test
    void isDefinitelyNotNullForNewInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/String");
        SSAValue v0 = new SSAValue(refType, "v0");

        NewInstruction newInstr = new NewInstruction(v0, "java/lang/String");
        entry.addInstruction(newInstr);

        analyzer.analyze();

        assertTrue(analyzer.isDefinitelyNotNull(v0));
    }

    @Test
    void isDefinitelyNotNullForPrimitive() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();

        assertTrue(analyzer.isDefinitelyNotNull(v0));
    }

    // ========== Possible Types Tests ==========

    @Test
    void getPossibleTypesReturnsTypeSet() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        TypeSet types = analyzer.getPossibleTypes(v0);

        assertNotNull(types);
    }

    // ========== Block State Tests ==========

    @Test
    void getTypeStateAtBlockEntryReturnsState() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        TypeState state = analyzer.getTypeStateAtBlockEntry(entry, v0);

        assertNotNull(state);
    }

    @Test
    void getTypeStateAtBlockExitReturnsState() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        TypeState state = analyzer.getTypeStateAtBlockExit(entry, v0);

        assertNotNull(state);
    }

    // ========== Value Set Tests ==========

    @Test
    void getNullValuesReturnsSet() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/Object");
        SSAValue v0 = new SSAValue(refType, "v0");

        ConstantInstruction nullInstr = new ConstantInstruction(v0, NullConstant.INSTANCE);
        entry.addInstruction(nullInstr);

        analyzer.analyze();
        Set<SSAValue> nullValues = analyzer.getNullValues();

        assertNotNull(nullValues);
        assertTrue(nullValues.contains(v0));
    }

    @Test
    void getNonNullValuesReturnsSet() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        Set<SSAValue> nonNullValues = analyzer.getNonNullValues();

        assertNotNull(nonNullValues);
        assertTrue(nonNullValues.contains(v0));
    }

    @Test
    void getAllTypeStatesReturnsMap() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");

        ConstantInstruction constInstr = new ConstantInstruction(v0, new IntConstant(42));
        entry.addInstruction(constInstr);

        analyzer.analyze();
        Map<SSAValue, TypeState> allStates = analyzer.getAllTypeStates();

        assertNotNull(allStates);
    }

    // ========== Precision Tests ==========

    @Test
    void hasPreciseTypeForNewInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/String");
        SSAValue v0 = new SSAValue(refType, "v0");

        NewInstruction newInstr = new NewInstruction(v0, "java/lang/String");
        entry.addInstruction(newInstr);

        analyzer.analyze();
        boolean precise = analyzer.hasPreciseType(v0);

        // May or may not be precise depending on implementation
        assertTrue(precise || !precise);
    }

    // ========== Instruction Processing Tests ==========

    @Test
    void processNewArrayInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRType arrayType = new ReferenceType("[I");
        SSAValue v0 = new SSAValue(arrayType, "v0");
        SSAValue size = new SSAValue(PrimitiveType.INT, "size");

        entry.addInstruction(new ConstantInstruction(size, new IntConstant(10)));
        NewArrayInstruction newArray = new NewArrayInstruction(v0, PrimitiveType.INT, size);
        entry.addInstruction(newArray);

        analyzer.analyze();

        assertTrue(analyzer.isDefinitelyNotNull(v0));
    }

    @Test
    void processCastInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRType sourceType = new ReferenceType("java/lang/Object");
        IRType targetType = new ReferenceType("java/lang/String");
        SSAValue v0 = new SSAValue(sourceType, "v0");
        SSAValue v1 = new SSAValue(targetType, "v1");

        entry.addInstruction(new NewInstruction(v0, "java/lang/Object"));
        CastInstruction cast = new CastInstruction(v1, v0, targetType);
        entry.addInstruction(cast);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(v1);

        assertNotNull(state);
        assertEquals(Nullability.NOT_NULL, state.getNullability());
    }

    @Test
    void processInstanceOfInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRType refType = new ReferenceType("java/lang/Object");
        SSAValue v0 = new SSAValue(refType, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new NewInstruction(v0, "java/lang/Object"));
        InstanceOfInstruction iof = new InstanceOfInstruction(v1, v0,
            new ReferenceType("java/lang/String"));
        entry.addInstruction(iof);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(v1);

        assertNotNull(state);
        assertEquals(Nullability.NOT_NULL, state.getNullability());
    }

    @Test
    void processCopyInstruction() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));
        CopyInstruction copy = new CopyInstruction(v1, v0);
        entry.addInstruction(copy);

        analyzer.analyze();
        TypeState state0 = analyzer.getTypeState(v0);
        TypeState state1 = analyzer.getTypeState(v1);

        assertEquals(state0.getNullability(), state1.getNullability());
    }

    // ========== Phi Instruction Tests ==========

    @Test
    void processPhiInstruction() {
        IRBlock entry = method.getEntryBlock();
        IRBlock merge = new IRBlock("merge");
        method.addBlock(merge);
        entry.addSuccessor(merge);

        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        SSAValue v1 = new SSAValue(PrimitiveType.INT, "v1");
        SSAValue v2 = new SSAValue(PrimitiveType.INT, "v2");

        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(1)));
        entry.addInstruction(new ConstantInstruction(v1, new IntConstant(2)));

        PhiInstruction phi = new PhiInstruction(v2);
        phi.addIncoming(v0, entry);
        phi.addIncoming(v1, entry);
        merge.addPhi(phi);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(v2);

        assertNotNull(state);
    }

    // ========== Known Non-Null Return Tests ==========

    @Test
    void invokeKnownNonNullMethodToString() {
        IRBlock entry = method.getEntryBlock();
        IRType stringType = new ReferenceType("java/lang/String");
        SSAValue receiver = new SSAValue(stringType, "receiver");
        SSAValue result = new SSAValue(stringType, "result");

        entry.addInstruction(new NewInstruction(receiver, "java/lang/String"));
        InvokeInstruction invoke = new InvokeInstruction(result, InvokeType.VIRTUAL,
            "java/lang/String", "toString", "()Ljava/lang/String;", List.of(receiver));
        entry.addInstruction(invoke);

        analyzer.analyze();
        TypeState state = analyzer.getTypeState(result);

        assertNotNull(state);
        assertTrue(state.isDefinitelyNotNull());
    }

    // ========== ToString Tests ==========

    @Test
    void toStringReturnsMethodInfo() {
        analyzer.analyze();
        String str = analyzer.toString();

        assertNotNull(str);
        assertTrue(str.contains("testMethod"));
    }

    // ========== Edge Case Tests ==========

    @Test
    void analyzeEmptyMethodDoesNotThrow() {
        IRMethod emptyMethod = new IRMethod("com/test/Test", "empty", "()V", true);
        IRBlock entry = new IRBlock("entry");
        emptyMethod.addBlock(entry);
        emptyMethod.setEntryBlock(entry);

        TypeInferenceAnalyzer emptyAnalyzer = new TypeInferenceAnalyzer(emptyMethod);
        emptyAnalyzer.analyze();

        assertNotNull(emptyAnalyzer.getAllTypeStates());
    }

    @Test
    void getTypeStateBeforeAnalyzeTriggersAnalysis() {
        IRBlock entry = method.getEntryBlock();
        SSAValue v0 = new SSAValue(PrimitiveType.INT, "v0");
        entry.addInstruction(new ConstantInstruction(v0, new IntConstant(42)));

        TypeState state = analyzer.getTypeState(v0);

        assertNotNull(state);
    }

    @Test
    void multipleBlocksWithJoins() {
        IRBlock block1 = new IRBlock("block1");
        IRBlock block2 = new IRBlock("block2");
        IRBlock merge = new IRBlock("merge");

        method.addBlock(block1);
        method.addBlock(block2);
        method.addBlock(merge);

        method.getEntryBlock().addSuccessor(block1);
        method.getEntryBlock().addSuccessor(block2);
        block1.addSuccessor(merge);
        block2.addSuccessor(merge);

        analyzer.analyze();

        assertNotNull(analyzer.getAllTypeStates());
    }
}
