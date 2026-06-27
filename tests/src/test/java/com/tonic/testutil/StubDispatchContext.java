package com.tonic.testutil;

import com.tonic.analysis.execution.dispatch.*;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;

public class StubDispatchContext implements DispatchContext {
    private int lastIntConstant;
    private long lastLongConstant;
    private float lastFloatConstant;
    private double lastDoubleConstant;
    private String lastStringConstant;
    private ObjectInstance lastClassConstant;
    private int branchTarget;

    public void setLastIntConstant(int value) {
        this.lastIntConstant = value;
    }

    public void setLastLongConstant(long value) {
        this.lastLongConstant = value;
    }

    public void setLastFloatConstant(float value) {
        this.lastFloatConstant = value;
    }

    public void setLastDoubleConstant(double value) {
        this.lastDoubleConstant = value;
    }

    public void setLastStringConstant(String value) {
        this.lastStringConstant = value;
    }

    public void setLastClassConstant(ObjectInstance value) {
        this.lastClassConstant = value;
    }

    @Override
    public int resolveIntConstant(int index) {
        return lastIntConstant;
    }

    @Override
    public long resolveLongConstant(int index) {
        return lastLongConstant;
    }

    @Override
    public float resolveFloatConstant(int index) {
        return lastFloatConstant;
    }

    @Override
    public double resolveDoubleConstant(int index) {
        return lastDoubleConstant;
    }

    @Override
    public String resolveStringConstant(int index) {
        return lastStringConstant;
    }

    @Override
    public ObjectInstance resolveStringObject(int index) {
        return null;
    }

    @Override
    public ObjectInstance resolveClassConstant(int index) {
        return lastClassConstant;
    }

    @Override
    public ArrayInstance getArray(ObjectInstance ref) {
        return (ArrayInstance) ref;
    }

    @Override
    public void checkArrayBounds(ArrayInstance array, int index) {
        if (index < 0 || index >= array.getLength()) {
            throw new ArrayIndexOutOfBoundsException("Index: " + index + ", Length: " + array.getLength());
        }
    }

    @Override
    public void checkNullReference(ObjectInstance ref, String operation) {
        if (ref == null) {
            throw new NullPointerException(operation);
        }
    }

    @Override
    public FieldInfo resolveField(int cpIndex) {
        return null;
    }

    @Override
    public MethodInfo resolveMethod(int cpIndex) {
        return null;
    }

    @Override
    public boolean isInstanceOf(ObjectInstance obj, String className) {
        return obj.isInstanceOf(className);
    }

    @Override
    public void checkCast(ObjectInstance obj, String className) {
    }

    @Override
    public MethodInfo getPendingInvoke() {
        return null;
    }

    @Override
    public FieldInfo getPendingFieldAccess() {
        return null;
    }

    @Override
    public String getPendingNewClass() {
        return null;
    }

    @Override
    public int[] getPendingArrayDimensions() {
        return null;
    }

    @Override
    public void setPendingInvoke(MethodInfo methodInfo) {
    }

    @Override
    public void setPendingFieldAccess(FieldInfo fieldInfo) {
    }

    @Override
    public void setPendingNewClass(String className) {
    }

    @Override
    public void setPendingArrayDimensions(int[] dimensions) {
    }

    @Override
    public void setBranchTarget(int target) {
        this.branchTarget = target;
    }

    @Override
    public int getBranchTarget() {
        return branchTarget;
    }

    @Override
    public void setPendingInvokeDynamic(InvokeDynamicInfo info) {
    }

    @Override
    public InvokeDynamicInfo getPendingInvokeDynamic() {
        return null;
    }

    @Override
    public void setPendingMethodHandle(MethodHandleInfo info) {
    }

    @Override
    public MethodHandleInfo getPendingMethodHandle() {
        return null;
    }

    @Override
    public void setPendingMethodType(MethodTypeInfo info) {
    }

    @Override
    public MethodTypeInfo getPendingMethodType() {
        return null;
    }

    @Override
    public void setPendingConstantDynamic(ConstantDynamicInfo info) {
    }

    @Override
    public ConstantDynamicInfo getPendingConstantDynamic() {
        return null;
    }
}
