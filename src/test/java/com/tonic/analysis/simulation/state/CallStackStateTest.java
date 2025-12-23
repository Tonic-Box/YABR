package com.tonic.analysis.simulation.state;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;
import com.tonic.analysis.ssa.ir.InvokeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallStackStateTest {

    private IRMethod method1;
    private IRMethod method2;
    private IRMethod method3;
    private InvokeInstruction invoke1;
    private InvokeInstruction invoke2;
    private SimulationState state;
    private IRBlock returnBlock;

    @BeforeEach
    void setUp() {
        method1 = new IRMethod("TestClass", "testMethod1", "()V", false);
        method2 = new IRMethod("TestClass", "testMethod2", "(I)V", false);
        method3 = new IRMethod("TestClass", "testMethod3", "(II)I", true);
        invoke1 = new InvokeInstruction(InvokeType.VIRTUAL, "Owner1", "method1", "()V", Collections.emptyList());
        invoke2 = new InvokeInstruction(InvokeType.STATIC, "Owner2", "method2", "(I)V", Collections.emptyList());
        state = SimulationState.empty();
        returnBlock = new IRBlock("return");
    }

    @Test
    void emptyCallStack() {
        CallStackState stack = CallStackState.empty();

        assertTrue(stack.isEmpty());
        assertEquals(0, stack.depth());
        assertNull(stack.peek());
        assertNotNull(stack.getFrames());
        assertTrue(stack.getFrames().isEmpty());
    }

    @Test
    void withFrameCreatesNonEmptyStack() {
        CallStackState.CallFrame frame = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState stack = CallStackState.withFrame(frame);

        assertFalse(stack.isEmpty());
        assertEquals(1, stack.depth());
        assertNotNull(stack.peek());
        assertEquals(method1, stack.peek().getMethod());
    }

    @Test
    void pushAndPopFrames() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState stack = CallStackState.empty();
        assertEquals(0, stack.depth());

        stack = stack.push(frame1);
        assertEquals(1, stack.depth());
        assertEquals(method1, stack.peek().getMethod());

        stack = stack.push(frame2);
        assertEquals(2, stack.depth());
        assertEquals(method2, stack.peek().getMethod());

        stack = stack.pop();
        assertEquals(1, stack.depth());
        assertEquals(method1, stack.peek().getMethod());

        stack = stack.pop();
        assertEquals(0, stack.depth());
        assertTrue(stack.isEmpty());
    }

    @Test
    void popOnEmptyReturnsSameStack() {
        CallStackState empty = CallStackState.empty();
        CallStackState afterPop = empty.pop();

        assertTrue(afterPop.isEmpty());
        assertEquals(empty, afterPop);
    }

    @Test
    void peekReturnsTopFrameWithoutRemoving() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState stack = CallStackState.empty().push(frame1).push(frame2);

        CallStackState.CallFrame peeked = stack.peek();
        assertEquals(method2, peeked.getMethod());
        assertEquals(2, stack.depth());

        CallStackState.CallFrame peekedAgain = stack.peek();
        assertEquals(method2, peekedAgain.getMethod());
        assertEquals(2, stack.depth());
    }

    @Test
    void peekOnEmptyReturnsNull() {
        CallStackState empty = CallStackState.empty();
        assertNull(empty.peek());
    }

    @Test
    void containsDetectsRecursion() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState stack = CallStackState.empty().push(frame1).push(frame2);

        assertTrue(stack.contains(method1));
        assertTrue(stack.contains(method2));
        assertFalse(stack.contains(method3));
    }

    @Test
    void containsOnEmptyReturnsFalse() {
        CallStackState empty = CallStackState.empty();
        assertFalse(empty.contains(method1));
    }

    @Test
    void depthTrackingAfterMultipleOperations() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);
        CallStackState.CallFrame frame3 = new CallStackState.CallFrame(method3, invoke1, state, returnBlock, 2);

        CallStackState stack = CallStackState.empty();
        assertEquals(0, stack.depth());

        stack = stack.push(frame1);
        assertEquals(1, stack.depth());

        stack = stack.push(frame2);
        assertEquals(2, stack.depth());

        stack = stack.push(frame3);
        assertEquals(3, stack.depth());

        stack = stack.pop();
        assertEquals(2, stack.depth());

        stack = stack.pop();
        assertEquals(1, stack.depth());
    }

    @Test
    void getFrameByIndex() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);
        CallStackState.CallFrame frame3 = new CallStackState.CallFrame(method3, invoke1, state, returnBlock, 2);

        CallStackState stack = CallStackState.empty().push(frame1).push(frame2).push(frame3);

        assertEquals(method1, stack.getFrame(0).getMethod());
        assertEquals(method2, stack.getFrame(1).getMethod());
        assertEquals(method3, stack.getFrame(2).getMethod());

        assertNull(stack.getFrame(-1));
        assertNull(stack.getFrame(3));
        assertNull(stack.getFrame(100));
    }

    @Test
    void getCallChain() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState stack = CallStackState.empty().push(frame1).push(frame2);

        List<IRMethod> chain = stack.getCallChain();
        assertEquals(2, chain.size());
        assertEquals(method1, chain.get(0));
        assertEquals(method2, chain.get(1));
    }

    @Test
    void getCallChainOnEmpty() {
        CallStackState empty = CallStackState.empty();
        List<IRMethod> chain = empty.getCallChain();
        assertTrue(chain.isEmpty());
    }

    @Test
    void getCallChainString() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState stack = CallStackState.empty().push(frame1).push(frame2);

        String chainString = stack.getCallChainString();
        assertTrue(chainString.contains("testMethod1"));
        assertTrue(chainString.contains("testMethod2"));
        assertTrue(chainString.contains("->"));
    }

    @Test
    void getCallChainStringOnEmpty() {
        CallStackState empty = CallStackState.empty();
        assertEquals("<empty>", empty.getCallChainString());
    }

    @Test
    void immutabilityAfterPush() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState original = CallStackState.empty().push(frame1);
        CallStackState modified = original.push(frame2);

        assertEquals(1, original.depth());
        assertEquals(2, modified.depth());

        assertNotEquals(original, modified);
    }

    @Test
    void immutabilityAfterPop() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState original = CallStackState.empty().push(frame1).push(frame2);
        CallStackState popped = original.pop();

        assertEquals(2, original.depth());
        assertEquals(1, popped.depth());

        assertNotEquals(original, popped);
    }

    @Test
    void getFramesReturnsUnmodifiableList() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState stack = CallStackState.empty().push(frame1);

        List<CallStackState.CallFrame> frames = stack.getFrames();

        assertThrows(UnsupportedOperationException.class, () -> {
            frames.add(new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1));
        });
    }

    @Test
    void callStackStateEquality() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);

        CallStackState stack1 = CallStackState.empty().push(frame1);
        CallStackState stack2 = CallStackState.empty().push(frame1);

        assertEquals(stack1, stack2);
        assertEquals(stack1.hashCode(), stack2.hashCode());
    }

    @Test
    void callStackStateInequalityDifferentFrames() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke2, state, returnBlock, 1);

        CallStackState stack1 = CallStackState.empty().push(frame1);
        CallStackState stack2 = CallStackState.empty().push(frame2);

        assertNotEquals(stack1, stack2);
    }

    @Test
    void callStackStateToString() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 0);
        CallStackState stack = CallStackState.empty().push(frame1);

        String str = stack.toString();
        assertTrue(str.contains("depth=1"));
        assertTrue(str.contains("testMethod1"));
    }

    @Test
    void callFrameGetters() {
        CallStackState.CallFrame frame = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 5);

        assertEquals(method1, frame.getMethod());
        assertEquals(invoke1, frame.getCallSite());
        assertEquals(state, frame.getCallerState());
        assertEquals(returnBlock, frame.getReturnBlock());
        assertEquals(5, frame.getReturnInstructionIndex());
    }

    @Test
    void callFrameEquality() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 5);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 5);

        assertEquals(frame1, frame2);
        assertEquals(frame1.hashCode(), frame2.hashCode());
    }

    @Test
    void callFrameInequalityDifferentMethod() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 5);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method2, invoke1, state, returnBlock, 5);

        assertNotEquals(frame1, frame2);
    }

    @Test
    void callFrameInequalityDifferentInstructionIndex() {
        CallStackState.CallFrame frame1 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 5);
        CallStackState.CallFrame frame2 = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 10);

        assertNotEquals(frame1, frame2);
    }

    @Test
    void callFrameToString() {
        CallStackState.CallFrame frame = new CallStackState.CallFrame(method1, invoke1, state, returnBlock, 5);

        String str = frame.toString();
        assertTrue(str.contains("testMethod1"));
        assertTrue(str.contains("return"));
    }

    @Test
    void callFrameWithNullReturnBlock() {
        CallStackState.CallFrame frame = new CallStackState.CallFrame(method1, invoke1, state, null, 0);

        assertNull(frame.getReturnBlock());
        String str = frame.toString();
        assertTrue(str.contains("null"));
    }
}
