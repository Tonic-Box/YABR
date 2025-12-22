package com.tonic.analysis.callgraph;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.ssa.ir.InvokeType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the CallSite class.
 * Tests cover construction, property access, call type classification, equality, and edge cases.
 */
class CallSiteTest {

    // ========== Test Data Factory Methods ==========

    private MethodReference createMethodRef(String owner, String name, String descriptor) {
        return new MethodReference(owner, name, descriptor);
    }

    private CallSite createCallSite(String callerOwner, String callerName, String targetOwner,
                                   String targetName, InvokeType invokeType) {
        MethodReference caller = createMethodRef(callerOwner, callerName, "()V");
        MethodReference target = createMethodRef(targetOwner, targetName, "()V");
        return new CallSite(caller, target, invokeType);
    }

    private CallSite createCallSiteWithOffset(String callerOwner, String callerName, String targetOwner,
                                             String targetName, InvokeType invokeType, int offset) {
        MethodReference caller = createMethodRef(callerOwner, callerName, "()V");
        MethodReference target = createMethodRef(targetOwner, targetName, "()V");
        return new CallSite(caller, target, invokeType, offset);
    }

    // ========== Construction Tests ==========

    @Nested
    class ConstructionTests {

        @Test
        void constructWithThreeParameters() {
            MethodReference caller = createMethodRef("com/test/Caller", "method1", "()V");
            MethodReference target = createMethodRef("com/test/Target", "method2", "()V");
            InvokeType invokeType = InvokeType.VIRTUAL;

            CallSite site = new CallSite(caller, target, invokeType);

            assertNotNull(site);
            assertEquals(caller, site.getCaller());
            assertEquals(target, site.getTarget());
            assertEquals(invokeType, site.getInvokeType());
            assertEquals(-1, site.getBytecodeOffset());
        }

        @Test
        void constructWithFourParameters() {
            MethodReference caller = createMethodRef("com/test/Caller", "method1", "()V");
            MethodReference target = createMethodRef("com/test/Target", "method2", "()V");
            InvokeType invokeType = InvokeType.STATIC;
            int offset = 42;

            CallSite site = new CallSite(caller, target, invokeType, offset);

            assertNotNull(site);
            assertEquals(caller, site.getCaller());
            assertEquals(target, site.getTarget());
            assertEquals(invokeType, site.getInvokeType());
            assertEquals(offset, site.getBytecodeOffset());
        }

        @Test
        void constructWithZeroOffset() {
            MethodReference caller = createMethodRef("com/test/A", "a", "()V");
            MethodReference target = createMethodRef("com/test/B", "b", "()V");

            CallSite site = new CallSite(caller, target, InvokeType.INTERFACE, 0);

            assertEquals(0, site.getBytecodeOffset());
        }

        @Test
        void constructWithNegativeOffset() {
            MethodReference caller = createMethodRef("com/test/A", "a", "()V");
            MethodReference target = createMethodRef("com/test/B", "b", "()V");

            CallSite site = new CallSite(caller, target, InvokeType.SPECIAL, -1);

            assertEquals(-1, site.getBytecodeOffset());
        }

        @Test
        void constructWithLargeOffset() {
            MethodReference caller = createMethodRef("com/test/A", "a", "()V");
            MethodReference target = createMethodRef("com/test/B", "b", "()V");

            CallSite site = new CallSite(caller, target, InvokeType.VIRTUAL, 65535);

            assertEquals(65535, site.getBytecodeOffset());
        }
    }

    // ========== Property Access Tests ==========

    @Nested
    class PropertyAccessTests {

        @Test
        void getCallerReturnsCorrectReference() {
            MethodReference caller = createMethodRef("com/example/MyClass", "myMethod", "(I)V");
            MethodReference target = createMethodRef("com/example/Other", "otherMethod", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.VIRTUAL);

            MethodReference result = site.getCaller();

            assertEquals(caller, result);
            assertEquals("com/example/MyClass", result.getOwner());
            assertEquals("myMethod", result.getName());
        }

        @Test
        void getTargetReturnsCorrectReference() {
            MethodReference caller = createMethodRef("com/example/MyClass", "myMethod", "()V");
            MethodReference target = createMethodRef("com/example/Other", "otherMethod", "(Ljava/lang/String;)I");
            CallSite site = new CallSite(caller, target, InvokeType.INTERFACE);

            MethodReference result = site.getTarget();

            assertEquals(target, result);
            assertEquals("com/example/Other", result.getOwner());
            assertEquals("otherMethod", result.getName());
        }

        @Test
        void getInvokeTypeReturnsCorrectType() {
            MethodReference caller = createMethodRef("com/test/A", "a", "()V");
            MethodReference target = createMethodRef("com/test/B", "b", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.DYNAMIC);

            assertEquals(InvokeType.DYNAMIC, site.getInvokeType());
        }

        @Test
        void getBytecodeOffsetReturnsCorrectValue() {
            MethodReference caller = createMethodRef("com/test/A", "a", "()V");
            MethodReference target = createMethodRef("com/test/B", "b", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.VIRTUAL, 123);

            assertEquals(123, site.getBytecodeOffset());
        }
    }

    // ========== Call Type Classification Tests ==========

    @Nested
    class CallTypeClassificationTests {

        @Test
        void isPolymorphicTrueForVirtualCall() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.VIRTUAL);

            assertTrue(site.isPolymorphic());
            assertFalse(site.isStatic());
            assertFalse(site.isSpecial());
            assertFalse(site.isDynamic());
        }

        @Test
        void isPolymorphicTrueForInterfaceCall() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.INTERFACE);

            assertTrue(site.isPolymorphic());
            assertFalse(site.isStatic());
            assertFalse(site.isSpecial());
            assertFalse(site.isDynamic());
        }

        @Test
        void isStaticTrueForStaticCall() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.STATIC);

            assertTrue(site.isStatic());
            assertFalse(site.isPolymorphic());
            assertFalse(site.isSpecial());
            assertFalse(site.isDynamic());
        }

        @Test
        void isSpecialTrueForSpecialCall() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.SPECIAL);

            assertTrue(site.isSpecial());
            assertFalse(site.isPolymorphic());
            assertFalse(site.isStatic());
            assertFalse(site.isDynamic());
        }

        @Test
        void isDynamicTrueForDynamicCall() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.DYNAMIC);

            assertTrue(site.isDynamic());
            assertFalse(site.isPolymorphic());
            assertFalse(site.isStatic());
            assertFalse(site.isSpecial());
        }

        @Test
        void allTypeChecksFalseForNonMatchingType() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.STATIC);

            assertFalse(site.isPolymorphic());
            assertFalse(site.isSpecial());
            assertFalse(site.isDynamic());
        }
    }

    // ========== Specific Invoke Type Tests ==========

    @Nested
    class InvokeTypeSpecificTests {

        @Test
        void virtualCallToVirtualMethod() {
            CallSite site = createCallSite("com/test/Caller", "caller", "com/test/Base", "virtualMethod", InvokeType.VIRTUAL);

            assertEquals(InvokeType.VIRTUAL, site.getInvokeType());
            assertTrue(site.isPolymorphic());
        }

        @Test
        void interfaceCallToInterfaceMethod() {
            CallSite site = createCallSite("com/test/Impl", "impl", "com/test/Interface", "interfaceMethod", InvokeType.INTERFACE);

            assertEquals(InvokeType.INTERFACE, site.getInvokeType());
            assertTrue(site.isPolymorphic());
        }

        @Test
        void staticCallToStaticMethod() {
            CallSite site = createCallSite("com/test/Caller", "caller", "com/test/Util", "staticHelper", InvokeType.STATIC);

            assertEquals(InvokeType.STATIC, site.getInvokeType());
            assertTrue(site.isStatic());
        }

        @Test
        void specialCallToConstructor() {
            MethodReference caller = createMethodRef("com/test/Caller", "caller", "()V");
            MethodReference target = createMethodRef("com/test/Target", "<init>", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.SPECIAL);

            assertEquals(InvokeType.SPECIAL, site.getInvokeType());
            assertTrue(site.isSpecial());
            assertTrue(target.isConstructor());
        }

        @Test
        void specialCallToSuperMethod() {
            CallSite site = createCallSite("com/test/Subclass", "method", "com/test/Superclass", "method", InvokeType.SPECIAL);

            assertEquals(InvokeType.SPECIAL, site.getInvokeType());
            assertTrue(site.isSpecial());
        }

        @Test
        void specialCallToPrivateMethod() {
            CallSite site = createCallSite("com/test/MyClass", "public", "com/test/MyClass", "private", InvokeType.SPECIAL);

            assertEquals(InvokeType.SPECIAL, site.getInvokeType());
            assertTrue(site.isSpecial());
        }

        @Test
        void dynamicCallForLambda() {
            CallSite site = createCallSite("com/test/Lambda", "method", "com/test/Interface", "lambda$method$0", InvokeType.DYNAMIC);

            assertEquals(InvokeType.DYNAMIC, site.getInvokeType());
            assertTrue(site.isDynamic());
        }
    }

    // ========== Equality and HashCode Tests ==========

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void equalsSameInstance() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.VIRTUAL);

            assertEquals(site, site);
        }

        @Test
        void equalsIdenticalCallSites() {
            MethodReference caller = createMethodRef("com/test/A", "method", "()V");
            MethodReference target = createMethodRef("com/test/B", "target", "()V");
            CallSite site1 = new CallSite(caller, target, InvokeType.STATIC, 10);
            CallSite site2 = new CallSite(caller, target, InvokeType.STATIC, 10);

            assertEquals(site1, site2);
            assertEquals(site1.hashCode(), site2.hashCode());
        }

        @Test
        void equalsWithDefaultOffset() {
            MethodReference caller = createMethodRef("com/test/A", "method", "()V");
            MethodReference target = createMethodRef("com/test/B", "target", "()V");
            CallSite site1 = new CallSite(caller, target, InvokeType.VIRTUAL);
            CallSite site2 = new CallSite(caller, target, InvokeType.VIRTUAL, -1);

            assertEquals(site1, site2);
            assertEquals(site1.hashCode(), site2.hashCode());
        }

        @Test
        void notEqualsDifferentCaller() {
            MethodReference caller1 = createMethodRef("com/test/A", "method", "()V");
            MethodReference caller2 = createMethodRef("com/test/B", "method", "()V");
            MethodReference target = createMethodRef("com/test/Target", "target", "()V");
            CallSite site1 = new CallSite(caller1, target, InvokeType.VIRTUAL);
            CallSite site2 = new CallSite(caller2, target, InvokeType.VIRTUAL);

            assertNotEquals(site1, site2);
        }

        @Test
        void notEqualsDifferentTarget() {
            MethodReference caller = createMethodRef("com/test/Caller", "method", "()V");
            MethodReference target1 = createMethodRef("com/test/A", "target", "()V");
            MethodReference target2 = createMethodRef("com/test/B", "target", "()V");
            CallSite site1 = new CallSite(caller, target1, InvokeType.VIRTUAL);
            CallSite site2 = new CallSite(caller, target2, InvokeType.VIRTUAL);

            assertNotEquals(site1, site2);
        }

        @Test
        void notEqualsDifferentInvokeType() {
            MethodReference caller = createMethodRef("com/test/A", "method", "()V");
            MethodReference target = createMethodRef("com/test/B", "target", "()V");
            CallSite site1 = new CallSite(caller, target, InvokeType.VIRTUAL);
            CallSite site2 = new CallSite(caller, target, InvokeType.INTERFACE);

            assertNotEquals(site1, site2);
        }

        @Test
        void notEqualsDifferentOffset() {
            MethodReference caller = createMethodRef("com/test/A", "method", "()V");
            MethodReference target = createMethodRef("com/test/B", "target", "()V");
            CallSite site1 = new CallSite(caller, target, InvokeType.STATIC, 10);
            CallSite site2 = new CallSite(caller, target, InvokeType.STATIC, 20);

            assertNotEquals(site1, site2);
        }

        @Test
        void notEqualsNull() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.VIRTUAL);

            assertNotEquals(null, site);
        }

        @Test
        void notEqualsDifferentType() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.VIRTUAL);
            String notACallSite = "Not a CallSite";

            assertNotEquals(site, notACallSite);
        }

        @Test
        void hashCodeConsistentWithEquals() {
            MethodReference caller = createMethodRef("com/test/A", "method", "()V");
            MethodReference target = createMethodRef("com/test/B", "target", "()V");
            CallSite site1 = new CallSite(caller, target, InvokeType.VIRTUAL, 42);
            CallSite site2 = new CallSite(caller, target, InvokeType.VIRTUAL, 42);

            assertEquals(site1, site2);
            assertEquals(site1.hashCode(), site2.hashCode());
        }

        @Test
        void hashCodeStableAcrossMultipleCalls() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.STATIC);

            int hash1 = site.hashCode();
            int hash2 = site.hashCode();

            assertEquals(hash1, hash2);
        }
    }

    // ========== ToString Tests ==========

    @Nested
    class ToStringTests {

        @Test
        void toStringContainsCaller() {
            CallSite site = createCallSite("com/test/Caller", "callerMethod", "com/test/Target", "targetMethod", InvokeType.VIRTUAL);

            String str = site.toString();

            assertNotNull(str);
            assertTrue(str.contains("Caller") || str.contains("callerMethod"));
        }

        @Test
        void toStringContainsTarget() {
            CallSite site = createCallSite("com/test/Caller", "callerMethod", "com/test/Target", "targetMethod", InvokeType.VIRTUAL);

            String str = site.toString();

            assertNotNull(str);
            assertTrue(str.contains("Target") || str.contains("targetMethod"));
        }

        @Test
        void toStringContainsInvokeType() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.STATIC);

            String str = site.toString();

            assertNotNull(str);
            assertTrue(str.contains("STATIC"));
        }

        @Test
        void toStringContainsArrow() {
            CallSite site = createCallSite("com/test/A", "a", "com/test/B", "b", InvokeType.INTERFACE);

            String str = site.toString();

            assertNotNull(str);
            assertTrue(str.contains("->"));
        }

        @Test
        void toStringForVirtualCall() {
            CallSite site = createCallSite("com/test/A", "method", "com/test/B", "virtualMethod", InvokeType.VIRTUAL);

            String str = site.toString();

            assertNotNull(str);
            assertTrue(str.contains("VIRTUAL"));
        }

        @Test
        void toStringForDynamicCall() {
            CallSite site = createCallSite("com/test/Lambda", "method", "com/test/Target", "lambda", InvokeType.DYNAMIC);

            String str = site.toString();

            assertNotNull(str);
            assertTrue(str.contains("DYNAMIC"));
        }
    }

    // ========== Edge Cases and Complex Scenarios ==========

    @Nested
    class EdgeCasesAndComplexScenarios {

        @Test
        void recursiveCallSameCallerAndTarget() {
            MethodReference method = createMethodRef("com/test/Recursive", "factorial", "(I)I");
            CallSite site = new CallSite(method, method, InvokeType.VIRTUAL);

            assertEquals(method, site.getCaller());
            assertEquals(method, site.getTarget());
        }

        @Test
        void multipleCallSitesToSameTarget() {
            MethodReference caller = createMethodRef("com/test/Caller", "method", "()V");
            MethodReference target = createMethodRef("com/test/Target", "helper", "()V");

            CallSite site1 = new CallSite(caller, target, InvokeType.STATIC, 10);
            CallSite site2 = new CallSite(caller, target, InvokeType.STATIC, 20);
            CallSite site3 = new CallSite(caller, target, InvokeType.STATIC, 30);

            assertNotEquals(site1, site2);
            assertNotEquals(site2, site3);
            assertEquals(target, site1.getTarget());
            assertEquals(target, site2.getTarget());
            assertEquals(target, site3.getTarget());
        }

        @Test
        void callSiteWithComplexDescriptor() {
            MethodReference caller = createMethodRef("com/test/A", "complex", "(ILjava/lang/String;[I)Ljava/util/List;");
            MethodReference target = createMethodRef("com/test/B", "process", "([[Ljava/lang/Object;)V");
            CallSite site = new CallSite(caller, target, InvokeType.INTERFACE, 100);

            assertEquals("(ILjava/lang/String;[I)Ljava/util/List;", caller.getDescriptor());
            assertEquals("([[Ljava/lang/Object;)V", target.getDescriptor());
        }

        @Test
        void callSiteWithInnerClass() {
            MethodReference caller = createMethodRef("com/test/Outer$Inner", "innerMethod", "()V");
            MethodReference target = createMethodRef("com/test/Outer", "outerMethod", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.VIRTUAL);

            assertTrue(caller.getOwner().contains("$"));
            assertEquals("com/test/Outer$Inner", caller.getOwner());
        }

        @Test
        void callSiteWithAnonymousClass() {
            MethodReference caller = createMethodRef("com/test/MyClass$1", "run", "()V");
            MethodReference target = createMethodRef("java/lang/System", "println", "(Ljava/lang/String;)V");
            CallSite site = new CallSite(caller, target, InvokeType.VIRTUAL);

            assertTrue(caller.getOwner().contains("$1"));
            assertEquals("java/lang/System", target.getOwner());
        }

        @Test
        void callSiteToJavaStandardLibrary() {
            MethodReference caller = createMethodRef("com/test/MyApp", "main", "([Ljava/lang/String;)V");
            MethodReference target = createMethodRef("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
            CallSite site = new CallSite(caller, target, InvokeType.VIRTUAL);

            assertTrue(target.getOwner().startsWith("java/"));
            assertEquals(InvokeType.VIRTUAL, site.getInvokeType());
        }

        @Test
        void callSiteWithConstructorCall() {
            MethodReference caller = createMethodRef("com/test/Factory", "create", "()Lcom/test/Product;");
            MethodReference target = createMethodRef("com/test/Product", "<init>", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.SPECIAL);

            assertTrue(target.isConstructor());
            assertTrue(site.isSpecial());
        }

        @Test
        void callSiteWithStaticInitializer() {
            MethodReference caller = createMethodRef("com/test/MyClass", "<clinit>", "()V");
            MethodReference target = createMethodRef("com/test/Config", "load", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.STATIC);

            assertTrue(caller.isStaticInitializer());
            assertTrue(site.isStatic());
        }

        @Test
        void multipleInvokeTypesForSameMethodPair() {
            MethodReference caller = createMethodRef("com/test/Caller", "method", "()V");
            MethodReference target = createMethodRef("com/test/Target", "target", "()V");

            CallSite virtualSite = new CallSite(caller, target, InvokeType.VIRTUAL);
            CallSite specialSite = new CallSite(caller, target, InvokeType.SPECIAL);

            assertNotEquals(virtualSite, specialSite);
            assertTrue(virtualSite.isPolymorphic());
            assertTrue(specialSite.isSpecial());
        }

        @Test
        void callSiteIntegrityAfterConstruction() {
            MethodReference caller = createMethodRef("com/test/A", "a", "()V");
            MethodReference target = createMethodRef("com/test/B", "b", "()V");
            CallSite site = new CallSite(caller, target, InvokeType.INTERFACE, 50);

            // Verify all properties remain consistent
            assertEquals(caller, site.getCaller());
            assertEquals(target, site.getTarget());
            assertEquals(InvokeType.INTERFACE, site.getInvokeType());
            assertEquals(50, site.getBytecodeOffset());
            assertTrue(site.isPolymorphic());
            assertFalse(site.isStatic());
        }
    }
}
