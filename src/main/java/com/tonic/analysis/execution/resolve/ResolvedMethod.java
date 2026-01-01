package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.utill.Modifiers;

public class ResolvedMethod {

    public enum InvokeKind { STATIC, VIRTUAL, SPECIAL, INTERFACE }

    private final MethodEntry method;
    private final ClassFile declaringClass;
    private final InvokeKind kind;

    public ResolvedMethod(MethodEntry method, ClassFile declaringClass, InvokeKind kind) {
        this.method = method;
        this.declaringClass = declaringClass;
        this.kind = kind;
    }

    public MethodEntry getMethod() {
        return method;
    }

    public ClassFile getDeclaringClass() {
        return declaringClass;
    }

    public InvokeKind getKind() {
        return kind;
    }

    public boolean isStatic() {
        return (method.getAccess() & Modifiers.STATIC) != 0;
    }

    public boolean isNative() {
        return (method.getAccess() & Modifiers.NATIVE) != 0;
    }

    public boolean isAbstract() {
        return (method.getAccess() & Modifiers.ABSTRACT) != 0;
    }

    @Override
    public String toString() {
        return "ResolvedMethod{" +
                "method=" + method.getOwnerName() + "." + method.getName() + method.getDesc() +
                ", kind=" + kind +
                '}';
    }
}
