package com.tonic.renamer;

import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.renamer.exception.RenameException;
import com.tonic.renamer.hierarchy.ClassHierarchy;
import com.tonic.renamer.hierarchy.ClassNode;
import com.tonic.renamer.mapping.*;
import com.tonic.renamer.validation.RenameValidator;
import com.tonic.renamer.validation.ValidationResult;

import java.util.HashSet;
import java.util.Set;

/**
 * Main API for renaming classes, methods, and fields in a ClassPool.
 *
 * <p>Usage example:
 * <pre>{@code
 * Renamer renamer = new Renamer(classPool);
 * renamer.mapClass("com/old/MyClass", "com/new/RenamedClass")
 *        .mapMethodInHierarchy("com/old/Service", "process", "(I)V", "handle")
 *        .mapField("com/old/Model", "data", "Ljava/lang/String;", "content")
 *        .apply();
 * }</pre>
 *
 * <p>The renamer:
 * <ul>
 *   <li>Updates all constant pool references across all classes in the pool</li>
 *   <li>Handles method overrides when using {@link #mapMethodInHierarchy}</li>
 *   <li>Updates descriptors containing renamed class types</li>
 *   <li>Updates generic signatures</li>
 *   <li>Updates invokedynamic/bootstrap method references</li>
 * </ul>
 */
public class Renamer {

    private final ClassPool classPool;
    private final MappingStore mappings;
    private RenamerContext context;

    /**
     * Creates a new Renamer for the given ClassPool.
     *
     * @param classPool The ClassPool containing classes to rename
     */
    public Renamer(ClassPool classPool) {
        this.classPool = classPool;
        this.mappings = new MappingStore();
    }

    /**
     * Maps a class to a new name.
     *
     * @param oldName The old internal class name (e.g., "com/old/MyClass")
     * @param newName The new internal class name (e.g., "com/new/RenamedClass")
     * @return this for fluent chaining
     */
    public Renamer mapClass(String oldName, String newName) {
        mappings.addClassMapping(new ClassMapping(oldName, newName));
        return this;
    }

    /**
     * Maps a method to a new name (single class only, no hierarchy propagation).
     *
     * @param owner      The internal name of the class owning the method
     * @param name       The old method name
     * @param descriptor The method descriptor
     * @param newName    The new method name
     * @return this for fluent chaining
     */
    public Renamer mapMethod(String owner, String name, String descriptor, String newName) {
        mappings.addMethodMapping(new MethodMapping(owner, name, descriptor, newName, false));
        return this;
    }

    /**
     * Maps a method to a new name with hierarchy propagation.
     * This will rename the method in the specified class AND all overrides/implementations
     * in subclasses and interface implementations.
     *
     * @param owner      The internal name of the class owning the method
     * @param name       The old method name
     * @param descriptor The method descriptor
     * @param newName    The new method name
     * @return this for fluent chaining
     */
    public Renamer mapMethodInHierarchy(String owner, String name, String descriptor, String newName) {
        mappings.addMethodMapping(new MethodMapping(owner, name, descriptor, newName, true));
        return this;
    }

    /**
     * Maps a field to a new name.
     *
     * @param owner      The internal name of the class owning the field
     * @param name       The old field name
     * @param descriptor The field descriptor
     * @param newName    The new field name
     * @return this for fluent chaining
     */
    public Renamer mapField(String owner, String name, String descriptor, String newName) {
        mappings.addFieldMapping(new FieldMapping(owner, name, descriptor, newName));
        return this;
    }

    /**
     * Validates all mappings without applying them.
     *
     * @return ValidationResult containing any errors or warnings
     */
    public ValidationResult validate() {
        RenameValidator validator = new RenameValidator(classPool, mappings);
        return validator.validate();
    }

    /**
     * Applies all rename mappings.
     * Validates first and throws RenameException if validation fails.
     *
     * @throws RenameException if validation fails or an error occurs during renaming
     */
    public void apply() {
        if (mappings.isEmpty()) {
            return;
        }

        // Validate first
        ValidationResult result = validate();
        if (!result.isValid()) {
            throw new RenameException("Validation failed:\n" + result.getReport());
        }

        // Create the context
        context = new RenamerContext(classPool, mappings);

        // Apply class renames first (updates all class references)
        ClassRenamer classRenamer = new ClassRenamer(context);
        classRenamer.applyRenames();

        // Rebuild hierarchy after class renames
        context.rebuildHierarchy();

        // Apply method renames
        MethodRenamer methodRenamer = new MethodRenamer(context);
        methodRenamer.applyRenames();

        // Apply field renames
        FieldRenamer fieldRenamer = new FieldRenamer(context);
        fieldRenamer.applyRenames();
    }

    /**
     * Applies all rename mappings without validation.
     * Use with caution - prefer {@link #apply()} for safety.
     */
    public void applyUnsafe() {
        if (mappings.isEmpty()) {
            return;
        }

        context = new RenamerContext(classPool, mappings);

        ClassRenamer classRenamer = new ClassRenamer(context);
        classRenamer.applyRenames();

        context.rebuildHierarchy();

        MethodRenamer methodRenamer = new MethodRenamer(context);
        methodRenamer.applyRenames();

        FieldRenamer fieldRenamer = new FieldRenamer(context);
        fieldRenamer.applyRenames();
    }

    /**
     * Finds all methods that override or implement the specified method.
     *
     * @param owner      The internal name of the class owning the method
     * @param name       The method name
     * @param descriptor The method descriptor
     * @return Set of MethodEntry objects that override this method
     */
    public Set<MethodEntry> findOverrides(String owner, String name, String descriptor) {
        if (context == null) {
            context = new RenamerContext(classPool, mappings);
        }

        Set<MethodEntry> overrides = new HashSet<>();
        ClassHierarchy hierarchy = context.getHierarchy();

        Set<ClassNode> methodClasses = hierarchy.findMethodHierarchy(owner, name, descriptor);
        for (ClassNode node : methodClasses) {
            MethodEntry method = hierarchy.getMethod(node.getName(), name, descriptor);
            if (method != null) {
                overrides.add(method);
            }
        }

        return overrides;
    }

    /**
     * Returns the current mapping store.
     * Useful for inspecting or modifying mappings.
     *
     * @return The MappingStore
     */
    public MappingStore getMappings() {
        return mappings;
    }

    /**
     * Clears all mappings.
     *
     * @return this for fluent chaining
     */
    public Renamer clear() {
        mappings.clear();
        context = null;
        return this;
    }

    /**
     * Returns the class hierarchy.
     * Builds it if not already built.
     *
     * @return The ClassHierarchy
     */
    public ClassHierarchy getHierarchy() {
        if (context == null) {
            context = new RenamerContext(classPool, mappings);
        }
        return context.getHierarchy();
    }
}
