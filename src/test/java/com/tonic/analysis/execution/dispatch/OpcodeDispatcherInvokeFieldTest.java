package com.tonic.analysis.execution.dispatch;

import com.tonic.testutil.BytecodeBuilder;
import com.tonic.analysis.execution.core.*;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.resolve.ClassResolver;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OpcodeDispatcherInvokeFieldTest {
    private BytecodeContext context;
    private SimpleHeapManager heapManager;
    private ClassPool classPool;

    @BeforeEach
    void setUp() {
        heapManager = new SimpleHeapManager();
        classPool = new ClassPool(true);
        context = new BytecodeContext.Builder()
            .heapManager(heapManager)
            .classResolver(new ClassResolver(classPool))
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

    @Nested
    class GetStaticTests {
        @Test
        void testGetStaticEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestGetStatic")
                    .publicStaticMethod("test", "()I")
                        .getstatic("java/lang/Integer", "MAX_VALUE", "I")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method);
            assertNotNull(result);
        }
    }

    @Nested
    class PutStaticTests {
        @Test
        void testPutStaticEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestPutStatic")
                    .publicStaticMethod("test", "(I)V")
                        .iload(0)
                        .putstatic("TestClass", "staticField", "I")
                        .vreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(42));
            assertNotNull(result);
        }
    }

    @Nested
    class GetFieldTests {
        @Test
        void testGetFieldEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestGetField")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)I")
                        .aload(0)
                        .getfield("TestClass", "intField", "I")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("TestClass");
            ConcreteValue objRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objRef);
            assertNotNull(result);
        }
    }

    @Nested
    class PutFieldTests {
        @Test
        void testPutFieldEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestPutField")
                    .publicStaticMethod("test", "(Ljava/lang/Object;I)V")
                        .aload(0)
                        .iload(1)
                        .putfield("TestClass", "intField", "I")
                        .vreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("TestClass");
            ConcreteValue objRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objRef, ConcreteValue.intValue(99));
            assertNotNull(result);
        }
    }

    @Nested
    class InvokeStaticTests {
        @Test
        void testInvokeStaticEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestInvokeStatic")
                    .publicStaticMethod("test", "(I)I")
                        .iload(0)
                        .invokestatic("java/lang/Math", "abs", "(I)I")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(-5));
            assertNotNull(result);
        }
    }

    @Nested
    class InvokeVirtualTests {
        @Test
        void testInvokeVirtualEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestInvokeVirtual")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)I")
                        .aload(0)
                        .invokevirtual("java/lang/Object", "hashCode", "()I")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue objRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objRef);
            assertNotNull(result);
        }
    }

    @Nested
    class InvokeSpecialTests {
        @Test
        void testInvokeSpecialEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestInvokeSpecial")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)V")
                        .aload(0)
                        .invokespecial("java/lang/Object", "<init>", "()V")
                        .vreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue objRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objRef);
            assertNotNull(result);
        }
    }

    @Nested
    class InvokeInterfaceTests {
        @Test
        void testInvokeInterfaceEncodesCorrectly() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestInvokeInterface")
                    .publicStaticMethod("test", "(Ljava/util/List;)I")
                        .aload(0)
                        .invokeinterface("java/util/List", "size", "()I", 1)
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/util/ArrayList");
            ConcreteValue objRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objRef);
            assertNotNull(result);
        }
    }

    @Nested
    class CombinedInvokeFieldTests {
        @Test
        void testMultipleFieldAccess() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestMultiField")
                    .publicStaticMethod("test", "(I)V")
                        .iload(0)
                        .putstatic("TestClass", "field1", "I")
                        .getstatic("TestClass", "field1", "I")
                        .putstatic("TestClass", "field2", "I")
                        .vreturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            BytecodeResult result = execute(method, ConcreteValue.intValue(100));
            assertNotNull(result);
        }

        @Test
        void testFieldAndInvoke() throws IOException {
            ClassFile cf = BytecodeBuilder.forClass("TestFieldInvoke")
                    .publicStaticMethod("test", "(Ljava/lang/Object;)I")
                        .aload(0)
                        .invokevirtual("java/lang/Object", "hashCode", "()I")
                        .putstatic("TestClass", "hash", "I")
                        .getstatic("TestClass", "hash", "I")
                        .ireturn()
                    .build();
            MethodEntry method = findMethod(cf, "test");
            ObjectInstance obj = heapManager.newObject("java/lang/Object");
            ConcreteValue objRef = ConcreteValue.reference(obj);
            BytecodeResult result = execute(method, objRef);
            assertNotNull(result);
        }
    }
}
