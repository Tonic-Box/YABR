package com.tonic.parser;

import com.tonic.utill.Logger;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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

    @Getter
    private final List<ClassFile> classes = new ArrayList<>();

    public ClassPool() throws IOException {
        loadAllJavaBuiltInClasses();
    }

    /**
     * Creates an empty class pool without loading any built-in classes.
     * Use this for custom class pools that only need user-loaded classes.
     *
     * @param empty ignored parameter to differentiate from default constructor
     */
    public ClassPool(boolean empty) {
        // Don't load JRT classes - useful for UI and testing
    }

    private void loadAllJavaBuiltInClasses() throws IOException {
        if (isUsingJRT()) {
            loadFromJRT();
        } else {
            loadFromRTJar();
        }
    }

    private boolean isUsingJRT() {
        String version = System.getProperty("java.version");
        return !version.startsWith("1.");
    }

    private void loadFromJRT() throws IOException {
        try (FileSystem jrtFS = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap())) {
            Path javaBasePath = jrtFS.getPath("modules", "java.base");
            try (Stream<Path> paths = Files.walk(javaBasePath)) {
                paths.filter(path -> path.toString().endsWith(".class"))
                        .forEach(path -> {
                            try (InputStream is = Files.newInputStream(path)) {
                                loadClass(is);
                            } catch (IOException e) {
                                System.err.println("Failed to load class from JRT: " + path);
                            }
                        });
            }
        }
    }

    private void loadFromRTJar() throws IOException {
        String javaHome = System.getProperty("java.home");
        Path rtJarPath = Paths.get(javaHome, "lib", "rt.jar");
        if (!Files.exists(rtJarPath)) {
            throw new IOException("rt.jar not found at: " + rtJarPath);
        }
        try (JarFile rtJar = new JarFile(rtJarPath.toFile())) {
            loadJar(rtJar);
        }
    }

    /**
     * Adds a ClassFile to the pool.
     *
     * @param classFile the ClassFile object to add
     */
    public void put(ClassFile classFile) {
        classes.add(classFile);
    }

    /**
     * Retrieves a ClassFile from the pool.
     *
     * @param internalName the internal name (e.g., "java/lang/Object")
     * @return the ClassFile if present, or null if not found
     */
    public ClassFile get(String internalName) {
        return classes.stream()
                .filter(cf -> cf.getClassName().equals(internalName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Loads a class from a raw byte array into the pool.
     *
     * @param classData a byte array containing a complete .class file
     * @return the loaded ClassFile object
     */
    public ClassFile loadClass(byte[] classData) {
        ClassFile cf = new ClassFile(classData);
        classes.add(cf);
        return cf;
    }

    /**
     * Loads a class from an InputStream into the pool.
     *
     * @param is an InputStream containing a .class file
     * @return the loaded ClassFile object
     * @throws IOException if reading or parsing fails
     */
    public ClassFile loadClass(InputStream is) throws IOException {
        byte[] data = is.readAllBytes();
        return loadClass(data);
    }

    /**
     * Loads a class from the system class loader into the pool.
     *
     * @param clazz the internal name of the class (e.g., "java/lang/Object")
     * @return the loaded ClassFile object
     * @throws IOException if reading or parsing fails
     */
    public ClassFile loadSystemClass(String clazz) throws IOException {
        try (InputStream is = ClassLoader.getSystemResourceAsStream(clazz)) {
            if (is == null) {
                throw new IOException("Failed to load class: " + clazz);
            }
            return loadClass(is);
        }
    }

    /**
     * Loads a class from the platform class loader into the pool.
     *
     * @param clazz the internal name of the class (e.g., "java/lang/Object")
     * @return the loaded ClassFile object
     * @throws IOException if reading or parsing fails
     */
    public ClassFile loadPlatformClass(String clazz) throws IOException {
        try (InputStream is = ClassLoader.getPlatformClassLoader().getResourceAsStream(clazz)) {
            if (is == null) {
                throw new IOException("Failed to load class: " + clazz);
            }
            return loadClass(is);
        }
    }

    /**
     * Loads all .class files from a JarFile into this pool.
     *
     * @param jar the JarFile to read
     * @throws IOException if reading any entry fails
     */
    public void loadJar(JarFile jar) throws IOException {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                try (InputStream is = jar.getInputStream(entry)) {
                    loadClass(is);
                }
            }
        }
    }

    /**
     * Creates a new empty class with the specified name and access flags.
     *
     * @param className the internal name of the class (e.g., "com/tonic/NewClass")
     * @param accessFlags the access flags for the class
     * @return the newly created ClassFile object
     * @throws IOException if rebuilding the class file fails
     * @throws IllegalArgumentException if the class name is invalid or already exists
     */
    public ClassFile createNewClass(String className, int accessFlags) throws IOException {
        if (className == null || className.isEmpty()) {
            throw new IllegalArgumentException("Class name cannot be null or empty.");
        }
        if (className.contains(".")) {
            throw new IllegalArgumentException("Class name must use '/' as package separators, e.g., 'com/tonic/NewClass'.");
        }

        if (get(className) != null) {
            throw new IllegalArgumentException("Class " + className + " already exists in the pool.");
        }

        ClassFile newClass = new ClassFile(className, accessFlags);

        newClass.rebuild();

        put(newClass);

        Logger.info("Created new class: " + className + " with access flags: 0x" + Integer.toHexString(accessFlags));

        return newClass;
    }
}
