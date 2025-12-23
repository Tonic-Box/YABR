package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.core.BytecodeContext;
import com.tonic.analysis.execution.core.BytecodeEngine;
import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.frame.StackFrame;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.state.ConcreteValue;
import com.tonic.analysis.instruction.Instruction;
import com.tonic.parser.MethodEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DebugSession {

    private final BytecodeContext context;
    private final BreakpointManager breakpointManager;
    private final List<DebugEventListener> listeners;

    private BytecodeEngine engine;
    private DebugSessionState state;
    private StepMode currentStepMode;
    private int stepStartDepth;
    private int runToCursorPC;
    private String runToCursorMethod;

    private DebugState lastState;
    private BytecodeResult result;
    private volatile boolean pauseRequested;

    public DebugSession(BytecodeContext context) {
        this(context, new BreakpointManager());
    }

    public DebugSession(BytecodeContext context, BreakpointManager breakpointManager) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (breakpointManager == null) {
            throw new IllegalArgumentException("Breakpoint manager cannot be null");
        }

        this.context = context;
        this.breakpointManager = breakpointManager;
        this.listeners = new CopyOnWriteArrayList<>();
        this.state = DebugSessionState.IDLE;
        this.currentStepMode = StepMode.RUN;
        this.stepStartDepth = 0;
        this.runToCursorPC = -1;
        this.runToCursorMethod = null;
        this.pauseRequested = false;
    }

    public void start(MethodEntry method, ConcreteValue... args) {
        if (state != DebugSessionState.IDLE) {
            throw new IllegalStateException("Session already started. Current state: " + state);
        }

        this.engine = new BytecodeEngine(context);
        this.engine.addListener(new DebugInterceptor());
        this.result = null;
        this.lastState = null;
        this.pauseRequested = false;

        try {
            StackFrame initialFrame = new StackFrame(method, args);
            engine.getCallStack().push(initialFrame);
            changeState(DebugSessionState.PAUSED);
            notifySessionStart();
        } catch (Exception e) {
            changeState(DebugSessionState.STOPPED);
            throw e;
        }
    }

    public void stop() {
        if (state == DebugSessionState.IDLE) {
            throw new IllegalStateException("Session not started");
        }
        if (state == DebugSessionState.STOPPED) {
            return;
        }

        if (engine != null) {
            engine.interrupt();
        }

        if (result == null) {
            result = BytecodeResult.interrupted();
        }

        changeState(DebugSessionState.STOPPED);
        notifySessionStop(result);
    }

    public void pause() {
        if (state != DebugSessionState.RUNNING) {
            throw new IllegalStateException("Cannot pause. Current state: " + state);
        }
        this.pauseRequested = true;
    }

    public DebugState resume() {
        ensureValidForStepping();

        this.currentStepMode = StepMode.RUN;
        changeState(DebugSessionState.RUNNING);

        return executeUntilPause();
    }

    public DebugState stepInto() {
        ensureValidForStepping();

        this.currentStepMode = StepMode.STEP_INTO;
        this.stepStartDepth = engine.getCallStack().depth();
        changeState(DebugSessionState.RUNNING);

        return executeStep();
    }

    public DebugState stepOver() {
        ensureValidForStepping();

        this.currentStepMode = StepMode.STEP_OVER;
        this.stepStartDepth = engine.getCallStack().depth();
        changeState(DebugSessionState.RUNNING);

        return executeStep();
    }

    public DebugState stepOut() {
        ensureValidForStepping();

        this.currentStepMode = StepMode.STEP_OUT;
        this.stepStartDepth = engine.getCallStack().depth();
        changeState(DebugSessionState.RUNNING);

        return executeStep();
    }

    public DebugState runToCursor(int pc) {
        ensureValidForStepping();

        StackFrame currentFrame = engine.getCurrentFrame();
        if (currentFrame == null) {
            throw new IllegalStateException("No current frame");
        }

        this.currentStepMode = StepMode.RUN_TO_CURSOR;
        this.runToCursorPC = pc;
        this.runToCursorMethod = currentFrame.getMethodSignature();
        changeState(DebugSessionState.RUNNING);

        return executeStep();
    }

    public DebugSessionState getState() {
        return state;
    }

    public boolean isRunning() {
        return state == DebugSessionState.RUNNING;
    }

    public boolean isPaused() {
        return state == DebugSessionState.PAUSED;
    }

    public boolean isStopped() {
        return state == DebugSessionState.STOPPED;
    }

    public DebugState getCurrentState() {
        return buildCurrentState();
    }

    public BytecodeResult getResult() {
        if (state != DebugSessionState.STOPPED) {
            throw new IllegalStateException("Result only available when stopped. Current state: " + state);
        }
        return result;
    }

    public void addBreakpoint(Breakpoint bp) {
        breakpointManager.addBreakpoint(bp);
    }

    public void removeBreakpoint(Breakpoint bp) {
        breakpointManager.removeBreakpoint(bp);
    }

    public List<Breakpoint> getBreakpoints() {
        return breakpointManager.getAllBreakpoints();
    }

    public StackFrame getCurrentFrame() {
        return engine != null ? engine.getCurrentFrame() : null;
    }

    public List<StackFrame> getCallStack() {
        if (engine == null || engine.getCallStack() == null) {
            return new ArrayList<>();
        }
        return engine.getCallStack().snapshot();
    }

    public void addListener(DebugEventListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(DebugEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void ensureValidForStepping() {
        if (state == DebugSessionState.IDLE) {
            throw new IllegalStateException("Session not started");
        }
        if (state == DebugSessionState.STOPPED) {
            throw new IllegalStateException("Session already stopped");
        }
        if (state == DebugSessionState.RUNNING) {
            throw new IllegalStateException("Session already running");
        }
    }

    private DebugState executeStep() {
        try {
            while (true) {
                if (engine.getCallStack().isEmpty()) {
                    ConcreteValue returnValue = ConcreteValue.nullRef();
                    result = BytecodeResult.completed(returnValue);
                    changeState(DebugSessionState.STOPPED);
                    notifySessionStop(result);
                    return buildCurrentState();
                }

                StackFrame currentFrame = engine.getCurrentFrame();

                if (currentFrame.isCompleted()) {
                    if (!engine.step()) {
                        result = BytecodeResult.completed(ConcreteValue.nullRef());
                        changeState(DebugSessionState.STOPPED);
                        notifySessionStop(result);
                        return buildCurrentState();
                    }
                    continue;
                }

                Breakpoint bp = breakpointManager.checkBreakpoint(currentFrame);
                if (bp != null && currentStepMode == StepMode.RUN) {
                    bp.incrementHitCount();
                    changeState(DebugSessionState.PAUSED);
                    lastState = buildCurrentState(bp);
                    notifyBreakpointHit(bp);
                    return lastState;
                }

                boolean stepResult = engine.step();

                if (!stepResult) {
                    result = BytecodeResult.completed(ConcreteValue.nullRef());
                    changeState(DebugSessionState.STOPPED);
                    notifySessionStop(result);
                    return buildCurrentState();
                }

                currentFrame = engine.getCurrentFrame();

                if (currentFrame != null && currentFrame.getException() != null) {
                    ObjectInstance exception = currentFrame.getException();
                    List<String> trace = buildStackTrace();
                    result = BytecodeResult.exception(exception, trace);
                    changeState(DebugSessionState.STOPPED);
                    notifyException(exception);
                    notifySessionStop(result);
                    return buildCurrentState();
                }

                if (currentFrame != null && shouldPause(currentFrame)) {
                    changeState(DebugSessionState.PAUSED);
                    lastState = buildCurrentState();
                    notifyStepComplete(lastState);
                    return lastState;
                }
            }
        } catch (Exception e) {
            ObjectInstance exception = wrapException(e);
            List<String> trace = buildStackTrace();
            result = BytecodeResult.exception(exception, trace);
            changeState(DebugSessionState.STOPPED);
            notifyException(exception);
            notifySessionStop(result);
            return buildCurrentState();
        }
    }

    private DebugState executeUntilPause() {
        this.pauseRequested = false;
        return executeStep();
    }

    private boolean shouldPause(StackFrame frame) {
        if (pauseRequested) {
            pauseRequested = false;
            return true;
        }

        int currentDepth = engine.getCallStack().depth();

        switch (currentStepMode) {
            case STEP_INTO:
                return true;

            case STEP_OVER:
                return currentDepth <= stepStartDepth;

            case STEP_OUT:
                return currentDepth < stepStartDepth;

            case RUN_TO_CURSOR:
                return frame.getPC() == runToCursorPC &&
                       frame.getMethodSignature().equals(runToCursorMethod);

            case RUN:
            default:
                return false;
        }
    }

    private DebugState buildCurrentState() {
        return buildCurrentState(null);
    }

    private DebugState buildCurrentState(Breakpoint hitBreakpoint) {
        if (engine == null || engine.getCallStack().isEmpty()) {
            return new DebugState.Builder()
                    .status(mapStateToDebugStatus())
                    .instructionCount(engine != null ? engine.getInstructionCount() : 0)
                    .hitBreakpoint(hitBreakpoint)
                    .build();
        }

        StackFrame currentFrame = engine.getCurrentFrame();
        List<StackFrameInfo> stackInfo = new ArrayList<>();
        for (StackFrame frame : engine.getCallStack().topToBottom()) {
            stackInfo.add(new StackFrameInfo(frame));
        }

        return new DebugState.Builder()
                .status(mapStateToDebugStatus())
                .currentMethod(currentFrame.getMethodSignature())
                .currentPC(currentFrame.getPC())
                .currentLine(currentFrame.getLineNumber())
                .callDepth(engine.getCallStack().depth())
                .instructionCount(engine.getInstructionCount())
                .hitBreakpoint(hitBreakpoint)
                .callStack(stackInfo)
                .locals(new LocalsSnapshot(currentFrame.getLocals()))
                .operandStack(new StackSnapshot(currentFrame.getStack()))
                .build();
    }

    private DebugState.Status mapStateToDebugStatus() {
        switch (state) {
            case IDLE:
                return DebugState.Status.IDLE;
            case RUNNING:
                return DebugState.Status.RUNNING;
            case PAUSED:
                return DebugState.Status.PAUSED;
            case STOPPED:
                return result != null && result.hasException()
                        ? DebugState.Status.EXCEPTION
                        : DebugState.Status.COMPLETED;
            default:
                return DebugState.Status.IDLE;
        }
    }

    private void changeState(DebugSessionState newState) {
        DebugSessionState oldState = this.state;
        this.state = newState;
        if (oldState != newState) {
            notifyStateChange(oldState, newState);
        }
    }

    private ObjectInstance wrapException(Exception e) {
        try {
            return context.getHeapManager().newObject("java/lang/Exception");
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> buildStackTrace() {
        List<String> trace = new ArrayList<>();
        if (engine != null && !engine.getCallStack().isEmpty()) {
            for (StackFrame frame : engine.getCallStack().topToBottom()) {
                StringBuilder sb = new StringBuilder();
                sb.append(frame.getMethodSignature());
                sb.append(" (pc=").append(frame.getPC());
                int line = frame.getLineNumber();
                if (line >= 0) {
                    sb.append(", line=").append(line);
                }
                sb.append(")");
                trace.add(sb.toString());
            }
        }
        return trace;
    }

    private void notifySessionStart() {
        for (DebugEventListener listener : listeners) {
            try {
                listener.onSessionStart(this);
            } catch (Exception e) {
            }
        }
    }

    private void notifySessionStop(BytecodeResult result) {
        for (DebugEventListener listener : listeners) {
            try {
                listener.onSessionStop(this, result);
            } catch (Exception e) {
            }
        }
    }

    private void notifyBreakpointHit(Breakpoint breakpoint) {
        for (DebugEventListener listener : listeners) {
            try {
                listener.onBreakpointHit(this, breakpoint);
            } catch (Exception e) {
            }
        }
    }

    private void notifyStepComplete(DebugState state) {
        for (DebugEventListener listener : listeners) {
            try {
                listener.onStepComplete(this, state);
            } catch (Exception e) {
            }
        }
    }

    private void notifyException(ObjectInstance exception) {
        for (DebugEventListener listener : listeners) {
            try {
                listener.onException(this, exception);
            } catch (Exception e) {
            }
        }
    }

    private void notifyStateChange(DebugSessionState oldState, DebugSessionState newState) {
        for (DebugEventListener listener : listeners) {
            try {
                listener.onStateChange(this, oldState, newState);
            } catch (Exception e) {
            }
        }
    }

    private class DebugInterceptor implements BytecodeEngine.BytecodeListener {
        @Override
        public void beforeInstruction(StackFrame frame, Instruction instruction) {
        }

        @Override
        public void afterInstruction(StackFrame frame, Instruction instruction) {
        }
    }
}
