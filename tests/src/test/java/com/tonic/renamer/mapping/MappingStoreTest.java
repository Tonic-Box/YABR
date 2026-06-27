package com.tonic.renamer.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class MappingStoreTest {

    private MappingStore store;

    @BeforeEach
    void setUp() {
        store = new MappingStore();
    }

    @Nested
    class ClassMappingTests {

        @Test
        void addAndRetrieveClassMapping() {
            ClassMapping mapping = new ClassMapping("com/old/Class", "com/new/Class");
            store.addClassMapping(mapping);

            assertEquals("com/new/Class", store.getClassMapping("com/old/Class"));
        }

        @Test
        void getClassMappingReturnsNullForNonExistent() {
            assertNull(store.getClassMapping("com/nonexistent/Class"));
        }

        @Test
        void hasClassMappingReturnsTrueWhenExists() {
            ClassMapping mapping = new ClassMapping("com/old/Class", "com/new/Class");
            store.addClassMapping(mapping);

            assertTrue(store.hasClassMapping("com/old/Class"));
        }

        @Test
        void hasClassMappingReturnsFalseWhenNotExists() {
            assertFalse(store.hasClassMapping("com/nonexistent/Class"));
        }

        @Test
        void replacesExistingClassMapping() {
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class1"));
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class2"));

            assertEquals("com/new/Class2", store.getClassMapping("com/old/Class"));
        }

        @Test
        void getClassMappingsReturnsAllMappings() {
            store.addClassMapping(new ClassMapping("com/old/ClassA", "com/new/ClassA"));
            store.addClassMapping(new ClassMapping("com/old/ClassB", "com/new/ClassB"));

            Collection<ClassMapping> mappings = store.getClassMappings();
            assertEquals(2, mappings.size());
        }

        @Test
        void getClassMappingsIsUnmodifiable() {
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class"));
            Collection<ClassMapping> mappings = store.getClassMappings();

            assertThrows(UnsupportedOperationException.class, () ->
                mappings.add(new ClassMapping("other", "other2"))
            );
        }
    }

    @Nested
    class MethodMappingTests {

        @Test
        void addAndRetrieveMethodMapping() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle"
            );
            store.addMethodMapping(mapping);

            MethodMapping retrieved = store.getMethodMapping(
                "com/example/Service", "process", "(I)V"
            );
            assertNotNull(retrieved);
            assertEquals("handle", retrieved.getNewName());
        }

        @Test
        void getMethodMappingReturnsNullForNonExistent() {
            assertNull(store.getMethodMapping("com/example/Class", "nonexistent", "()V"));
        }

        @Test
        void hasMethodMappingReturnsTrueWhenExists() {
            MethodMapping mapping = new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle"
            );
            store.addMethodMapping(mapping);

            assertTrue(store.hasMethodMapping("com/example/Service", "process", "(I)V"));
        }

        @Test
        void hasMethodMappingReturnsFalseWhenNotExists() {
            assertFalse(store.hasMethodMapping("com/example/Class", "nonexistent", "()V"));
        }

        @Test
        void methodMappingKeyIncludesDescriptor() {
            store.addMethodMapping(new MethodMapping(
                "com/example/Class", "method", "(I)V", "renamed1"
            ));
            store.addMethodMapping(new MethodMapping(
                "com/example/Class", "method", "(J)V", "renamed2"
            ));

            assertEquals("renamed1",
                store.getMethodMapping("com/example/Class", "method", "(I)V").getNewName());
            assertEquals("renamed2",
                store.getMethodMapping("com/example/Class", "method", "(J)V").getNewName());
        }

        @Test
        void getMethodMappingsReturnsAllMappings() {
            store.addMethodMapping(new MethodMapping(
                "com/example/ClassA", "method1", "()V", "renamed1"
            ));
            store.addMethodMapping(new MethodMapping(
                "com/example/ClassB", "method2", "()V", "renamed2"
            ));

            Collection<MethodMapping> mappings = store.getMethodMappings();
            assertEquals(2, mappings.size());
        }
    }

    @Nested
    class FieldMappingTests {

        @Test
        void addAndRetrieveFieldMapping() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;", "content"
            );
            store.addFieldMapping(mapping);

            FieldMapping retrieved = store.getFieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;"
            );
            assertNotNull(retrieved);
            assertEquals("content", retrieved.getNewName());
        }

        @Test
        void getFieldMappingReturnsNullForNonExistent() {
            assertNull(store.getFieldMapping("com/example/Class", "nonexistent", "I"));
        }

        @Test
        void hasFieldMappingReturnsTrueWhenExists() {
            FieldMapping mapping = new FieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;", "content"
            );
            store.addFieldMapping(mapping);

            assertTrue(store.hasFieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;"
            ));
        }

        @Test
        void hasFieldMappingReturnsFalseWhenNotExists() {
            assertFalse(store.hasFieldMapping("com/example/Class", "nonexistent", "I"));
        }

        @Test
        void fieldMappingKeyIncludesDescriptor() {
            store.addFieldMapping(new FieldMapping(
                "com/example/Class", "field", "I", "renamedInt"
            ));
            store.addFieldMapping(new FieldMapping(
                "com/example/Class", "field", "J", "renamedLong"
            ));

            assertEquals("renamedInt",
                store.getFieldMapping("com/example/Class", "field", "I").getNewName());
            assertEquals("renamedLong",
                store.getFieldMapping("com/example/Class", "field", "J").getNewName());
        }

        @Test
        void getFieldMappingsReturnsAllMappings() {
            store.addFieldMapping(new FieldMapping(
                "com/example/ClassA", "field1", "I", "renamed1"
            ));
            store.addFieldMapping(new FieldMapping(
                "com/example/ClassB", "field2", "J", "renamed2"
            ));

            Collection<FieldMapping> mappings = store.getFieldMappings();
            assertEquals(2, mappings.size());
        }
    }

    @Nested
    class GeneralOperationsTests {

        @Test
        void sizeCountsAllMappings() {
            store.addClassMapping(new ClassMapping("com/old/ClassA", "com/new/ClassA"));
            store.addMethodMapping(new MethodMapping(
                "com/example/Class", "method", "()V", "renamed"
            ));
            store.addFieldMapping(new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            ));

            assertEquals(3, store.size());
        }

        @Test
        void isEmptyReturnsTrueForNewStore() {
            assertTrue(store.isEmpty());
        }

        @Test
        void isEmptyReturnsFalseWhenMappingsExist() {
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class"));
            assertFalse(store.isEmpty());
        }

        @Test
        void clearRemovesAllMappings() {
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class"));
            store.addMethodMapping(new MethodMapping(
                "com/example/Class", "method", "()V", "renamed"
            ));
            store.addFieldMapping(new FieldMapping(
                "com/example/Class", "field", "I", "renamed"
            ));

            store.clear();

            assertTrue(store.isEmpty());
            assertEquals(0, store.size());
            assertNull(store.getClassMapping("com/old/Class"));
        }

        @Test
        void toStringContainsSizes() {
            store.addClassMapping(new ClassMapping("com/old/Class", "com/new/Class"));
            store.addMethodMapping(new MethodMapping(
                "com/example/Class", "method", "()V", "renamed"
            ));

            String str = store.toString();
            assertTrue(str.contains("classes=1"));
            assertTrue(str.contains("methods=1"));
        }

        @Test
        void fluentChainingSupportedForAllAddMethods() {
            MappingStore result = store
                .addClassMapping(new ClassMapping("com/old/Class", "com/new/Class"))
                .addMethodMapping(new MethodMapping(
                    "com/example/Class", "method", "()V", "renamed"
                ))
                .addFieldMapping(new FieldMapping(
                    "com/example/Class", "field", "I", "renamed"
                ));

            assertSame(store, result);
            assertEquals(3, store.size());
        }
    }
}
