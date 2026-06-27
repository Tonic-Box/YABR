package com.tonic.analysis.execution;

import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.listener.TracingListener;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class ExceptionHandlerDebugTest {

    private ClassFile classFile;
    private ClassPool classPool;
    private BytecodeContext context;

    @BeforeEach
    void setUp() throws IOException {
        classFile = TestUtils.loadTestFixture("ExecutionTestFixture");
        classPool = new ClassPool();
        classPool.put(classFile);

        context = new BytecodeContext.Builder()
            .heapManager(new SimpleHeapManager())
            .classResolver(new ClassResolver(classPool))
            .maxCallDepth(1000)
            .maxInstructions(100000)
            .trackStatistics(true)
            .build();
    }

    private MethodEntry findMethod(String name) {
        return classFile.getMethods().stream()
            .filter(m -> m.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Method not found: " + name));
    }

    @Test
    void testDivideOrThrow() {
        MethodEntry method = findMethod("divideOrThrow");
        BytecodeEngine engine = new BytecodeEngine(context);

        TracingListener tracer = new TracingListener();
        engine.addListener(tracer);

        System.out.println("=== divideOrThrow(10, 0) ===");
        System.out.println("Method: " + method.getName() + method.getDesc());

        BytecodeResult result = engine.execute(method,
            ConcreteValue.intValue(10),
            ConcreteValue.intValue(0));

        System.out.println("Result status: " + result.getStatus());
        System.out.println("Exception: " + result.getException());
        if (result.getException() != null) {
            System.out.println("Exception class: " + result.getException().getClassName());
        }
        System.out.println("Return value: " + result.getReturnValue());
        System.out.println("Stack trace: " + result.getStackTrace());

        System.out.println("\n" + tracer.formatTrace(50));
    }
}
