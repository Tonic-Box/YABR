package com.tonic.analysis.execution.dispatch;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;

/**
 * Runtime services the opcode dispatcher relies on: constant resolution, heap checks,
 * and pending-operation handoff to the interpreter.
 */
public interface DispatchContext
{

    /**
     * Reads an int constant from the constant pool.
     *
     * @param index the constant pool index
     * @return the constant value
     */
    int resolveIntConstant(int index);

    /**
     * Reads a long constant from the constant pool.
     *
     * @param index the constant pool index
     * @return the constant value
     */
    long resolveLongConstant(int index);

    /**
     * Reads a float constant from the constant pool.
     *
     * @param index the constant pool index
     * @return the constant value
     */
    float resolveFloatConstant(int index);

    /**
     * Reads a double constant from the constant pool.
     *
     * @param index the constant pool index
     * @return the constant value
     */
    double resolveDoubleConstant(int index);

    /**
     * Reads a string constant from the constant pool as plain text.
     *
     * @param index the constant pool index
     * @return the constant text
     */
    String resolveStringConstant(int index);

    /**
     * Resolves a string constant to its interned heap object.
     *
     * @param index the constant pool index
     * @return the interned string instance
     */
    ObjectInstance resolveStringObject(int index);

    /**
     * Resolves a class constant to a heap object representing it.
     *
     * @param index the constant pool index
     * @return the class instance
     */
    ObjectInstance resolveClassConstant(int index);

    /**
     * Views a reference as an array.
     *
     * @param ref the reference to view
     * @return the array behind the reference
     * @throws IllegalArgumentException if the reference is not an array
     */
    ArrayInstance getArray(ObjectInstance ref);

    /**
     * Validates an array index before an element access.
     *
     * @param array the array being accessed
     * @param index the element index
     * @throws ArrayIndexOutOfBoundsException if the index is outside the array
     */
    void checkArrayBounds(ArrayInstance array, int index);

    /**
     * Validates that a reference is non-null before it is dereferenced.
     *
     * @param ref the reference to check
     * @param operation description of the access, used in the failure message
     * @throws NullPointerException if the reference is null
     */
    void checkNullReference(ObjectInstance ref, String operation);

    /**
     * Resolves a field reference from the constant pool.
     *
     * @param cpIndex the constant pool index of the field reference
     * @return owner, name, descriptor, and staticness of the field
     */
    FieldInfo resolveField(int cpIndex);

    /**
     * Resolves a method reference from the constant pool.
     *
     * @param cpIndex the constant pool index of the method reference
     * @return owner, name, descriptor, and invocation kind of the method
     */
    MethodInfo resolveMethod(int cpIndex);

    /**
     * Tests assignability of an object to a type, treating unloaded types as compatible.
     *
     * @param obj the object to test
     * @param className the internal name of the target type
     * @return true if the object is, or cannot be disproven to be, an instance of the type
     */
    boolean isInstanceOf(ObjectInstance obj, String className);

    /**
     * Validates a checkcast against an object.
     *
     * @param obj the object being cast
     * @param className the internal name of the target type
     * @throws ClassCastException if the object is not an instance of the type
     */
    void checkCast(ObjectInstance obj, String className);

    /**
     * @return the invocation the dispatcher handed off, or null if none is pending
     */
    MethodInfo getPendingInvoke();

    /**
     * @return the field access the dispatcher handed off, or null if none is pending
     */
    FieldInfo getPendingFieldAccess();

    /**
     * @return the internal name of the class to instantiate, or null if none is pending
     */
    String getPendingNewClass();

    /**
     * @return the requested array dimension lengths, or null if none is pending
     */
    int[] getPendingArrayDimensions();

    /**
     * Hands an invocation to the interpreter.
     *
     * @param methodInfo the resolved target method
     */
    void setPendingInvoke(MethodInfo methodInfo);

    /**
     * Hands a field access to the interpreter.
     *
     * @param fieldInfo the resolved target field
     */
    void setPendingFieldAccess(FieldInfo fieldInfo);

    /**
     * Hands an allocation to the interpreter.
     *
     * @param className the internal name of the class to instantiate
     */
    void setPendingNewClass(String className);

    /**
     * Hands an array allocation to the interpreter.
     *
     * @param dimensions the requested length of each dimension
     */
    void setPendingArrayDimensions(int[] dimensions);

    /**
     * Records the bytecode offset a taken branch should continue at.
     *
     * @param target the destination offset
     */
    void setBranchTarget(int target);

    /**
     * @return the destination offset recorded by the last branch
     */
    int getBranchTarget();

    /**
     * Hands an invokedynamic call site to the interpreter.
     *
     * @param info the resolved call site
     */
    void setPendingInvokeDynamic(InvokeDynamicInfo info);

    /**
     * @return the invokedynamic call site the dispatcher handed off, or null if none is pending
     */
    InvokeDynamicInfo getPendingInvokeDynamic();

    /**
     * Hands a method handle constant to the interpreter.
     *
     * @param info the resolved handle
     */
    void setPendingMethodHandle(MethodHandleInfo info);

    /**
     * @return the method handle the dispatcher handed off, or null if none is pending
     */
    MethodHandleInfo getPendingMethodHandle();

    /**
     * Hands a method type constant to the interpreter.
     *
     * @param info the resolved type
     */
    void setPendingMethodType(MethodTypeInfo info);

    /**
     * @return the method type the dispatcher handed off, or null if none is pending
     */
    MethodTypeInfo getPendingMethodType();

    /**
     * Hands a dynamic constant to the interpreter.
     *
     * @param info the resolved constant
     */
    void setPendingConstantDynamic(ConstantDynamicInfo info);

    /**
     * @return the dynamic constant the dispatcher handed off, or null if none is pending
     */
    ConstantDynamicInfo getPendingConstantDynamic();
}
