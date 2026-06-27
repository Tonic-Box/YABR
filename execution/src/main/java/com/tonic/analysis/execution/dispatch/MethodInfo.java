package com.tonic.analysis.execution.dispatch;

public class MethodInfo {
    private final String ownerClass;
    private final String methodName;
    private final String descriptor;
    private final boolean isStatic;
    private final boolean isInterface;
    private final boolean isSpecial;

    public MethodInfo(String ownerClass, String methodName, String descriptor, boolean isStatic, boolean isInterface) {
        this(ownerClass, methodName, descriptor, isStatic, isInterface, false);
    }

    public MethodInfo(String ownerClass, String methodName, String descriptor, boolean isStatic, boolean isInterface, boolean isSpecial) {
        this.ownerClass = ownerClass;
        this.methodName = methodName;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.isInterface = isInterface;
        this.isSpecial = isSpecial;
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

    public boolean isSpecial() {
        return isSpecial;
    }

    @Override
    public String toString() {
        return ownerClass + "." + methodName + descriptor +
               (isStatic ? " (static)" : "") + (isInterface ? " (interface)" : "") + (isSpecial ? " (special)" : "");
    }
}
