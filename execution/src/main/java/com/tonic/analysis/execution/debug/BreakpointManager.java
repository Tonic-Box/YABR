package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of breakpoints indexed by key and by owning method for fast location
 * checks.
 */
public final class BreakpointManager
{

    private final Map<String, Breakpoint> breakpoints;
    private final Map<String, Set<Breakpoint>> byMethod;

    /**
     * Creates an empty manager backed by concurrent maps.
     */
    public BreakpointManager()
    {
        this.breakpoints = new ConcurrentHashMap<>();
        this.byMethod = new ConcurrentHashMap<>();
    }

    /**
     * Registers a breakpoint, indexing it by key and by owning method.
     * @param bp the breakpoint to add
     * @throws IllegalArgumentException if bp is null
     */
    public void addBreakpoint(Breakpoint bp)
    {
        if (bp == null)
        {
            throw new IllegalArgumentException("Breakpoint cannot be null");
        }

        String key = bp.getKey();
        breakpoints.put(key, bp);

        String methodKey = methodKey(bp.getClassName(), bp.getMethodName(), bp.getMethodDesc());
        byMethod.computeIfAbsent(methodKey, k -> ConcurrentHashMap.newKeySet()).add(bp);
    }

    /**
     * Removes the given breakpoint.
     * @param bp the breakpoint to remove
     * @return true if it was registered and removed
     */
    public boolean removeBreakpoint(Breakpoint bp)
    {
        if (bp == null)
        {
            return false;
        }
        return removeBreakpoint(bp.getKey());
    }

    /**
     * Removes the breakpoint with the given key, cleaning up the per-method index.
     * @param key the breakpoint key
     * @return true if a breakpoint was removed
     */
    public boolean removeBreakpoint(String key)
    {
        if (key == null)
        {
            return false;
        }

        Breakpoint bp = breakpoints.remove(key);
        if (bp == null)
        {
            return false;
        }

        String methodKey = methodKey(bp.getClassName(), bp.getMethodName(), bp.getMethodDesc());
        Set<Breakpoint> methodBps = byMethod.get(methodKey);
        if (methodBps != null)
        {
            methodBps.remove(bp);
            if (methodBps.isEmpty())
            {
                byMethod.remove(methodKey);
            }
        }

        return true;
    }

    /**
     * Removes every registered breakpoint.
     */
    public void removeAllBreakpoints()
    {
        breakpoints.clear();
        byMethod.clear();
    }

    /**
     * Enables the breakpoint with the given key, if present.
     * @param key the breakpoint key
     */
    public void enableBreakpoint(String key)
    {
        if (key == null)
        {
            return;
        }

        Breakpoint bp = breakpoints.get(key);
        if (bp != null)
        {
            bp.setEnabled(true);
        }
    }

    /**
     * Disables the breakpoint with the given key, if present.
     * @param key the breakpoint key
     */
    public void disableBreakpoint(String key)
    {
        if (key == null)
        {
            return;
        }

        Breakpoint bp = breakpoints.get(key);
        if (bp != null)
        {
            bp.setEnabled(false);
        }
    }

    /**
     * Enables every registered breakpoint.
     */
    public void enableAll()
    {
        for (Breakpoint bp : breakpoints.values())
        {
            bp.setEnabled(true);
        }
    }

    /**
     * Disables every registered breakpoint.
     */
    public void disableAll()
    {
        for (Breakpoint bp : breakpoints.values())
        {
            bp.setEnabled(false);
        }
    }

    /**
     * Looks up a breakpoint by key.
     * @param key the breakpoint key
     * @return the breakpoint, or null if absent
     */
    public Breakpoint getBreakpoint(String key)
    {
        if (key == null)
        {
            return null;
        }
        return breakpoints.get(key);
    }

    /**
     * @return a copy of all registered breakpoints
     */
    public List<Breakpoint> getAllBreakpoints()
    {
        return new ArrayList<>(breakpoints.values());
    }

    /**
     * Lists the breakpoints registered for one method.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return a copy of the method's breakpoints, empty if none
     */
    public List<Breakpoint> getBreakpointsForMethod(String className, String methodName, String methodDesc)
    {
        String methodKey = methodKey(className, methodName, methodDesc);
        Set<Breakpoint> bps = byMethod.get(methodKey);
        return bps == null ? Collections.emptyList() : new ArrayList<>(bps);
    }

    /**
     * @return true if any breakpoints are registered
     */
    public boolean hasBreakpoints()
    {
        return !breakpoints.isEmpty();
    }

    /**
     * @return the number of registered breakpoints
     */
    public int getBreakpointCount()
    {
        return breakpoints.size();
    }

    /**
     * Finds an enabled breakpoint matching the frame's current location.
     * @param frame the frame to test, may be null
     * @return the matching breakpoint, or null
     */
    public Breakpoint checkBreakpoint(StackFrame frame)
    {
        if (frame == null)
        {
            return null;
        }

        String frameClassName = frame.getMethod().getOwnerName();
        String frameMethodName = frame.getMethod().getName();
        String frameMethodDesc = frame.getMethod().getDesc();
        int framePC = frame.getPC();

        return checkBreakpoint(frameClassName, frameMethodName, frameMethodDesc, framePC);
    }

    /**
     * Finds an enabled breakpoint matching the given location.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the current program counter
     * @return the matching breakpoint, or null
     */
    public Breakpoint checkBreakpoint(String className, String methodName, String methodDesc, int pc)
    {
        String methodKey = methodKey(className, methodName, methodDesc);
        Set<Breakpoint> methodBps = byMethod.get(methodKey);

        if (methodBps == null || methodBps.isEmpty())
        {
            return null;
        }

        for (Breakpoint bp : methodBps)
        {
            if (bp.isEnabled() && bp.matches(className, methodName, methodDesc, pc))
            {
                return bp;
            }
        }

        return null;
    }

    /**
     * Builds the per-method index key.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return the combined method key
     * @throws IllegalArgumentException if any component is null
     */
    public static String methodKey(String className, String methodName, String methodDesc)
    {
        if (className == null || methodName == null || methodDesc == null)
        {
            throw new IllegalArgumentException("Method key components cannot be null");
        }
        return className + "." + methodName + methodDesc;
    }
}
