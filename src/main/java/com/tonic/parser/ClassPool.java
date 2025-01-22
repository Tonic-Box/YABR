package com.tonic.parser;

import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A simple class pool that can store and retrieve ClassFile objects by their internal names.
 */
public class ClassPool {
    @Getter
    private static final ClassPool Default;

    static
    {
        try {
            Default = new ClassPool();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final List<ClassFile> classMap = new ArrayList<>();

    public ClassPool() throws IOException {
        loadClass("java/lang/Object.class");
    }

    /**
     * Adds a ClassFile to the pool, indexed by its internal class name.
     *
     * @param classFile    The ClassFile object
     */
    public void put(ClassFile classFile) {
        classMap.add(classFile);
    }

    /**
     * Retrieves a ClassFile from the pool.
     *
     * @param internalName The internal name, e.g. "java/lang/Object"
     * @return The ClassFile if present, or null if not found
     */
    public ClassFile get(String internalName) {
        return classMap.stream()
                .filter(cf -> cf.getClassName().equals(internalName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Loads a .class from a raw byte array into the pool.
     *
     * @param classData A byte[] containing an entire .class file
     * @return The loaded ClassFile object
     * @throws IOException If parsing fails
     */
    public ClassFile loadClass(byte[] classData) {
        ClassFile cf = new ClassFile(classData);
        classMap.add(cf);
        return cf;
    }

    /**
     * Loads a .class from an InputStream into the pool.
     *
     * @param is An InputStream containing a .class file
     * @return The loaded ClassFile object
     * @throws IOException If reading or parsing fails
     */
    public ClassFile loadClass(InputStream is) throws IOException {
        // Read all bytes, then delegate to loadClass(byte[])
        byte[] data = is.readAllBytes();
        return loadClass(data);
    }

    public ClassFile loadClass(String clazz) throws IOException {
        try (InputStream is = ClassLoader.getSystemResourceAsStream(clazz)) {
            if (is == null) {
                throw new IOException("Failed to load class: " + clazz);
            }
            return loadClass(is);
        }
    }

    /**
     * Loads all .class files from a JarFile into this pool.
     *
     * @param jar The JarFile to read
     * @throws IOException If reading any entry fails
     */
    public void loadJar(JarFile jar) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                try (InputStream is = jar.getInputStream(entry)) {
                    // parse and store
                    loadClass(is);
                }
            }
        }
    }

    /**
     * Creates a new empty class with the specified name and access flags.
     * The superclass is set to java/lang/Object by default.
     * The class is set to target Java 11 (major version 55, minor version 0).
     *
     * @param className   The internal name of the class, e.g., "com/tonic/NewClass".
     * @param accessFlags The access flags for the class, e.g., Modifiers.PUBLIC | Modifiers.FINAL.
     * @return The newly created ClassFile object.
     */
    public ClassFile createNewClass(String className, int accessFlags) throws IOException {
        // Validate the class name
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty.");
        }
        if (className.contains(".")) {
            throw new IllegalArgumentException("Class name must use '/' as package separators, e.g., 'com/tonic/NewClass'.");
        }

        // Check if the class already exists
        if (get(className) != null) {
            throw new IllegalArgumentException("Class " + className + " already exists in the pool.");
        }

        // Create a new ClassFile instance
        ClassFile newClass = new ClassFile(className, accessFlags);

        // Rebuild the class file to generate the byte array
        newClass.rebuild();

        // Add the new ClassFile to the pool
        put(newClass);

        // Log the creation
        Logger.info("Created new class: " + className + " with access flags: 0x" + Integer.toHexString(accessFlags));

        return newClass;
    }
}
