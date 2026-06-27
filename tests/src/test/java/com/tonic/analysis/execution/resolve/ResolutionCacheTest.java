package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionCacheTest {

    private ResolutionCache cache;

    @BeforeEach
    void setUp() {
        cache = new ResolutionCache();
    }

    @Test
    void getClassCachesResult() throws Exception {
        ClassFile cf = TestUtils.createMinimalClass("com/test/TestClass");
        AtomicInteger supplierCalls = new AtomicInteger(0);

        ClassFile result1 = cache.getClass("com/test/TestClass", () -> {
            supplierCalls.incrementAndGet();
            return cf;
        });

        ClassFile result2 = cache.getClass("com/test/TestClass", () -> {
            supplierCalls.incrementAndGet();
            return cf;
        });

        assertSame(cf, result1);
        assertSame(cf, result2);
        assertEquals(1, supplierCalls.get());
    }

    @Test
    void getMethodCachesResult() {
        ResolvedMethod method = new ResolvedMethod(null, null, ResolvedMethod.InvokeKind.STATIC);
        AtomicInteger supplierCalls = new AtomicInteger(0);

        ResolvedMethod result1 = cache.getMethod("key1", () -> {
            supplierCalls.incrementAndGet();
            return method;
        });

        ResolvedMethod result2 = cache.getMethod("key1", () -> {
            supplierCalls.incrementAndGet();
            return method;
        });

        assertSame(method, result1);
        assertSame(method, result2);
        assertEquals(1, supplierCalls.get());
    }

    @Test
    void getFieldCachesResult() {
        ResolvedField field = new ResolvedField(null, null);
        AtomicInteger supplierCalls = new AtomicInteger(0);

        ResolvedField result1 = cache.getField("key1", () -> {
            supplierCalls.incrementAndGet();
            return field;
        });

        ResolvedField result2 = cache.getField("key1", () -> {
            supplierCalls.incrementAndGet();
            return field;
        });

        assertSame(field, result1);
        assertSame(field, result2);
        assertEquals(1, supplierCalls.get());
    }

    @Test
    void getAssignabilityCachesResult() {
        AtomicInteger supplierCalls = new AtomicInteger(0);

        Boolean result1 = cache.getAssignability("key1", () -> {
            supplierCalls.incrementAndGet();
            return true;
        });

        Boolean result2 = cache.getAssignability("key1", () -> {
            supplierCalls.incrementAndGet();
            return true;
        });

        assertTrue(result1);
        assertTrue(result2);
        assertEquals(1, supplierCalls.get());
    }

    @Test
    void clearEmptiesAllCaches() throws Exception {
        ClassFile cf = TestUtils.createMinimalClass("com/test/TestClass");
        ResolvedMethod method = new ResolvedMethod(null, null, ResolvedMethod.InvokeKind.STATIC);
        ResolvedField field = new ResolvedField(null, null);

        cache.getClass("class1", () -> cf);
        cache.getMethod("method1", () -> method);
        cache.getField("field1", () -> field);
        cache.getAssignability("assign1", () -> true);

        assertTrue(cache.size() > 0);

        cache.clear();

        assertEquals(0, cache.size());
    }

    @Test
    void sizeCountsAllEntries() throws Exception {
        ClassFile cf = TestUtils.createMinimalClass("com/test/TestClass");
        ResolvedMethod method = new ResolvedMethod(null, null, ResolvedMethod.InvokeKind.STATIC);
        ResolvedField field = new ResolvedField(null, null);

        assertEquals(0, cache.size());

        cache.getClass("class1", () -> cf);
        assertEquals(1, cache.size());

        cache.getMethod("method1", () -> method);
        assertEquals(2, cache.size());

        cache.getField("field1", () -> field);
        assertEquals(3, cache.size());

        cache.getAssignability("assign1", () -> true);
        assertEquals(4, cache.size());
    }

    @Test
    void differentKeysStoredSeparately() throws Exception {
        ClassFile cf1 = TestUtils.createMinimalClass("com/test/Class1");
        ClassFile cf2 = TestUtils.createMinimalClass("com/test/Class2");

        cache.getClass("class1", () -> cf1);
        cache.getClass("class2", () -> cf2);

        ClassFile result1 = cache.getClass("class1", () -> null);
        ClassFile result2 = cache.getClass("class2", () -> null);

        assertSame(cf1, result1);
        assertSame(cf2, result2);
        assertNotSame(cf1, cf2);
    }

    @Test
    void concurrentAccessThreadSafe() throws Exception {
        ClassFile cf = TestUtils.createMinimalClass("com/test/TestClass");
        AtomicInteger supplierCalls = new AtomicInteger(0);
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    cache.getClass("concurrent", () -> {
                        supplierCalls.incrementAndGet();
                        return cf;
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertEquals(1, supplierCalls.get());
    }

    @Test
    void nullValuesNotCached() {
        AtomicInteger supplierCalls = new AtomicInteger(0);

        ClassFile result1 = cache.getClass("null", () -> {
            supplierCalls.incrementAndGet();
            return null;
        });
        ClassFile result2 = cache.getClass("null", () -> {
            supplierCalls.incrementAndGet();
            return null;
        });

        assertNull(result1);
        assertNull(result2);
        assertEquals(2, supplierCalls.get());
    }

    @Test
    void cachePersistsAcrossOperations() throws Exception {
        ClassFile cf = TestUtils.createMinimalClass("com/test/TestClass");
        cache.getClass("key", () -> cf);

        cache.getMethod("method", () -> null);
        cache.getField("field", () -> null);
        cache.getAssignability("assign", () -> true);

        ClassFile result = cache.getClass("key", () -> {
            throw new RuntimeException("Should use cached value");
        });

        assertSame(cf, result);
    }
}
