package com.tonic.analysis.dependency;

import com.tonic.parser.*;
import com.tonic.parser.constpool.*;
import com.tonic.util.DescriptorUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Analyzes class dependencies by scanning constant pools.
 */
public class DependencyAnalyzer
{

    private final Map<String, DependencyNode> nodes = new LinkedHashMap<>();
    private final ClassPool classPool;

    /**
     * Creates a new DependencyAnalyzer and analyzes the given ClassPool.
     *
     * @param classPool the pool whose classes are scanned for dependencies
     */
    public DependencyAnalyzer(ClassPool classPool)
    {
        this.classPool = classPool;
        analyze();
    }

    /**
     * Analyzes all classes in the ClassPool.
     */
    private void analyze()
    {
        List<ClassFile> classes = getClassList(classPool);
        if (classes == null) return;

        // First pass: create nodes for all classes
        for (ClassFile cf : classes)
        {
            getOrCreateNode(cf.getClassName(), cf);
        }

        // Second pass: analyze dependencies
        for (ClassFile cf : classes)
        {
            analyzeClass(cf);
        }
    }

    /**
     * Analyzes dependencies for a single class.
     */
    private void analyzeClass(ClassFile cf)
    {
        String className = cf.getClassName();
        DependencyNode node = getOrCreateNode(className, cf);

        String superName = cf.getSuperClassName();
        if (superName != null && !superName.equals(className))
        {
            addDependency(node, superName, DependencyType.EXTENDS);
        }

        ConstPool cp = cf.getConstPool();
        for (Integer ifaceIndex : cf.getInterfaces())
        {
            String ifaceName = resolveClassName(cp, ifaceIndex);
            if (ifaceName != null)
            {
                addDependency(node, ifaceName, DependencyType.IMPLEMENTS);
            }
        }

        for (FieldEntry field : cf.getFields())
        {
            extractTypesFromDescriptor(field.getDesc()).forEach(type ->
                    addDependency(node, type, DependencyType.FIELD_TYPE));
        }

        for (MethodEntry method : cf.getMethods())
        {
            // Parameter and return types
            extractTypesFromDescriptor(method.getDesc()).forEach(type ->
                    addDependency(node, type, DependencyType.PARAMETER_TYPE));
        }

        analyzeConstantPool(node, cp);
    }

    /**
     * Analyzes the constant pool for class references.
     */
    private void analyzeConstantPool(DependencyNode node, ConstPool cp)
    {
        for (Item<?> item : cp.getItems())
        {
            if (item == null) continue;

            if (item instanceof ClassRefItem)
            {
                String refClass = ((ClassRefItem) item).getClassName();
                if (refClass != null && !refClass.equals(node.getClassName()))
                {
                    // Could be type check, class literal, etc.
                    addDependency(node, refClass, DependencyType.CLASS_LITERAL);
                }
            }
            else if (item instanceof MethodRefItem)
            {
                MethodRefItem ref = (MethodRefItem) item;
                String owner = ref.getOwner();
                if (owner != null && !owner.equals(node.getClassName()))
                {
                    addDependency(node, owner, DependencyType.METHOD_CALL);
                }
            }
            else if (item instanceof InterfaceRefItem)
            {
                InterfaceRefItem ref = (InterfaceRefItem) item;
                String owner = ref.getOwner();
                if (owner != null && !owner.equals(node.getClassName()))
                {
                    addDependency(node, owner, DependencyType.METHOD_CALL);
                }
            }
            else if (item instanceof FieldRefItem)
            {
                FieldRefItem ref = (FieldRefItem) item;
                String owner = ref.getOwner();
                if (owner != null && !owner.equals(node.getClassName()))
                {
                    addDependency(node, owner, DependencyType.FIELD_ACCESS);
                }
            }
        }
    }

    /**
     * Extracts class names from a type descriptor.
     */
    private Set<String> extractTypesFromDescriptor(String descriptor)
    {
        if (descriptor == null) return new LinkedHashSet<>();
        return DescriptorUtil.extractClassNames(descriptor);
    }

    /**
     * Adds a dependency edge.
     */
    private void addDependency(DependencyNode from, String toClassName, DependencyType type)
    {
        if (toClassName == null || toClassName.isEmpty()) return;
        if (toClassName.startsWith("[")) return; // Skip array types (we care about the component)
        if (toClassName.startsWith("(")) return; // Skip method descriptors
        if (toClassName.contains(";")) return; // Skip malformed class names
        if (isPrimitive(toClassName)) return;

        DependencyNode to = getOrCreateNode(toClassName, null);
        Dependency dep = new Dependency(from.getClassName(), toClassName, type);

        from.addOutgoingDependency(dep);
        to.addIncomingDependency(dep);
    }

    /**
     * Checks if a type name is a primitive.
     */
    private boolean isPrimitive(String name)
    {
        return name.length() == 1 &&
               "ZBCSIJFD".indexOf(name.charAt(0)) >= 0;
    }

    /**
     * Gets or creates a node for the given class name.
     */
    private DependencyNode getOrCreateNode(String className, ClassFile classFile)
    {
        return nodes.computeIfAbsent(className, n -> new DependencyNode(n, classFile));
    }

    /**
     * Resolves a class name from a constant pool index.
     */
    private String resolveClassName(ConstPool cp, int classIndex)
    {
        try
        {
            Item<?> item = cp.getItem(classIndex);
            if (item instanceof ClassRefItem)
            {
                return ((ClassRefItem) item).getClassName();
            }
        }
        catch (Exception e)
        {
            // Ignore
        }
        return null;
    }

    // Public API

    /**
     * Looks up the graph node for a class.
     *
     * @param className the class to look up
     * @return the node, or null if the class was never seen
     */
    public DependencyNode getNode(String className)
    {
        return nodes.get(className);
    }

    /**
     * @return an unmodifiable view of every node in the graph
     */
    public Collection<DependencyNode> getAllNodes()
    {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Selects the nodes backed by a class present in the pool.
     *
     * @return the in-pool nodes, excluding referenced-only classes
     */
    public Collection<DependencyNode> getPoolNodes()
    {
        return nodes.values().stream()
                .filter(DependencyNode::isInPool)
                .collect(Collectors.toList());
    }

    /**
     * Looks up the direct dependencies recorded for a class.
     *
     * @param className the class to query
     * @return the names of classes it depends on, or an empty set if it has no node
     */
    public Set<String> getDependencies(String className)
    {
        DependencyNode node = nodes.get(className);
        if (node == null) return Collections.emptySet();
        return node.getDependencies();
    }

    /**
     * Looks up the direct dependents recorded for a class.
     *
     * @param className the class to query
     * @return the names of classes depending on it, or an empty set if it has no node
     */
    public Set<String> getDependents(String className)
    {
        DependencyNode node = nodes.get(className);
        if (node == null) return Collections.emptySet();
        return node.getDependents();
    }

    /**
     * Walks the dependency edges breadth-first from the given class.
     *
     * @param className the class to start from
     * @return every class reachable from it, excluding the class itself
     */
    public Set<String> getTransitiveDependencies(String className)
    {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> worklist = new ArrayDeque<>();
        worklist.add(className);

        while (!worklist.isEmpty())
        {
            String current = worklist.poll();
            if (!visited.add(current)) continue;

            DependencyNode node = nodes.get(current);
            if (node != null)
            {
                for (String dep : node.getDependencies())
                {
                    if (!visited.contains(dep))
                    {
                        worklist.add(dep);
                    }
                }
            }
        }

        visited.remove(className); // Don't include self
        return visited;
    }

    /**
     * Walks the dependent edges breadth-first from the given class.
     *
     * @param className the class to start from
     * @return every class that transitively depends on it, excluding the class itself
     */
    public Set<String> getTransitiveDependents(String className)
    {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> worklist = new ArrayDeque<>();
        worklist.add(className);

        while (!worklist.isEmpty())
        {
            String current = worklist.poll();
            if (!visited.add(current)) continue;

            DependencyNode node = nodes.get(current);
            if (node != null)
            {
                for (String dep : node.getDependents())
                {
                    if (!visited.contains(dep))
                    {
                        worklist.add(dep);
                    }
                }
            }
        }

        visited.remove(className); // Don't include self
        return visited;
    }

    /**
     * Walks the pool classes depth-first collecting every dependency cycle found.
     *
     * @return one list of class names per cycle, repeating the entry class as the final element
     */
    public List<List<String>> findCircularDependencies()
    {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> onStack = new HashSet<>();

        for (DependencyNode node : nodes.values())
        {
            if (!node.isInPool()) continue;
            if (!visited.contains(node.getClassName()))
            {
                findCyclesDFS(node.getClassName(), visited, onStack, new ArrayList<>(), cycles);
            }
        }

        return cycles;
    }

    private void findCyclesDFS(String current, Set<String> visited, Set<String> onStack, List<String> path, List<List<String>> cycles)
    {
        visited.add(current);
        onStack.add(current);
        path.add(current);

        DependencyNode node = nodes.get(current);
        if (node != null)
        {
            for (String dep : node.getDependencies())
            {
                DependencyNode depNode = nodes.get(dep);
                if (depNode == null || !depNode.isInPool()) continue;

                if (!visited.contains(dep))
                {
                    findCyclesDFS(dep, visited, onStack, path, cycles);
                }
                else if (onStack.contains(dep))
                {
                    // Found a cycle
                    int startIndex = path.indexOf(dep);
                    List<String> cycle = new ArrayList<>(path.subList(startIndex, path.size()));
                    cycle.add(dep); // Complete the cycle
                    cycles.add(cycle);
                }
            }
        }

        path.remove(path.size() - 1);
        onStack.remove(current);
    }

    /**
     * Finds classes whose node satisfies a predicate.
     *
     * @param predicate the test applied to every node in the graph
     * @return the matching class names, in graph insertion order
     */
    public Set<String> findClasses(Predicate<DependencyNode> predicate)
    {
        return nodes.values().stream()
                .filter(predicate)
                .map(DependencyNode::getClassName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Finds pool classes that depend on nothing.
     *
     * @return the class names with a dependency count of zero
     */
    public Set<String> findLeafClasses()
    {
        return findClasses(n -> n.isInPool() && n.getDependencyCount() == 0);
    }

    /**
     * Finds pool classes that nothing else depends on.
     *
     * @return the class names with a dependent count of zero
     */
    public Set<String> findRootClasses()
    {
        return findClasses(n -> n.isInPool() && n.getDependentCount() == 0);
    }

    /**
     * Selects pool classes whose name starts with the given prefix.
     *
     * @param packagePrefix the package name prefix to match against
     * @return the matching class names, in graph insertion order
     */
    public Set<String> getClassesInPackage(String packagePrefix)
    {
        return nodes.values().stream()
                .filter(n -> n.isInPool() && n.getClassName().startsWith(packagePrefix))
                .map(DependencyNode::getClassName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Checks for a direct dependency edge between two classes.
     *
     * @param classA the dependent class name
     * @param classB the candidate dependency class name
     * @return true if classA directly depends on classB, false if classA has no node
     */
    public boolean dependsOn(String classA, String classB)
    {
        DependencyNode node = nodes.get(classA);
        if (node == null) return false;
        return node.getDependencies().contains(classB);
    }

    /**
     * Checks for a dependency reachable through any chain of intermediate classes.
     *
     * @param classA the dependent class name
     * @param classB the candidate dependency class name
     * @return true if classA transitively depends on classB
     */
    public boolean transitivelyDependsOn(String classA, String classB)
    {
        return getTransitiveDependencies(classA).contains(classB);
    }

    /**
     * @return the number of nodes in the graph, including classes outside the pool
     */
    public int size()
    {
        return nodes.size();
    }

    /**
     * Sums the outgoing dependency edges over every node.
     *
     * @return the total number of dependency edges in the graph
     */
    public int edgeCount()
    {
        int count = 0;
        for (DependencyNode node : nodes.values())
        {
            count += node.getOutgoingDependencies().size();
        }
        return count;
    }

    /**
     * Gets the list of classes from a ClassPool.
     */
    private static List<ClassFile> getClassList(ClassPool classPool)
    {
        return classPool.getClasses();
    }

    @Override
    public String toString()
    {
        long poolCount = nodes.values().stream().filter(DependencyNode::isInPool).count();
        return "DependencyAnalyzer{classes=" + nodes.size() + ", inPool=" + poolCount + ", edges=" + edgeCount() + "}";
    }
}
