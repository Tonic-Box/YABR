package com.tonic.analysis.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.core.ExecutionMode;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.listener.BytecodeListener;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.type.AccessFlags;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InheritedFieldTest
{

    @Test
    void fieldReachedThroughSubclassSharesStorageWithDeclaringClass() throws Exception
    {
        ClassPool pool = new ClassPool();
        pool.loadClass(new ByteArrayInputStream(ClassBuilder.create("test/Base").access(AccessFlags.ACC_PUBLIC).addField(AccessFlags.ACC_PUBLIC, "x", "I").end().toByteArray()));
        ClassFile sub = pool.loadClass(new ByteArrayInputStream(ClassBuilder.create("test/Sub").access(AccessFlags.ACC_PUBLIC).superClass("test/Base").addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "run", "(Ltest/Sub;)I").code().aload(0).iconst(7).putfield("test/Base", "x", "I").aload(0).getfield("test/Sub", "x", "I").ireturn().end().end().toByteArray()));

        SimpleHeapManager heap = new SimpleHeapManager();
        BytecodeContext ctx = new BytecodeContext.Builder().mode(ExecutionMode.RECURSIVE).heapManager(heap).classResolver(new ClassResolver(pool)).maxInstructions(10_000).build();
        List<String> owners = new ArrayList<>();
        BytecodeEngine engine = new BytecodeEngine(ctx);
        engine.addListener(new BytecodeListener()
        {
            @Override
            public void onFieldWrite(ObjectInstance instance, String owner, String fieldName, String descriptor, ConcreteValue oldValue, ConcreteValue newValue)
            {
                owners.add(owner + "." + fieldName + ":" + descriptor);
            }
        });

        BytecodeResult result = engine.execute(sub.getMethod("run", "(Ltest/Sub;)I"), ConcreteValue.reference(heap.newObject("test/Sub")));

        assertTrue(result.isSuccess());
        assertEquals(7, result.getReturnValue().asInt());
        assertEquals(List.of("test/Base.x:I"), owners);
    }
}
