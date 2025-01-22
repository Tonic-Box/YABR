package com.tonic.utill;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClassFileUtil
{
    /**
     * Saves a byte array as a .class file in the specified directory.
     *
     * @param classBytes    The byte array representing the class file content.
     * @param directoryPath The path to the directory where the .class file should be saved.
     * @param className     The fully qualified name of the class (e.g., "com.tonic.ANewClass").
     * @throws IOException              If an I/O error occurs during writing.
     * @throws IllegalArgumentException If the className is null or empty.
     */
    public static void saveClassFile(byte[] classBytes, String directoryPath, String className) throws IOException {
        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException("className cannot be null or empty.");
        }

        // Convert the fully qualified class name to a relative file path
        // e.g., "com.tonic.ANewClass" -> "com/tonic/ANewClass.class"
        String relativePath = className.replace('.', File.separatorChar) + ".class";

        // Combine the directory path with the relative path
        Path targetPath = Paths.get(directoryPath, relativePath);

        File targetFile = targetPath.toFile();

        // Ensure the parent directories exist; if not, create them
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists()) {
            boolean dirsCreated = parentDir.mkdirs();
            if (!dirsCreated) {
                throw new IOException("Failed to create directories: " + parentDir.getAbsolutePath());
            }
        }

        // Write the byte array to the target file
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile))) {
            bos.write(classBytes);
            bos.flush();
        } catch (IOException e) {
            throw new IOException("Failed to write class file: " + targetFile.getAbsolutePath(), e);
        }

        Logger.info("Class file saved successfully at: " + targetFile.getAbsolutePath());
    }
}
