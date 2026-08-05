package com.tonic.analysis.cpg;

import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.*;

/**
 * Secondary lookup structure over CPG nodes, keyed by node type, method signature,
 * call target, instruction class, and property key/value pairs.
 */
public class CPGIndex
{

    private final Map<CPGNodeType, Set<CPGNode>> nodesByType = new EnumMap<>(CPGNodeType.class);
    private final Map<String, MethodNode> methodsBySignature = new LinkedHashMap<>();
    private final Map<String, Set<CallSiteNode>> callSitesByTarget = new LinkedHashMap<>();
    private final Map<Class<? extends IRInstruction>, Set<InstructionNode>> instructionsByClass = new LinkedHashMap<>();
    private final Map<String, Set<CPGNode>> nodesByProperty = new LinkedHashMap<>();

    /**
     * Adds a node to every applicable index.
     * @param node the node to index
     */
    public void index(CPGNode node)
    {
        nodesByType.computeIfAbsent(node.getNodeType(), k -> new LinkedHashSet<>()).add(node);

        if (node instanceof MethodNode)
        {
            MethodNode methodNode = (MethodNode) node;
            methodsBySignature.put(methodNode.getFullSignature(), methodNode);
        }

        if (node instanceof CallSiteNode)
        {
            CallSiteNode callSite = (CallSiteNode) node;
            String target = callSite.getFullTarget();
            callSitesByTarget.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(callSite);
        }

        if (node instanceof InstructionNode)
        {
            InstructionNode instrNode = (InstructionNode) node;
            Class<? extends IRInstruction> instrClass = instrNode.getInstruction().getClass();
            instructionsByClass.computeIfAbsent(instrClass, k -> new LinkedHashSet<>()).add(instrNode);
        }

        for (Map.Entry<String, Object> prop : node.getProperties().entrySet())
        {
            String key = prop.getKey() + "=" + prop.getValue();
            nodesByProperty.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(node);
        }
    }

    /**
     * Removes and re-adds a node, refreshing stale index entries.
     * @param node the node to reindex
     */
    public void reindex(CPGNode node)
    {
        remove(node);
        index(node);
    }

    /**
     * Removes a node from every applicable index.
     * @param node the node to remove
     */
    public void remove(CPGNode node)
    {
        Set<CPGNode> typeSet = nodesByType.get(node.getNodeType());
        if (typeSet != null)
        {
            typeSet.remove(node);
        }

        if (node instanceof MethodNode)
        {
            MethodNode methodNode = (MethodNode) node;
            methodsBySignature.remove(methodNode.getFullSignature());
        }

        if (node instanceof CallSiteNode)
        {
            CallSiteNode callSite = (CallSiteNode) node;
            Set<CallSiteNode> callSites = callSitesByTarget.get(callSite.getFullTarget());
            if (callSites != null)
            {
                callSites.remove(callSite);
            }
        }

        if (node instanceof InstructionNode)
        {
            InstructionNode instrNode = (InstructionNode) node;
            Set<InstructionNode> instrs = instructionsByClass.get(instrNode.getInstruction().getClass());
            if (instrs != null)
            {
                instrs.remove(instrNode);
            }
        }

        for (Map.Entry<String, Object> prop : node.getProperties().entrySet())
        {
            String key = prop.getKey() + "=" + prop.getValue();
            Set<CPGNode> nodes = nodesByProperty.get(key);
            if (nodes != null)
            {
                nodes.remove(node);
            }
        }
    }

    /**
     * Looks up all nodes of a given node type.
     * @param type the node type
     * @return the indexed nodes, or an empty set
     */
    public Set<CPGNode> getByType(CPGNodeType type)
    {
        return nodesByType.getOrDefault(type, Collections.emptySet());
    }

    /**
     * Looks up a method node by its exact signature.
     * @param owner the declaring class internal name
     * @param name the method name
     * @param descriptor the method descriptor
     * @return the method node, or null if not indexed
     */
    public MethodNode getMethod(String owner, String name, String descriptor)
    {
        return methodsBySignature.get(owner + "." + name + descriptor);
    }

    /**
     * @return a copy of all indexed method nodes
     */
    public Set<MethodNode> getAllMethods()
    {
        return new LinkedHashSet<>(methodsBySignature.values());
    }

    /**
     * Looks up call sites targeting an exact method signature.
     * @param owner the target class internal name
     * @param name the target method name
     * @param descriptor the target method descriptor
     * @return the matching call sites, or an empty set
     */
    public Set<CallSiteNode> getCallsTo(String owner, String name, String descriptor)
    {
        return callSitesByTarget.getOrDefault(owner + "." + name + descriptor, Collections.emptySet());
    }

    /**
     * Looks up call sites targeting a method name regardless of descriptor.
     * @param owner the target class internal name
     * @param name the target method name
     * @return the matching call sites across all overloads
     */
    public Set<CallSiteNode> getCallsTo(String owner, String name)
    {
        Set<CallSiteNode> result = new LinkedHashSet<>();
        String prefix = owner + "." + name;
        for (Map.Entry<String, Set<CallSiteNode>> entry : callSitesByTarget.entrySet())
        {
            if (entry.getKey().startsWith(prefix))
            {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    /**
     * Looks up instruction nodes wrapping a specific IR instruction class.
     * @param type the IR instruction class
     * @return the matching instruction nodes, or an empty set
     */
    public Set<InstructionNode> getInstructionsOfType(Class<? extends IRInstruction> type)
    {
        return instructionsByClass.getOrDefault(type, Collections.emptySet());
    }

    /**
     * Looks up nodes carrying a property with the given key and value.
     * @param key the property key
     * @param value the property value
     * @return the matching nodes, or an empty set
     */
    public Set<CPGNode> getByProperty(String key, Object value)
    {
        return nodesByProperty.getOrDefault(key + "=" + value, Collections.emptySet());
    }

    /**
     * @return the total number of indexed nodes across all node types
     */
    public int getNodeCount()
    {
        int count = 0;
        for (Set<CPGNode> nodes : nodesByType.values())
        {
            count += nodes.size();
        }
        return count;
    }

    /**
     * @return the number of indexed methods
     */
    public int getMethodCount()
    {
        return methodsBySignature.size();
    }

    /**
     * Empties every index.
     */
    public void clear()
    {
        nodesByType.clear();
        methodsBySignature.clear();
        callSitesByTarget.clear();
        instructionsByClass.clear();
        nodesByProperty.clear();
    }
}
