package com.tonic.analysis.instrumentation.hook;

import com.tonic.analysis.instrumentation.HookDescriptor;
import com.tonic.analysis.instrumentation.InstrumentationTarget;
import com.tonic.analysis.instrumentation.filter.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for various Hook implementations.
 * Verifies hook configuration and registration behavior.
 */
class HookTests {

    // ========== MethodEntryHook Tests ==========

    @Test
    void methodEntryHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.METHOD_ENTRY, hook.getTarget());
        assertEquals(descriptor, hook.getHookDescriptor());
        assertTrue(hook.isEnabled());
        assertEquals(100, hook.getPriority());
        assertFalse(hook.isPassThis());
        assertFalse(hook.isPassMethodName());
        assertFalse(hook.isPassClassName());
        assertFalse(hook.isPassAllParameters());
    }

    @Test
    void methodEntryHookWithPassThis() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry",
                "(Ljava/lang/Object;)V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .passThis(true)
                .build();

        assertTrue(hook.isPassThis());
    }

    @Test
    void methodEntryHookWithPassMethodName() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry",
                "(Ljava/lang/String;)V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .passMethodName(true)
                .build();

        assertTrue(hook.isPassMethodName());
    }

    @Test
    void methodEntryHookWithPassClassName() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry",
                "(Ljava/lang/String;)V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .passClassName(true)
                .build();

        assertTrue(hook.isPassClassName());
    }

    @Test
    void methodEntryHookWithAllParameters() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry",
                "([Ljava/lang/Object;)V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .passAllParameters(true)
                .build();

        assertTrue(hook.isPassAllParameters());
    }

    @Test
    void methodEntryHookWithFilters() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        List<InstrumentationFilter> filters = List.of(
                ClassFilter.exact("com/test/Target"),
                MethodFilter.matching("test.*")
        );

        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .filters(filters)
                .build();

        assertEquals(2, hook.getFilters().size());
    }

    @Test
    void methodEntryHookWithPriority() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .priority(50)
                .build();

        assertEquals(50, hook.getPriority());
    }

    @Test
    void methodEntryHookSimpleFactory() {
        MethodEntryHook hook = MethodEntryHook.simple("com/test/Hooks", "onEntry", "()V");

        assertNotNull(hook);
        assertEquals(InstrumentationTarget.METHOD_ENTRY, hook.getTarget());
        assertTrue(hook.isEnabled());
    }

    // ========== MethodExitHook Tests ==========

    @Test
    void methodExitHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onExit", "()V");
        MethodExitHook hook = MethodExitHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.METHOD_EXIT, hook.getTarget());
        assertEquals(descriptor, hook.getHookDescriptor());
        assertTrue(hook.isEnabled());
        assertEquals(100, hook.getPriority());
        assertFalse(hook.isPassThis());
        assertFalse(hook.isPassMethodName());
        assertFalse(hook.isPassClassName());
        assertFalse(hook.isPassReturnValue());
        assertFalse(hook.isCanModifyReturn());
    }

    @Test
    void methodExitHookWithReturnValue() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onExit", "(I)V");
        MethodExitHook hook = MethodExitHook.builder()
                .hookDescriptor(descriptor)
                .passReturnValue(true)
                .build();

        assertTrue(hook.isPassReturnValue());
    }

    @Test
    void methodExitHookCanModifyReturn() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onExit", "(I)I");
        MethodExitHook hook = MethodExitHook.builder()
                .hookDescriptor(descriptor)
                .passReturnValue(true)
                .canModifyReturn(true)
                .build();

        assertTrue(hook.isCanModifyReturn());
    }

    // ========== FieldWriteHook Tests ==========

    @Test
    void fieldWriteHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite", "()V");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.FIELD_WRITE, hook.getTarget());
        assertTrue(hook.isEnabled());
        assertTrue(hook.isInstrumentStatic());
        assertTrue(hook.isInstrumentInstance());
        assertFalse(hook.isPassOwner());
        assertFalse(hook.isPassFieldName());
        assertFalse(hook.isPassNewValue());
        assertFalse(hook.isPassOldValue());
        assertFalse(hook.isCanModifyValue());
    }

    @Test
    void fieldWriteHookStaticOnly() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite", "()V");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .instrumentStatic(true)
                .instrumentInstance(false)
                .build();

        assertTrue(hook.isInstrumentStatic());
        assertFalse(hook.isInstrumentInstance());
    }

    @Test
    void fieldWriteHookInstanceOnly() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite", "()V");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .instrumentStatic(false)
                .instrumentInstance(true)
                .build();

        assertFalse(hook.isInstrumentStatic());
        assertTrue(hook.isInstrumentInstance());
    }

    @Test
    void fieldWriteHookWithNewValue() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite", "(I)V");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .passNewValue(true)
                .build();

        assertTrue(hook.isPassNewValue());
    }

    @Test
    void fieldWriteHookWithOldValue() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite", "(I)V");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .passOldValue(true)
                .build();

        assertTrue(hook.isPassOldValue());
    }

    @Test
    void fieldWriteHookCanModifyValue() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite", "(I)I");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .passNewValue(true)
                .canModifyValue(true)
                .build();

        assertTrue(hook.isCanModifyValue());
    }

    @Test
    void fieldWriteHookWithFieldName() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onWrite",
                "(Ljava/lang/String;)V");
        FieldWriteHook hook = FieldWriteHook.builder()
                .hookDescriptor(descriptor)
                .passFieldName(true)
                .build();

        assertTrue(hook.isPassFieldName());
    }

    // ========== FieldReadHook Tests ==========

    @Test
    void fieldReadHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onRead", "()V");
        FieldReadHook hook = FieldReadHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.FIELD_READ, hook.getTarget());
        assertTrue(hook.isEnabled());
        assertTrue(hook.isInstrumentStatic());
        assertTrue(hook.isInstrumentInstance());
        assertFalse(hook.isPassOwner());
        assertFalse(hook.isPassFieldName());
        assertFalse(hook.isPassReadValue());
    }

    @Test
    void fieldReadHookWithReadValue() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onRead", "(I)V");
        FieldReadHook hook = FieldReadHook.builder()
                .hookDescriptor(descriptor)
                .passReadValue(true)
                .build();

        assertTrue(hook.isPassReadValue());
    }

    // ========== ArrayStoreHook Tests ==========

    @Test
    void arrayStoreHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onArrayStore", "()V");
        ArrayStoreHook hook = ArrayStoreHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.ARRAY_STORE, hook.getTarget());
        assertTrue(hook.isEnabled());
        assertFalse(hook.isPassArray());
        assertFalse(hook.isPassIndex());
        assertFalse(hook.isPassValue());
        assertFalse(hook.isCanModifyValue());
        assertNull(hook.getArrayTypeFilter());
    }

    @Test
    void arrayStoreHookWithArrayTypeFilter() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onArrayStore", "()V");
        ArrayStoreHook hook = ArrayStoreHook.builder()
                .hookDescriptor(descriptor)
                .arrayTypeFilter("[I")
                .build();

        assertEquals("[I", hook.getArrayTypeFilter());
    }

    @Test
    void arrayStoreHookWithAllParameters() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onArrayStore",
                "([III)V");
        ArrayStoreHook hook = ArrayStoreHook.builder()
                .hookDescriptor(descriptor)
                .passArray(true)
                .passIndex(true)
                .passValue(true)
                .build();

        assertTrue(hook.isPassArray());
        assertTrue(hook.isPassIndex());
        assertTrue(hook.isPassValue());
    }

    @Test
    void arrayStoreHookCanModifyValue() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onArrayStore",
                "(I)I");
        ArrayStoreHook hook = ArrayStoreHook.builder()
                .hookDescriptor(descriptor)
                .passValue(true)
                .canModifyValue(true)
                .build();

        assertTrue(hook.isCanModifyValue());
    }

    // ========== ArrayLoadHook Tests ==========

    @Test
    void arrayLoadHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onArrayLoad", "()V");
        ArrayLoadHook hook = ArrayLoadHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.ARRAY_LOAD, hook.getTarget());
        assertTrue(hook.isEnabled());
        assertFalse(hook.isPassArray());
        assertFalse(hook.isPassIndex());
        assertFalse(hook.isPassValue());
        assertNull(hook.getArrayTypeFilter());
    }

    @Test
    void arrayLoadHookWithParameters() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onArrayLoad",
                "([II)V");
        ArrayLoadHook hook = ArrayLoadHook.builder()
                .hookDescriptor(descriptor)
                .passArray(true)
                .passIndex(true)
                .build();

        assertTrue(hook.isPassArray());
        assertTrue(hook.isPassIndex());
    }

    // ========== MethodCallHook Tests ==========

    @Test
    void methodCallHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onCall", "()V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.METHOD_CALL_BEFORE, hook.getTarget());
        assertTrue(hook.isEnabled());
        assertNull(hook.getTargetClass());
        assertNull(hook.getTargetMethod());
        assertNull(hook.getTargetDescriptor());
        assertTrue(hook.isBefore());
        assertFalse(hook.isAfter());
        assertFalse(hook.isPassReceiver());
        assertFalse(hook.isPassArguments());
        assertFalse(hook.isPassResult());
        assertFalse(hook.isPassMethodName());
    }

    @Test
    void methodCallHookWithTarget() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onCall", "()V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .targetClass("com/example/Target")
                .targetMethod("doSomething")
                .targetDescriptor("()V")
                .build();

        assertEquals("com/example/Target", hook.getTargetClass());
        assertEquals("doSomething", hook.getTargetMethod());
        assertEquals("()V", hook.getTargetDescriptor());
    }

    @Test
    void methodCallHookBefore() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "beforeCall", "()V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .before(true)
                .after(false)
                .build();

        assertTrue(hook.isBefore());
        assertFalse(hook.isAfter());
    }

    @Test
    void methodCallHookAfter() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "afterCall", "()V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .before(false)
                .after(true)
                .build();

        assertFalse(hook.isBefore());
        assertTrue(hook.isAfter());
    }

    @Test
    void methodCallHookWithReceiver() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onCall",
                "(Ljava/lang/Object;)V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .passReceiver(true)
                .build();

        assertTrue(hook.isPassReceiver());
    }

    @Test
    void methodCallHookWithArguments() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onCall",
                "([Ljava/lang/Object;)V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .passArguments(true)
                .build();

        assertTrue(hook.isPassArguments());
    }

    @Test
    void methodCallHookWithResult() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "afterCall",
                "(Ljava/lang/Object;)V");
        MethodCallHook hook = MethodCallHook.builder()
                .hookDescriptor(descriptor)
                .after(true)
                .passResult(true)
                .build();

        assertTrue(hook.isPassResult());
    }

    // ========== ExceptionHook Tests ==========

    @Test
    void exceptionHookDefaultValues() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onException", "()V");
        ExceptionHook hook = ExceptionHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(InstrumentationTarget.EXCEPTION_HANDLER, hook.getTarget());
        assertTrue(hook.isEnabled());
        assertNull(hook.getExceptionType());
        assertTrue(hook.isPassException()); // Default is true
        assertFalse(hook.isPassMethodName());
        assertFalse(hook.isPassClassName());
        assertFalse(hook.isCanSuppress());
    }

    @Test
    void exceptionHookWithExceptionType() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onException", "()V");
        ExceptionHook hook = ExceptionHook.builder()
                .hookDescriptor(descriptor)
                .exceptionType("java/lang/RuntimeException")
                .build();

        assertEquals("java/lang/RuntimeException", hook.getExceptionType());
    }

    @Test
    void exceptionHookWithException() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onException",
                "(Ljava/lang/Throwable;)V");
        ExceptionHook hook = ExceptionHook.builder()
                .hookDescriptor(descriptor)
                .passException(true)
                .build();

        assertTrue(hook.isPassException());
    }

    @Test
    void exceptionHookWithMethodName() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onException",
                "(Ljava/lang/String;)V");
        ExceptionHook hook = ExceptionHook.builder()
                .hookDescriptor(descriptor)
                .passMethodName(true)
                .build();

        assertTrue(hook.isPassMethodName());
    }

    @Test
    void exceptionHookCanSuppress() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onException",
                "(Ljava/lang/Throwable;)Z");
        ExceptionHook hook = ExceptionHook.builder()
                .hookDescriptor(descriptor)
                .passException(true)
                .canSuppress(true)
                .build();

        assertTrue(hook.isCanSuppress());
    }

    // ========== General Hook Interface Tests ==========

    @Test
    void hookCanBeDisabled() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .enabled(false)
                .build();

        assertFalse(hook.isEnabled());
    }

    @Test
    void hookPriorityDefaultsTo100() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(100, hook.getPriority());
    }

    @Test
    void hookDescriptorIsAccessible() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .build();

        assertEquals(descriptor, hook.getHookDescriptor());
        assertEquals("com/test/Hooks", descriptor.getOwner());
        assertEquals("onEntry", descriptor.getName());
        assertEquals("()V", descriptor.getDescriptor());
    }

    @Test
    void multipleFiltersCanBeApplied() {
        HookDescriptor descriptor = HookDescriptor.staticHook("com/test/Hooks", "onEntry", "()V");
        List<InstrumentationFilter> filters = List.of(
                ClassFilter.exact("com/test/Target"),
                PackageFilter.forPackage("com/test/"),
                MethodFilter.matching("test.*")
        );

        MethodEntryHook hook = MethodEntryHook.builder()
                .hookDescriptor(descriptor)
                .filters(filters)
                .build();

        assertEquals(3, hook.getFilters().size());
    }
}
