package com.tonic.analysis.cpg.node;

import com.tonic.analysis.ssa.cfg.IRMethod;

/**
 * CPG node wrapping a lifted IR method together with its owner class.
 */
public class MethodNode extends CPGNode
{

    private final IRMethod method;
    private final String owner;
    private final String name;
    private final String descriptor;

    /**
     * Creates a node for a lifted method.
     * @param id the unique node id
     * @param method the wrapped IR method
     * @param owner the declaring class internal name
     */
    public MethodNode(long id, IRMethod method, String owner)
    {
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

    /**
     * @return the method
     */
    public IRMethod getMethod()
    {
        return method;
    }

    /**
     * @return the owner
     */
    public String getOwner()
    {
        return owner;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    @Override
    public String getLabel()
    {
        return owner + "." + name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getUnderlying()
    {
        return (T) method;
    }

    /**
     * @return the signature as owner.name plus descriptor
     */
    public String getFullSignature()
    {
        return owner + "." + name + descriptor;
    }

    /**
     * @return whether the method is static
     */
    public boolean isStatic()
    {
        return method.isStatic();
    }

    /**
     * @return the number of declared parameters
     */
    public int getParameterCount()
    {
        return method.getParameters().size();
    }

    /**
     * @return the number of basic blocks in the method
     */
    public int getBlockCount()
    {
        return method.getBlockCount();
    }

    @Override
    public String toString()
    {
        return String.format("MethodNode[%d: %s.%s]", getId(), owner, name);
    }
}
