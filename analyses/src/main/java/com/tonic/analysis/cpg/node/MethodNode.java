package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.cfg.IRMethod;
import lombok.Getter;

@Getter
public class MethodNode extends CPGNode {

    private final IRMethod method;
    private final String owner;
    private final String name;
    private final String descriptor;

    public MethodNode(long id, IRMethod method, String owner) {
        super(id, CPGNodeType.METHOD);
        this.method = method;
        this.owner = owner;
        this.name = method.getName();
        this.descriptor = method.getDescriptor();

        setProperty("owner", owner);
        setProperty("name", name);
        setProperty("descriptor", descriptor);
        setProperty("isStatic", method.isStatic());
    }

    @Override
    public String getLabel() {
        return owner + "." + name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying() {
        return (T) method;
    }

    public String getFullSignature() {
        return owner + "." + name + descriptor;
    }

    public boolean isStatic() {
        return method.isStatic();
    }

    public int getParameterCount() {
        return method.getParameters().size();
    }

    public int getBlockCount() {
        return method.getBlockCount();
    }

    @Override
    public String toString() {
        return String.format("MethodNode[%d: %s.%s]", getId(), owner, name);
    }
}
