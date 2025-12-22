package com.tonic.analysis.simulation.util;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.simulation.state.SimValue;
import com.tonic.analysis.ssa.ir.*;
import com.tonic.analysis.ssa.type.ArrayType;
import com.tonic.analysis.ssa.type.PrimitiveType;
import com.tonic.analysis.ssa.type.ReferenceType;
import com.tonic.analysis.ssa.value.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for StateTransitions class.
 * Tests all instruction types and stack effect calculations.
 */
class StateTransitionsTest {

    @BeforeEach
    void setUp() {
        SSAValue.resetIdCounter();
    }

    // ========== ConstantInstruction Tests ==========

    @Nested
    class ConstantInstructionTests {

        @Test
        void applyIntConstant() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            ConstantInstruction instr = new ConstantInstruction(result, IntConstant.of(42));

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
            assertEquals(PrimitiveType.INT, newState.peek().getType());
        }

        @Test
        void applyLongConstant() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            ConstantInstruction instr = new ConstantInstruction(result, LongConstant.of(100L));

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
            assertTrue(newState.peek().isWideSecondSlot());
        }

        @Test
        void applyDoubleConstant() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(PrimitiveType.DOUBLE, "r");
            ConstantInstruction instr = new ConstantInstruction(result, DoubleConstant.of(3.14));

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void applyNullConstant() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "r");
            ConstantInstruction instr = new ConstantInstruction(result, NullConstant.INSTANCE);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void getPopCountConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            ConstantInstruction instr = new ConstantInstruction(result, IntConstant.of(1));

            assertEquals(0, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountConstant() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            ConstantInstruction instr = new ConstantInstruction(result, IntConstant.of(1));

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountConstantWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            ConstantInstruction instr = new ConstantInstruction(result, LongConstant.of(1L));

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== LoadLocalInstruction Tests ==========

    @Nested
    class LoadLocalInstructionTests {

        @Test
        void applyLoadLocal() {
            SimulationState state = SimulationState.empty()
                .setLocal(0, SimValue.constant(42, PrimitiveType.INT, null));
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            LoadLocalInstruction instr = new LoadLocalInstruction(result, 0);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyLoadLocalWide() {
            SimulationState state = SimulationState.empty()
                .setLocalWide(0, SimValue.ofType(PrimitiveType.LONG, null));
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            LoadLocalInstruction instr = new LoadLocalInstruction(result, 0);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void applyLoadLocalUndefined() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            LoadLocalInstruction instr = new LoadLocalInstruction(result, 5);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
            assertNotNull(newState.peek());
        }

        @Test
        void getPopCountLoadLocal() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            LoadLocalInstruction instr = new LoadLocalInstruction(result, 0);

            assertEquals(0, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountLoadLocal() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            LoadLocalInstruction instr = new LoadLocalInstruction(result, 0);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountLoadLocalWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            LoadLocalInstruction instr = new LoadLocalInstruction(result, 0);

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== StoreLocalInstruction Tests ==========

    @Nested
    class StoreLocalInstructionTests {

        @Test
        void applyStoreLocal() {
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(value);
            SSAValue ssaValue = new SSAValue(PrimitiveType.INT, "v");
            StoreLocalInstruction instr = new StoreLocalInstruction(0, ssaValue);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
            assertTrue(newState.hasLocal(0));
        }

        @Test
        void applyStoreLocalWide() {
            SimValue value = SimValue.ofType(PrimitiveType.LONG, null);
            SimulationState state = SimulationState.empty().pushWide(value);
            SSAValue ssaValue = new SSAValue(PrimitiveType.LONG, "v");
            StoreLocalInstruction instr = new StoreLocalInstruction(0, ssaValue);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
            assertTrue(newState.hasLocal(0));
            assertTrue(newState.hasLocal(1));
        }

        @Test
        void getPopCountStoreLocal() {
            SSAValue value = new SSAValue(PrimitiveType.INT, "v");
            StoreLocalInstruction instr = new StoreLocalInstruction(0, value);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountStoreLocalWide() {
            SSAValue value = new SSAValue(PrimitiveType.LONG, "v");
            StoreLocalInstruction instr = new StoreLocalInstruction(0, value);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }
    }

    // ========== BinaryOpInstruction Tests ==========

    @Nested
    class BinaryOpInstructionTests {

        @Test
        void applyBinaryOpInt() {
            SimValue v1 = SimValue.constant(5, PrimitiveType.INT, null);
            SimValue v2 = SimValue.constant(3, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v1).push(v2);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue left = new SSAValue(PrimitiveType.INT, "l");
            SSAValue right = new SSAValue(PrimitiveType.INT, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyBinaryOpLong() {
            SimValue v1 = SimValue.ofType(PrimitiveType.LONG, null);
            SimValue v2 = SimValue.ofType(PrimitiveType.LONG, null);
            SimulationState state = SimulationState.empty().pushWide(v1).pushWide(v2);

            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue left = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue right = new SSAValue(PrimitiveType.LONG, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void getPopCountBinaryOpInt() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue left = new SSAValue(PrimitiveType.INT, "l");
            SSAValue right = new SSAValue(PrimitiveType.INT, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountBinaryOpLong() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue left = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue right = new SSAValue(PrimitiveType.LONG, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            assertEquals(4, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountBinaryOp() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue left = new SSAValue(PrimitiveType.INT, "l");
            SSAValue right = new SSAValue(PrimitiveType.INT, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountBinaryOpWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue left = new SSAValue(PrimitiveType.LONG, "l");
            SSAValue right = new SSAValue(PrimitiveType.LONG, "r2");
            BinaryOpInstruction instr = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== UnaryOpInstruction Tests ==========

    @Nested
    class UnaryOpInstructionTests {

        @Test
        void applyUnaryOpInt() {
            SimValue v = SimValue.constant(5, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.NEG, operand);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyUnaryOpLongToInt() {
            SimValue v = SimValue.ofType(PrimitiveType.LONG, null);
            SimulationState state = SimulationState.empty().pushWide(v);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue operand = new SSAValue(PrimitiveType.LONG, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.L2I, operand);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyUnaryOpIntToLong() {
            SimValue v = SimValue.constant(5, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v);

            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.I2L, operand);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void getPopCountUnaryOp() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.NEG, operand);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountUnaryOpWide() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue operand = new SSAValue(PrimitiveType.LONG, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.L2I, operand);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountUnaryOp() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.NEG, operand);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountUnaryOpWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            UnaryOpInstruction instr = new UnaryOpInstruction(result, UnaryOp.I2L, operand);

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== CastInstruction Tests ==========

    @Nested
    class CastInstructionTests {

        @Test
        void applyCastIntToLong() {
            SimValue v = SimValue.constant(5, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v);

            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue source = new SSAValue(PrimitiveType.INT, "src");
            CastInstruction instr = new CastInstruction(result, source, PrimitiveType.LONG);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void applyCastLongToInt() {
            SimValue v = SimValue.ofType(PrimitiveType.LONG, null);
            SimulationState state = SimulationState.empty().pushWide(v);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue source = new SSAValue(PrimitiveType.LONG, "src");
            CastInstruction instr = new CastInstruction(result, source, PrimitiveType.INT);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void getPopCountCast() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue source = new SSAValue(PrimitiveType.INT, "src");
            CastInstruction instr = new CastInstruction(result, source, PrimitiveType.LONG);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountCastWide() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue source = new SSAValue(PrimitiveType.LONG, "src");
            CastInstruction instr = new CastInstruction(result, source, PrimitiveType.INT);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountCast() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue source = new SSAValue(PrimitiveType.LONG, "src");
            CastInstruction instr = new CastInstruction(result, source, PrimitiveType.INT);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountCastWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue source = new SSAValue(PrimitiveType.INT, "src");
            CastInstruction instr = new CastInstruction(result, source, PrimitiveType.LONG);

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== GetFieldInstruction Tests ==========

    @Nested
    class GetFieldInstructionTests {

        @Test
        void applyGetFieldInstance() {
            SimValue obj = SimValue.ofType(new ReferenceType("com/test/A"), null);
            SimulationState state = SimulationState.empty().push(obj);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue objectRef = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "field", "I", objectRef);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyGetFieldStatic() {
            SimulationState state = SimulationState.empty();

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyGetFieldWide() {
            SimulationState state = SimulationState.empty();

            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "LONG_VAL", "J");

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void getPopCountGetFieldInstance() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue objectRef = new SSAValue(new ReferenceType("com/test/A"), "obj");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "field", "I", objectRef);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountGetFieldStatic() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "CONST", "I");

            assertEquals(0, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountGetField() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "field", "I");

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountGetFieldWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            GetFieldInstruction instr = new GetFieldInstruction(result, "com/test/A", "field", "J");

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== PutFieldInstruction Tests ==========

    @Nested
    class PutFieldInstructionTests {

        @Test
        void applyPutFieldInstance() {
            SimValue obj = SimValue.ofType(new ReferenceType("com/test/A"), null);
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(obj).push(value);

            SSAValue objectRef = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue val = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "field", "I", objectRef, val);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void applyPutFieldStatic() {
            SimValue value = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(value);

            SSAValue val = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "CONST", "I", val);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void applyPutFieldWide() {
            SimValue value = SimValue.ofType(PrimitiveType.LONG, null);
            SimulationState state = SimulationState.empty().pushWide(value);

            SSAValue val = new SSAValue(PrimitiveType.LONG, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "LONG_VAL", "J", val);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountPutFieldInstance() {
            SSAValue objectRef = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue val = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "field", "I", objectRef, val);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountPutFieldStatic() {
            SSAValue val = new SSAValue(PrimitiveType.INT, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "CONST", "I", val);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountPutFieldInstanceWide() {
            SSAValue objectRef = new SSAValue(new ReferenceType("com/test/A"), "obj");
            SSAValue val = new SSAValue(PrimitiveType.LONG, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "field", "J", objectRef, val);

            assertEquals(3, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountPutFieldStaticWide() {
            SSAValue val = new SSAValue(PrimitiveType.LONG, "val");
            PutFieldInstruction instr = new PutFieldInstruction("com/test/A", "LONG_CONST", "J", val);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }
    }

    // ========== ArrayLoadInstruction Tests ==========

    @Nested
    class ArrayLoadInstructionTests {

        @Test
        void applyArrayLoad() {
            SimValue arr = SimValue.ofType(new ArrayType(PrimitiveType.INT, 1), null);
            SimValue idx = SimValue.constant(0, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(arr).push(idx);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            ArrayLoadInstruction instr = new ArrayLoadInstruction(result, array, index);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyArrayLoadWide() {
            SimValue arr = SimValue.ofType(new ArrayType(PrimitiveType.LONG, 1), null);
            SimValue idx = SimValue.constant(0, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(arr).push(idx);

            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.LONG, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            ArrayLoadInstruction instr = new ArrayLoadInstruction(result, array, index);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void getPopCountArrayLoad() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            ArrayLoadInstruction instr = new ArrayLoadInstruction(result, array, index);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountArrayLoad() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            ArrayLoadInstruction instr = new ArrayLoadInstruction(result, array, index);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountArrayLoadWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.LONG, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            ArrayLoadInstruction instr = new ArrayLoadInstruction(result, array, index);

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== ArrayStoreInstruction Tests ==========

    @Nested
    class ArrayStoreInstructionTests {

        @Test
        void applyArrayStore() {
            SimValue arr = SimValue.ofType(new ArrayType(PrimitiveType.INT, 1), null);
            SimValue idx = SimValue.constant(0, PrimitiveType.INT, null);
            SimValue val = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(arr).push(idx).push(val);

            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction instr = new ArrayStoreInstruction(array, index, value);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void applyArrayStoreWide() {
            SimValue arr = SimValue.ofType(new ArrayType(PrimitiveType.LONG, 1), null);
            SimValue idx = SimValue.constant(0, PrimitiveType.INT, null);
            SimValue val = SimValue.ofType(PrimitiveType.LONG, null);
            SimulationState state = SimulationState.empty().push(arr).push(idx).pushWide(val);

            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.LONG, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.LONG, "val");
            ArrayStoreInstruction instr = new ArrayStoreInstruction(array, index, value);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountArrayStore() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.INT, "val");
            ArrayStoreInstruction instr = new ArrayStoreInstruction(array, index, value);

            assertEquals(3, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountArrayStoreWide() {
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.LONG, 1), "arr");
            SSAValue index = new SSAValue(PrimitiveType.INT, "idx");
            SSAValue value = new SSAValue(PrimitiveType.LONG, "val");
            ArrayStoreInstruction instr = new ArrayStoreInstruction(array, index, value);

            assertEquals(4, StateTransitions.getPopCount(instr));
        }
    }

    // ========== ArrayLengthInstruction Tests ==========

    @Nested
    class ArrayLengthInstructionTests {

        @Test
        void applyArrayLength() {
            SimValue arr = SimValue.ofType(new ArrayType(PrimitiveType.INT, 1), null);
            SimulationState state = SimulationState.empty().push(arr);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayLengthInstruction instr = new ArrayLengthInstruction(result, array);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
            assertEquals(PrimitiveType.INT, newState.peek().getType());
        }

        @Test
        void getPopCountArrayLength() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayLengthInstruction instr = new ArrayLengthInstruction(result, array);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountArrayLength() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue array = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "arr");
            ArrayLengthInstruction instr = new ArrayLengthInstruction(result, array);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }
    }

    // ========== NewInstruction Tests ==========

    @Nested
    class NewInstructionTests {

        @Test
        void applyNew() {
            SimulationState state = SimulationState.empty();

            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "r");
            NewInstruction instr = new NewInstruction(result, "java/lang/Object");

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void getPopCountNew() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "r");
            NewInstruction instr = new NewInstruction(result, "java/lang/Object");

            assertEquals(0, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountNew() {
            SSAValue result = new SSAValue(new ReferenceType("java/lang/Object"), "r");
            NewInstruction instr = new NewInstruction(result, "java/lang/Object");

            assertEquals(1, StateTransitions.getPushCount(instr));
        }
    }

    // ========== NewArrayInstruction Tests ==========

    @Nested
    class NewArrayInstructionTests {

        @Test
        void applyNewArraySingleDimension() {
            SimValue size = SimValue.constant(10, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(size);

            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "r");
            SSAValue dim = new SSAValue(PrimitiveType.INT, "size");
            NewArrayInstruction instr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim));

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyNewArrayMultiDimension() {
            SimValue size1 = SimValue.constant(10, PrimitiveType.INT, null);
            SimValue size2 = SimValue.constant(20, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(size1).push(size2);

            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "r");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            NewArrayInstruction instr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void getPopCountNewArray() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "r");
            SSAValue dim = new SSAValue(PrimitiveType.INT, "size");
            NewArrayInstruction instr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim));

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountNewArrayMultiDimension() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 2), "r");
            SSAValue dim1 = new SSAValue(PrimitiveType.INT, "d1");
            SSAValue dim2 = new SSAValue(PrimitiveType.INT, "d2");
            NewArrayInstruction instr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim1, dim2));

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountNewArray() {
            SSAValue result = new SSAValue(new ArrayType(PrimitiveType.INT, 1), "r");
            SSAValue dim = new SSAValue(PrimitiveType.INT, "size");
            NewArrayInstruction instr = new NewArrayInstruction(result, PrimitiveType.INT, List.of(dim));

            assertEquals(1, StateTransitions.getPushCount(instr));
        }
    }

    // ========== InvokeInstruction Tests ==========

    @Nested
    class InvokeInstructionTests {

        @Test
        void applyInvokeStatic() {
            SSAValue arg = new SSAValue(PrimitiveType.INT, "arg");
            SimValue argVal = SimValue.constant(5, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(argVal);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            InvokeInstruction instr = new InvokeInstruction(
                result, InvokeType.STATIC, "java/lang/Math", "abs", "(I)I", List.of(arg)
            );

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyInvokeVirtual() {
            SSAValue receiver = new SSAValue(new ReferenceType("java/lang/String"), "str");
            SimValue objVal = SimValue.ofType(new ReferenceType("java/lang/String"), null);
            SimulationState state = SimulationState.empty().push(objVal);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            InvokeInstruction instr = new InvokeInstruction(
                result, InvokeType.VIRTUAL, "java/lang/String", "length", "()I", List.of(receiver)
            );

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyInvokeVoid() {
            SSAValue receiver = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            SimValue objVal = SimValue.ofType(new ReferenceType("java/lang/Object"), null);
            SimulationState state = SimulationState.empty().push(objVal);

            InvokeInstruction instr = new InvokeInstruction(
                InvokeType.VIRTUAL, "java/lang/Object", "wait", "()V", List.of(receiver)
            );

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void applyInvokeWithMultipleArgs() {
            SSAValue arg1 = new SSAValue(PrimitiveType.INT, "arg1");
            SSAValue arg2 = new SSAValue(PrimitiveType.INT, "arg2");
            SimValue val1 = SimValue.constant(10, PrimitiveType.INT, null);
            SimValue val2 = SimValue.constant(5, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(val1).push(val2);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            InvokeInstruction instr = new InvokeInstruction(
                result, InvokeType.STATIC, "com/test/A", "method", "(II)I", List.of(arg1, arg2)
            );

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void getPopCountInvokeStatic() {
            SSAValue arg = new SSAValue(PrimitiveType.INT, "arg");
            InvokeInstruction instr = new InvokeInstruction(
                InvokeType.STATIC, "A", "m", "(I)V", List.of(arg)
            );

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountInvokeVirtual() {
            SSAValue receiver = new SSAValue(new ReferenceType("A"), "obj");
            InvokeInstruction instr = new InvokeInstruction(
                InvokeType.VIRTUAL, "A", "m", "()V", List.of(receiver)
            );

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountInvokeWithWideArgs() {
            SSAValue receiver = new SSAValue(new ReferenceType("A"), "obj");
            SSAValue arg1 = new SSAValue(PrimitiveType.LONG, "arg1");
            SSAValue arg2 = new SSAValue(PrimitiveType.INT, "arg2");
            InvokeInstruction instr = new InvokeInstruction(
                InvokeType.VIRTUAL, "A", "m", "(JI)V", List.of(receiver, arg1, arg2)
            );

            assertEquals(4, StateTransitions.getPopCount(instr)); // 1 receiver + 2 for long + 1 for int
        }

        @Test
        void getPushCountInvokeVoid() {
            InvokeInstruction instr = new InvokeInstruction(
                InvokeType.STATIC, "A", "m", "()V", List.of()
            );

            assertEquals(0, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountInvokeReturnsInt() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            InvokeInstruction instr = new InvokeInstruction(
                result, InvokeType.STATIC, "A", "m", "()I", List.of()
            );

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountInvokeReturnsLong() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "r");
            InvokeInstruction instr = new InvokeInstruction(
                result, InvokeType.STATIC, "A", "m", "()J", List.of()
            );

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== ReturnInstruction Tests ==========

    @Nested
    class ReturnInstructionTests {

        @Test
        void applyReturnVoid() {
            SimulationState state = SimulationState.empty();
            ReturnInstruction instr = new ReturnInstruction(null);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void applyReturnValue() {
            SimValue val = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(val);
            SSAValue returnValue = new SSAValue(PrimitiveType.INT, "ret");
            ReturnInstruction instr = new ReturnInstruction(returnValue);

            SimulationState newState = StateTransitions.apply(state, instr);

            // Return doesn't modify state
            assertEquals(1, newState.stackDepth());
        }
    }

    // ========== ThrowInstruction Tests ==========

    @Nested
    class ThrowInstructionTests {

        @Test
        void applyThrow() {
            SimValue ex = SimValue.ofType(new ReferenceType("java/lang/Exception"), null);
            SimulationState state = SimulationState.empty().push(ex);
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction instr = new ThrowInstruction(exception);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountThrow() {
            SSAValue exception = new SSAValue(new ReferenceType("java/lang/Exception"), "ex");
            ThrowInstruction instr = new ThrowInstruction(exception);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }
    }

    // ========== BranchInstruction Tests ==========

    @Nested
    class BranchInstructionTests {

        @Test
        void applyBranchTwoOperands() {
            SimValue v1 = SimValue.constant(5, PrimitiveType.INT, null);
            SimValue v2 = SimValue.constant(3, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v1).push(v2);

            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            BranchInstruction instr = new BranchInstruction(CompareOp.EQ, left, right, null, null);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void applyBranchSingleOperand() {
            SimValue v = SimValue.constant(0, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v);

            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            BranchInstruction instr = new BranchInstruction(CompareOp.EQ, operand, null, null);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountBranchTwoOperands() {
            SSAValue left = new SSAValue(PrimitiveType.INT, "left");
            SSAValue right = new SSAValue(PrimitiveType.INT, "right");
            BranchInstruction instr = new BranchInstruction(CompareOp.EQ, left, right, null, null);

            assertEquals(2, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPopCountBranchSingleOperand() {
            SSAValue operand = new SSAValue(PrimitiveType.INT, "op");
            BranchInstruction instr = new BranchInstruction(CompareOp.EQ, operand, null, null);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }
    }

    // ========== GotoInstruction Tests ==========

    @Nested
    class GotoInstructionTests {

        @Test
        void applyGoto() {
            SimValue v = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v);
            GotoInstruction instr = new GotoInstruction(null);

            SimulationState newState = StateTransitions.apply(state, instr);

            // Goto has no stack effect
            assertEquals(1, newState.stackDepth());
        }
    }

    // ========== SwitchInstruction Tests ==========

    @Nested
    class SwitchInstructionTests {

        @Test
        void applySwitch() {
            SimValue key = SimValue.constant(1, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(key);
            SSAValue keyVal = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction instr = new SwitchInstruction(keyVal, null);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountSwitch() {
            SSAValue keyVal = new SSAValue(PrimitiveType.INT, "key");
            SwitchInstruction instr = new SwitchInstruction(keyVal, null);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }
    }

    // ========== InstanceOfInstruction Tests ==========

    @Nested
    class InstanceOfInstructionTests {

        @Test
        void applyInstanceOf() {
            SimValue obj = SimValue.ofType(new ReferenceType("java/lang/Object"), null);
            SimulationState state = SimulationState.empty().push(obj);

            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instr = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
            assertEquals(PrimitiveType.INT, newState.peek().getType());
        }

        @Test
        void getPopCountInstanceOf() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instr = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            assertEquals(1, StateTransitions.getPopCount(instr));
        }

        @Test
        void getPushCountInstanceOf() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "r");
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "obj");
            InstanceOfInstruction instr = new InstanceOfInstruction(result, object, new ReferenceType("java/lang/String"));

            assertEquals(1, StateTransitions.getPushCount(instr));
        }
    }

    // ========== MonitorEnterInstruction Tests ==========

    @Nested
    class MonitorEnterInstructionTests {

        @Test
        void applyMonitorEnter() {
            SimValue obj = SimValue.ofType(new ReferenceType("java/lang/Object"), null);
            SimulationState state = SimulationState.empty().push(obj);
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction instr = new MonitorEnterInstruction(object);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountMonitorEnter() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorEnterInstruction instr = new MonitorEnterInstruction(object);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }
    }

    // ========== MonitorExitInstruction Tests ==========

    @Nested
    class MonitorExitInstructionTests {

        @Test
        void applyMonitorExit() {
            SimValue obj = SimValue.ofType(new ReferenceType("java/lang/Object"), null);
            SimulationState state = SimulationState.empty().push(obj);
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction instr = new MonitorExitInstruction(object);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(0, newState.stackDepth());
        }

        @Test
        void getPopCountMonitorExit() {
            SSAValue object = new SSAValue(new ReferenceType("java/lang/Object"), "lock");
            MonitorExitInstruction instr = new MonitorExitInstruction(object);

            assertEquals(1, StateTransitions.getPopCount(instr));
        }
    }

    // ========== PhiInstruction Tests ==========

    @Nested
    class PhiInstructionTests {

        @Test
        void applyPhi() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            PhiInstruction instr = new PhiInstruction(result);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(1, newState.stackDepth());
        }

        @Test
        void applyPhiWide() {
            SimulationState state = SimulationState.empty();
            SSAValue result = new SSAValue(PrimitiveType.LONG, "phi");
            PhiInstruction instr = new PhiInstruction(result);

            SimulationState newState = StateTransitions.apply(state, instr);

            assertEquals(2, newState.stackDepth());
        }

        @Test
        void getPushCountPhi() {
            SSAValue result = new SSAValue(PrimitiveType.INT, "phi");
            PhiInstruction instr = new PhiInstruction(result);

            assertEquals(1, StateTransitions.getPushCount(instr));
        }

        @Test
        void getPushCountPhiWide() {
            SSAValue result = new SSAValue(PrimitiveType.LONG, "phi");
            PhiInstruction instr = new PhiInstruction(result);

            assertEquals(2, StateTransitions.getPushCount(instr));
        }
    }

    // ========== CopyInstruction Tests ==========

    @Nested
    class CopyInstructionTests {

        @Test
        void applyCopy() {
            SimValue v = SimValue.constant(42, PrimitiveType.INT, null);
            SimulationState state = SimulationState.empty().push(v);
            SSAValue result = new SSAValue(PrimitiveType.INT, "copy");
            SSAValue source = new SSAValue(PrimitiveType.INT, "src");
            CopyInstruction instr = new CopyInstruction(result, source);

            SimulationState newState = StateTransitions.apply(state, instr);

            // Copy has no stack effect in SSA
            assertEquals(1, newState.stackDepth());
        }
    }

    // ========== Unknown Instruction Tests ==========

    @Nested
    class UnknownInstructionTests {

        @Test
        void applyUnknownInstruction() {
            SimulationState state = SimulationState.empty()
                .push(SimValue.constant(42, PrimitiveType.INT, null));

            // Use a mock instruction that doesn't match any known type
            IRInstruction unknownInstr = new IRInstruction() {
                @Override
                public List<Value> getOperands() {
                    return List.of();
                }

                @Override
                public void replaceOperand(Value oldValue, Value newValue) {
                }

                @Override
                public <T> T accept(com.tonic.analysis.ssa.visitor.IRVisitor<T> visitor) {
                    return null;
                }

                @Override
                public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
                    return null;
                }
            };

            SimulationState newState = StateTransitions.apply(state, unknownInstr);

            // Unknown instruction returns state unchanged
            assertEquals(1, newState.stackDepth());
            assertEquals(state, newState);
        }

        @Test
        void getPopCountUnknown() {
            IRInstruction unknownInstr = new IRInstruction() {
                @Override
                public List<Value> getOperands() {
                    return List.of();
                }

                @Override
                public void replaceOperand(Value oldValue, Value newValue) {
                }

                @Override
                public <T> T accept(com.tonic.analysis.ssa.visitor.IRVisitor<T> visitor) {
                    return null;
                }

                @Override
                public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
                    return null;
                }
            };

            assertEquals(0, StateTransitions.getPopCount(unknownInstr));
        }

        @Test
        void getPushCountUnknown() {
            IRInstruction unknownInstr = new IRInstruction() {
                @Override
                public List<Value> getOperands() {
                    return List.of();
                }

                @Override
                public void replaceOperand(Value oldValue, Value newValue) {
                }

                @Override
                public <T> T accept(com.tonic.analysis.ssa.visitor.IRVisitor<T> visitor) {
                    return null;
                }

                @Override
                public IRInstruction copyWithNewOperands(SSAValue newResult, List<Value> newOperands) {
                    return null;
                }
            };

            assertEquals(0, StateTransitions.getPushCount(unknownInstr));
        }
    }
}
