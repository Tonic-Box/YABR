package com.tonic.testutil;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the hook callbacks an instrumented class makes at run time, so a test can assert that a
 * registered hook was actually woven in and fired - not merely that the builder accepted it.
 *
 * <p>Instrumented classes reference this by name, so it must be reachable from the parent of whatever
 * loader defines them.
 */
public final class InstrumentationRecorder
{

    private static final List<String> EVENTS = new ArrayList<>();

    private InstrumentationRecorder()
    {
    }

    /**
     * Clears the recorded events. Call from test setup.
     */
    public static void reset()
    {
        EVENTS.clear();
    }

    /**
     * @return the events recorded so far, in order
     */
    public static List<String> events()
    {
        return new ArrayList<>(EVENTS);
    }

    /**
     * Records a bare method-entry callback.
     */
    public static void onEntry()
    {
        EVENTS.add("entry");
    }

    /**
     * Records a bare method-exit callback.
     */
    public static void onExit()
    {
        EVENTS.add("exit");
    }

    /**
     * Records a method-entry callback that carries the instrumented method's name.
     *
     * @param methodName the name the hook passed
     */
    public static void onEntryNamed(String methodName)
    {
        EVENTS.add("entry:" + methodName);
    }

    /**
     * Records a field-write callback.
     *
     * @param value the value being written
     */
    public static void onFieldWrite(int value)
    {
        EVENTS.add("write:" + value);
    }
}
