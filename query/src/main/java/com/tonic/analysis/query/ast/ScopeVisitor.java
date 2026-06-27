package com.tonic.analysis.query.ast;

public interface ScopeVisitor<T> {

    T visitAll(AllScope scope);

    T visitClass(ClassScope scope);

    T visitMethod(MethodScope scope);

    T visitDuring(DuringScope scope);
}
