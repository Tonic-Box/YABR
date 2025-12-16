package com.tonic.analysis.dependency;

import com.tonic.parser.*;
import com.tonic.parser.constpool.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Analyzes class dependencies by scanning constant pools.
 * Provides queries for dependency relationships and circular dependency detection.
 */
public class DependencyAnalyzer {

    private final Map<String, DependencyNode> nodes = new LinkedHashMap<>();
    private final ClassPool classPool;

    /**
     * Creates a new DependencyAnalyzer and analyzes the given ClassPool.
     */
    public DependencyAnalyzer(ClassPool classPool) {
        this.classPool = classPool;
        analyze();
    }

    /**
     * Analyzes all classes in the ClassPool.
     */
    private void analyze() {
        List<ClassFile> classes = getClassList(classPool);
        if (classes == null) return;

        // First pass: create nodes for all classes
        for (ClassFile cf : classes) {
            getOrCreateNode(cf.getClassName(), cf);
        }

        // Second pass: analyze dependencies
        for (ClassFile cf : classes) {
            analyzeClass(cf);
        }
    }

    /**
     * Analyzes dependencies for a single class.
     */
    private void analyzeClass(ClassFile cf) {
        String className = cf.getClassName();
        DependencyNode node = getOrCreateNode(className, cf);

        // Superclass dependency
        String superName = cf.getSuperClassName();
        if (superName != null && !superName.equals(className)) {
            addDependency(node, superName, DependencyType.EXTENDS);
        }

        // Interface dependencies
        ConstPool cp = cf.getConstPool();
        for (Integer ifaceIndex : cf.getInterfaces()) {
            String ifaceName = resolveClassName(cp, ifaceIndex);
            if (ifaceName != null) {
                addDependency(node, ifaceName, DependencyType.IMPLEMENTS);
            }
        }

        // Field type dependencies
        for (FieldEntry field : cf.getFields()) {
            extractTypesFromDescriptor(field.getDesc()).forEach(type ->
                    addDependency(node, type, DependencyType.FIELD_TYPE));
        }

        // Method dependencies
        for (MethodEntry method : cf.getMethods()) {
            // Parameter and return types
            extractTypesFromDescriptor(method.getDesc()).forEach(type ->
                    addDependency(node, type, DependencyType.PARAMETER_TYPE));
        }

        // Constant pool dependencies
        analyzeConstantPool(node, cp);
    }

    /**
     * Analyzes the constant pool for class references.
     */
    private void analyzeConstantPool(DependencyNode node, ConstPool cp) {
        for (Item<?> item : cp.getItems()) {
            if (item == null) continue;

            if (item instanceof ClassRefItem) {
                String refClass = ((ClassRefItem) item).getClassName();
                if (refClass != null && !refClass.equals(node.getClassName())) {
                    // Could be type check, class literal, etc.
                    addDependency(node, refClass, DependencyType.CLASS_LITERAL);
                }
            } else if (item instanceof MethodRefItem) {
                MethodRefItem ref = (MethodRefItem) item;
                String owner = ref.getOwner();
                if (owner != null && !owner.equals(node.getClassName())) {
                    addDependency(node, owner, DependencyType.METHOD_CALL);
                }
            } else if (item instanceof InterfaceRefItem) {
                InterfaceRefItem ref = (InterfaceRefItem) item;
                String owner = ref.getOwner();
                if (owner != null && !owner.equals(node.getClassName())) {
                    addDependency(node, owner, DependencyType.METHOD_CALL);
                }
            } else if (item instanceof FieldRefItem) {
                FieldRefItem ref = (FieldRefItem) item;
                String owner = ref.getOwner();
                if (owner != null && !owner.equals(node.getClassName())) {
                    addDependency(node, owner, DependencyType.FIELD_ACCESS);
                }
            }
        }
    }

    /**
     * Extracts class names from a type descriptor.
     */
    private Set<String> extractTypesFromDescriptor(String descriptor) {
        Set<String> types = new LinkedHashSet<>();
        if (descriptor == null) return types;

        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                // Object type: L...;
                int end = descriptor.indexOf(';', i);
                if (end > i) {
                    types.add(descriptor.substring(i + 1, end));
                    i = end + 1;
                } else {
                    i++;
                }
            } else if (c == '[') {
                // Array type: skip to element type
                i++;
            } else {
                // Primitive or other
                i++;
            }
        }

        return types;
    }

    /**
     * Adds a dependency edge.
     */
    private void addDependency(DependencyNode from, String toClassName, DependencyType type) {
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
    private boolean isPrimitive(String name) {
        return name.length() == 1 &&
               "ZBCSIJFD".indexOf(name.charAt(0)) >= 0;
    }

    /**
     * Gets or creates a node for the given class name.
     */
    private DependencyNode getOrCreateNode(String className, ClassFile classFile) {
        return nodes.computeIfAbsent(className, n -> new DependencyNode(n, classFile));
    }

    /**
     * Resolves a class name from a constant pool index.
     */
    private String resolveClassName(ConstPool cp, int classIndex) {
        try {
            Item<?> item = cp.getItem(classIndex);
            if (item instanceof ClassRefItem) {
                return ((ClassRefItem) item).getClassName();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    // ===== Public API =====

    /**
     * Gets the node for a class name.
     */
    public DependencyNode getNode(String className) {
        return nodes.get(className);
    }

    /**
     * Gets all nodes in the dependency graph.
     */
    public Collection<DependencyNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Gets only nodes that are in the ClassPool.
     */
    public Collection<DependencyNode> getPoolNodes() {
        return nodes.values().stream()
                .filter(DependencyNode::isInPool)
                .collect(Collectors.toList());
    }

    /**
     * Gets all classes that the specified class depends on.
     */
    public Set<String> getDependencies(String className) {
        DependencyNode node = nodes.get(className);
        if (node == null) return Collections.emptySet();
        return node.getDependencies();
    }

    /**
     * Gets all classes that depend on the specified class.
     */
    public Set<String> getDependents(String className) {
        DependencyNode node = nodes.get(className);
        if (node == null) return Collections.emptySet();
        return node.getDependents();
    }

    /**
     * Gets transitive dependencies (all classes reachable from this class).
     */
    public Set<String> getTransitiveDependencies(String className) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> worklist = new ArrayDeque<>();
        worklist.add(className);

        while (!worklist.isEmpty()) {
            String current = worklist.poll();
            if (!visited.add(current)) continue;

            DependencyNode node = nodes.get(current);
            if (node != null) {
                for (String dep : node.getDependencies()) {
                    if (!visited.contains(dep)) {
                        worklist.add(dep);
                    }
                }
            }
        }

        visited.remove(className); // Don't include self
        return visited;
    }

    /**
     * Gets transitive dependents (all classes that transitively depend on this class).
     */
    public Set<String> getTransitiveDependents(String className) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> worklist = new ArrayDeque<>();
        worklist.add(className);

        while (!worklist.isEmpty()) {
            String current = worklist.poll();
            if (!visited.add(current)) continue;

            DependencyNode node = nodes.get(current);
            if (node != null) {
                for (String dep : node.getDependents()) {
                    if (!visited.contains(dep)) {
                        worklist.add(dep);
                    }
                }
            }
        }

        visited.remove(className); // Don't include self
        return visited;
    }

    /**
     * Finds all circular dependencies in the graph.
     * Returns a list of cycles, where each cycle is a list of class names.
     */
    public List<List<String>> findCircularDependencies() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> onStack = new HashSet<>();

        for (DependencyNode node : nodes.values()) {
            if (!node.isInPool()) continue;
            if (!visited.contains(node.getClassName())) {
                findCyclesDFS(node.getClassName(), visited, onStack, new ArrayList<>(), cycles);
            }
        }

        return cycles;
    }

    private void findCyclesDFS(String current, Set<String> visited, Set<String> onStack,
                               List<String> path, List<List<String>> cycles) {
        visited.add(current);
        onStack.add(current);
        path.add(current);

        DependencyNode node = nodes.get(current);
        if (node != null) {
            for (String dep : node.getDependencies()) {
                DependencyNode depNode = nodes.get(dep);
                if (depNode == null || !depNode.isInPool()) continue;

                if (!visited.contains(dep)) {
                    findCyclesDFS(dep, visited, onStack, path, cycles);
                } else if (onStack.contains(dep)) {
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
     * Finds classes matching a predicate.
     */
    public Set<String> findClasses(Predicate<DependencyNode> predicate) {
        return nodes.values().stream()
                .filter(predicate)
                .map(DependencyNode::getClassName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Gets classes with no dependencies (leaf classes).
     */
    public Set<String> findLeafClasses() {
        return findClasses(n -> n.isInPool() && n.getDependencyCount() == 0);
    }

    /**
     * Gets classes with no dependents (root classes).
     */
    public Set<String> findRootClasses() {
        return findClasses(n -> n.isInPool() && n.getDependentCount() == 0);
    }

    /**
     * Gets classes that match a package pattern.
     */
    public Set<String> getClassesInPackage(String packagePrefix) {
        return nodes.values().stream()
                .filter(n -> n.isInPool() && n.getClassName().startsWith(packagePrefix))
                .map(DependencyNode::getClassName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Checks if class A depends on class B (directly).
     */
    public boolean dependsOn(String classA, String classB) {
        DependencyNode node = nodes.get(classA);
        if (node == null) return false;
        return node.getDependencies().contains(classB);
    }

    /**
     * Checks if class A depends on class B (transitively).
     */
    public boolean transitivelyDependsOn(String classA, String classB) {
        return getTransitiveDependencies(classA).contains(classB);
    }

    /**
     * Returns the number of classes in the graph.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Returns the number of dependency edges.
     */
    public int edgeCount() {
        int count = 0;
        for (DependencyNode node : nodes.values()) {
            count += node.getOutgoingDependencies().size();
        }
        return count;
    }

    /**
     * Gets the list of classes from a ClassPool.
     */
    private static List<ClassFile> getClassList(ClassPool classPool) {
        return classPool.getClasses();
    }

    @Override
    public String toString() {
        long poolCount = nodes.values().stream().filter(DependencyNode::isInPool).count();
        return "DependencyAnalyzer{classes=" + nodes.size() + ", inPool=" + poolCount + ", edges=" + edgeCount() + "}";
    }
}
