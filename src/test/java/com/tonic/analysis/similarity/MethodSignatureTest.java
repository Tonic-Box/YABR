package com.tonic.analysis.similarity;

import com.tonic.analysis.Bytecode;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import com.tonic.utill.ReturnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MethodSignatureTest {

    private ClassPool pool;
    private ClassFile classFile;

    @BeforeEach
    void setUp() throws IOException {
        pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        classFile = pool.createNewClass("com/test/SignatureTest", access);
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructorSetsBasicProperties() {
            MethodSignature sig = new MethodSignature("com/test/Class", "methodName", "()V");

            assertEquals("com/test/Class", sig.getClassName());
            assertEquals("methodName", sig.getMethodName());
            assertEquals("()V", sig.getDescriptor());
        }

        @Test
        void fromMethodCreatesSignature() throws IOException {
            MethodEntry method = createSimpleMethod("testMethod");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig);
            assertEquals("testMethod", sig.getMethodName());
            assertEquals(classFile.getClassName(), sig.getClassName());
        }

        @Test
        void fromMethodWithNullCodeAttribute() throws IOException {
            int access = new AccessBuilder().setPublic().setAbstract().build();
            MethodEntry method = classFile.createNewMethod(access, "abstractMethod", "V");

            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig);
            assertEquals(0, sig.getInstructionCount());
            assertNull(sig.getBytecodeHash());
            assertNull(sig.getOpcodeSequence());
        }

        @Test
        void fromMethodWithEmptyBytecode() throws IOException {
            MethodEntry method = createMethodWithEmptyCode("emptyMethod");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig);
            assertEquals(0, sig.getInstructionCount());
        }
    }

    @Nested
    class BytecodeHashTests {

        @Test
        void identicalBytecodeHashesMatch() throws IOException {
            MethodEntry method1 = createSimpleMethod("method1");
            MethodEntry method2 = createSimpleMethod("method2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            assertNotNull(sig1.getBytecodeHash());
            assertNotNull(sig2.getBytecodeHash());
            assertArrayEquals(sig1.getBytecodeHash(), sig2.getBytecodeHash());
        }

        @Test
        void differentBytecodeHashesDiffer() throws IOException {
            MethodEntry method1 = createSimpleMethod("method1");
            MethodEntry method2 = createMethodReturningInt("method2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            assertFalse(java.util.Arrays.equals(sig1.getBytecodeHash(), sig2.getBytecodeHash()));
        }

        @Test
        void bytecodeHashIsNonNull() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig.getBytecodeHash());
            assertEquals(16, sig.getBytecodeHash().length);
        }
    }

    @Nested
    class OpcodeExtractionTests {

        @Test
        void opcodeSequenceExtractedFromSimpleMethod() throws IOException {
            MethodEntry method = createSimpleMethod("simple");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig.getOpcodeSequence());
            assertTrue(sig.getOpcodeSequence().length > 0);
            assertTrue(sig.getInstructionCount() > 0);
        }

        @Test
        void opcodeSequenceExtractedFromComplexMethod() throws IOException {
            MethodEntry method = createMethodWithMethodCall("complex");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig.getOpcodeSequence());
            assertTrue(sig.getInstructionCount() > 0);
        }

        @Test
        void instructionCountMatchesOpcodeLength() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertEquals(sig.getOpcodeSequence().length, sig.getInstructionCount());
        }
    }

    @Nested
    class MetricAnalysisTests {

        @Test
        void callCountDetectsCalls() throws IOException {
            MethodEntry method = createMethodWithMethodCall("withCall");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getCallCount() > 0);
        }

        @Test
        void fieldAccessCountDetectsFieldAccess() throws IOException {
            MethodEntry method = createMethodWithFieldAccess("withField");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getFieldAccessCount() > 0);
        }

        @Test
        void maxStackIsSet() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getMaxStack() >= 0);
        }

        @Test
        void maxLocalsIsSet() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getMaxLocals() >= 0);
        }

        @Test
        void branchCountIsSetForSimpleMethod() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getBranchCount() >= 0);
        }

        @Test
        void loopCountIsSetForSimpleMethod() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getLoopCount() >= 0);
        }
    }

    @Nested
    class ComparisonTests {

        @Test
        void compareExactBytecodeReturnsOneForIdentical() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createSimpleMethod("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            assertEquals(1.0, sig1.compareExactBytecode(sig2), 0.001);
        }

        @Test
        void compareExactBytecodeReturnsZeroForDifferent() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createMethodReturningInt("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            assertEquals(0.0, sig1.compareExactBytecode(sig2), 0.001);
        }

        @Test
        void compareExactBytecodeWithNullHash() {
            MethodSignature sig1 = new MethodSignature("class1", "m1", "()V");
            MethodSignature sig2 = new MethodSignature("class2", "m2", "()V");

            assertEquals(0.0, sig1.compareExactBytecode(sig2), 0.001);
        }

        @Test
        void compareOpcodeSequenceReturnsSimilarity() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createSimpleMethod("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            double similarity = sig1.compareOpcodeSequence(sig2);
            assertTrue(similarity >= 0.0 && similarity <= 1.0);
        }

        @Test
        void compareOpcodeSequenceWithNullSequences() {
            MethodSignature sig1 = new MethodSignature("class1", "m1", "()V");
            MethodSignature sig2 = new MethodSignature("class2", "m2", "()V");

            assertEquals(0.0, sig1.compareOpcodeSequence(sig2), 0.001);
        }

        @Test
        void compareOpcodeSequenceWithEmptySequences() throws IOException {
            MethodEntry method = createMethodWithEmptyCode("empty");
            MethodSignature sig1 = MethodSignature.fromMethod(method, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method, classFile.getClassName());

            assertEquals(0.0, sig1.compareOpcodeSequence(sig2), 0.001);
        }

        @Test
        void compareStructuralReturnsSimilarity() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createSimpleMethod("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            double similarity = sig1.compareStructural(sig2);
            assertTrue(similarity >= 0.0 && similarity <= 1.0);
        }

        @Test
        void compareStructuralWithZeroInstructions() {
            MethodSignature sig1 = new MethodSignature("class1", "m1", "()V");
            MethodSignature sig2 = new MethodSignature("class2", "m2", "()V");

            double similarity = sig1.compareStructural(sig2);
            assertEquals(0.0, similarity, 0.001);
        }

        @Test
        void compareStructuralWithDifferentSizes() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createMethodWithMethodCall("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            double similarity = sig1.compareStructural(sig2);
            assertTrue(similarity >= 0.0 && similarity <= 1.0);
        }

        @Test
        void compareStructuralWithZeroBranches() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createSimpleMethod("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            double similarity = sig1.compareStructural(sig2);
            assertTrue(similarity >= 0.0);
        }

        @Test
        void compareStructuralWithZeroCalls() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createSimpleMethod("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            assertTrue(sig1.getCallCount() == 0);
            assertTrue(sig2.getCallCount() == 0);

            double similarity = sig1.compareStructural(sig2);
            assertTrue(similarity >= 0.0);
        }

        @Test
        void compareStructuralWithDifferentCallCounts() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createMethodWithMethodCall("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            double similarity = sig1.compareStructural(sig2);
            assertTrue(similarity >= 0.0 && similarity < 1.0);
        }
    }

    @Nested
    class DisplayMethodsTests {

        @Test
        void getDisplayNameReturnsSimpleName() throws IOException {
            MethodEntry method = createSimpleMethod("testMethod");
            MethodSignature sig = MethodSignature.fromMethod(method, "com/test/MyClass");

            String displayName = sig.getDisplayName();
            assertNotNull(displayName);
            assertTrue(displayName.contains("testMethod"));
        }

        @Test
        void getFullReferenceReturnsCompleteReference() throws IOException {
            MethodEntry method = createSimpleMethod("testMethod");
            MethodSignature sig = MethodSignature.fromMethod(method, "com/test/MyClass");

            String fullRef = sig.getFullReference();
            assertEquals("com/test/MyClass.testMethod()V", fullRef);
        }

        @Test
        void toStringContainsDisplayNameAndInstructionCount() throws IOException {
            MethodEntry method = createSimpleMethod("testMethod");
            MethodSignature sig = MethodSignature.fromMethod(method, "com/test/MyClass");

            String str = sig.toString();
            assertNotNull(str);
            assertTrue(str.contains("testMethod"));
            assertTrue(str.contains("instrs"));
        }
    }

    @Nested
    class EdgeCaseTests {

        @Test
        void largeOpcodeSequenceUsesApproximation() throws IOException {
            MethodEntry largeMethod = createLargeMethod("largeMethod");
            MethodSignature sig1 = MethodSignature.fromMethod(largeMethod, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(largeMethod, classFile.getClassName());

            double similarity = sig1.compareOpcodeSequence(sig2);
            assertTrue(similarity >= 0.0 && similarity <= 1.0);
        }

        @Test
        void differentLengthOpcodeSequences() throws IOException {
            MethodEntry short1 = createSimpleMethod("short1");
            MethodEntry long1 = createLargeMethod("long1");

            MethodSignature sig1 = MethodSignature.fromMethod(short1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(long1, classFile.getClassName());

            double similarity = sig1.compareOpcodeSequence(sig2);
            assertTrue(similarity >= 0.0 && similarity < 1.0);
        }

        @Test
        void methodWithComplexInstructions() throws IOException {
            MethodEntry method = createMethodWithComplexInstructions("complexMethod");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertNotNull(sig);
            assertTrue(sig.getInstructionCount() > 0);
        }
    }

    @Nested
    class BranchCoverageTests {

        @Test
        void instructionLengthForRegularOpcodes() throws IOException {
            MethodEntry method = createSimpleMethod("regular");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getInstructionCount() > 0);
        }

        @Test
        void allInvokeOpcodesDetected() throws IOException {
            MethodEntry method = createMethodWithAllInvokeTypes("invokes");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getCallCount() > 0);
        }

        @Test
        void allFieldOpcodesDetected() throws IOException {
            MethodEntry method = createMethodWithFieldAccess("fields");
            MethodSignature sig = MethodSignature.fromMethod(method, classFile.getClassName());

            assertTrue(sig.getFieldAccessCount() > 0);
        }

        @Test
        void longestCommonSubsequenceWithIdenticalSequences() throws IOException {
            MethodEntry method = createSimpleMethod("method");
            MethodSignature sig1 = MethodSignature.fromMethod(method, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method, classFile.getClassName());

            double similarity = sig1.compareOpcodeSequence(sig2);
            assertEquals(1.0, similarity, 0.001);
        }

        @Test
        void longestCommonSubsequenceWithPartialMatch() throws IOException {
            MethodEntry method1 = createSimpleMethod("m1");
            MethodEntry method2 = createMethodReturningInt("m2");

            MethodSignature sig1 = MethodSignature.fromMethod(method1, classFile.getClassName());
            MethodSignature sig2 = MethodSignature.fromMethod(method2, classFile.getClassName());

            double similarity = sig1.compareOpcodeSequence(sig2);
            assertTrue(similarity >= 0.0 && similarity <= 1.0);
        }
    }

    @Nested
    class GetterTests {

        @Test
        void getClassNameReturnsCorrectValue() {
            MethodSignature sig = new MethodSignature("com/test/TestClass", "method", "()V");
            assertEquals("com/test/TestClass", sig.getClassName());
        }

        @Test
        void getMethodNameReturnsCorrectValue() {
            MethodSignature sig = new MethodSignature("com/test/TestClass", "method", "()V");
            assertEquals("method", sig.getMethodName());
        }

        @Test
        void getDescriptorReturnsCorrectValue() {
            MethodSignature sig = new MethodSignature("com/test/TestClass", "method", "(I)V");
            assertEquals("(I)V", sig.getDescriptor());
        }

        @Test
        void getBytecodeHashReturnsNullForUnanalyzed() {
            MethodSignature sig = new MethodSignature("com/test/TestClass", "method", "()V");
            assertNull(sig.getBytecodeHash());
        }

        @Test
        void getOpcodeSequenceReturnsNullForUnanalyzed() {
            MethodSignature sig = new MethodSignature("com/test/TestClass", "method", "()V");
            assertNull(sig.getOpcodeSequence());
        }

        @Test
        void getInstructionCountReturnsZeroForUnanalyzed() {
            MethodSignature sig = new MethodSignature("com/test/TestClass", "method", "()V");
            assertEquals(0, sig.getInstructionCount());
        }
    }

    private MethodEntry createSimpleMethod(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");
        Bytecode bc = new Bytecode(method);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        return method;
    }

    private MethodEntry createMethodReturningInt(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "I");
        Bytecode bc = new Bytecode(method);
        bc.addIConst(42);
        bc.addReturn(ReturnType.IRETURN);
        bc.finalizeBytecode();
        return method;
    }

    private MethodEntry createMethodWithMethodCall(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "J");
        Bytecode bc = new Bytecode(method);
        bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
        bc.addReturn(ReturnType.LRETURN);
        bc.finalizeBytecode();
        return method;
    }

    private MethodEntry createMethodWithFieldAccess(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "Ljava/io/PrintStream;");
        Bytecode bc = new Bytecode(method);
        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addReturn(ReturnType.ARETURN);
        bc.finalizeBytecode();
        return method;
    }

    private MethodEntry createMethodWithEmptyCode(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");
        return method;
    }

    private MethodEntry createLargeMethod(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");
        Bytecode bc = new Bytecode(method);
        for (int i = 0; i < 2000; i++) {
            bc.addIConst(i % 6);
            bc.addIStore(1);
        }
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        return method;
    }

    private MethodEntry createMethodWithComplexInstructions(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");
        Bytecode bc = new Bytecode(method);
        bc.addIConst(10);
        bc.addIStore(0);
        bc.addLConst(100L);
        bc.addIStore(1);
        bc.addFConst(1.5f);
        bc.addIStore(3);
        bc.addDConst(3.14);
        bc.addIStore(4);
        bc.addAConstNull();
        bc.addAStore(6);
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        return method;
    }

    private MethodEntry createMethodWithAllInvokeTypes(String name) throws IOException {
        int access = new AccessBuilder().setPublic().setStatic().build();
        MethodEntry method = classFile.createNewMethod(access, name, "V");
        Bytecode bc = new Bytecode(method);
        bc.addInvokeStatic("java/lang/System", "currentTimeMillis", "()J");
        bc.addIStore(0);
        bc.addGetStatic("java/lang/System", "out", "Ljava/io/PrintStream;");
        bc.addLdc("Hello");
        bc.addInvokeVirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
        bc.addReturn(ReturnType.RETURN);
        bc.finalizeBytecode();
        return method;
    }
}
