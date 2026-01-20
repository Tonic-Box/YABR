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
 * Pool for storing and retrieving ClassFile objects by internal name.
 */
@Getter
public class ClassPool {
    private static ClassPool Default;

    /**
     * Gets the default ClassPool with JDK classes loaded.
     * Lazy-loaded to avoid startup penalty.
     * @return the default ClassPool
     */
    public static synchronized ClassPool getDefault() {
        if (Default == null) {
            try {
                Default = new ClassPool();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return Default;
    }

    private final List<ClassFile> classes = new ArrayList<>();

    public ClassPool() throws IOException {
        loadAllJavaBuiltInClasses();
    }

    /**
     * Creates empty pool without loading built-in classes.
     *
     * @param empty ignored parameter to differentiate from default constructor
     */
    public ClassPool(boolean empty) {
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
     * @param classFile ClassFile to add
     */
    public void put(ClassFile classFile) {
        classes.add(classFile);
    }

    /**
     * Removes a ClassFile from the pool by internal name.
     *
     * @param internalName internal name (e.g., "java/lang/Object")
     * @return true if a class was removed, false otherwise
     */
    public boolean remove(String internalName) {
        return classes.removeIf(cf -> cf.getClassName().equals(internalName));
    }

    /**
     * Retrieves ClassFile by internal name.
     *
     * @param internalName internal name (e.g., "java/lang/Object")
     * @return ClassFile if found, null otherwise
     */
    public ClassFile get(String internalName) {
        return classes.stream()
                .filter(cf -> cf.getClassName().equals(internalName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Loads class from byte array.
     *
     * @param classData complete .class file bytes
     * @return loaded ClassFile
     */
    public ClassFile loadClass(byte[] classData) {
        ClassFile cf = new ClassFile(classData);
        classes.add(cf);
        return cf;
    }

    /**
     * Loads class from InputStream.
     *
     * @param is InputStream containing .class file
     * @return loaded ClassFile
     * @throws IOException if reading or parsing fails
     */
    public ClassFile loadClass(InputStream is) throws IOException {
        byte[] data = is.readAllBytes();
        return loadClass(data);
    }

    /**
     * Loads class from system class loader.
     *
     * @param clazz internal class name (e.g., "java/lang/Object")
     * @return loaded ClassFile
     * @throws IOException if loading fails
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
     * Loads class from platform class loader.
     *
     * @param clazz internal class name (e.g., "java/lang/Object")
     * @return loaded ClassFile
     * @throws IOException if loading fails
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
     * Loads all .class files from JAR into pool.
     *
     * @param jar JarFile to read
     * @throws IOException if reading fails
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
     * Creates new empty class with specified name and access flags.
     *
     * @param className internal class name (e.g., "com/tonic/NewClass")
     * @param accessFlags access flags for the class
     * @return newly created ClassFile
     * @throws IOException if rebuilding fails
     * @throws IllegalArgumentException if name invalid or class exists
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
