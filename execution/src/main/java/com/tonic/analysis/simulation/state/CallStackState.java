package com.tonic.analysis.simulation.state;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;

import java.util.*;

/**
 * Represents the call stack for inter-procedural simulation.
 *
 * <p>Each frame contains:
 * <ul>
 *   <li>The method being executed</li>
 *   <li>The simulation state at the call site</li>
 *   <li>The call instruction</li>
 *   <li>The return block to continue execution</li>
 * </ul>
 *
 * <p>This class is immutable - all operations return new instances.
 */
public final class CallStackState {

    private final List<CallFrame> frames;

    private CallStackState(List<CallFrame> frames) {
        this.frames = Collections.unmodifiableList(new ArrayList<>(frames));
    }

    /**
     * Creates an empty call stack.
     */
    public static CallStackState empty() {
        return new CallStackState(Collections.emptyList());
    }

    /**
     * Creates a call stack with an initial frame.
     */
    public static CallStackState withFrame(CallFrame frame) {
        return new CallStackState(List.of(frame));
    }

    /**
     * Pushes a new call frame onto the stack.
     */
    public CallStackState push(CallFrame frame) {
        List<CallFrame> newFrames = new ArrayList<>(frames);
        newFrames.add(frame);
        return new CallStackState(newFrames);
    }

    /**
     * Pops the top call frame from the stack.
     */
    public CallStackState pop() {
        if (frames.isEmpty()) return this;
        List<CallFrame> newFrames = new ArrayList<>(frames);
        newFrames.remove(newFrames.size() - 1);
        return new CallStackState(newFrames);
    }

    /**
     * Gets the top frame without removing it.
     */
    public CallFrame peek() {
        if (frames.isEmpty()) return null;
        return frames.get(frames.size() - 1);
    }

    /**
     * Gets the current call depth.
     */
    public int depth() {
        return frames.size();
    }

    /**
     * Checks if the stack is empty.
     */
    public boolean isEmpty() {
        return frames.isEmpty();
    }

    /**
     * Gets all frames (bottom to top).
     */
    public List<CallFrame> getFrames() {
        return frames;
    }

    /**
     * Gets the frame at a specific depth (0 = bottom).
     */
    public CallFrame getFrame(int depth) {
        if (depth < 0 || depth >= frames.size()) return null;
        return frames.get(depth);
    }

    /**
     * Checks if a method is already on the call stack (recursion detection).
     */
    public boolean contains(IRMethod method) {
        for (CallFrame frame : frames) {
            if (frame.getMethod().equals(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the call chain as a list of methods.
     */
    public List<IRMethod> getCallChain() {
        List<IRMethod> chain = new ArrayList<>();
        for (CallFrame frame : frames) {
            chain.add(frame.getMethod());
        }
        return chain;
    }

    /**
     * Gets the call chain as a string (for debugging).
     */
    public String getCallChainString() {
        if (frames.isEmpty()) return "<empty>";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) sb.append(" -> ");
            CallFrame frame = frames.get(i);
            sb.append(frame.getMethod().getName());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallStackState)) return false;
        CallStackState that = (CallStackState) o;
        return Objects.equals(frames, that.frames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(frames);
    }

    @Override
    public String toString() {
        return "CallStackState[depth=" + frames.size() + ", chain=" + getCallChainString() + "]";
    }

    /**
     * Represents a single frame on the call stack.
     */
    public static class CallFrame {
        private final IRMethod method;
        private final InvokeInstruction callSite;
        private final SimulationState callerState;
        private final IRBlock returnBlock;
        private final int returnInstructionIndex;

        public CallFrame(IRMethod method, InvokeInstruction callSite, SimulationState callerState,
                        IRBlock returnBlock, int returnInstructionIndex) {
            this.method = method;
            this.callSite = callSite;
            this.callerState = callerState;
            this.returnBlock = returnBlock;
            this.returnInstructionIndex = returnInstructionIndex;
        }

        public IRMethod getMethod() {
            return method;
        }

        public InvokeInstruction getCallSite() {
            return callSite;
        }

        public SimulationState getCallerState() {
            return callerState;
        }

        public IRBlock getReturnBlock() {
            return returnBlock;
        }

        public int getReturnInstructionIndex() {
            return returnInstructionIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CallFrame)) return false;
            CallFrame callFrame = (CallFrame) o;
            return returnInstructionIndex == callFrame.returnInstructionIndex &&
                   Objects.equals(method, callFrame.method) &&
                   Objects.equals(callSite, callFrame.callSite) &&
                   Objects.equals(returnBlock, callFrame.returnBlock);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, callSite, returnBlock, returnInstructionIndex);
        }

        @Override
        public String toString() {
            return "CallFrame[method=" + method.getName() +
                ", returnTo=" + (returnBlock != null ? returnBlock.getId() : "null") + "]";
        }
    }
}
