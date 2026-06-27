package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.type.PrimitiveSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.ast.type.VoidSourceType;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link TypeResolver#resolveSamMethod}, which underpins lambda return-type and
 * descriptor inference (target typing).
 */
class SamResolverTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    private TypeResolver resolver() {
        return new TypeResolver(pool, "com/test/Owner");
    }

    @Test
    void resolvesRunnableToVoidNoArgs() {
        String[] sam = resolver().resolveSamMethod("java/lang/Runnable");
        assertNotNull(sam);
        assertEquals("run", sam[0]);
        assertEquals("()V", sam[1]);
    }

    @Test
    void resolvesFunctionToObjectObject() {
        String[] sam = resolver().resolveSamMethod("java/util/function/Function");
        assertNotNull(sam);
        assertEquals("apply", sam[0]);
        assertEquals("(Ljava/lang/Object;)Ljava/lang/Object;", sam[1]);
    }

    @Test
    void resolvesPredicateToBoolean() {
        String[] sam = resolver().resolveSamMethod("java/util/function/Predicate");
        assertNotNull(sam);
        assertEquals("test", sam[0]);
        assertEquals("(Ljava/lang/Object;)Z", sam[1]);
    }

    @Test
    void resolvesCustomInterfaceFromClassPool() throws Exception {
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().setAbstract().build();
        ClassFile iface = pool.createNewClass("com/test/MyFn", ifaceAccess);
        int methodAccess = new AccessBuilder().setPublic().setAbstract().build();
        iface.createNewMethodWithDescriptor(methodAccess, "compute", "(I)I");

        String[] sam = resolver().resolveSamMethod("com/test/MyFn");
        assertNotNull(sam);
        assertEquals("compute", sam[0]);
        assertEquals("(I)I", sam[1]);
    }

    @Test
    void unknownInterfaceReturnsNull() {
        assertNull(resolver().resolveSamMethod("com/test/NotInPoolAndNotJdk"));
    }

    @Test
    void resolveClassNameFindsSamePackageType() throws Exception {
        pool.createNewClass("pkg/Helper", new AccessBuilder().setPublic().build());
        TypeResolver r = new TypeResolver(pool, "pkg/Owner");
        assertEquals("pkg/Helper", r.resolveClassName("Helper"),
            "a simple name should resolve to a same-package class in the pool");
    }

    @Test
    void returnTypeFromDescriptorParsesVoidAndPrimitives() {
        TypeResolver r = resolver();
        assertTrue(r.returnTypeFromDescriptor("()V") instanceof VoidSourceType);
        assertEquals(PrimitiveSourceType.BOOLEAN, r.returnTypeFromDescriptor("(Ljava/lang/Object;)Z"));
        assertEquals(PrimitiveSourceType.INT, r.returnTypeFromDescriptor("(I)I"));
    }

    @Test
    void paramTypesFromDescriptorParsesAll() {
        java.util.List<SourceType> params = resolver().paramTypesFromDescriptor("(IJLjava/lang/String;)V");
        assertEquals(3, params.size());
        assertEquals(PrimitiveSourceType.INT, params.get(0));
        assertEquals(PrimitiveSourceType.LONG, params.get(1));
    }
}
