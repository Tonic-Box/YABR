package com.tonic.analysis.execution.dispatch;

public class MethodInfo {
    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final boolean isStatic;
    private final boolean isInterface;

    public MethodInfo(String ownerClass, String methodName, String descriptor, boolean isStatic, boolean isInterface) {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.isInterface = isInterface;
    }

    public String getOwnerClass() {
        return ownerClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isInterface() {
        return isInterface;
    }

    @Override
    public String toString() {
        return ownerClass + "." + methodName + descriptor +
               (isStatic ? " (static)" : "") + (isInterface ? " (interface)" : "");
    }
}
