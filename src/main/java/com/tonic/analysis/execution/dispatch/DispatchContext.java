package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;

public interface DispatchContext {

    int resolveIntConstant(int index);

    long resolveLongConstant(int index);

    float resolveFloatConstant(int index);

    double resolveDoubleConstant(int index);

    String resolveStringConstant(int index);

    ObjectInstance resolveClassConstant(int index);

    ArrayInstance getArray(ObjectInstance ref);

    void checkArrayBounds(ArrayInstance array, int index);

    void checkNullReference(ObjectInstance ref, String operation);

    FieldInfo resolveField(int cpIndex);

    MethodInfo resolveMethod(int cpIndex);

    boolean isInstanceOf(ObjectInstance obj, String className);

    void checkCast(ObjectInstance obj, String className);

    MethodInfo getPendingInvoke();

    FieldInfo getPendingFieldAccess();

    String getPendingNewClass();

    int[] getPendingArrayDimensions();

    void setPendingInvoke(MethodInfo methodInfo);

    void setPendingFieldAccess(FieldInfo fieldInfo);

    void setPendingNewClass(String className);

    void setPendingArrayDimensions(int[] dimensions);

    void setBranchTarget(int target);

    int getBranchTarget();
}
