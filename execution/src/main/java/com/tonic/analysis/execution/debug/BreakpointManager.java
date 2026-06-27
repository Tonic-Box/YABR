package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BreakpointManager {

    private final Map<String, Breakpoint> breakpoints;
    private final Map<String, Set<Breakpoint>> byMethod;

    public BreakpointManager() {
        this.breakpoints = new ConcurrentHashMap<>();
        this.byMethod = new ConcurrentHashMap<>();
    }

    public void addBreakpoint(Breakpoint bp) {
        if (bp == null) {
            throw new IllegalArgumentException("Breakpoint cannot be null");
        }

        String key = bp.getKey();
        breakpoints.put(key, bp);

        String methodKey = methodKey(bp.getClassName(), bp.getMethodName(), bp.getMethodDesc());
        byMethod.computeIfAbsent(methodKey, k -> ConcurrentHashMap.newKeySet()).add(bp);
    }

    public boolean removeBreakpoint(Breakpoint bp) {
        if (bp == null) {
            return false;
        }
        return removeBreakpoint(bp.getKey());
    }

    public boolean removeBreakpoint(String key) {
        if (key == null) {
            return false;
        }

        Breakpoint bp = breakpoints.remove(key);
        if (bp == null) {
            return false;
        }

        String methodKey = methodKey(bp.getClassName(), bp.getMethodName(), bp.getMethodDesc());
        Set<Breakpoint> methodBps = byMethod.get(methodKey);
        if (methodBps != null) {
            methodBps.remove(bp);
            if (methodBps.isEmpty()) {
                byMethod.remove(methodKey);
            }
        }

        return true;
    }

    public void removeAllBreakpoints() {
        breakpoints.clear();
        byMethod.clear();
    }

    public void enableBreakpoint(String key) {
        if (key == null) {
            return;
        }

        Breakpoint bp = breakpoints.get(key);
        if (bp != null) {
            bp.setEnabled(true);
        }
    }

    public void disableBreakpoint(String key) {
        if (key == null) {
            return;
        }

        Breakpoint bp = breakpoints.get(key);
        if (bp != null) {
            bp.setEnabled(false);
        }
    }

    public void enableAll() {
        for (Breakpoint bp : breakpoints.values()) {
            bp.setEnabled(true);
        }
    }

    public void disableAll() {
        for (Breakpoint bp : breakpoints.values()) {
            bp.setEnabled(false);
        }
    }

    public Breakpoint getBreakpoint(String key) {
        if (key == null) {
            return null;
        }
        return breakpoints.get(key);
    }

    public List<Breakpoint> getAllBreakpoints() {
        return new ArrayList<>(breakpoints.values());
    }

    public List<Breakpoint> getBreakpointsForMethod(String className, String methodName, String methodDesc) {
        String methodKey = methodKey(className, methodName, methodDesc);
        Set<Breakpoint> bps = byMethod.get(methodKey);
        return bps == null ? Collections.emptyList() : new ArrayList<>(bps);
    }

    public boolean hasBreakpoints() {
        return !breakpoints.isEmpty();
    }

    public int getBreakpointCount() {
        return breakpoints.size();
    }

    public Breakpoint checkBreakpoint(StackFrame frame) {
        if (frame == null) {
            return null;
        }

        String frameClassName = frame.getMethod().getOwnerName();
        String frameMethodName = frame.getMethod().getName();
        String frameMethodDesc = frame.getMethod().getDesc();
        int framePC = frame.getPC();

        return checkBreakpoint(frameClassName, frameMethodName, frameMethodDesc, framePC);
    }

    public Breakpoint checkBreakpoint(String className, String methodName, String methodDesc, int pc) {
        String methodKey = methodKey(className, methodName, methodDesc);
        Set<Breakpoint> methodBps = byMethod.get(methodKey);

        if (methodBps == null || methodBps.isEmpty()) {
            return null;
        }

        for (Breakpoint bp : methodBps) {
            if (bp.isEnabled() && bp.matches(className, methodName, methodDesc, pc)) {
                return bp;
            }
        }

        return null;
    }

    public static String methodKey(String className, String methodName, String methodDesc) {
        if (className == null || methodName == null || methodDesc == null) {
            throw new IllegalArgumentException("Method key components cannot be null");
        }
        return className + "." + methodName + methodDesc;
    }
}
