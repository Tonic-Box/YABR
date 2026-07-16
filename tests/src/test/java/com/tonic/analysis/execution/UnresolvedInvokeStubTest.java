package com.tonic.analysis.execution;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: an unresolved (library) instance call whose only stack operand is the receiver must not
 * underflow the operand stack. {@code handleInvoke} already pops the receiver, so the stub path must
 * not pop it again. This reproduces the double-pop that made every super-constructor call on an
 * unresolved superclass throw a reified {@code IllegalStateException} ("Stack underflow").
 */
class UnresolvedInvokeStubTest {

    @Test
    void unresolvedInstanceCallDoesNotUnderflowStack() throws Exception {
        byte[] bytes = ClassBuilder.create("test/Sub")
                .addMethod(AccessFlags.ACC_PUBLIC, "run", "()V")
                .code().aload(0).invokevirtual("some/Unknown", "foo", "()V").vreturn().end()
                .end()
                .toByteArray();

        ClassPool pool = new ClassPool();
        ClassFile cf = pool.loadClass(new ByteArrayInputStream(bytes));
        MethodEntry run = cf.getMethod("run", "()V");

        SimpleHeapManager heap = new SimpleHeapManager();
        BytecodeContext ctx = new BytecodeContext.Builder()
                .mode(ExecutionMode.RECURSIVE)
                .heapManager(heap)
                .classResolver(new ClassResolver(pool))
                .maxInstructions(10_000)
                .build();
        ConcreteValue self = ConcreteValue.reference(heap.newObject("test/Sub"));

        BytecodeResult result = new BytecodeEngine(ctx).execute(run, self);

        assertTrue(result.isSuccess(),
                "unresolved instance call must not underflow the stack; got "
                        + (result.hasException() ? "throw " + result.getException().getClassName()
                        : "status " + result.getStatus()));
    }
}
