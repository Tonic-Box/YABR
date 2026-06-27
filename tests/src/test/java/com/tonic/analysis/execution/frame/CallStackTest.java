package com.tonic.analysis.execution.frame;

import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallStackTest {

    private ClassFile classFile;
    private MethodEntry testMethod;

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
                    testMethod = method;
                    break;
                }
            }
        }
    }

    @Test
    void testConstruction() {
        CallStack stack = new CallStack(100);
        assertNotNull(stack);
        assertEquals(0, stack.depth());
        assertTrue(stack.isEmpty());
    }

    @Test
    void testConstructionWithZeroMaxDepthThrows() {
        assertThrows(IllegalArgumentException.class, () -> new CallStack(0));
    }

    @Test
    void testConstructionWithNegativeMaxDepthThrows() {
        assertThrows(IllegalArgumentException.class, () -> new CallStack(-5));
    }

    @Test
    void testPushIncreasesDepth() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame);
            assertEquals(1, stack.depth());
            assertFalse(stack.isEmpty());
        }
    }

    @Test
    void testPushNullFrameThrows() {
        CallStack stack = new CallStack(10);
        assertThrows(IllegalArgumentException.class, () -> stack.push(null));
    }

    @Test
    void testPushExceedingMaxDepthThrows() {
        if (testMethod != null) {
            CallStack stack = new CallStack(2);

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            assertThrows(StackOverflowError.class,
                () -> stack.push(new StackFrame(testMethod, new ConcreteValue[0])));
        }
    }

    @Test
    void testPopDecreasesDepth() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame);
            StackFrame popped = stack.pop();

            assertEquals(frame, popped);
            assertEquals(0, stack.depth());
            assertTrue(stack.isEmpty());
        }
    }

    @Test
    void testPopEmptyStackThrows() {
        CallStack stack = new CallStack(10);
        assertThrows(IllegalStateException.class, stack::pop);
    }

    @Test
    void testPeekReturnsTopFrame() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame1 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame2 = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame1);
            stack.push(frame2);

            assertEquals(frame2, stack.peek());
            assertEquals(2, stack.depth());
        }
    }

    @Test
    void testPeekEmptyStackThrows() {
        CallStack stack = new CallStack(10);
        assertThrows(IllegalStateException.class, stack::peek);
    }

    @Test
    void testPeekAt() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame1 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame2 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame3 = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame1);
            stack.push(frame2);
            stack.push(frame3);

            assertEquals(frame3, stack.peekAt(0));
            assertEquals(frame2, stack.peekAt(1));
            assertEquals(frame1, stack.peekAt(2));
        }
    }

    @Test
    void testPeekAtNegativeDepthThrows() {
        CallStack stack = new CallStack(10);
        assertThrows(IllegalArgumentException.class, () -> stack.peekAt(-1));
    }

    @Test
    void testPeekAtExcessiveDepthThrows() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            assertThrows(IndexOutOfBoundsException.class, () -> stack.peekAt(5));
        }
    }

    @Test
    void testDepthTracking() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);

            assertEquals(0, stack.depth());

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            assertEquals(1, stack.depth());

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            assertEquals(2, stack.depth());

            stack.pop();
            assertEquals(1, stack.depth());

            stack.pop();
            assertEquals(0, stack.depth());
        }
    }

    @Test
    void testIsEmpty() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);

            assertTrue(stack.isEmpty());

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            assertFalse(stack.isEmpty());

            stack.pop();
            assertTrue(stack.isEmpty());
        }
    }

    @Test
    void testClear() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            assertEquals(3, stack.depth());

            stack.clear();

            assertEquals(0, stack.depth());
            assertTrue(stack.isEmpty());
        }
    }

    @Test
    void testSnapshot() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame1 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame2 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame3 = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame1);
            stack.push(frame2);
            stack.push(frame3);

            List<StackFrame> snapshot = stack.snapshot();

            assertEquals(3, snapshot.size());
            assertEquals(frame1, snapshot.get(0));
            assertEquals(frame2, snapshot.get(1));
            assertEquals(frame3, snapshot.get(2));
        }
    }

    @Test
    void testSnapshotIsUnmodifiable() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            List<StackFrame> snapshot = stack.snapshot();

            assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new StackFrame(testMethod, new ConcreteValue[0])));
        }
    }

    @Test
    void testSnapshotIsBottomToTop() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame bottom = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame middle = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame top = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(bottom);
            stack.push(middle);
            stack.push(top);

            List<StackFrame> snapshot = stack.snapshot();

            assertEquals(bottom, snapshot.get(0));
            assertEquals(middle, snapshot.get(1));
            assertEquals(top, snapshot.get(2));
        }
    }

    @Test
    void testTopToBottomIteration() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame1 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame2 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame3 = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame1);
            stack.push(frame2);
            stack.push(frame3);

            int count = 0;
            StackFrame[] expected = {frame3, frame2, frame1};

            for (StackFrame frame : stack.topToBottom()) {
                assertEquals(expected[count], frame);
                count++;
            }

            assertEquals(3, count);
        }
    }

    @Test
    void testFormatStackTraceEmpty() {
        CallStack stack = new CallStack(10);
        String trace = stack.formatStackTrace();

        assertNotNull(trace);
        assertTrue(trace.contains("empty"));
    }

    @Test
    void testFormatStackTraceWithFrames() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            String trace = stack.formatStackTrace();

            assertNotNull(trace);
            assertTrue(trace.contains("Call Stack Trace"));
            assertTrue(trace.contains("[0]"));
            assertTrue(trace.contains("[1]"));
            assertTrue(trace.contains(testMethod.getName()));
        }
    }

    @Test
    void testFormatStackTraceWithCompletedFrame() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame = new StackFrame(testMethod, new ConcreteValue[0]);
            frame.complete(ConcreteValue.intValue(0));

            stack.push(frame);

            String trace = stack.formatStackTrace();

            assertTrue(trace.contains("completed"));
        }
    }

    @Test
    void testFormatStackTraceWithException() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame = new StackFrame(testMethod, new ConcreteValue[0]);
            ObjectInstance exception = new ObjectInstance(1, "java/lang/RuntimeException");
            frame.completeExceptionally(exception);

            stack.push(frame);

            String trace = stack.formatStackTrace();

            assertTrue(trace.contains("exception"));
        }
    }

    @Test
    void testToString() {
        CallStack stack = new CallStack(100);
        String str = stack.toString();

        assertNotNull(str);
        assertTrue(str.contains("CallStack"));
        assertTrue(str.contains("depth="));
        assertTrue(str.contains("maxDepth="));
    }

    @Test
    void testPushPopSequence() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            StackFrame frame1 = new StackFrame(testMethod, new ConcreteValue[0]);
            StackFrame frame2 = new StackFrame(testMethod, new ConcreteValue[0]);

            stack.push(frame1);
            stack.push(frame2);

            assertEquals(frame2, stack.pop());
            assertEquals(frame1, stack.pop());
            assertTrue(stack.isEmpty());
        }
    }

    @Test
    void testMaxDepthEnforcement() {
        if (testMethod != null) {
            CallStack stack = new CallStack(3);

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            assertEquals(3, stack.depth());

            assertThrows(StackOverflowError.class,
                () -> stack.push(new StackFrame(testMethod, new ConcreteValue[0])));
        }
    }

    @Test
    void testSnapshotAfterModification() {
        if (testMethod != null) {
            CallStack stack = new CallStack(10);
            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            List<StackFrame> snapshot1 = stack.snapshot();
            assertEquals(1, snapshot1.size());

            stack.push(new StackFrame(testMethod, new ConcreteValue[0]));

            List<StackFrame> snapshot2 = stack.snapshot();
            assertEquals(2, snapshot2.size());

            assertEquals(1, snapshot1.size());
        }
    }
}
