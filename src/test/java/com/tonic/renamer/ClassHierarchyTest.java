package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ClassHierarchy functionality.
 * Tests building hierarchy from ClassPool, inheritance relationships,
 * method resolution, and override detection.
 */
class ClassHierarchyTest {

    private ClassPool pool;
    private ClassHierarchy hierarchy;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
        hierarchy = new ClassHierarchy();
    }

    // ========== Building Hierarchy Tests ==========

    @Test
    void buildFromEmptyPoolCreatesEmptyHierarchy() {
        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        assertEquals(0, h.size(), "Hierarchy from empty pool should be empty");
    }

    @Test
    void buildFromSingleClassCreatesNode() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/Single", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertEquals(2, h.size(), "Should have class + java/lang/Object");
        assertNotNull(h.getNode("com/test/Single"));
    }

    @Test
    void buildFromMultipleClassesCreatesAllNodes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);
        pool.createNewClass("com/test/ClassC", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertTrue(h.size() >= 3, "Should have at least 3 classes");
        assertNotNull(h.getNode("com/test/ClassA"));
        assertNotNull(h.getNode("com/test/ClassB"));
        assertNotNull(h.getNode("com/test/ClassC"));
    }

    @Test
    void buildSetsClassFilesInNodes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithFile", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode node = h.getNode("com/test/WithFile");

        assertNotNull(node);
        assertSame(cf, node.getClassFile());
        assertTrue(node.isInPool());
    }

    @Test
    void buildCreatesExternalNodesForSuperclass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode objectNode = h.getNode("java/lang/Object");

        assertNotNull(objectNode, "Should create external node for java/lang/Object");
        assertFalse(objectNode.isInPool(), "java/lang/Object should be external");
        assertNull(objectNode.getClassFile());
    }

    // ========== Superclass Relationship Tests ==========

    @Test
    void classHasObjectAsSuperclass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode node = h.getNode("com/test/MyClass");

        assertNotNull(node.getSuperClass());
        assertEquals("java/lang/Object", node.getSuperClass().getName());
    }

    @Test
    void childClassHasCorrectSuperclass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);

        // Set child's superclass to parent
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode childNode = h.getNode("com/test/Child");
        ClassNode parentNode = h.getNode("com/test/Parent");

        assertNotNull(childNode.getSuperClass());
        assertEquals("com/test/Parent", childNode.getSuperClass().getName());
        assertSame(parentNode, childNode.getSuperClass());
    }

    @Test
    void superclassHasSubclassReference() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode parentNode = h.getNode("com/test/Parent");
        ClassNode childNode = h.getNode("com/test/Child");

        assertTrue(parentNode.getSubClasses().contains(childNode),
                "Parent should have child in subclasses");
    }

    @Test
    void multiLevelInheritanceChain() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile grandparent = pool.createNewClass("com/test/GrandParent", access);
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);

        parent.setSuperClassName("com/test/GrandParent");
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode childNode = h.getNode("com/test/Child");

        assertEquals("com/test/Parent", childNode.getSuperClass().getName());
        assertEquals("com/test/GrandParent", childNode.getSuperClass().getSuperClass().getName());
    }

    // ========== Interface Relationship Tests ==========

    @Test
    void classWithInterfaceHasInterfaceNode() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface = pool.createNewClass("com/test/MyInterface", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/Implementation", access);

        // Add interface to implementation
        impl.addInterface("com/test/MyInterface");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode implNode = h.getNode("com/test/Implementation");
        ClassNode ifaceNode = h.getNode("com/test/MyInterface");

        assertTrue(implNode.getInterfaces().contains(ifaceNode),
                "Class should have interface in interfaces list");
        assertTrue(ifaceNode.isInterface());
    }

    @Test
    void interfaceHasImplementorReference() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface = pool.createNewClass("com/test/MyInterface", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/Implementation", access);

        impl.addInterface("com/test/MyInterface");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode ifaceNode = h.getNode("com/test/MyInterface");
        ClassNode implNode = h.getNode("com/test/Implementation");

        assertTrue(ifaceNode.getImplementors().contains(implNode),
                "Interface should have implementor in implementors list");
    }

    @Test
    void classWithMultipleInterfaces() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface1 = pool.createNewClass("com/test/Interface1", ifaceAccess);
        ClassFile iface2 = pool.createNewClass("com/test/Interface2", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/MultiImpl", access);

        impl.addInterface("com/test/Interface1");
        impl.addInterface("com/test/Interface2");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode implNode = h.getNode("com/test/MultiImpl");

        assertEquals(2, implNode.getInterfaces().size(),
                "Class should have both interfaces");
    }

    // ========== Ancestor/Descendant Tests ==========

    @Test
    void getAllAncestorsIncludesSuperclass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode childNode = h.getNode("com/test/Child");
        Set<ClassNode> ancestors = childNode.getAllAncestors();

        assertTrue(ancestors.stream().anyMatch(n -> n.getName().equals("com/test/Parent")),
                "Ancestors should include parent");
    }

    @Test
    void getAllAncestorsIncludesInterfaces() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface = pool.createNewClass("com/test/Interface", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/Impl", access);

        impl.addInterface("com/test/Interface");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode implNode = h.getNode("com/test/Impl");
        Set<ClassNode> ancestors = implNode.getAllAncestors();

        assertTrue(ancestors.stream().anyMatch(n -> n.getName().equals("com/test/Interface")),
                "Ancestors should include interface");
    }

    @Test
    void getAllDescendantsIncludesSubclasses() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode parentNode = h.getNode("com/test/Parent");
        Set<ClassNode> descendants = parentNode.getAllDescendants();

        assertTrue(descendants.stream().anyMatch(n -> n.getName().equals("com/test/Child")),
                "Descendants should include child");
    }

    @Test
    void getAllDescendantsIncludesImplementors() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface = pool.createNewClass("com/test/Interface", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/Impl", access);

        impl.addInterface("com/test/Interface");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode ifaceNode = h.getNode("com/test/Interface");
        Set<ClassNode> descendants = ifaceNode.getAllDescendants();

        assertTrue(descendants.stream().anyMatch(n -> n.getName().equals("com/test/Impl")),
                "Descendants should include implementor");
    }

    // ========== isAncestorOf Tests ==========

    @Test
    void isAncestorOfReturnsTrueForParent() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertTrue(h.isAncestorOf("com/test/Parent", "com/test/Child"));
    }

    @Test
    void isAncestorOfReturnsTrueForGrandparent() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile grandparent = pool.createNewClass("com/test/GrandParent", access);
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);

        parent.setSuperClassName("com/test/GrandParent");
        child.setSuperClassName("com/test/Parent");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertTrue(h.isAncestorOf("com/test/GrandParent", "com/test/Child"));
    }

    @Test
    void isAncestorOfReturnsTrueForInterface() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface = pool.createNewClass("com/test/Interface", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/Impl", access);

        impl.addInterface("com/test/Interface");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertTrue(h.isAncestorOf("com/test/Interface", "com/test/Impl"));
    }

    @Test
    void isAncestorOfReturnsFalseForUnrelated() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertFalse(h.isAncestorOf("com/test/ClassA", "com/test/ClassB"));
    }

    @Test
    void isAncestorOfReturnsFalseForNonexistentClass() {
        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertFalse(h.isAncestorOf("com/test/A", "com/test/Nonexistent"));
    }

    // ========== Method Hierarchy Tests ==========

    @Test
    void findMethodHierarchyForNonexistentClassReturnsEmpty() {
        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        Set<ClassNode> result = h.findMethodHierarchy("com/test/Nonexistent", "method", "()V");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findMethodHierarchyForMethodInSingleClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/MyClass", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethodWithDescriptor(methodAccess, "myMethod", "()V");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Set<ClassNode> result = h.findMethodHierarchy("com/test/MyClass", "myMethod", "()V");

        assertEquals(1, result.size());
        assertTrue(result.stream().anyMatch(n -> n.getName().equals("com/test/MyClass")));
    }

    @Test
    void findMethodHierarchyIncludesOverriddenMethods() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile parent = pool.createNewClass("com/test/Parent", access);
        ClassFile child = pool.createNewClass("com/test/Child", access);
        child.setSuperClassName("com/test/Parent");

        int methodAccess = new AccessBuilder().setPublic().build();
        parent.createNewMethodWithDescriptor(methodAccess, "process", "()V");
        child.createNewMethodWithDescriptor(methodAccess, "process", "()V");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Set<ClassNode> result = h.findMethodHierarchy("com/test/Parent", "process", "()V");

        assertTrue(result.size() >= 2, "Should include parent and child");
        assertTrue(result.stream().anyMatch(n -> n.getName().equals("com/test/Parent")));
        assertTrue(result.stream().anyMatch(n -> n.getName().equals("com/test/Child")));
    }

    @Test
    void findMethodHierarchyWithInterfaceMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        ClassFile iface = pool.createNewClass("com/test/Interface", ifaceAccess);
        ClassFile impl = pool.createNewClass("com/test/Impl", access);

        impl.addInterface("com/test/Interface");

        int methodAccess = new AccessBuilder().setPublic().setAbstract().build();
        iface.createNewMethodWithDescriptor(methodAccess, "execute", "()V");

        int implMethodAccess = new AccessBuilder().setPublic().build();
        impl.createNewMethodWithDescriptor(implMethodAccess, "execute", "()V");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Set<ClassNode> result = h.findMethodHierarchy("com/test/Interface", "execute", "()V");

        assertTrue(result.stream().anyMatch(n -> n.getName().equals("com/test/Interface")),
                "Should include interface");
        assertTrue(result.stream().anyMatch(n -> n.getName().equals("com/test/Impl")),
                "Should include implementation");
    }

    @Test
    void findMethodHierarchyReturnsEmptyForNonexistentMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Set<ClassNode> result = h.findMethodHierarchy("com/test/MyClass", "nonexistent", "()V");

        assertTrue(result.isEmpty());
    }

    // ========== getMethod Tests ==========

    @Test
    void getMethodReturnsMethodEntry() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/MyClass", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        MethodEntry method = cf.createNewMethodWithDescriptor(methodAccess, "myMethod", "()V");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        MethodEntry result = h.getMethod("com/test/MyClass", "myMethod", "()V");

        assertNotNull(result);
        assertEquals("myMethod", result.getName());
        assertEquals("()V", result.getDesc());
    }

    @Test
    void getMethodReturnsNullForNonexistentMethod() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        MethodEntry result = h.getMethod("com/test/MyClass", "nonexistent", "()V");

        assertNull(result);
    }

    @Test
    void getMethodReturnsNullForNonexistentClass() {
        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        MethodEntry result = h.getMethod("com/test/Nonexistent", "method", "()V");

        assertNull(result);
    }

    @Test
    void getMethodReturnsNullForExternalClass() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        MethodEntry result = h.getMethod("java/lang/Object", "toString", "()Ljava/lang/String;");

        assertNull(result, "External classes should return null");
    }

    // ========== Node Retrieval Tests ==========

    @Test
    void getNodeReturnsNode() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode node = h.getNode("com/test/MyClass");

        assertNotNull(node);
        assertEquals("com/test/MyClass", node.getName());
    }

    @Test
    void getNodeReturnsNullForNonexistent() {
        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        ClassNode node = h.getNode("com/test/Nonexistent");

        assertNull(node);
    }

    @Test
    void getAllNodesReturnsAllNodes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Collection<ClassNode> nodes = h.getAllNodes();

        assertTrue(nodes.size() >= 2, "Should have at least 2 classes");
        assertTrue(nodes.stream().anyMatch(n -> n.getName().equals("com/test/ClassA")));
        assertTrue(nodes.stream().anyMatch(n -> n.getName().equals("com/test/ClassB")));
    }

    @Test
    void getPoolNodesReturnsOnlyPoolNodes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Collection<ClassNode> poolNodes = h.getPoolNodes();

        assertEquals(2, poolNodes.size(), "Should only return classes in pool");
        assertTrue(poolNodes.stream().allMatch(ClassNode::isInPool));
    }

    @Test
    void getPoolNodesExcludesExternalNodes() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Collection<ClassNode> poolNodes = h.getPoolNodes();

        assertFalse(poolNodes.stream().anyMatch(n -> n.getName().equals("java/lang/Object")),
                "Should not include java/lang/Object");
    }

    // ========== Size Tests ==========

    @Test
    void sizeReturnsZeroForEmptyHierarchy() {
        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        assertEquals(0, h.size());
    }

    @Test
    void sizeReturnsCorrectCount() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);
        pool.createNewClass("com/test/ClassB", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        // Size includes classes + java/lang/Object
        assertTrue(h.size() >= 2);
    }

    // ========== Complex Hierarchy Tests ==========

    @Test
    void complexInheritanceHierarchy() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int ifaceAccess = new AccessBuilder().setPublic().setInterface().build();

        // Create interface
        ClassFile iface = pool.createNewClass("com/test/Runnable", ifaceAccess);

        // Create base class
        ClassFile base = pool.createNewClass("com/test/Base", access);

        // Create child class that extends base and implements interface
        ClassFile child = pool.createNewClass("com/test/Child", access);
        child.setSuperClassName("com/test/Base");
        child.addInterface("com/test/Runnable");

        // Create grandchild
        ClassFile grandchild = pool.createNewClass("com/test/GrandChild", access);
        grandchild.setSuperClassName("com/test/Child");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        // Verify hierarchy
        assertTrue(h.isAncestorOf("com/test/Base", "com/test/Child"));
        assertTrue(h.isAncestorOf("com/test/Base", "com/test/GrandChild"));
        assertTrue(h.isAncestorOf("com/test/Runnable", "com/test/Child"));
        assertTrue(h.isAncestorOf("com/test/Runnable", "com/test/GrandChild"));

        ClassNode grandchildNode = h.getNode("com/test/GrandChild");
        Set<ClassNode> ancestors = grandchildNode.getAllAncestors();

        assertTrue(ancestors.stream().anyMatch(n -> n.getName().equals("com/test/Child")));
        assertTrue(ancestors.stream().anyMatch(n -> n.getName().equals("com/test/Base")));
        assertTrue(ancestors.stream().anyMatch(n -> n.getName().equals("com/test/Runnable")));
    }

    @Test
    void methodOverrideAcrossMultipleLevels() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        int methodAccess = new AccessBuilder().setPublic().build();

        ClassFile base = pool.createNewClass("com/test/Base", access);
        base.createNewMethodWithDescriptor(methodAccess, "process", "(I)V");

        ClassFile middle = pool.createNewClass("com/test/Middle", access);
        middle.setSuperClassName("com/test/Base");
        middle.createNewMethodWithDescriptor(methodAccess, "process", "(I)V");

        ClassFile leaf = pool.createNewClass("com/test/Leaf", access);
        leaf.setSuperClassName("com/test/Middle");
        leaf.createNewMethodWithDescriptor(methodAccess, "process", "(I)V");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        Set<ClassNode> hierarchy = h.findMethodHierarchy("com/test/Base", "process", "(I)V");

        assertTrue(hierarchy.size() >= 3);
        assertTrue(hierarchy.stream().anyMatch(n -> n.getName().equals("com/test/Base")));
        assertTrue(hierarchy.stream().anyMatch(n -> n.getName().equals("com/test/Middle")));
        assertTrue(hierarchy.stream().anyMatch(n -> n.getName().equals("com/test/Leaf")));
    }

    // ========== Edge Cases ==========

    @Test
    void hierarchyToStringReturnsDescription() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/MyClass", access);

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);
        String result = h.toString();

        assertNotNull(result);
        assertTrue(result.contains("ClassHierarchy"));
    }

    @Test
    void rebuildHierarchyAfterChanges() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        pool.createNewClass("com/test/ClassA", access);

        ClassHierarchy h1 = ClassHierarchyBuilder.build(pool);
        int size1 = h1.size();

        pool.createNewClass("com/test/ClassB", access);
        ClassHierarchy h2 = ClassHierarchyBuilder.rebuild(pool);
        int size2 = h2.size();

        assertTrue(size2 > size1, "Rebuilt hierarchy should include new class");
    }

    @Test
    void hierarchyHandlesMethodsWithDifferentDescriptors() throws IOException {
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/Overloaded", access);

        int methodAccess = new AccessBuilder().setPublic().build();
        cf.createNewMethodWithDescriptor(methodAccess, "process", "()V");
        cf.createNewMethodWithDescriptor(methodAccess, "process", "(I)V");
        cf.createNewMethodWithDescriptor(methodAccess, "process", "(Ljava/lang/String;)V");

        ClassHierarchy h = ClassHierarchyBuilder.build(pool);

        assertNotNull(h.getMethod("com/test/Overloaded", "process", "()V"));
        assertNotNull(h.getMethod("com/test/Overloaded", "process", "(I)V"));
        assertNotNull(h.getMethod("com/test/Overloaded", "process", "(Ljava/lang/String;)V"));
        assertNull(h.getMethod("com/test/Overloaded", "process", "(Z)V"));
    }
}
