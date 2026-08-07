package com.tonic.analysis.simulation.state;

import com.tonic.analysis.simulation.core.SimulationState;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.analysis.ssa.ir.InvokeInstruction;

import java.util.*;

/**
 * Immutable call stack for inter-procedural simulation.
 */
public final class CallStackState
{

    private final List<CallFrame> frames;

    private CallStackState(List<CallFrame> frames)
    {
        this.frames = List.copyOf(frames);
    }

    /**
     * @return a call stack with no frames
     */
    public static CallStackState empty()
    {
        return new CallStackState(Collections.emptyList());
    }

    /**
     * Creates a stack holding a single frame.
     *
     * @param frame the initial frame
     * @return a stack of depth one
     */
    public static CallStackState withFrame(CallFrame frame)
    {
        return new CallStackState(List.of(frame));
    }

    /**
     * Adds a frame on top of the existing ones.
     *
     * @param frame the frame to push
     * @return a new stack ending with that frame
     */
    public CallStackState push(CallFrame frame)
    {
        List<CallFrame> newFrames = new ArrayList<>(frames);
        newFrames.add(frame);
        return new CallStackState(newFrames);
    }

    /**
     * Removes the top frame.
     *
     * @return a new stack without the top frame, or this stack if it is already empty
     */
    public CallStackState pop()
    {
        if (frames.isEmpty()) return this;
        List<CallFrame> newFrames = new ArrayList<>(frames);
        newFrames.remove(newFrames.size() - 1);
        return new CallStackState(newFrames);
    }

    /**
     * @return the top frame, or null if the stack is empty
     */
    public CallFrame peek()
    {
        if (frames.isEmpty()) return null;
        return frames.get(frames.size() - 1);
    }

    /**
     * @return the number of frames on the stack
     */
    public int depth()
    {
        return frames.size();
    }

    /**
     * @return true if no frame is on the stack
     */
    public boolean isEmpty()
    {
        return frames.isEmpty();
    }

    /**
     * @return an unmodifiable view of the frames, bottom to top
     */
    public List<CallFrame> getFrames()
    {
        return frames;
    }

    /**
     * Reads a frame by position.
     *
     * @param depth the frame index, 0 being the bottom of the stack
     * @return the frame, or null if the index is out of range
     */
    public CallFrame getFrame(int depth)
    {
        if (depth < 0 || depth >= frames.size()) return null;
        return frames.get(depth);
    }

    /**
     * Detects recursion by searching the frames for a method.
     *
     * @param method the method to look for
     * @return true if any frame is executing it
     */
    public boolean contains(IRMethod method)
    {
        for (CallFrame frame : frames)
        {
            if (frame.getMethod().equals(method))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the method of every frame, bottom to top
     */
    public List<IRMethod> getCallChain()
    {
        List<IRMethod> chain = new ArrayList<>();
        for (CallFrame frame : frames)
        {
            chain.add(frame.getMethod());
        }
        return chain;
    }

    /**
     * Renders the call chain for debugging.
     *
     * @return the method names bottom to top joined by " -&gt; ", or "&lt;empty&gt;" for an empty stack
     */
    public String getCallChainString()
    {
        if (frames.isEmpty()) return "<empty>";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < frames.size(); i++)
        {
            if (i > 0) sb.append(" -> ");
            CallFrame frame = frames.get(i);
            sb.append(frame.getMethod().getName());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof CallStackState)) return false;
        CallStackState that = (CallStackState) o;
        return Objects.equals(frames, that.frames);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(frames);
    }

    @Override
    public String toString()
    {
        return "CallStackState[depth=" + frames.size() + ", chain=" + getCallChainString() + "]";
    }

    /**
     * Represents a single frame on the call stack.
     */
    public static class CallFrame
    {
        private final IRMethod method;
        private final InvokeInstruction callSite;
        private final SimulationState callerState;
        private final IRBlock returnBlock;
        private final int returnInstructionIndex;

        public CallFrame(IRMethod method, InvokeInstruction callSite, SimulationState callerState, IRBlock returnBlock, int returnInstructionIndex)
        {
            this.method = method;
            this.callSite = callSite;
            this.callerState = callerState;
            this.returnBlock = returnBlock;
            this.returnInstructionIndex = returnInstructionIndex;
        }

        /**
         * @return the method
         */
        public IRMethod getMethod()
        {
            return method;
        }

        /**
         * @return the call site
         */
        public InvokeInstruction getCallSite()
        {
            return callSite;
        }

        /**
         * @return the caller state
         */
        public SimulationState getCallerState()
        {
            return callerState;
        }

        /**
         * @return the return block
         */
        public IRBlock getReturnBlock()
        {
            return returnBlock;
        }

        /**
         * @return the return instruction index
         */
        public int getReturnInstructionIndex()
        {
            return returnInstructionIndex;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof CallFrame)) return false;
            CallFrame callFrame = (CallFrame) o;
            return returnInstructionIndex == callFrame.returnInstructionIndex &&
                   Objects.equals(method, callFrame.method) &&
                   Objects.equals(callSite, callFrame.callSite) &&
                   Objects.equals(returnBlock, callFrame.returnBlock);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(method, callSite, returnBlock, returnInstructionIndex);
        }

        @Override
        public String toString()
        {
            return "CallFrame[method=" + method.getName() +
                ", returnTo=" + (returnBlock != null ? returnBlock.getId() : "null") + "]";
        }
    }
}
