package com.tonic.analysis.execution.debug;

import com.tonic.analysis.execution.frame.StackFrame;

import java.util.Objects;

/**
 * Breakpoint location keyed by class, method, and pc, with mutable enablement, condition, and hit
 * tracking.
 */
public final class Breakpoint
{

    private final String className;
    private final String methodName;
    private final String methodDesc;
    private final int pc;
    private final int lineNumber;
    private boolean enabled;
    private String condition;
    private int hitCount;

    /**
     * Creates an enabled breakpoint at the given location with no line number.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset, or -1 for method entry
     * @throws IllegalArgumentException if className, methodName, or methodDesc is null or empty
     */
    public Breakpoint(String className, String methodName, String methodDesc, int pc)
    {
        this(className, methodName, methodDesc, pc, -1);
    }

    /**
     * Creates an enabled breakpoint at the given location.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset, or -1 for method entry
     * @param lineNumber the source line, or -1 if unknown
     * @throws IllegalArgumentException if className, methodName, or methodDesc is null or empty
     */
    public Breakpoint(String className, String methodName, String methodDesc, int pc, int lineNumber)
    {
        if (className == null || className.isEmpty())
        {
            throw new IllegalArgumentException("Class name cannot be null or empty");
        }
        if (methodName == null || methodName.isEmpty())
        {
            throw new IllegalArgumentException("Method name cannot be null or empty");
        }
        if (methodDesc == null || methodDesc.isEmpty())
        {
            throw new IllegalArgumentException("Method descriptor cannot be null or empty");
        }

        this.className = className;
        this.methodName = methodName;
        this.methodDesc = methodDesc;
        this.pc = pc;
        this.lineNumber = lineNumber;
        this.enabled = true;
        this.condition = null;
        this.hitCount = 0;
    }

    /**
     * Creates a breakpoint that triggers on method entry.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @return the breakpoint
     */
    public static Breakpoint methodEntry(String className, String methodName, String methodDesc)
    {
        return new Breakpoint(className, methodName, methodDesc, -1);
    }

    /**
     * Creates a breakpoint carrying a source line number; matching still uses pc -1, i.e. method
     * entry.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param line the source line number
     * @return the breakpoint
     * @throws IllegalArgumentException if line is negative
     */
    public static Breakpoint atLine(String className, String methodName, String methodDesc, int line)
    {
        if (line < 0)
        {
            throw new IllegalArgumentException("Line number cannot be negative");
        }
        return new Breakpoint(className, methodName, methodDesc, -1, line);
    }

    /**
     * Creates a breakpoint at a specific bytecode offset.
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the bytecode offset, or -1 for method entry
     * @return the breakpoint
     * @throws IllegalArgumentException if pc is less than -1
     */
    public static Breakpoint atPC(String className, String methodName, String methodDesc, int pc)
    {
        if (pc < -1)
        {
            throw new IllegalArgumentException("PC cannot be less than -1");
        }
        return new Breakpoint(className, methodName, methodDesc, pc);
    }

    /**
     * @return the class name
     */
    public String getClassName()
    {
        return className;
    }

    /**
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    /**
     * @return the method desc
     */
    public String getMethodDesc()
    {
        return methodDesc;
    }

    /**
     * @return the target bytecode offset, or -1 for method entry
     */
    public int getPC()
    {
        return pc;
    }

    /**
     * @return the line number
     */
    public int getLineNumber()
    {
        return lineNumber;
    }

    /**
     * @return whether enabled
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Enables or disables this breakpoint.
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * @return the condition
     */
    public String getCondition()
    {
        return condition;
    }

    /**
     * Sets an optional condition expression attached to this breakpoint.
     * @param condition the condition, or null to clear
     */
    public void setCondition(String condition)
    {
        this.condition = condition;
    }

    /**
     * @return the hit count
     */
    public int getHitCount()
    {
        return hitCount;
    }

    /**
     * Records one more hit of this breakpoint.
     */
    public void incrementHitCount()
    {
        this.hitCount++;
    }

    /**
     * Resets the hit counter to zero.
     */
    public void resetHitCount()
    {
        this.hitCount = 0;
    }

    /**
     * Tests whether this breakpoint matches the frame's current location.
     * @param frame the frame to test, may be null
     * @return true if the frame location matches
     */
    public boolean matches(StackFrame frame)
    {
        if (frame == null)
        {
            return false;
        }

        String frameClassName = frame.getMethod().getOwnerName();
        String frameMethodName = frame.getMethod().getName();
        String frameMethodDesc = frame.getMethod().getDesc();
        int framePC = frame.getPC();

        return matches(frameClassName, frameMethodName, frameMethodDesc, framePC);
    }

    /**
     * Tests whether this breakpoint matches the given location, treating a -1 pc as method entry
     * (pc 0).
     * @param className the class internal name
     * @param methodName the method name
     * @param methodDesc the method descriptor
     * @param pc the current program counter
     * @return true if the location matches
     */
    public boolean matches(String className, String methodName, String methodDesc, int pc)
    {
        if (!this.className.equals(className))
        {
            return false;
        }
        if (!this.methodName.equals(methodName))
        {
            return false;
        }
        if (!this.methodDesc.equals(methodDesc))
        {
            return false;
        }

        if (this.pc == -1)
        {
            return pc == 0;
        }

        return this.pc == pc;
    }

    /**
     * @return a unique map key combining class, method, descriptor, and pc
     */
    public String getKey()
    {
        return className + "." + methodName + "+" + methodDesc + "@" + pc;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Breakpoint that = (Breakpoint) o;
        return pc == that.pc &&
                lineNumber == that.lineNumber &&
                className.equals(that.className) &&
                methodName.equals(that.methodName) &&
                methodDesc.equals(that.methodDesc);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(className, methodName, methodDesc, pc, lineNumber);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Breakpoint{");
        sb.append(className).append(".").append(methodName).append(methodDesc);
        sb.append(" @pc=").append(pc);
        if (lineNumber >= 0)
        {
            sb.append(", line=").append(lineNumber);
        }
        sb.append(", enabled=").append(enabled);
        if (condition != null)
        {
            sb.append(", condition='").append(condition).append("'");
        }
        sb.append(", hits=").append(hitCount);
        sb.append("}");
        return sb.toString();
    }
}
