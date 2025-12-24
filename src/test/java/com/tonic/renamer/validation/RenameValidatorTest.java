package com.tonic.renamer.validation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.renamer.mapping.ClassMapping;
import com.tonic.renamer.mapping.FieldMapping;
import com.tonic.renamer.mapping.MappingStore;
import com.tonic.renamer.mapping.MethodMapping;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class RenameValidatorTest {

    private ClassPool classPool;
    private MappingStore mappings;
    private RenameValidator validator;

    @BeforeEach
    void setUp() {
        classPool = TestUtils.emptyPool();
        mappings = new MappingStore();
    }

    @Nested
    class ClassValidationTests {

        @Test
        void validClassMappingPasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/OldClass", access);

            mappings.addClassMapping(new ClassMapping("com/example/OldClass", "com/example/NewClass"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.isValid());
        }

        @Test
        void rejectsNonExistentClass() {
            mappings.addClassMapping(new ClassMapping("com/example/NonExistent", "com/example/New"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Class not found")));
        }

        @Test
        void rejectsInvalidClassName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/OldClass", access);

            mappings.addClassMapping(new ClassMapping("com/example/OldClass", "invalid-name"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Invalid class name")));
        }

        @Test
        void rejectsDuplicateTargetNames() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/Class1", access);
            classPool.createNewClass("com/example/Class2", access);

            mappings.addClassMapping(new ClassMapping("com/example/Class1", "com/example/Same"));
            mappings.addClassMapping(new ClassMapping("com/example/Class2", "com/example/Same"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Duplicate target class name")));
        }

        @Test
        void rejectsConflictWithExistingClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/OldClass", access);
            classPool.createNewClass("com/example/Existing", access);

            mappings.addClassMapping(new ClassMapping("com/example/OldClass", "com/example/Existing"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Class name conflict")));
        }

        @Test
        void allowsRenamingToClassThatIsAlsoBeingRenamed() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/Class1", access);
            classPool.createNewClass("com/example/Class2", access);

            mappings.addClassMapping(new ClassMapping("com/example/Class1", "com/example/Class2"));
            mappings.addClassMapping(new ClassMapping("com/example/Class2", "com/example/Class3"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.isValid());
        }
    }

    @Nested
    class MethodValidationTests {

        @Test
        void validMethodMappingPasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Service", access);
            classFile.createNewMethodWithDescriptor(access, "process", "(I)V");

            mappings.addMethodMapping(new MethodMapping(
                "com/example/Service", "process", "(I)V", "handle"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.isValid());
        }

        @Test
        void rejectsMethodInNonExistentClass() {
            mappings.addMethodMapping(new MethodMapping(
                "com/example/NonExistent", "method", "()V", "renamed"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Class not found for method")));
        }

        @Test
        void rejectsNonExistentMethod() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/Service", access);

            mappings.addMethodMapping(new MethodMapping(
                "com/example/Service", "nonexistent", "()V", "renamed"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Method not found")));
        }

        @Test
        void rejectsInvalidMethodName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Service", access);
            classFile.createNewMethodWithDescriptor(access, "process", "()V");

            mappings.addMethodMapping(new MethodMapping(
                "com/example/Service", "process", "()V", "invalid-name"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Invalid method name")));
        }

        @Test
        void rejectsNameConflictInSameClass() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Service", access);
            classFile.createNewMethodWithDescriptor(access, "process", "()V");
            classFile.createNewMethodWithDescriptor(access, "handle", "()V");

            mappings.addMethodMapping(new MethodMapping(
                "com/example/Service", "process", "()V", "handle"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Method name conflict")));
        }

        @Test
        void allowsRenamingMethodToItsOwnName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Service", access);
            classFile.createNewMethodWithDescriptor(access, "process", "()V");

            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.isValid());
        }
    }

    @Nested
    class FieldValidationTests {

        @Test
        void validFieldMappingPasses() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Model", access);
            classFile.createNewField(access, "data", "Ljava/lang/String;", Collections.emptyList());

            mappings.addFieldMapping(new FieldMapping(
                "com/example/Model", "data", "Ljava/lang/String;", "content"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.isValid());
        }

        @Test
        void rejectsFieldInNonExistentClass() {
            mappings.addFieldMapping(new FieldMapping(
                "com/example/NonExistent", "field", "I", "renamed"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Class not found for field")));
        }

        @Test
        void rejectsNonExistentField() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/Model", access);

            mappings.addFieldMapping(new FieldMapping(
                "com/example/Model", "nonexistent", "I", "renamed"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Field not found")));
        }

        @Test
        void rejectsInvalidFieldName() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Model", access);
            classFile.createNewField(access, "data", "I", Collections.emptyList());

            mappings.addFieldMapping(new FieldMapping(
                "com/example/Model", "data", "I", "invalid-name"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Invalid field name")));
        }

        @Test
        void rejectsFieldNameConflict() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            ClassFile classFile = classPool.createNewClass("com/example/Model", access);
            classFile.createNewField(access, "data", "I", Collections.emptyList());
            classFile.createNewField(access, "content", "I", Collections.emptyList());

            mappings.addFieldMapping(new FieldMapping(
                "com/example/Model", "data", "I", "content"
            ));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertFalse(result.isValid());
            assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("Field name conflict")));
        }
    }

    @Nested
    class CircularRenameDetectionTests {

        @Test
        void detectsSimpleCircularRename() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/ClassA", access);
            classPool.createNewClass("com/example/ClassB", access);

            mappings.addClassMapping(new ClassMapping("com/example/ClassA", "com/example/ClassB"));
            mappings.addClassMapping(new ClassMapping("com/example/ClassB", "com/example/ClassA"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Circular class rename")));
        }

        @Test
        void detectsChainedRename() throws IOException {
            int access = new AccessBuilder().setPublic().build();
            classPool.createNewClass("com/example/ClassA", access);
            classPool.createNewClass("com/example/ClassB", access);
            classPool.createNewClass("com/example/ClassC", access);

            mappings.addClassMapping(new ClassMapping("com/example/ClassA", "com/example/ClassB"));
            mappings.addClassMapping(new ClassMapping("com/example/ClassB", "com/example/ClassC"));
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.hasWarnings());
            assertTrue(result.getWarnings().stream()
                .anyMatch(w -> w.contains("Chained class rename")));
        }
    }

    @Nested
    class EmptyMappingsTests {

        @Test
        void emptyMappingsAreValid() {
            validator = new RenameValidator(classPool, mappings);

            ValidationResult result = validator.validate();
            assertTrue(result.isValid());
            assertFalse(result.hasWarnings());
        }
    }
}
