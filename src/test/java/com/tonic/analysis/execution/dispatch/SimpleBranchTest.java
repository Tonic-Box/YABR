package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleBranchTest {
    private BytecodeContext context;

    @BeforeEach
    void setUp() {
        context = new BytecodeContext.Builder()
            .heapManager(new SimpleHeapManager())
            .classResolver(new ClassResolver(new ClassPool(true)))
            .maxInstructions(10000)
            .build();
    }

    private BytecodeResult execute(MethodEntry method, ConcreteValue... args) {
        return new BytecodeEngine(context).execute(method, args);
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        for (MethodEntry method : cf.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        throw new AssertionError("Method not found: " + name);
    }

    @Test
    void testSimpleReturn() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("TestClass")
            .publicStaticMethod("test", "()I")
                .iconst(42)
                .ireturn()
            .build();

        MethodEntry method = findMethod(cf, "test");
        BytecodeResult result = execute(method);

        System.out.println("Status: " + result.getStatus());
        System.out.println("Return value: " + result.getReturnValue());

        assertEquals(BytecodeResult.Status.COMPLETED, result.getStatus());
        assertNotNull(result.getReturnValue());
        assertEquals(42, result.getReturnValue().asInt());
    }
}
