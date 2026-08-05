package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.util.Modifiers;

/**
 * A resolved method reference paired with its declaring class and invoke kind.
 */
public class ResolvedMethod
{

    /**
     * Dispatch style the resolved method should be invoked with.
     */
    public enum InvokeKind { STATIC, VIRTUAL, SPECIAL, INTERFACE }

    private final MethodEntry method;
    private final ClassFile declaringClass;
    private final InvokeKind kind;

    /**
     * Creates a resolved method.
     * @param method the matched method entry
     * @param declaringClass the class that declares it
     * @param kind the dispatch style to use
     */
    public ResolvedMethod(MethodEntry method, ClassFile declaringClass, InvokeKind kind)
    {
        this.method = method;
        this.declaringClass = declaringClass;
        this.kind = kind;
    }

    /**
     * @return the method
     */
    public MethodEntry getMethod()
    {
        return method;
    }

    /**
     * @return the declaring class
     */
    public ClassFile getDeclaringClass()
    {
        return declaringClass;
    }

    /**
     * @return the kind
     */
    public InvokeKind getKind()
    {
        return kind;
    }

    /**
     * @return true if the method has the static modifier
     */
    public boolean isStatic()
    {
        return (method.getAccess() & Modifiers.STATIC) != 0;
    }

    /**
     * @return true if the method has the native modifier
     */
    public boolean isNative()
    {
        return (method.getAccess() & Modifiers.NATIVE) != 0;
    }

    /**
     * @return true if the method has the abstract modifier
     */
    public boolean isAbstract()
    {
        return (method.getAccess() & Modifiers.ABSTRACT) != 0;
    }

    @Override
    public String toString()
    {
        return "ResolvedMethod{" +
                "method=" + method.getOwnerName() + "." + method.getName() + method.getDesc() +
                ", kind=" + kind +
                '}';
    }
}
