package com.tonic.utill;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for working with Java class files.
 */
public class ClassFileUtil
{
    /**
     * Saves a byte array as a .class file in the specified directory.
     *
     * @param classBytes    the byte array representing the class file content
     * @param directoryPath the path to the directory where the .class file should be saved
     * @param className     the fully qualified name of the class (e.g., "com.tonic.ANewClass")
     * @throws IOException              if an I/O error occurs during writing
     * @throws IllegalArgumentException if the className is null or empty
     */
    public static void saveClassFile(byte[] classBytes, String directoryPath, String className) throws IOException {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("className cannot be null or empty.");
        }

        String relativePath = className.replace('.', File.separatorChar) + ".class";
        Path targetPath = Paths.get(directoryPath, relativePath);
        File targetFile = targetPath.toFile();

        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs();
            if (!dirsCreated) {
                throw new IOException("Failed to create directories: " + parentDir.getAbsolutePath());
            }
        }

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile))) {
            bos.write(classBytes);
            bos.flush();
        } catch (IOException e) {
            throw new IOException("Failed to write class file: " + targetFile.getAbsolutePath(), e);
        }

        Logger.info("Class file saved successfully at: " + targetFile.getAbsolutePath());
    }
}
