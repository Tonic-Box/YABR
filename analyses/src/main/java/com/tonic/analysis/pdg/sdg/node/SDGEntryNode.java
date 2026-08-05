package com.tonic.analysis.pdg.sdg.node;

import com.tonic.analysis.common.MethodReference;
import com.tonic.analysis.pdg.PDG;
import com.tonic.analysis.pdg.node.PDGNode;
import com.tonic.analysis.pdg.node.PDGNodeType;
import com.tonic.analysis.ssa.cfg.IRBlock;
import com.tonic.analysis.ssa.value.SSAValue;
import com.tonic.analysis.ssa.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The entry node of one procedure in a system dependence graph, owning that
 * procedure's PDG plus its formal-in and formal-out parameter nodes.
 */
public class SDGEntryNode extends PDGNode
{

    private final MethodReference methodRef;
    private PDG procedurePDG;
    private final List<SDGFormalInNode> formalIns = new ArrayList<>();
    private SDGFormalOutNode formalOut;

    /**
     * Creates an entry node for a procedure.
     * @param id the graph-unique node id
     * @param methodRef the procedure this node opens
     * @param entryBlock the procedure's entry block
     */
    public SDGEntryNode(int id, MethodReference methodRef, IRBlock entryBlock)
    {
        super(id, PDGNodeType.ENTRY, entryBlock);
        this.methodRef = methodRef;
    }

    /**
     * @return the method ref
     */
    public MethodReference getMethodRef()
    {
        return methodRef;
    }

    /**
     * @return the dependence graph of the procedure body, or null if not yet attached
     */
    public PDG getProcedurePDG()
    {
        return procedurePDG;
    }

    /**
     * Attaches the dependence graph built for this procedure's body.
     * @param procedurePDG the procedure's PDG
     */
    public void setProcedurePDG(PDG procedurePDG)
    {
        this.procedurePDG = procedurePDG;
    }

    /**
     * @return the formal out
     */
    public SDGFormalOutNode getFormalOut()
    {
        return formalOut;
    }

    /**
     * Sets the node representing this procedure's return value.
     * @param formalOut the formal-out node
     */
    public void setFormalOut(SDGFormalOutNode formalOut)
    {
        this.formalOut = formalOut;
    }

    /**
     * Registers a parameter node on this entry.
     * @param formalIn the formal-in node to add
     */
    public void addFormalIn(SDGFormalInNode formalIn)
    {
        formalIns.add(formalIn);
    }

    /**
     * Looks up the parameter node at a position.
     * @param parameterIndex the parameter position to match
     * @return the matching formal-in node, or null if none is registered
     */
    public SDGFormalInNode getFormalIn(int parameterIndex)
    {
        for (SDGFormalInNode formalIn : formalIns)
        {
            if (formalIn.getParameterIndex() == parameterIndex)
            {
                return formalIn;
            }
        }
        return null;
    }

    /**
     * @return the number of registered parameter nodes
     */
    public int getFormalInCount()
    {
        return formalIns.size();
    }

    /**
     * @return an unmodifiable view of the parameter nodes, in registration order
     */
    public List<SDGFormalInNode> getFormalIns()
    {
        return Collections.unmodifiableList(formalIns);
    }

    /**
     * @return true if a return-value node has been set
     */
    public boolean hasFormalOut()
    {
        return formalOut != null;
    }

    @Override
    public String getLabel()
    {
        return "ENTRY:" + methodRef.getName();
    }

    @Override
    public List<Value> getUsedValues()
    {
        return Collections.emptyList();
    }

    @Override
    public SSAValue getDefinedValue()
    {
        return null;
    }

    /**
     * @return owner, name and descriptor of the procedure joined into one string
     */
    public String getFullSignature()
    {
        return methodRef.getOwner() + "." + methodRef.getName() + methodRef.getDescriptor();
    }

    /**
     * @return owner and name of the procedure, without the descriptor
     */
    public String getMethodName()
    {
        return methodRef.getOwner() + "." + methodRef.getName();
    }

    @Override
    public String toString()
    {
        return String.format("SDGEntry[%d: %s, %d params]", getId(), methodRef.getName(), formalIns.size());
    }
}
