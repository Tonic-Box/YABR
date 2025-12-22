package com.tonic.analysis.source.recovery;

import com.tonic.analysis.source.ast.type.*;
import com.tonic.analysis.ssa.ir.BinaryOp;
import com.tonic.analysis.ssa.ir.BinaryOpInstruction;
import com.tonic.analysis.ssa.ir.InstanceOfInstruction;
import com.tonic.analysis.ssa.type.*;
import com.tonic.analysis.ssa.value.IntConstant;
import com.tonic.analysis.ssa.value.SSAValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TypeRecoverer class.
 * Tests all methods including recoverType() for primitives, references, arrays,
 * computeCommonType() for type unification, and computeWidestPrimitive() for type promotion.
 */
class TypeRecovererTest {

    private TypeRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new TypeRecoverer();
        SSAValue.resetIdCounter();
    }

    // ========== Recovery from SSAValue Tests ==========

    @Nested
    class RecoverFromSSAValueTests {

        @Test
        void recoverType_nullSSAValue_returnsVoid() {
            SourceType result = recoverer.recoverType((SSAValue) null);
            assertEquals(VoidSourceType.INSTANCE, result);
            assertTrue(result.isVoid());
        }

        @Test
        void recoverType_ssaValueWithNullType_returnsVoid() {
            SSAValue value = new SSAValue(null);
            SourceType result = recoverer.recoverType(value);
            assertEquals(VoidSourceType.INSTANCE, result);
            assertTrue(result.isVoid());
        }

        @Test
        void recoverType_ssaValueWithIntType_returnsInt() {
            SSAValue value = new SSAValue(PrimitiveType.INT);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.INT, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithLongType_returnsLong() {
            SSAValue value = new SSAValue(PrimitiveType.LONG);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.LONG, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithFloatType_returnsFloat() {
            SSAValue value = new SSAValue(PrimitiveType.FLOAT);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.FLOAT, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithDoubleType_returnsDouble() {
            SSAValue value = new SSAValue(PrimitiveType.DOUBLE);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.DOUBLE, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithBooleanType_returnsBoolean() {
            SSAValue value = new SSAValue(PrimitiveType.BOOLEAN);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.BOOLEAN, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithByteType_returnsByte() {
            SSAValue value = new SSAValue(PrimitiveType.BYTE);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.BYTE, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithShortType_returnsShort() {
            SSAValue value = new SSAValue(PrimitiveType.SHORT);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.SHORT, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithCharType_returnsChar() {
            SSAValue value = new SSAValue(PrimitiveType.CHAR);
            SourceType result = recoverer.recoverType(value);
            assertEquals(PrimitiveSourceType.CHAR, result);
            assertTrue(result.isPrimitive());
        }

        @Test
        void recoverType_ssaValueWithReferenceType_returnsReferenceSourceType() {
            ReferenceType refType = new ReferenceType("java/lang/String");
            SSAValue value = new SSAValue(refType);
            SourceType result = recoverer.recoverType(value);

            assertTrue(result.isReference());
            ReferenceSourceType refSource = (ReferenceSourceType) result;
            assertEquals("java/lang/String", refSource.getInternalName());
        }

        @Test
        void recoverType_ssaValueWithArrayType_returnsArraySourceType() {
            ArrayType arrayType = new ArrayType(PrimitiveType.INT, 1);
            SSAValue value = new SSAValue(arrayType);
            SourceType result = recoverer.recoverType(value);

            assertTrue(result.isArray());
            ArraySourceType arraySource = (ArraySourceType) result;
            assertEquals(1, arraySource.getDimensions());
            assertEquals(PrimitiveSourceType.INT, arraySource.getElementType());
        }

        @Test
        void recoverType_ssaValueWithMultiDimensionalArray_returnsCorrectArrayType() {
            ArrayType arrayType = new ArrayType(PrimitiveType.DOUBLE, 3);
            SSAValue value = new SSAValue(arrayType);
            SourceType result = recoverer.recoverType(value);

            assertTrue(result.isArray());
            ArraySourceType arraySource = (ArraySourceType) result;
            assertEquals(3, arraySource.getDimensions());
            assertEquals(PrimitiveSourceType.DOUBLE, arraySource.getElementType());
        }
    }

    // ========== Recovery with Instruction Context Tests ==========

    @Nested
    class RecoverWithInstructionContextTests {

        @Test
        void recoverTypeWithContext_instanceOfInstruction_returnsBoolean() {
            SSAValue result = new SSAValue(PrimitiveType.INT);
            SSAValue objectRef = new SSAValue(ReferenceType.OBJECT);
            InstanceOfInstruction instanceOf = new InstanceOfInstruction(
                result, objectRef, ReferenceType.STRING
            );
            result.setDefinition(instanceOf);

            SourceType recovered = recoverer.recoverTypeWithInstructionContext(result);
            assertEquals(PrimitiveSourceType.BOOLEAN, recovered);
        }

        @Test
        void recoverTypeWithContext_binaryOpAnd_withBooleanOperand_returnsBoolean() {
            SSAValue left = new SSAValue(PrimitiveType.INT);
            SSAValue leftDef = new SSAValue(PrimitiveType.INT);
            InstanceOfInstruction instanceOf = new InstanceOfInstruction(
                leftDef, new SSAValue(ReferenceType.OBJECT), ReferenceType.STRING
            );
            leftDef.setDefinition(instanceOf);

            SSAValue right = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction andOp = new BinaryOpInstruction(result, BinaryOp.AND, leftDef, right);
            result.setDefinition(andOp);

            SourceType recovered = recoverer.recoverTypeWithInstructionContext(result);
            assertEquals(PrimitiveSourceType.BOOLEAN, recovered);
        }

        @Test
        void recoverTypeWithContext_binaryOpOr_withBooleanOperand_returnsBoolean() {
            SSAValue left = new SSAValue(PrimitiveType.INT);
            SSAValue leftDef = new SSAValue(PrimitiveType.INT);
            InstanceOfInstruction instanceOf = new InstanceOfInstruction(
                leftDef, new SSAValue(ReferenceType.OBJECT), ReferenceType.STRING
            );
            leftDef.setDefinition(instanceOf);

            SSAValue right = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction orOp = new BinaryOpInstruction(result, BinaryOp.OR, leftDef, right);
            result.setDefinition(orOp);

            SourceType recovered = recoverer.recoverTypeWithInstructionContext(result);
            assertEquals(PrimitiveSourceType.BOOLEAN, recovered);
        }

        @Test
        void recoverTypeWithContext_binaryOpXor_withBooleanOperand_returnsBoolean() {
            SSAValue left = new SSAValue(PrimitiveType.INT);
            SSAValue leftDef = new SSAValue(PrimitiveType.INT);
            InstanceOfInstruction instanceOf = new InstanceOfInstruction(
                leftDef, new SSAValue(ReferenceType.OBJECT), ReferenceType.STRING
            );
            leftDef.setDefinition(instanceOf);

            SSAValue right = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction xorOp = new BinaryOpInstruction(result, BinaryOp.XOR, leftDef, right);
            result.setDefinition(xorOp);

            SourceType recovered = recoverer.recoverTypeWithInstructionContext(result);
            assertEquals(PrimitiveSourceType.BOOLEAN, recovered);
        }

        @Test
        void recoverTypeWithContext_binaryOpAnd_withoutBooleanOperand_returnsInt() {
            SSAValue left = new SSAValue(PrimitiveType.INT);
            SSAValue right = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction andOp = new BinaryOpInstruction(result, BinaryOp.AND, left, right);
            result.setDefinition(andOp);

            SourceType recovered = recoverer.recoverTypeWithInstructionContext(result);
            assertEquals(PrimitiveSourceType.INT, recovered);
        }

        @Test
        void recoverTypeWithContext_binaryOpAdd_returnsOriginalType() {
            SSAValue left = new SSAValue(PrimitiveType.INT);
            SSAValue right = new SSAValue(PrimitiveType.INT);
            SSAValue result = new SSAValue(PrimitiveType.INT);
            BinaryOpInstruction addOp = new BinaryOpInstruction(result, BinaryOp.ADD, left, right);
            result.setDefinition(addOp);

            SourceType recovered = recoverer.recoverTypeWithInstructionContext(result);
            assertEquals(PrimitiveSourceType.INT, recovered);
        }
    }

    // ========== Recovery from Value Tests ==========

    @Nested
    class RecoverFromValueTests {

        @Test
        void recoverType_nullValue_returnsVoid() {
            SourceType result = recoverer.recoverType((com.tonic.analysis.ssa.value.Value) null);
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void recoverType_constantWithIntType_returnsInt() {
            IntConstant constant = IntConstant.of(42);
            SourceType result = recoverer.recoverType(constant);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void recoverType_constantWithNullType_returnsVoid() {
            com.tonic.analysis.ssa.value.Constant constant = new com.tonic.analysis.ssa.value.Constant() {
                @Override
                public IRType getType() {
                    return null;
                }

                @Override
                public Object getValue() {
                    return 5;
                }
            };
            SourceType result = recoverer.recoverType(constant);
            assertEquals(VoidSourceType.INSTANCE, result);
        }
    }

    // ========== Recovery from IRType Tests ==========

    @Nested
    class RecoverFromIRTypeTests {

        @Test
        void recoverType_primitiveIRType_int_returnsInt() {
            SourceType result = recoverer.recoverType(PrimitiveType.INT);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void recoverType_primitiveIRType_long_returnsLong() {
            SourceType result = recoverer.recoverType(PrimitiveType.LONG);
            assertEquals(PrimitiveSourceType.LONG, result);
        }

        @Test
        void recoverType_referenceIRType_returnsReferenceSourceType() {
            ReferenceType refType = new ReferenceType("java/util/List");
            SourceType result = recoverer.recoverType(refType);

            assertTrue(result.isReference());
            ReferenceSourceType refSource = (ReferenceSourceType) result;
            assertEquals("java/util/List", refSource.getInternalName());
        }

        @Test
        void recoverType_arrayIRType_returnsArraySourceType() {
            ArrayType arrayType = new ArrayType(PrimitiveType.BYTE, 2);
            SourceType result = recoverer.recoverType(arrayType);

            assertTrue(result.isArray());
            ArraySourceType arraySource = (ArraySourceType) result;
            assertEquals(2, arraySource.getDimensions());
            assertEquals(PrimitiveSourceType.BYTE, arraySource.getElementType());
        }
    }

    // ========== Recovery from Descriptor Tests ==========

    @Nested
    class RecoverFromDescriptorTests {

        @Test
        void recoverType_nullDescriptor_returnsVoid() {
            SourceType result = recoverer.recoverType((String) null);
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void recoverType_emptyDescriptor_returnsVoid() {
            SourceType result = recoverer.recoverType("");
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void recoverType_voidDescriptor_returnsVoid() {
            SourceType result = recoverer.recoverType("V");
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void recoverType_booleanDescriptor_returnsBoolean() {
            SourceType result = recoverer.recoverType("Z");
            assertEquals(PrimitiveSourceType.BOOLEAN, result);
        }

        @Test
        void recoverType_byteDescriptor_returnsByte() {
            SourceType result = recoverer.recoverType("B");
            assertEquals(PrimitiveSourceType.BYTE, result);
        }

        @Test
        void recoverType_charDescriptor_returnsChar() {
            SourceType result = recoverer.recoverType("C");
            assertEquals(PrimitiveSourceType.CHAR, result);
        }

        @Test
        void recoverType_shortDescriptor_returnsShort() {
            SourceType result = recoverer.recoverType("S");
            assertEquals(PrimitiveSourceType.SHORT, result);
        }

        @Test
        void recoverType_intDescriptor_returnsInt() {
            SourceType result = recoverer.recoverType("I");
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void recoverType_longDescriptor_returnsLong() {
            SourceType result = recoverer.recoverType("J");
            assertEquals(PrimitiveSourceType.LONG, result);
        }

        @Test
        void recoverType_floatDescriptor_returnsFloat() {
            SourceType result = recoverer.recoverType("F");
            assertEquals(PrimitiveSourceType.FLOAT, result);
        }

        @Test
        void recoverType_doubleDescriptor_returnsDouble() {
            SourceType result = recoverer.recoverType("D");
            assertEquals(PrimitiveSourceType.DOUBLE, result);
        }

        @Test
        void recoverType_referenceDescriptor_returnsReferenceSourceType() {
            SourceType result = recoverer.recoverType("Ljava/lang/String;");

            assertTrue(result.isReference());
            ReferenceSourceType refSource = (ReferenceSourceType) result;
            assertEquals("java/lang/String", refSource.getInternalName());
        }

        @Test
        void recoverType_referenceDescriptorWithoutSemicolon_returnsReferenceSourceType() {
            SourceType result = recoverer.recoverType("Ljava/lang/Object");

            assertTrue(result.isReference());
            ReferenceSourceType refSource = (ReferenceSourceType) result;
            assertEquals("java/lang/Object", refSource.getInternalName());
        }

        @Test
        void recoverType_arrayDescriptor_returnsArraySourceType() {
            SourceType result = recoverer.recoverType("[I");

            assertTrue(result.isArray());
            ArraySourceType arraySource = (ArraySourceType) result;
            assertEquals(1, arraySource.getDimensions());
            assertEquals(PrimitiveSourceType.INT, arraySource.getElementType());
        }

        @Test
        void recoverType_multiDimensionalArrayDescriptor_returnsArraySourceType() {
            SourceType result = recoverer.recoverType("[[Ljava/lang/String;");

            assertTrue(result.isArray());
            ArraySourceType arraySource = (ArraySourceType) result;
            assertEquals(2, arraySource.getTotalDimensions());
            assertTrue(arraySource.getElementType().isReference());
        }

        @Test
        void recoverType_unknownDescriptor_returnsVoid() {
            SourceType result = recoverer.recoverType("X");
            assertEquals(VoidSourceType.INSTANCE, result);
        }
    }

    // ========== Common Type Computation Tests ==========

    @Nested
    class ComputeCommonTypeTests {

        @Test
        void computeCommonType_nullCollection_returnsVoid() {
            SourceType result = recoverer.computeCommonType(null);
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void computeCommonType_emptyCollection_returnsVoid() {
            SourceType result = recoverer.computeCommonType(Collections.emptyList());
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void computeCommonType_singleType_returnsThatType() {
            Collection<SourceType> types = Collections.singletonList(PrimitiveSourceType.INT);
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void computeCommonType_identicalTypes_returnsCommonType() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.INT,
                PrimitiveSourceType.INT,
                PrimitiveSourceType.INT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void computeCommonType_allVoidTypes_returnsVoid() {
            Collection<SourceType> types = Arrays.asList(
                VoidSourceType.INSTANCE,
                VoidSourceType.INSTANCE
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(VoidSourceType.INSTANCE, result);
        }

        @Test
        void computeCommonType_mixedWithVoid_ignoresVoid() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.INT,
                VoidSourceType.INSTANCE,
                PrimitiveSourceType.INT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void computeCommonType_mixedWithNull_ignoresNull() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.LONG,
                null,
                PrimitiveSourceType.LONG
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.LONG, result);
        }

        @Test
        void computeCommonType_byteAndShort_returnsShort() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.BYTE,
                PrimitiveSourceType.SHORT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.SHORT, result);
        }

        @Test
        void computeCommonType_byteAndInt_returnsInt() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.BYTE,
                PrimitiveSourceType.INT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void computeCommonType_intAndLong_returnsLong() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.INT,
                PrimitiveSourceType.LONG
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.LONG, result);
        }

        @Test
        void computeCommonType_longAndFloat_returnsFloat() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.LONG,
                PrimitiveSourceType.FLOAT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.FLOAT, result);
        }

        @Test
        void computeCommonType_floatAndDouble_returnsDouble() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.FLOAT,
                PrimitiveSourceType.DOUBLE
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.DOUBLE, result);
        }

        @Test
        void computeCommonType_intAndDouble_returnsDouble() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.INT,
                PrimitiveSourceType.DOUBLE
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.DOUBLE, result);
        }

        @Test
        void computeCommonType_charAndInt_returnsInt() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.CHAR,
                PrimitiveSourceType.INT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.INT, result);
        }

        @Test
        void computeCommonType_booleanAndInt_returnsObject() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.BOOLEAN,
                PrimitiveSourceType.INT
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(ReferenceSourceType.OBJECT, result);
        }

        @Test
        void computeCommonType_booleanOnly_returnsBoolean() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.BOOLEAN,
                PrimitiveSourceType.BOOLEAN
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.BOOLEAN, result);
        }

        @Test
        void computeCommonType_sameReferenceTypes_returnsCommonType() {
            ReferenceSourceType stringType = new ReferenceSourceType("java/lang/String");
            Collection<SourceType> types = Arrays.asList(stringType, stringType);
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(stringType, result);
        }

        @Test
        void computeCommonType_differentReferenceTypes_returnsObject() {
            Collection<SourceType> types = Arrays.asList(
                new ReferenceSourceType("java/lang/String"),
                new ReferenceSourceType("java/lang/Integer")
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(ReferenceSourceType.OBJECT, result);
        }

        @Test
        void computeCommonType_sameDimensionArrays_sameElementType_returnsArray() {
            ArraySourceType intArray = new ArraySourceType(PrimitiveSourceType.INT, 1);
            Collection<SourceType> types = Arrays.asList(intArray, intArray);
            SourceType result = recoverer.computeCommonType(types);

            assertTrue(result.isArray());
            ArraySourceType arrayResult = (ArraySourceType) result;
            assertEquals(1, arrayResult.getDimensions());
            assertEquals(PrimitiveSourceType.INT, arrayResult.getElementType());
        }

        @Test
        void computeCommonType_sameDimensionArrays_differentElementTypes_returnsArrayWithCommonElement() {
            Collection<SourceType> types = Arrays.asList(
                new ArraySourceType(PrimitiveSourceType.BYTE, 1),
                new ArraySourceType(PrimitiveSourceType.INT, 1)
            );
            SourceType result = recoverer.computeCommonType(types);

            assertTrue(result.isArray());
            ArraySourceType arrayResult = (ArraySourceType) result;
            assertEquals(1, arrayResult.getDimensions());
            assertEquals(PrimitiveSourceType.INT, arrayResult.getElementType());
        }

        @Test
        void computeCommonType_differentDimensionArrays_returnsObject() {
            Collection<SourceType> types = Arrays.asList(
                new ArraySourceType(PrimitiveSourceType.INT, 1),
                new ArraySourceType(PrimitiveSourceType.INT, 2)
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(ReferenceSourceType.OBJECT, result);
        }

        @Test
        void computeCommonType_mixedPrimitiveAndReference_returnsObject() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.INT,
                new ReferenceSourceType("java/lang/String")
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(ReferenceSourceType.OBJECT, result);
        }

        @Test
        void computeCommonType_mixedArrayAndReference_returnsObject() {
            Collection<SourceType> types = Arrays.asList(
                new ArraySourceType(PrimitiveSourceType.INT, 1),
                new ReferenceSourceType("java/lang/String")
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(ReferenceSourceType.OBJECT, result);
        }

        @Test
        void computeCommonType_allPrimitiveTypes_returnsDouble() {
            Collection<SourceType> types = Arrays.asList(
                PrimitiveSourceType.BYTE,
                PrimitiveSourceType.SHORT,
                PrimitiveSourceType.CHAR,
                PrimitiveSourceType.INT,
                PrimitiveSourceType.LONG,
                PrimitiveSourceType.FLOAT,
                PrimitiveSourceType.DOUBLE
            );
            SourceType result = recoverer.computeCommonType(types);
            assertEquals(PrimitiveSourceType.DOUBLE, result);
        }
    }
}
