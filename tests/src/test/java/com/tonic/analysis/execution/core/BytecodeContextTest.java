package com.tonic.analysis.execution.core;

import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BytecodeContextTest {

    @Test
    void testBuilderDefaults() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertEquals(ExecutionMode.RECURSIVE, context.getMode());
        assertEquals(1000, context.getMaxCallDepth());
        assertEquals(10_000_000, context.getMaxInstructions());
        assertFalse(context.isTrackStatistics());
        assertEquals(heap, context.getHeapManager());
        assertEquals(resolver, context.getClassResolver());
    }

    @Test
    void testBuilderSetMode() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .mode(ExecutionMode.DELEGATED)
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertEquals(ExecutionMode.DELEGATED, context.getMode());
    }

    @Test
    void testBuilderSetMaxCallDepth() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .maxCallDepth(500)
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertEquals(500, context.getMaxCallDepth());
    }

    @Test
    void testBuilderSetMaxInstructions() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .maxInstructions(1000)
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertEquals(1000, context.getMaxInstructions());
    }

    @Test
    void testBuilderSetTrackStatistics() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .trackStatistics(true)
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertTrue(context.isTrackStatistics());
    }

    @Test
    void testBuilderAllOptions() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .mode(ExecutionMode.DELEGATED)
            .heapManager(heap)
            .classResolver(resolver)
            .maxCallDepth(2000)
            .maxInstructions(5000000)
            .trackStatistics(true)
            .build();

        assertEquals(ExecutionMode.DELEGATED, context.getMode());
        assertEquals(heap, context.getHeapManager());
        assertEquals(resolver, context.getClassResolver());
        assertEquals(2000, context.getMaxCallDepth());
        assertEquals(5000000, context.getMaxInstructions());
        assertTrue(context.isTrackStatistics());
    }

    @Test
    void testBuilderRequiresHeapManager() {
        ClassResolver resolver = mock(ClassResolver.class);

        assertThrows(IllegalStateException.class, () -> {
            new BytecodeContext.Builder()
                .classResolver(resolver)
                .build();
        });
    }

    @Test
    void testBuilderRequiresClassResolver() {
        HeapManager heap = mock(HeapManager.class);

        assertThrows(IllegalStateException.class, () -> {
            new BytecodeContext.Builder()
                .heapManager(heap)
                .build();
        });
    }

    @Test
    void testBuilderRejectsNullMode() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BytecodeContext.Builder().mode(null);
        });
    }

    @Test
    void testBuilderRejectsNegativeMaxCallDepth() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BytecodeContext.Builder().maxCallDepth(-1);
        });
    }

    @Test
    void testBuilderRejectsZeroMaxCallDepth() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BytecodeContext.Builder().maxCallDepth(0);
        });
    }

    @Test
    void testBuilderRejectsNegativeMaxInstructions() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BytecodeContext.Builder().maxInstructions(-1);
        });
    }

    @Test
    void testBuilderRejectsZeroMaxInstructions() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BytecodeContext.Builder().maxInstructions(0);
        });
    }

    @Test
    void testBuilderChaining() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext.Builder builder = new BytecodeContext.Builder();
        BytecodeContext.Builder result = builder
            .mode(ExecutionMode.RECURSIVE)
            .heapManager(heap)
            .classResolver(resolver);

        assertSame(builder, result);
    }

    @Test
    void testContextIsImmutable() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertNotNull(context.getMode());
        assertNotNull(context.getHeapManager());
        assertNotNull(context.getClassResolver());
    }

    @Test
    void testMultipleBuildsSameBuilder() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext.Builder builder = new BytecodeContext.Builder()
            .heapManager(heap)
            .classResolver(resolver);

        BytecodeContext context1 = builder.build();
        BytecodeContext context2 = builder.build();

        assertNotSame(context1, context2);
        assertEquals(context1.getMode(), context2.getMode());
    }

    @Test
    void testBuilderAcceptsNullHeapManagerBeforeBuild() {
        BytecodeContext.Builder builder = new BytecodeContext.Builder();
        builder.heapManager(null);
    }

    @Test
    void testBuilderAcceptsNullClassResolverBeforeBuild() {
        BytecodeContext.Builder builder = new BytecodeContext.Builder();
        builder.classResolver(null);
    }

    @Test
    void testMinimalValidContext() {
        HeapManager heap = mock(HeapManager.class);
        ClassResolver resolver = mock(ClassResolver.class);

        BytecodeContext context = new BytecodeContext.Builder()
            .heapManager(heap)
            .classResolver(resolver)
            .build();

        assertNotNull(context);
    }
}
