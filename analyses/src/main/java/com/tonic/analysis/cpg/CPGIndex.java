package com.tonic.analysis.cpg;

import com.tonic.analysis.cpg.node.*;
import com.tonic.analysis.ssa.ir.IRInstruction;

import java.util.*;

public class CPGIndex {

    private final Map<CPGNodeType, Set<CPGNode>> nodesByType = new EnumMap<>(CPGNodeType.class);
    private final Map<String, MethodNode> methodsBySignature = new LinkedHashMap<>();
    private final Map<String, Set<CallSiteNode>> callSitesByTarget = new LinkedHashMap<>();
    private final Map<Class<? extends IRInstruction>, Set<InstructionNode>> instructionsByClass = new LinkedHashMap<>();
    private final Map<String, Set<CPGNode>> nodesByProperty = new LinkedHashMap<>();

    public void index(CPGNode node) {
        nodesByType.computeIfAbsent(node.getNodeType(), k -> new LinkedHashSet<>()).add(node);

        if (node instanceof MethodNode) {
            MethodNode methodNode = (MethodNode) node;
            methodsBySignature.put(methodNode.getFullSignature(), methodNode);
        }

        if (node instanceof CallSiteNode) {
            CallSiteNode callSite = (CallSiteNode) node;
            String target = callSite.getFullTarget();
            callSitesByTarget.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(callSite);
        }

        if (node instanceof InstructionNode) {
            InstructionNode instrNode = (InstructionNode) node;
            Class<? extends IRInstruction> instrClass = instrNode.getInstruction().getClass();
            instructionsByClass.computeIfAbsent(instrClass, k -> new LinkedHashSet<>()).add(instrNode);
        }

        for (Map.Entry<String, Object> prop : node.getProperties().entrySet()) {
            String key = prop.getKey() + "=" + prop.getValue();
            nodesByProperty.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(node);
        }
    }

    public void reindex(CPGNode node) {
        remove(node);
        index(node);
    }

    public void remove(CPGNode node) {
        Set<CPGNode> typeSet = nodesByType.get(node.getNodeType());
        if (typeSet != null) {
            typeSet.remove(node);
        }

        if (node instanceof MethodNode) {
            MethodNode methodNode = (MethodNode) node;
            methodsBySignature.remove(methodNode.getFullSignature());
        }

        if (node instanceof CallSiteNode) {
            CallSiteNode callSite = (CallSiteNode) node;
            Set<CallSiteNode> callSites = callSitesByTarget.get(callSite.getFullTarget());
            if (callSites != null) {
                callSites.remove(callSite);
            }
        }

        if (node instanceof InstructionNode) {
            InstructionNode instrNode = (InstructionNode) node;
            Set<InstructionNode> instrs = instructionsByClass.get(instrNode.getInstruction().getClass());
            if (instrs != null) {
                instrs.remove(instrNode);
            }
        }

        for (Map.Entry<String, Object> prop : node.getProperties().entrySet()) {
            String key = prop.getKey() + "=" + prop.getValue();
            Set<CPGNode> nodes = nodesByProperty.get(key);
            if (nodes != null) {
                nodes.remove(node);
            }
        }
    }

    public Set<CPGNode> getByType(CPGNodeType type) {
        return nodesByType.getOrDefault(type, Collections.emptySet());
    }

    public MethodNode getMethod(String owner, String name, String descriptor) {
        return methodsBySignature.get(owner + "." + name + descriptor);
    }

    public Set<MethodNode> getAllMethods() {
        return new LinkedHashSet<>(methodsBySignature.values());
    }

    public Set<CallSiteNode> getCallsTo(String owner, String name, String descriptor) {
        return callSitesByTarget.getOrDefault(owner + "." + name + descriptor, Collections.emptySet());
    }

    public Set<CallSiteNode> getCallsTo(String owner, String name) {
        Set<CallSiteNode> result = new LinkedHashSet<>();
        String prefix = owner + "." + name;
        for (Map.Entry<String, Set<CallSiteNode>> entry : callSitesByTarget.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    public Set<InstructionNode> getInstructionsOfType(Class<? extends IRInstruction> type) {
        return instructionsByClass.getOrDefault(type, Collections.emptySet());
    }

    public Set<CPGNode> getByProperty(String key, Object value) {
        return nodesByProperty.getOrDefault(key + "=" + value, Collections.emptySet());
    }

    public int getNodeCount() {
        int count = 0;
        for (Set<CPGNode> nodes : nodesByType.values()) {
            count += nodes.size();
        }
        return count;
    }

    public int getMethodCount() {
        return methodsBySignature.size();
    }

    public void clear() {
        nodesByType.clear();
        methodsBySignature.clear();
        callSitesByTarget.clear();
        instructionsByClass.clear();
        nodesByProperty.clear();
    }
}
