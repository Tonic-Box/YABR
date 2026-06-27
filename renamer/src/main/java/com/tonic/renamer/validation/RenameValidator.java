package com.tonic.renamer.validation;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.mapping.ClassMapping;
import com.tonic.renamer.mapping.FieldMapping;
import com.tonic.renamer.mapping.MappingStore;
import com.tonic.renamer.mapping.MethodMapping;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates rename mappings before application.
 */
public class RenameValidator {

    private final ClassPool classPool;
    private final MappingStore mappings;

    public RenameValidator(ClassPool classPool, MappingStore mappings) {
        this.classPool = classPool;
        this.mappings = mappings;
    }

    /**
     * Validates all mappings in the store.
     *
     * @return ValidationResult containing any errors or warnings
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();

        validateClassMappings(result);
        validateMethodMappings(result);
        validateFieldMappings(result);
        checkForCircularRenames(result);

        return result;
    }

    private void validateClassMappings(ValidationResult result) {
        Set<String> newNames = new HashSet<>();

        for (ClassMapping mapping : mappings.getClassMappings()) {
            // Check that the old class exists
            if (classPool.get(mapping.getOldName()) == null) {
                result.addError("Class not found: " + mapping.getOldName());
            }

            // Check that the new name is a valid identifier
            String newName = mapping.getNewName();
            if (!isValidClassName(newName)) {
                result.addError("Invalid class name: " + newName);
            }

            // Check for duplicate target names
            if (!newNames.add(newName)) {
                result.addError("Duplicate target class name: " + newName);
            }

            // Check that the new name doesn't conflict with existing classes
            // (unless that class is also being renamed)
            ClassFile existing = classPool.get(newName);
            if (existing != null && !mappings.hasClassMapping(newName)) {
                result.addError("Class name conflict: " + newName + " already exists");
            }
        }
    }

    private void validateMethodMappings(ValidationResult result) {
        for (MethodMapping mapping : mappings.getMethodMappings()) {
            String owner = mapping.getOwner();
            String oldName = mapping.getOldName();
            String descriptor = mapping.getDescriptor();
            String newName = mapping.getNewName();

            // Check that the owning class exists
            ClassFile cf = classPool.get(owner);
            if (cf == null) {
                result.addError("Class not found for method: " + owner);
                continue;
            }

            // Check that the method exists
            boolean found = false;
            for (MethodEntry method : cf.getMethods()) {
                if (method.getName().equals(oldName) && method.getDesc().equals(descriptor)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addError("Method not found: " + owner + "." + oldName + descriptor);
            }

            // Check that the new name is a valid identifier
            if (!isValidMethodName(newName)) {
                result.addError("Invalid method name: " + newName);
            }

            // Check for naming conflicts (same name and descriptor in same class)
            for (MethodEntry method : cf.getMethods()) {
                if (method.getName().equals(newName) && method.getDesc().equals(descriptor)) {
                    // Only a conflict if not the same method being renamed
                    if (!method.getName().equals(oldName)) {
                        result.addError("Method name conflict: " + owner + "." + newName + descriptor + " already exists");
                    }
                }
            }
        }
    }

    private void validateFieldMappings(ValidationResult result) {
        for (FieldMapping mapping : mappings.getFieldMappings()) {
            String owner = mapping.getOwner();
            String oldName = mapping.getOldName();
            String newName = mapping.getNewName();

            // Check that the owning class exists
            ClassFile cf = classPool.get(owner);
            if (cf == null) {
                result.addError("Class not found for field: " + owner);
                continue;
            }

            // Check that the field exists
            boolean found = false;
            for (FieldEntry field : cf.getFields()) {
                if (field.getName().equals(oldName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.addError("Field not found: " + owner + "." + oldName);
            }

            // Check that the new name is a valid identifier
            if (!isValidFieldName(newName)) {
                result.addError("Invalid field name: " + newName);
            }

            // Check for naming conflicts
            for (FieldEntry field : cf.getFields()) {
                if (field.getName().equals(newName)) {
                    if (!field.getName().equals(oldName)) {
                        result.addError("Field name conflict: " + owner + "." + newName + " already exists");
                    }
                }
            }
        }
    }

    private void checkForCircularRenames(ValidationResult result) {
        // Check for A->B, B->A patterns
        for (ClassMapping mapping : mappings.getClassMappings()) {
            String oldName = mapping.getOldName();
            String newName = mapping.getNewName();

            // Check if newName is also being renamed to something else
            String nextTarget = mappings.getClassMapping(newName);
            if (nextTarget != null) {
                if (nextTarget.equals(oldName)) {
                    result.addWarning("Circular class rename detected: " + oldName + " <-> " + newName);
                } else {
                    result.addWarning("Chained class rename: " + oldName + " -> " + newName + " -> " + nextTarget);
                }
            }
        }
    }

    private boolean isValidClassName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // Internal names use / as separator
        String[] parts = name.split("/");
        for (String part : parts) {
            if (!isValidIdentifier(part)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidMethodName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // Special method names
        if (name.equals("<init>") || name.equals("<clinit>")) {
            return true;
        }
        return isValidIdentifier(name);
    }

    private boolean isValidFieldName(String name) {
        return isValidIdentifier(name);
    }

    private boolean isValidIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
