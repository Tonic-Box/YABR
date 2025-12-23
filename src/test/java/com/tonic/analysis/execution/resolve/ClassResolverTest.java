package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassResolverTest {

    private ClassPool pool;
    private ClassResolver resolver;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
        resolver = new ClassResolver(pool);
    }

    @Nested
    class ResolveClassTests {

        @Test
        void resolveClassFound() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            ClassFile result = resolver.resolveClass("com/test/TestClass");

            assertSame(cf, result);
        }

        @Test
        void resolveClassNotFound() {
            assertThrows(ResolutionException.class, () ->
                    resolver.resolveClass("com/test/NonExistent"));
        }

        @Test
        void resolveClassCached() throws IOException {
            pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            ClassFile result1 = resolver.resolveClass("com/test/TestClass");
            ClassFile result2 = resolver.resolveClass("com/test/TestClass");

            assertSame(result1, result2);
        }

        @Test
        void resolveMultipleClasses() throws IOException {
            ClassFile cf1 = pool.createNewClass("com/test/Class1",
                    new AccessBuilder().setPublic().build());
            ClassFile cf2 = pool.createNewClass("com/test/Class2",
                    new AccessBuilder().setPublic().build());

            ClassFile result1 = resolver.resolveClass("com/test/Class1");
            ClassFile result2 = resolver.resolveClass("com/test/Class2");

            assertSame(cf1, result1);
            assertSame(cf2, result2);
        }
    }

    @Nested
    class RegisterClassTests {

        @Test
        void registerClassAddsToPool() throws IOException {
            ClassFile cf = TestUtils.createMinimalClass("com/test/NewClass");

            resolver.registerClass(cf);

            ClassFile result = pool.get("com/test/NewClass");
            assertSame(cf, result);
        }

        @Test
        void registerClassCanBeResolved() throws IOException {
            ClassFile cf = TestUtils.createMinimalClass("com/test/NewClass");

            resolver.registerClass(cf);

            ClassFile result = resolver.resolveClass("com/test/NewClass");
            assertSame(cf, result);
        }
    }

    @Nested
    class ResolveMethodTests {

        @Test
        void resolveMethodInClass() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            MethodEntry method = cf.createNewMethod(
                    new AccessBuilder().setPublic().build(),
                    "testMethod", "V");

            ResolvedMethod result = resolver.resolveMethod("com/test/TestClass",
                    "testMethod", "()V");

            assertNotNull(result);
            assertEquals("testMethod", result.getMethod().getName());
            assertSame(cf, result.getDeclaringClass());
        }

        @Test
        void resolveMethodInSuperclass() throws IOException {
            ClassFile parent = pool.createNewClass("com/test/Parent",
                    new AccessBuilder().setPublic().build());
            parent.createNewMethod(new AccessBuilder().setPublic().build(),
                    "parentMethod", "V");

            ClassFile child = pool.createNewClass("com/test/Child",
                    new AccessBuilder().setPublic().build());
            child.setSuperClassName("com/test/Parent");

            resolver = new ClassResolver(pool);

            ResolvedMethod result = resolver.resolveMethod("com/test/Child",
                    "parentMethod", "()V");

            assertNotNull(result);
            assertEquals("parentMethod", result.getMethod().getName());
            assertSame(parent, result.getDeclaringClass());
        }

        @Test
        void resolveMethodNotFound() throws IOException {
            pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            assertThrows(ResolutionException.class, () ->
                    resolver.resolveMethod("com/test/TestClass", "nonExistent", "()V"));
        }

        @Test
        void resolveMethodOwnerNotFound() {
            assertThrows(ResolutionException.class, () ->
                    resolver.resolveMethod("com/test/NonExistent", "method", "()V"));
        }

        @Test
        void resolveMethodCached() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPublic().build(),
                    "testMethod", "V");

            ResolvedMethod result1 = resolver.resolveMethod("com/test/TestClass",
                    "testMethod", "()V");
            ResolvedMethod result2 = resolver.resolveMethod("com/test/TestClass",
                    "testMethod", "()V");

            assertSame(result1, result2);
        }

        @Test
        void resolveStaticMethod() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPublic().setStatic().build(),
                    "staticMethod", "V");

            ResolvedMethod result = resolver.resolveMethod("com/test/TestClass",
                    "staticMethod", "()V");

            assertTrue(result.isStatic());
            assertEquals(ResolvedMethod.InvokeKind.STATIC, result.getKind());
        }

        @Test
        void resolveVirtualMethod() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPublic().build(),
                    "virtualMethod", "V");

            ResolvedMethod result = resolver.resolveMethod("com/test/TestClass",
                    "virtualMethod", "()V");

            assertFalse(result.isStatic());
            assertEquals(ResolvedMethod.InvokeKind.VIRTUAL, result.getKind());
        }
    }

    @Nested
    class ResolveVirtualMethodTests {

        @Test
        void resolveVirtualMethodBasic() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPublic().build(),
                    "method", "V");

            ResolvedMethod result = resolver.resolveVirtualMethod("com/test/TestClass",
                    "method", "()V", null);

            assertNotNull(result);
            assertEquals("method", result.getMethod().getName());
        }

        @Test
        void resolveVirtualMethodCached() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPublic().build(),
                    "method", "V");

            ResolvedMethod result1 = resolver.resolveVirtualMethod("com/test/TestClass",
                    "method", "()V", null);
            ResolvedMethod result2 = resolver.resolveVirtualMethod("com/test/TestClass",
                    "method", "()V", null);

            assertSame(result1, result2);
        }
    }

    @Nested
    class ResolveInterfaceMethodTests {

        @Test
        void resolveInterfaceMethodInInterface() throws IOException {
            ClassFile iface = pool.createNewClass("com/test/ITest",
                    new AccessBuilder().setPublic().setInterface().build());
            iface.createNewMethod(new AccessBuilder().setPublic().setAbstract().build(),
                    "interfaceMethod", "V");

            resolver = new ClassResolver(pool);

            ResolvedMethod result = resolver.resolveInterfaceMethod("com/test/ITest",
                    "interfaceMethod", "()V");

            assertNotNull(result);
            assertEquals("interfaceMethod", result.getMethod().getName());
            assertEquals(ResolvedMethod.InvokeKind.INTERFACE, result.getKind());
        }

        @Test
        void resolveInterfaceMethodNotFound() throws IOException {
            pool.createNewClass("com/test/ITest",
                    new AccessBuilder().setPublic().setInterface().build());

            assertThrows(ResolutionException.class, () ->
                    resolver.resolveInterfaceMethod("com/test/ITest", "nonExistent", "()V"));
        }

        @Test
        void resolveInterfaceNotFound() {
            assertThrows(ResolutionException.class, () ->
                    resolver.resolveInterfaceMethod("com/test/NonExistent", "method", "()V"));
        }
    }

    @Nested
    class ResolveSpecialMethodTests {

        @Test
        void resolveSpecialMethod() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPrivate().build(),
                    "privateMethod", "V");

            ResolvedMethod result = resolver.resolveSpecialMethod("com/test/TestClass",
                    "privateMethod", "()V");

            assertNotNull(result);
            assertEquals("privateMethod", result.getMethod().getName());
            assertEquals(ResolvedMethod.InvokeKind.SPECIAL, result.getKind());
        }

        @Test
        void resolveSpecialMethodNotFound() throws IOException {
            pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            assertThrows(ResolutionException.class, () ->
                    resolver.resolveSpecialMethod("com/test/TestClass", "nonExistent", "()V"));
        }

        @Test
        void resolveSpecialMethodCached() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewMethod(new AccessBuilder().setPrivate().build(),
                    "privateMethod", "V");

            ResolvedMethod result1 = resolver.resolveSpecialMethod("com/test/TestClass",
                    "privateMethod", "()V");
            ResolvedMethod result2 = resolver.resolveSpecialMethod("com/test/TestClass",
                    "privateMethod", "()V");

            assertSame(result1, result2);
        }
    }

    @Nested
    class ResolveFieldTests {

        @Test
        void resolveFieldInClass() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewField(new AccessBuilder().setPublic().build(),
                    "testField", "I", null);

            ResolvedField result = resolver.resolveField("com/test/TestClass",
                    "testField", "I");

            assertNotNull(result);
            assertEquals("testField", result.getField().getName());
            assertSame(cf, result.getDeclaringClass());
        }

        @Test
        void resolveFieldInSuperclass() throws IOException {
            ClassFile parent = pool.createNewClass("com/test/Parent",
                    new AccessBuilder().setPublic().build());
            parent.createNewField(new AccessBuilder().setPublic().build(),
                    "parentField", "I", null);

            ClassFile child = pool.createNewClass("com/test/Child",
                    new AccessBuilder().setPublic().build());
            child.setSuperClassName("com/test/Parent");

            resolver = new ClassResolver(pool);

            ResolvedField result = resolver.resolveField("com/test/Child",
                    "parentField", "I");

            assertNotNull(result);
            assertEquals("parentField", result.getField().getName());
            assertSame(parent, result.getDeclaringClass());
        }

        @Test
        void resolveFieldNotFound() throws IOException {
            pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            assertThrows(ResolutionException.class, () ->
                    resolver.resolveField("com/test/TestClass", "nonExistent", "I"));
        }

        @Test
        void resolveFieldCached() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewField(new AccessBuilder().setPublic().build(),
                    "testField", "I", null);

            ResolvedField result1 = resolver.resolveField("com/test/TestClass",
                    "testField", "I");
            ResolvedField result2 = resolver.resolveField("com/test/TestClass",
                    "testField", "I");

            assertSame(result1, result2);
        }

        @Test
        void resolveStaticField() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewField(new AccessBuilder().setPublic().setStatic().build(),
                    "staticField", "I", null);

            ResolvedField result = resolver.resolveField("com/test/TestClass",
                    "staticField", "I");

            assertTrue(result.isStatic());
        }

        @Test
        void resolveInstanceField() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());
            cf.createNewField(new AccessBuilder().setPublic().build(),
                    "instanceField", "I", null);

            ResolvedField result = resolver.resolveField("com/test/TestClass",
                    "instanceField", "I");

            assertFalse(result.isStatic());
        }
    }

    @Nested
    class IsAssignableFromTests {

        @Test
        void isAssignableFromSameType() {
            assertTrue(resolver.isAssignableFrom("java/lang/String", "java/lang/String"));
        }

        @Test
        void isAssignableFromObjectToAnyReference() {
            assertTrue(resolver.isAssignableFrom("java/lang/Object", "java/lang/String"));
            assertTrue(resolver.isAssignableFrom("java/lang/Object", "com/test/TestClass"));
        }

        @Test
        void isAssignableFromSuperclass() throws IOException {
            pool.createNewClass("com/test/Parent",
                    new AccessBuilder().setPublic().build());
            ClassFile child = pool.createNewClass("com/test/Child",
                    new AccessBuilder().setPublic().build());
            child.setSuperClassName("com/test/Parent");

            resolver = new ClassResolver(pool);

            assertTrue(resolver.isAssignableFrom("com/test/Parent", "com/test/Child"));
        }

        @Test
        void isAssignableFromNotRelated() throws IOException {
            pool.createNewClass("com/test/Class1",
                    new AccessBuilder().setPublic().build());
            pool.createNewClass("com/test/Class2",
                    new AccessBuilder().setPublic().build());

            assertFalse(resolver.isAssignableFrom("com/test/Class1", "com/test/Class2"));
        }

        @Test
        void isAssignableFromPrimitiveArrays() {
            assertTrue(resolver.isAssignableFrom("[I", "[I"));
            assertFalse(resolver.isAssignableFrom("[I", "[J"));
        }

        @Test
        void isAssignableFromObjectArrays() throws IOException {
            pool.createNewClass("com/test/Parent",
                    new AccessBuilder().setPublic().build());
            ClassFile child = pool.createNewClass("com/test/Child",
                    new AccessBuilder().setPublic().build());
            child.setSuperClassName("com/test/Parent");

            resolver = new ClassResolver(pool);

            assertTrue(resolver.isAssignableFrom("[Lcom/test/Parent;", "[Lcom/test/Child;"));
        }

        @Test
        void isAssignableFromCached() throws IOException {
            pool.createNewClass("com/test/Parent",
                    new AccessBuilder().setPublic().build());
            ClassFile child = pool.createNewClass("com/test/Child",
                    new AccessBuilder().setPublic().build());
            child.setSuperClassName("com/test/Parent");

            resolver = new ClassResolver(pool);

            boolean result1 = resolver.isAssignableFrom("com/test/Parent", "com/test/Child");
            boolean result2 = resolver.isAssignableFrom("com/test/Parent", "com/test/Child");

            assertTrue(result1);
            assertTrue(result2);
        }
    }

    @Nested
    class GetSuperclassTests {

        @Test
        void getSuperclassReturnsParent() throws IOException {
            ClassFile child = pool.createNewClass("com/test/Child",
                    new AccessBuilder().setPublic().build());
            child.setSuperClassName("com/test/Parent");

            String result = resolver.getSuperclass("com/test/Child");

            assertEquals("com/test/Parent", result);
        }

        @Test
        void getSuperclassNotFound() {
            String result = resolver.getSuperclass("com/test/NonExistent");

            assertNull(result);
        }

        @Test
        void getSuperclassObject() throws IOException {
            pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            String result = resolver.getSuperclass("com/test/TestClass");

            assertEquals("java/lang/Object", result);
        }
    }

    @Nested
    class GetInterfacesTests {

        @Test
        void getInterfacesReturnsEmpty() throws IOException {
            pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            resolver = new ClassResolver(pool);

            List<String> result = resolver.getInterfaces("com/test/TestClass");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void getInterfacesNotFound() {
            List<String> result = resolver.getInterfaces("com/test/NonExistent");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class CacheManagementTests {

        @Test
        void invalidateCacheClearsCache() throws IOException {
            ClassFile cf = pool.createNewClass("com/test/TestClass",
                    new AccessBuilder().setPublic().build());

            resolver.resolveClass("com/test/TestClass");

            resolver.invalidateCache();

            ClassFile result = resolver.resolveClass("com/test/TestClass");
            assertSame(cf, result);
        }
    }

    @Nested
    class AccessorTests {

        @Test
        void getClassPoolReturnsPool() {
            assertSame(pool, resolver.getClassPool());
        }

        @Test
        void getHierarchyReturnsHierarchy() {
            assertNotNull(resolver.getHierarchy());
        }
    }
}
