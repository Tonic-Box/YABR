package com.tonic.analysis.execution.frame;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class StackFrameTest {

    private ClassFile classFile;
    private MethodEntry methodWithCode;
    private MethodEntry abstractMethod;

    @BeforeEach
    void setUp() throws IOException {
        Path testClassPath = Paths.get("target/test-classes/com/tonic/demo/TestClass.class");
        if (!Files.exists(testClassPath)) {
            testClassPath = Paths.get("build/classes/java/test/com/tonic/demo/TestClass.class");
        }

        if (Files.exists(testClassPath)) {
            byte[] classBytes = Files.readAllBytes(testClassPath);
            classFile = new ClassFile(new ByteArrayInputStream(classBytes));

            for (MethodEntry method : classFile.getMethods()) {
                if (method.getName().equals("simpleMethod") && method.getCodeAttribute() != null) {
                    methodWithCode = method;
                }
            }
        }
    }

    @Test
    void testConstructionWithNullMethodThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StackFrame(null, new ConcreteValue[0]));
    }

    @Test
    void testConstructionWithAbstractMethodThrows() {
        if (classFile != null) {
            for (MethodEntry method : classFile.getMethods()) {
                if (method.getCodeAttribute() == null) {
                    assertThrows(IllegalArgumentException.class,
                        () -> new StackFrame(method, new ConcreteValue[0]));
                    return;
                }
            }
        }
    }

    @Test
    void testConstructionWithValidMethod() {
        if (methodWithCode != null) {
            ConcreteValue[] args = new ConcreteValue[]{ConcreteValue.intValue(42)};
            StackFrame frame = new StackFrame(methodWithCode, args);

            assertNotNull(frame);
            assertEquals(methodWithCode, frame.getMethod());
            assertNotNull(frame.getCode());
            assertNotNull(frame.getStack());
            assertNotNull(frame.getLocals());
            assertEquals(0, frame.getPC());
            assertFalse(frame.isCompleted());
        }
    }

    @Test
    void testPCManagement() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            assertEquals(0, frame.getPC());

            frame.setPC(10);
            assertEquals(10, frame.getPC());

            frame.advancePC(5);
            assertEquals(15, frame.getPC());

            frame.setPC(0);
            assertEquals(0, frame.getPC());
        }
    }

    @Test
    void testSetPCWithNegativeValueThrows() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);
            assertThrows(IllegalArgumentException.class, () -> frame.setPC(-1));
        }
    }

    @Test
    void testAdvancePCWithNegativeValueThrows() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);
            assertThrows(IllegalArgumentException.class, () -> frame.advancePC(-5));
        }
    }

    @Test
    void testGetCurrentInstruction() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            var instruction = frame.getCurrentInstruction();
            if (methodWithCode.getCodeAttribute().getCode().length > 0) {
                assertNotNull(instruction);
                assertEquals(0, instruction.getOffset());
            }
        }
    }

    @Test
    void testGetInstructionAt() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            var instruction = frame.getInstructionAt(0);
            if (methodWithCode.getCodeAttribute().getCode().length > 0) {
                assertNotNull(instruction);
                assertEquals(0, instruction.getOffset());
            }
        }
    }

    @Test
    void testHasMoreInstructions() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            if (methodWithCode.getCodeAttribute().getCode().length > 0) {
                assertTrue(frame.hasMoreInstructions());
            }

            frame.complete(ConcreteValue.intValue(0));
            assertFalse(frame.hasMoreInstructions());
        }
    }

    @Test
    void testNormalCompletion() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            assertFalse(frame.isCompleted());

            ConcreteValue returnVal = ConcreteValue.intValue(42);
            frame.complete(returnVal);

            assertTrue(frame.isCompleted());
            assertEquals(returnVal, frame.getReturnValue());
            assertNull(frame.getException());
        }
    }

    @Test
    void testVoidCompletion() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            frame.complete(null);

            assertTrue(frame.isCompleted());
            assertNull(frame.getReturnValue());
            assertNull(frame.getException());
        }
    }

    @Test
    void testExceptionalCompletion() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            ObjectInstance exception = new ObjectInstance(1, "java/lang/RuntimeException");
            frame.completeExceptionally(exception);

            assertTrue(frame.isCompleted());
            assertEquals(exception, frame.getException());
        }
    }

    @Test
    void testCompleteExceptionallyWithNullThrows() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);
            assertThrows(IllegalArgumentException.class, () -> frame.completeExceptionally(null));
        }
    }

    @Test
    void testDoubleCompletionThrows() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            frame.complete(ConcreteValue.intValue(0));
            assertThrows(IllegalStateException.class, () -> frame.complete(ConcreteValue.intValue(1)));
        }
    }

    @Test
    void testGetReturnValueBeforeCompletionThrows() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);
            assertThrows(IllegalStateException.class, frame::getReturnValue);
        }
    }

    @Test
    void testLocalsInitializationWithArgs() {
        if (methodWithCode != null) {
            ConcreteValue[] args = new ConcreteValue[]{
                ConcreteValue.intValue(42)
            };

            StackFrame frame = new StackFrame(methodWithCode, args);

            assertEquals(42, frame.getLocals().getInt(0));
        }
    }

    @Test
    void testLocalsInitializationWithWideValues() {
        if (methodWithCode != null) {
            ConcreteValue[] args = new ConcreteValue[]{
                ConcreteValue.longValue(123456789L)
            };

            StackFrame frame = new StackFrame(methodWithCode, args);

            assertEquals(123456789L, frame.getLocals().getLong(0));
        }
    }

    @Test
    void testGetMethodSignature() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            String signature = frame.getMethodSignature();
            assertNotNull(signature);
            assertTrue(signature.contains(methodWithCode.getName()));
            assertTrue(signature.contains(methodWithCode.getDesc()));
        }
    }

    @Test
    void testGetLineNumber() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            int lineNumber = frame.getLineNumber();
            assertTrue(lineNumber >= -1);
        }
    }

    @Test
    void testStackAccessors() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            frame.getStack().pushInt(42);
            assertEquals(42, frame.getStack().popInt());
        }
    }

    @Test
    void testToString() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            String str = frame.toString();
            assertNotNull(str);
            assertTrue(str.contains("StackFrame"));
            assertTrue(str.contains("pc="));
        }
    }

    @Test
    void testFrameStateAfterPCAdvancement() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            frame.advancePC(10);
            frame.getStack().pushInt(100);

            assertEquals(10, frame.getPC());
            assertEquals(100, frame.getStack().popInt());
        }
    }

    @Test
    void testMultipleLocalsAccess() {
        if (methodWithCode != null) {
            ConcreteValue[] args = new ConcreteValue[]{
                ConcreteValue.intValue(1),
                ConcreteValue.intValue(2),
                ConcreteValue.intValue(3)
            };

            StackFrame frame = new StackFrame(methodWithCode, args);

            assertEquals(1, frame.getLocals().getInt(0));
            assertEquals(2, frame.getLocals().getInt(1));
            assertEquals(3, frame.getLocals().getInt(2));
        }
    }

    @Test
    void testExceptionAccessBeforeCompletion() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);
            assertNull(frame.getException());
        }
    }

    @Test
    void testNormalCompletionDoesNotSetException() {
        if (methodWithCode != null) {
            StackFrame frame = new StackFrame(methodWithCode, new ConcreteValue[0]);

            frame.complete(ConcreteValue.intValue(0));

            assertTrue(frame.isCompleted());
            assertNull(frame.getException());
        }
    }
}
