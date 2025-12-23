package com.tonic.renamer;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.*;
import com.tonic.renamer.exception.RenameException;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassHierarchyBuilder;
import com.tonic.renamer.mapping.ClassMapping;
import com.tonic.renamer.mapping.FieldMapping;
import com.tonic.renamer.mapping.MappingStore;
import com.tonic.renamer.mapping.MethodMapping;
import com.tonic.renamer.validation.ValidationResult;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")

class RenamerTest {

    private ClassPool pool;

    @BeforeEach
    void setUp() {
        pool = TestUtils.emptyPool();
    }

    private FieldEntry findField(ClassFile cf, String name) {
        return cf.getFields().stream()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private FieldEntry findField(ClassFile cf, String name, String desc) {
        return cf.getFields().stream()
                .filter(f -> f.getName().equals(name) && f.getDesc().equals(desc))
                .findFirst()
                .orElse(null);
    }

    private MethodEntry findMethod(ClassFile cf, String name) {
        return cf.getMethods().stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private MethodEntry findMethod(ClassFile cf, String name, String desc) {
        return cf.getMethods().stream()
                .filter(m -> m.getName().equals(name) && m.getDesc().equals(desc))
                .findFirst()
                .orElse(null);
    }

    @Nested
    class RenamerApiTests {

        @Test
        void constructorCreatesRenamer() {
            Renamer renamer = new Renamer(pool);
            assertNotNull(renamer);
            assertNotNull(renamer.getMappings());
            assertTrue(renamer.getMappings().isEmpty());
        }

        @Test
        void mapClassAddsMappingAndReturnsThis() {
            Renamer renamer = new Renamer(pool);
            Renamer result = renamer.mapClass("com/old/Class", "com/new/Class");
            assertSame(renamer, result);
            assertFalse(renamer.getMappings().isEmpty());
        }

        @Test
        void mapMethodAddsMappingAndReturnsThis() {
            Renamer renamer = new Renamer(pool);
            Renamer result = renamer.mapMethod("com/test/Class", "oldMethod", "()V", "newMethod");
            assertSame(renamer, result);
            assertEquals(1, renamer.getMappings().getMethodMappings().size());
        }

        @Test
        void mapMethodInHierarchyAddsMappingWithPropagateFlag() {
            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/Class", "oldMethod", "()V", "newMethod");
            MethodMapping mapping = renamer.getMappings().getMethodMappings().iterator().next();
            assertTrue(mapping.isPropagate());
        }

        @Test
        void mapFieldAddsMappingAndReturnsThis() {
            Renamer renamer = new Renamer(pool);
            Renamer result = renamer.mapField("com/test/Class", "oldField", "I", "newField");
            assertSame(renamer, result);
            assertEquals(1, renamer.getMappings().getFieldMappings().size());
        }

        @Test
        void clearRemovesAllMappingsAndReturnsThis() {
            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/old/A", "com/new/A");
            renamer.mapMethod("com/test/B", "m", "()V", "n");
            renamer.mapField("com/test/C", "f", "I", "g");

            Renamer result = renamer.clear();

            assertSame(renamer, result);
            assertTrue(renamer.getMappings().isEmpty());
        }

        @Test
        void getHierarchyReturnsHierarchy() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            pool.createNewClass("com/test/TestClass", access);

            Renamer renamer = new Renamer(pool);
            ClassHierarchy hierarchy = renamer.getHierarchy();

            assertNotNull(hierarchy);
            assertNotNull(hierarchy.getNode("com/test/TestClass"));
        }

        @Test
        void applyWithNoMappingsDoesNothing() {
            Renamer renamer = new Renamer(pool);
            renamer.apply();
        }

        @Test
        void applyUnsafeWithNoMappingsDoesNothing() {
            Renamer renamer = new Renamer(pool);
            renamer.applyUnsafe();
        }

        @Test
        void validateReturnsValidResultForEmptyMappings() {
            Renamer renamer = new Renamer(pool);
            ValidationResult result = renamer.validate();
            assertNotNull(result);
        }
    }

    @Nested
    class RenamerContextTests {

        @Test
        void contextCreatesWithPoolAndMappings() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            pool.createNewClass("com/test/MyClass", access);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            assertNotNull(context);
            assertSame(pool, context.getClassPool());
            assertSame(mappings, context.getMappings());
            assertNotNull(context.getHierarchy());
            assertNotNull(context.getDescriptorRemapper());
            assertNotNull(context.getSignatureRemapper());
        }

        @Test
        void getAllClassesReturnsPoolClasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            pool.createNewClass("com/test/ClassA", access);
            pool.createNewClass("com/test/ClassB", access);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            assertEquals(2, context.getAllClasses().size());
        }

        @Test
        void getClassReturnsClassByName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            assertSame(cf, context.getClass("com/test/MyClass"));
        }

        @Test
        void getClassReturnsNullForUnknownClass() throws IOException {
            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            assertNull(context.getClass("com/unknown/Class"));
        }

        @Test
        void getClassLooksUpByNewNameAfterMapping() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/old/MyClass", access);

            MappingStore mappings = new MappingStore();
            mappings.addClassMapping(new ClassMapping("com/old/MyClass", "com/new/MyClass"));
            RenamerContext context = new RenamerContext(pool, mappings);

            assertSame(cf, context.getClass("com/old/MyClass"));
        }

        @Test
        void rebuildHierarchyUpdatesHierarchy() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            pool.createNewClass("com/test/ClassA", access);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);
            ClassHierarchy original = context.getHierarchy();

            pool.createNewClass("com/test/ClassB", access);
            context.rebuildHierarchy();

            assertNotSame(original, context.getHierarchy());
        }

        @Test
        void countNameAndTypeReferencesCountsCorrectly() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/TestClass", access);
            ConstPool cp = cf.getConstPool();

            Utf8Item nameUtf8 = cp.findOrAddUtf8("testMethod");
            Utf8Item descUtf8 = cp.findOrAddUtf8("()V");
            int nameIndex = cp.getIndexOf(nameUtf8);
            int descIndex = cp.getIndexOf(descUtf8);
            NameAndTypeRefItem nat = cp.findOrAddNameAndType(nameIndex, descIndex);
            int natIndex = cp.getIndexOf(nat);

            ClassRefItem classRef = cp.findOrAddClass("com/test/TestClass");
            cp.findOrAddMethodRef(cp.getIndexOf(classRef), natIndex);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            int count = context.countNameAndTypeReferences(cp, natIndex);
            assertEquals(1, count);
        }

        @Test
        void isSharedNameAndTypeReturnsFalseForSingleReference() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/TestClass", access);
            ConstPool cp = cf.getConstPool();

            Utf8Item nameUtf8 = cp.findOrAddUtf8("testMethod");
            Utf8Item descUtf8 = cp.findOrAddUtf8("()V");
            int nameIndex = cp.getIndexOf(nameUtf8);
            int descIndex = cp.getIndexOf(descUtf8);
            NameAndTypeRefItem nat = cp.findOrAddNameAndType(nameIndex, descIndex);
            int natIndex = cp.getIndexOf(nat);

            ClassRefItem classRef = cp.findOrAddClass("com/test/TestClass");
            cp.findOrAddMethodRef(cp.getIndexOf(classRef), natIndex);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            assertFalse(context.isSharedNameAndType(cp, natIndex));
        }

        @Test
        void findMatchingNameAndTypesFindsMatches() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/TestClass", access);
            ConstPool cp = cf.getConstPool();

            Utf8Item nameUtf8 = cp.findOrAddUtf8("myMethod");
            Utf8Item descUtf8 = cp.findOrAddUtf8("(I)V");
            int nameIndex = cp.getIndexOf(nameUtf8);
            int descIndex = cp.getIndexOf(descUtf8);
            cp.findOrAddNameAndType(nameIndex, descIndex);

            MappingStore mappings = new MappingStore();
            RenamerContext context = new RenamerContext(pool, mappings);

            Set<Integer> matches = context.findMatchingNameAndTypes(cf, "myMethod", "(I)V");
            assertEquals(1, matches.size());
        }
    }

    @Nested
    class ClassRenamerTests {

        @Test
        void renamesClassInConstantPool() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/old/MyClass", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/old/MyClass", "com/new/RenamedClass");
            renamer.applyUnsafe();

            assertEquals("com/new/RenamedClass", cf.getClassName());
        }

        @Test
        void renamesMultipleClasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf1 = pool.createNewClass("com/old/ClassA", access);
            ClassFile cf2 = pool.createNewClass("com/old/ClassB", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/old/ClassA", "com/new/RenamedA");
            renamer.mapClass("com/old/ClassB", "com/new/RenamedB");
            renamer.applyUnsafe();

            assertEquals("com/new/RenamedA", cf1.getClassName());
            assertEquals("com/new/RenamedB", cf2.getClassName());
        }

        @Test
        void updatesFieldDescriptorsContainingRenamedClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/Container", access);
            cf.createNewField(access, "ref", "Lcom/old/Referenced;", Collections.emptyList());
            pool.createNewClass("com/old/Referenced", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/old/Referenced", "com/new/Referenced");
            renamer.applyUnsafe();

            FieldEntry field = cf.getFields().get(0);
            assertEquals("Lcom/new/Referenced;", field.getDesc());
        }

        @Test
        void updatesMethodDescriptorsContainingRenamedClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/Service", access);
            cf.createNewMethodWithDescriptor(access, "process", "(Lcom/old/Input;)Lcom/old/Output;");
            pool.createNewClass("com/old/Input", access);
            pool.createNewClass("com/old/Output", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/old/Input", "com/new/Input");
            renamer.mapClass("com/old/Output", "com/new/Output");
            renamer.applyUnsafe();

            MethodEntry method = findMethod(cf, "process");
            assertEquals("(Lcom/new/Input;)Lcom/new/Output;", method.getDesc());
        }
    }

    @Nested
    class MethodRenamerTests {

        @Test
        void renamesMethodDeclaration() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewMethodWithDescriptor(access, "oldMethod", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/MyClass", "oldMethod", "()V", "newMethod");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf, "newMethod"));
            assertNull(findMethod(cf, "oldMethod"));
        }

        @Test
        void renamesMethodWithParameters() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewMethodWithDescriptor(access, "process", "(ILjava/lang/String;)Z");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/MyClass", "process", "(ILjava/lang/String;)Z", "handle");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf, "handle"));
            assertEquals("(ILjava/lang/String;)Z", findMethod(cf, "handle").getDesc());
        }

        @Test
        void renamesOnlyMatchingDescriptor() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewMethodWithDescriptor(access, "overloaded", "()V");
            cf.createNewMethodWithDescriptor(access, "overloaded", "(I)V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/MyClass", "overloaded", "(I)V", "overloadedInt");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf, "overloaded"));
            assertNotNull(findMethod(cf, "overloadedInt"));
            assertEquals("()V", findMethod(cf, "overloaded").getDesc());
            assertEquals("(I)V", findMethod(cf, "overloadedInt").getDesc());
        }

        @Test
        void hierarchyRenameAffectsSubclasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile base = pool.createNewClass("com/test/Base", access);
            base.createNewMethodWithDescriptor(access, "process", "()V");

            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/Base");
            child.createNewMethodWithDescriptor(access, "process", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/Base", "process", "()V", "handle");
            renamer.applyUnsafe();

            assertNotNull(findMethod(base, "handle"));
            assertNotNull(findMethod(child, "handle"));
        }
    }

    @Nested
    class FieldRenamerTests {

        @Test
        void renamesFieldDeclaration() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewField(access, "oldField", "I", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/MyClass", "oldField", "I", "newField");
            renamer.applyUnsafe();

            FieldEntry field = findField(cf, "newField");
            assertNotNull(field);
            assertEquals("I", field.getDesc());
            assertNull(findField(cf, "oldField"));
        }

        @Test
        void renamesFieldWithObjectType() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewField(access, "data", "Ljava/lang/String;", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/MyClass", "data", "Ljava/lang/String;", "content");
            renamer.applyUnsafe();

            FieldEntry field = findField(cf, "content");
            assertNotNull(field);
            assertEquals("Ljava/lang/String;", field.getDesc());
        }

        @Test
        void renamesOnlyMatchingDescriptorField() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewField(access, "value", "I", Collections.emptyList());
            cf.createNewField(access, "value", "J", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/MyClass", "value", "J", "longValue");
            renamer.applyUnsafe();

            FieldEntry intField = findField(cf, "value", "I");
            FieldEntry longField = findField(cf, "longValue");

            assertNotNull(intField);
            assertNotNull(longField);
            assertEquals("I", intField.getDesc());
            assertEquals("J", longField.getDesc());
        }

        @Test
        void renamesStaticField() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewField(access, "CONSTANT", "I", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/MyClass", "CONSTANT", "I", "VALUE");
            renamer.applyUnsafe();

            assertNotNull(findField(cf, "VALUE"));
            assertNull(findField(cf, "CONSTANT"));
        }
    }

    @Nested
    class MappingStoreTests {

        @Test
        void isEmptyReturnsTrueInitially() {
            MappingStore store = new MappingStore();
            assertTrue(store.isEmpty());
        }

        @Test
        void isEmptyReturnsFalseAfterAddingClassMapping() {
            MappingStore store = new MappingStore();
            store.addClassMapping(new ClassMapping("old", "new"));
            assertFalse(store.isEmpty());
        }

        @Test
        void getClassMappingReturnsNullForUnmapped() {
            MappingStore store = new MappingStore();
            assertNull(store.getClassMapping("unknown"));
        }

        @Test
        void getClassMappingReturnsNewName() {
            MappingStore store = new MappingStore();
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class"));
            assertEquals("com/new/Class", store.getClassMapping("com/old/Class"));
        }

        @Test
        void clearRemovesAllMappings() {
            MappingStore store = new MappingStore();
            store.addClassMapping(new ClassMapping("old", "new"));
            store.addMethodMapping(new MethodMapping("owner", "m", "()V", "n", false));
            store.addFieldMapping(new FieldMapping("owner", "f", "I", "g"));

            store.clear();

            assertTrue(store.isEmpty());
            assertTrue(store.getMethodMappings().isEmpty());
            assertTrue(store.getFieldMappings().isEmpty());
        }
    }

    @Nested
    class ClassMappingTests {

        @Test
        void constructorSetsFields() {
            ClassMapping mapping = new ClassMapping("com/old/Name", "com/new/Name");
            assertEquals("com/old/Name", mapping.getOldName());
            assertEquals("com/new/Name", mapping.getNewName());
        }
    }

    @Nested
    class MethodMappingTests {

        @Test
        void constructorSetsAllFields() {
            MethodMapping mapping = new MethodMapping("com/test/Owner", "oldMethod", "(I)V", "newMethod", true);
            assertEquals("com/test/Owner", mapping.getOwner());
            assertEquals("oldMethod", mapping.getOldName());
            assertEquals("(I)V", mapping.getDescriptor());
            assertEquals("newMethod", mapping.getNewName());
            assertTrue(mapping.isPropagate());
        }

        @Test
        void propagateFalseWhenNotSet() {
            MethodMapping mapping = new MethodMapping("owner", "old", "()V", "new", false);
            assertFalse(mapping.isPropagate());
        }
    }

    @Nested
    class FieldMappingTests {

        @Test
        void constructorSetsAllFields() {
            FieldMapping mapping = new FieldMapping("com/test/Owner", "oldField", "Ljava/lang/String;", "newField");
            assertEquals("com/test/Owner", mapping.getOwner());
            assertEquals("oldField", mapping.getOldName());
            assertEquals("Ljava/lang/String;", mapping.getDescriptor());
            assertEquals("newField", mapping.getNewName());
        }
    }

    @Nested
    class FindOverridesTests {

        @Test
        void findOverridesReturnsEmptyForNonExistentMethod() {
            Renamer renamer = new Renamer(pool);
            Set<MethodEntry> overrides = renamer.findOverrides("com/unknown/Class", "method", "()V");
            assertTrue(overrides.isEmpty());
        }

        @Test
        void findOverridesReturnsMethodInClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewMethodWithDescriptor(access, "process", "()V");

            Renamer renamer = new Renamer(pool);
            Set<MethodEntry> overrides = renamer.findOverrides("com/test/MyClass", "process", "()V");

            assertEquals(1, overrides.size());
            assertEquals("process", overrides.iterator().next().getName());
        }

        @Test
        void findOverridesIncludesSubclassMethods() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile base = pool.createNewClass("com/test/Base", access);
            base.createNewMethodWithDescriptor(access, "process", "()V");

            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/Base");
            child.createNewMethodWithDescriptor(access, "process", "()V");

            Renamer renamer = new Renamer(pool);
            Set<MethodEntry> overrides = renamer.findOverrides("com/test/Base", "process", "()V");

            assertEquals(2, overrides.size());
        }
    }

    @Nested
    class FieldReferenceTests {

        @Test
        void renamesFieldGetFieldAccess() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile owner = pool.createNewClass("com/test/Owner", access);
            owner.createNewField(access, "myField", "I", Collections.emptyList());

            ClassFile accessor = pool.createNewClass("com/test/Accessor", access);
            ConstPool cp = accessor.getConstPool();

            cp.findOrAddFieldRef("com/test/Owner", "myField", "I");

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/Owner", "myField", "I", "renamedField");
            renamer.applyUnsafe();

            FieldEntry field = findField(owner, "renamedField");
            assertNotNull(field);
            assertEquals("I", field.getDesc());
        }

        @Test
        void renamesFieldWithInheritance() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile parent = pool.createNewClass("com/test/Parent", access);
            parent.createNewField(access, "inherited", "Ljava/lang/String;", Collections.emptyList());

            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/Parent");

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/Parent", "inherited", "Ljava/lang/String;", "parentField");
            renamer.applyUnsafe();

            FieldEntry field = findField(parent, "parentField");
            assertNotNull(field);
            assertEquals("Ljava/lang/String;", field.getDesc());
        }

        @Test
        void renamesFieldWithShadowing() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile parent = pool.createNewClass("com/test/Parent", access);
            parent.createNewField(access, "field", "I", Collections.emptyList());

            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/Parent");
            child.createNewField(access, "field", "J", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/Parent", "field", "I", "intField");
            renamer.applyUnsafe();

            FieldEntry parentField = findField(parent, "intField", "I");
            FieldEntry childField = findField(child, "field", "J");

            assertNotNull(parentField);
            assertNotNull(childField);
            assertEquals("I", parentField.getDesc());
            assertEquals("J", childField.getDesc());
        }

        @Test
        void renamesMultipleFieldsWithSameType() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/Container", access);
            cf.createNewField(access, "first", "I", Collections.emptyList());
            cf.createNewField(access, "second", "I", Collections.emptyList());
            cf.createNewField(access, "third", "I", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/Container", "first", "I", "renamed1");
            renamer.mapField("com/test/Container", "second", "I", "renamed2");
            renamer.applyUnsafe();

            assertNotNull(findField(cf, "renamed1", "I"));
            assertNotNull(findField(cf, "renamed2", "I"));
            assertNotNull(findField(cf, "third", "I"));
        }

        @Test
        void renamesArrayTypeField() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/Container", access);
            cf.createNewField(access, "items", "[Ljava/lang/String;", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/Container", "items", "[Ljava/lang/String;", "elements");
            renamer.applyUnsafe();

            FieldEntry field = findField(cf, "elements");
            assertNotNull(field);
            assertEquals("[Ljava/lang/String;", field.getDesc());
        }
    }

    @Nested
    class MethodReferenceTests {

        @Test
        void renamesMethodWithInterfaceCall() throws IOException {
            int access = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile iface = pool.createNewClass("com/test/MyInterface", access);
            iface.createNewMethodWithDescriptor(access, "execute", "()V");

            int implAccess = new AccessBuilder().setPublic().build();
            ClassFile impl = pool.createNewClass("com/test/Implementation", implAccess);
            impl.addInterface("com/test/MyInterface");
            impl.createNewMethodWithDescriptor(implAccess, "execute", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/MyInterface", "execute", "()V", "run");
            renamer.applyUnsafe();

            assertNotNull(findMethod(iface, "run"));
            assertNotNull(findMethod(impl, "run"));
        }

        @Test
        void renamesMethodWithSuperCall() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile parent = pool.createNewClass("com/test/Parent", access);
            parent.createNewMethodWithDescriptor(access, "init", "()V");

            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/Parent");
            child.createNewMethodWithDescriptor(access, "init", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/Parent", "init", "()V", "initialize");
            renamer.applyUnsafe();

            assertNotNull(findMethod(parent, "initialize"));
            assertNotNull(findMethod(child, "initialize"));
        }

        @Test
        void renamesMethodWithOverloading() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/Service", access);
            cf.createNewMethodWithDescriptor(access, "send", "(Ljava/lang/String;)V");
            cf.createNewMethodWithDescriptor(access, "send", "(I)V");
            cf.createNewMethodWithDescriptor(access, "send", "([B)V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/Service", "send", "(I)V", "sendInt");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf, "send", "(Ljava/lang/String;)V"));
            assertNotNull(findMethod(cf, "sendInt", "(I)V"));
            assertNotNull(findMethod(cf, "send", "([B)V"));
        }

        @Test
        void doesNotRenameConstructors() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewMethodWithDescriptor(access, "<init>", "()V");
            cf.createNewMethodWithDescriptor(access, "<init>", "(I)V");

            assertNotNull(findMethod(cf, "<init>", "()V"));
            assertNotNull(findMethod(cf, "<init>", "(I)V"));
        }

        @Test
        void renamesStaticMethod() throws IOException {
            int access = new AccessBuilder().setPublic().setStatic().build();
            ClassFile cf = pool.createNewClass("com/test/Utils", access);
            cf.createNewMethodWithDescriptor(access, "helper", "(I)I");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/Utils", "helper", "(I)I", "utilHelper");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf, "utilHelper"));
            assertNull(findMethod(cf, "helper"));
        }

        @Test
        void renamesPrivateMethod() throws IOException {
            int access = new AccessBuilder().setPrivate().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", new AccessBuilder().setPublic().build());
            cf.createNewMethodWithDescriptor(access, "internal", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/MyClass", "internal", "()V", "privateInternal");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf, "privateInternal"));
        }

        @Test
        void renamesMultipleLevelsOfInheritance() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile grandparent = pool.createNewClass("com/test/GrandParent", access);
            grandparent.createNewMethodWithDescriptor(access, "method", "()V");

            ClassFile parent = pool.createNewClass("com/test/Parent", access);
            parent.setSuperClassName("com/test/GrandParent");
            parent.createNewMethodWithDescriptor(access, "method", "()V");

            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/Parent");
            child.createNewMethodWithDescriptor(access, "method", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/GrandParent", "method", "()V", "renamedMethod");
            renamer.applyUnsafe();

            assertNotNull(findMethod(grandparent, "renamedMethod"));
            assertNotNull(findMethod(parent, "renamedMethod"));
            assertNotNull(findMethod(child, "renamedMethod"));
        }

        @Test
        void doesNotRenameMethodInUnrelatedClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf1 = pool.createNewClass("com/test/ClassA", access);
            cf1.createNewMethodWithDescriptor(access, "process", "()V");

            ClassFile cf2 = pool.createNewClass("com/test/ClassB", access);
            cf2.createNewMethodWithDescriptor(access, "process", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/ClassA", "process", "()V", "handle");
            renamer.applyUnsafe();

            assertNotNull(findMethod(cf1, "handle"));
            assertNotNull(findMethod(cf2, "process"));
            assertNull(findMethod(cf2, "handle"));
        }

        @Test
        void renamesMethodWithMethodRefs() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile target = pool.createNewClass("com/test/Target", access);
            target.createNewMethodWithDescriptor(access, "oldMethod", "(I)I");

            ClassFile caller = pool.createNewClass("com/test/Caller", access);
            ConstPool cp = caller.getConstPool();
            cp.findOrAddMethodRef("com/test/Target", "oldMethod", "(I)I");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethod("com/test/Target", "oldMethod", "(I)I", "newMethod");
            renamer.applyUnsafe();

            assertNotNull(findMethod(target, "newMethod"));
            assertNull(findMethod(target, "oldMethod"));
        }

        @Test
        void renamesInterfaceMethod() throws IOException {
            int ifaceAccess = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile iface = pool.createNewClass("com/test/MyInterface", ifaceAccess);
            iface.createNewMethodWithDescriptor(ifaceAccess, "action", "(Ljava/lang/String;)V");

            ClassFile caller = pool.createNewClass("com/test/Caller", new AccessBuilder().setPublic().build());
            ConstPool cp = caller.getConstPool();
            cp.findOrAddInterfaceRef("com/test/MyInterface", "action", "(Ljava/lang/String;)V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/MyInterface", "action", "(Ljava/lang/String;)V", "perform");
            renamer.applyUnsafe();

            assertNotNull(findMethod(iface, "perform"));
            assertNull(findMethod(iface, "action"));
        }

        @Test
        void renamesMultipleInterfaceMethods() throws IOException {
            int ifaceAccess = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile iface = pool.createNewClass("com/test/Service", ifaceAccess);
            iface.createNewMethodWithDescriptor(ifaceAccess, "start", "()V");
            iface.createNewMethodWithDescriptor(ifaceAccess, "stop", "()V");
            iface.createNewMethodWithDescriptor(ifaceAccess, "restart", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/Service", "start", "()V", "begin");
            renamer.mapMethodInHierarchy("com/test/Service", "stop", "()V", "end");
            renamer.applyUnsafe();

            assertNotNull(findMethod(iface, "begin"));
            assertNotNull(findMethod(iface, "end"));
            assertNotNull(findMethod(iface, "restart"));
        }

        @Test
        void renamesMethodAcrossMultipleInterfaces() throws IOException {
            int ifaceAccess = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile iface1 = pool.createNewClass("com/test/Interface1", ifaceAccess);
            iface1.createNewMethodWithDescriptor(ifaceAccess, "common", "()V");

            ClassFile iface2 = pool.createNewClass("com/test/Interface2", ifaceAccess);
            iface2.setSuperClassName("com/test/Interface1");
            iface2.createNewMethodWithDescriptor(ifaceAccess, "common", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapMethodInHierarchy("com/test/Interface1", "common", "()V", "shared");
            renamer.applyUnsafe();

            assertNotNull(findMethod(iface1, "shared"));
            assertNotNull(findMethod(iface2, "shared"));
        }
    }

    @Nested
    class ClassReferenceTests {

        @Test
        void renamesInnerClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile outer = pool.createNewClass("com/test/Outer", access);
            ClassFile inner = pool.createNewClass("com/test/Outer$Inner", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/Outer$Inner", "com/test/Outer$Renamed");
            renamer.applyUnsafe();

            assertEquals("com/test/Outer$Renamed", inner.getClassName());
        }

        @Test
        void renamesClassInMethodDescriptor() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile service = pool.createNewClass("com/test/Service", access);
            service.createNewMethodWithDescriptor(access, "process",
                "(Lcom/test/Request;)Lcom/test/Response;");

            pool.createNewClass("com/test/Request", access);
            pool.createNewClass("com/test/Response", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/Request", "com/test/InputRequest");
            renamer.mapClass("com/test/Response", "com/test/OutputResponse");
            renamer.applyUnsafe();

            MethodEntry method = findMethod(service, "process");
            assertEquals("(Lcom/test/InputRequest;)Lcom/test/OutputResponse;", method.getDesc());
        }

        @Test
        void renamesClassInFieldDescriptor() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile container = pool.createNewClass("com/test/Container", access);
            container.createNewField(access, "data", "Lcom/test/Data;", Collections.emptyList());
            pool.createNewClass("com/test/Data", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/Data", "com/test/RenamedData");
            renamer.applyUnsafe();

            FieldEntry field = findField(container, "data");
            assertEquals("Lcom/test/RenamedData;", field.getDesc());
        }

        @Test
        void renamesClassWithArrayDescriptor() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile container = pool.createNewClass("com/test/Container", access);
            container.createNewField(access, "items", "[Lcom/test/Item;", Collections.emptyList());
            pool.createNewClass("com/test/Item", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/Item", "com/test/Element");
            renamer.applyUnsafe();

            FieldEntry field = findField(container, "items");
            assertEquals("[Lcom/test/Element;", field.getDesc());
        }

        @Test
        void renamesClassWithMultidimensionalArray() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile container = pool.createNewClass("com/test/Container", access);
            container.createNewField(access, "matrix", "[[Lcom/test/Cell;", Collections.emptyList());
            pool.createNewClass("com/test/Cell", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/Cell", "com/test/Node");
            renamer.applyUnsafe();

            FieldEntry field = findField(container, "matrix");
            assertEquals("[[Lcom/test/Node;", field.getDesc());
        }

        @Test
        void renamesClassInComplexMethodDescriptor() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile service = pool.createNewClass("com/test/Service", access);
            service.createNewMethodWithDescriptor(access, "complex",
                "(Lcom/test/A;Lcom/test/B;ILcom/test/C;)Ljava/util/List;");

            pool.createNewClass("com/test/A", access);
            pool.createNewClass("com/test/B", access);
            pool.createNewClass("com/test/C", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/A", "com/test/Alpha");
            renamer.mapClass("com/test/B", "com/test/Beta");
            renamer.mapClass("com/test/C", "com/test/Gamma");
            renamer.applyUnsafe();

            MethodEntry method = findMethod(service, "complex");
            assertEquals("(Lcom/test/Alpha;Lcom/test/Beta;ILcom/test/Gamma;)Ljava/util/List;",
                method.getDesc());
        }

        @Test
        void renamesClassAndUpdatesSuperClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile parent = pool.createNewClass("com/test/OldParent", access);
            ClassFile child = pool.createNewClass("com/test/Child", access);
            child.setSuperClassName("com/test/OldParent");

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/OldParent", "com/test/NewParent");
            renamer.applyUnsafe();

            assertEquals("com/test/NewParent", parent.getClassName());
        }

        @Test
        void renamesClassAndUpdatesInterface() throws IOException {
            int ifaceAccess = new AccessBuilder().setPublic().setInterface().setAbstract().build();
            ClassFile iface = pool.createNewClass("com/test/OldInterface", ifaceAccess);

            int access = new AccessBuilder().setPublic().build();
            ClassFile impl = pool.createNewClass("com/test/Implementation", access);
            impl.addInterface("com/test/OldInterface");

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/OldInterface", "com/test/NewInterface");
            renamer.applyUnsafe();

            assertEquals("com/test/NewInterface", iface.getClassName());
        }

        @Test
        void renamesMultipleClassesSimultaneously() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf1 = pool.createNewClass("com/test/ClassA", access);
            ClassFile cf2 = pool.createNewClass("com/test/ClassB", access);
            ClassFile cf3 = pool.createNewClass("com/test/ClassC", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/ClassA", "com/test/RenamedA");
            renamer.mapClass("com/test/ClassB", "com/test/RenamedB");
            renamer.mapClass("com/test/ClassC", "com/test/RenamedC");
            renamer.applyUnsafe();

            assertEquals("com/test/RenamedA", cf1.getClassName());
            assertEquals("com/test/RenamedB", cf2.getClassName());
            assertEquals("com/test/RenamedC", cf3.getClassName());
        }

        @Test
        void renamesClassWithPackageChange() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/old/pkg/MyClass", access);

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/old/pkg/MyClass", "com/new/pkg/MyClass");
            renamer.applyUnsafe();

            assertEquals("com/new/pkg/MyClass", cf.getClassName());
        }
    }

    @Nested
    class IntegrationTests {

        @Test
        void renameClassFieldAndMethod() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/OldClass", access);
            cf.createNewField(access, "oldField", "I", Collections.emptyList());
            cf.createNewMethodWithDescriptor(access, "oldMethod", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/OldClass", "com/test/NewClass");
            renamer.mapField("com/test/OldClass", "oldField", "I", "newField");
            renamer.mapMethod("com/test/OldClass", "oldMethod", "()V", "newMethod");
            renamer.applyUnsafe();

            assertEquals("com/test/NewClass", cf.getClassName());
            assertNotNull(findField(cf, "newField"));
            assertNotNull(findMethod(cf, "newMethod"));
        }

        @Test
        void renameWithCrossReferences() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf1 = pool.createNewClass("com/test/ClassA", access);
            cf1.createNewField(access, "ref", "Lcom/test/ClassB;", Collections.emptyList());

            ClassFile cf2 = pool.createNewClass("com/test/ClassB", access);
            cf2.createNewMethodWithDescriptor(access, "process", "(Lcom/test/ClassA;)V");

            Renamer renamer = new Renamer(pool);
            renamer.mapClass("com/test/ClassA", "com/test/RenamedA");
            renamer.mapClass("com/test/ClassB", "com/test/RenamedB");
            renamer.applyUnsafe();

            assertEquals("com/test/RenamedA", cf1.getClassName());
            assertEquals("com/test/RenamedB", cf2.getClassName());

            FieldEntry field = findField(cf1, "ref");
            assertEquals("Lcom/test/RenamedB;", field.getDesc());

            MethodEntry method = findMethod(cf2, "process");
            assertEquals("(Lcom/test/RenamedA;)V", method.getDesc());
        }

        @Test
        void clearAndReapplyMappings() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/MyClass", access);
            cf.createNewField(access, "field", "I", Collections.emptyList());

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/MyClass", "field", "I", "renamed1");
            renamer.clear();
            renamer.mapField("com/test/MyClass", "field", "I", "renamed2");
            renamer.applyUnsafe();

            assertNull(findField(cf, "renamed1"));
            assertNotNull(findField(cf, "renamed2"));
        }

        @Test
        void validateEmptyMappingsIsValid() {
            Renamer renamer = new Renamer(pool);
            ValidationResult result = renamer.validate();
            assertTrue(result.getErrors().isEmpty());
        }

        @Test
        void renameMultipleFieldsAndMethods() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile cf = pool.createNewClass("com/test/Service", access);
            cf.createNewField(access, "counter", "I", Collections.emptyList());
            cf.createNewField(access, "name", "Ljava/lang/String;", Collections.emptyList());
            cf.createNewMethodWithDescriptor(access, "increment", "()V");
            cf.createNewMethodWithDescriptor(access, "decrement", "()V");

            Renamer renamer = new Renamer(pool);
            renamer.mapField("com/test/Service", "counter", "I", "count");
            renamer.mapField("com/test/Service", "name", "Ljava/lang/String;", "identifier");
            renamer.mapMethod("com/test/Service", "increment", "()V", "inc");
            renamer.mapMethod("com/test/Service", "decrement", "()V", "dec");
            renamer.applyUnsafe();

            assertNotNull(findField(cf, "count"));
            assertNotNull(findField(cf, "identifier"));
            assertNotNull(findMethod(cf, "inc"));
            assertNotNull(findMethod(cf, "dec"));
        }
    }
}
