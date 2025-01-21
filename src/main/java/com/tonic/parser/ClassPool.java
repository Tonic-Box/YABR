package com.tonic.parser;

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
    private static final ClassPool Default = new ClassPool();

    private final List<ClassFile> classMap = new ArrayList<>();

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
    public ClassFile loadClass(byte[] classData) throws IOException {
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
}
