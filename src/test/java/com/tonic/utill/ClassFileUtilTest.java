package com.tonic.utill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ClassFileUtilTest {

    @Test
    void saveClassFileCreatesFileInCorrectLocation(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
        String className = "com.example.TestClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/example/TestClass.class");
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void saveClassFileWritesCorrectContent(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x01, 0x02};
        String className = "com.test.MyClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/test/MyClass.class");
        byte[] writtenBytes = Files.readAllBytes(expectedPath);
        assertArrayEquals(classBytes, writtenBytes);
    }

    @Test
    void saveClassFileCreatesNestedDirectories(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{0x00, 0x01};
        String className = "com.example.deep.package.structure.TestClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/example/deep/package/structure/TestClass.class");
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void saveClassFileThrowsExceptionForNullClassName(@TempDir Path tempDir) {
        byte[] classBytes = new byte[]{0x00};

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), null)
        );

        assertTrue(exception.getMessage().contains("className cannot be null or empty"));
    }

    @Test
    void saveClassFileThrowsExceptionForEmptyClassName(@TempDir Path tempDir) {
        byte[] classBytes = new byte[]{0x00};

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), "")
        );

        assertTrue(exception.getMessage().contains("className cannot be null or empty"));
    }

    @Test
    void saveClassFileThrowsExceptionForWhitespaceClassName(@TempDir Path tempDir) {
        byte[] classBytes = new byte[]{0x00};

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), "   ")
        );

        assertTrue(exception.getMessage().contains("className cannot be null or empty"));
    }

    @Test
    void saveClassFileHandlesSimpleClassName(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{0x01, 0x02, 0x03};
        String className = "SimpleClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("SimpleClass.class");
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void saveClassFileHandlesInnerClass(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{0x01, 0x02};
        String className = "com.example.Outer$Inner";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/example/Outer$Inner.class");
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void saveClassFileOverwritesExistingFile(@TempDir Path tempDir) throws IOException {
        byte[] originalBytes = new byte[]{0x01, 0x02};
        byte[] newBytes = new byte[]{0x03, 0x04, 0x05};
        String className = "com.test.OverwriteTest";

        ClassFileUtil.saveClassFile(originalBytes, tempDir.toString(), className);
        ClassFileUtil.saveClassFile(newBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/test/OverwriteTest.class");
        byte[] writtenBytes = Files.readAllBytes(expectedPath);
        assertArrayEquals(newBytes, writtenBytes);
    }

    @Test
    void saveClassFileHandlesEmptyByteArray(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{};
        String className = "com.test.EmptyClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/test/EmptyClass.class");
        assertTrue(Files.exists(expectedPath));
        assertEquals(0, Files.size(expectedPath));
    }

    @Test
    void saveClassFileHandlesLargeByteArray(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[10000];
        for (int i = 0; i < classBytes.length; i++) {
            classBytes[i] = (byte) (i % 256);
        }
        String className = "com.test.LargeClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/test/LargeClass.class");
        byte[] writtenBytes = Files.readAllBytes(expectedPath);
        assertArrayEquals(classBytes, writtenBytes);
    }

    @Test
    void saveClassFileHandlesDirectoryAlreadyExists(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("com/example"));

        byte[] classBytes = new byte[]{0x01, 0x02};
        String className = "com.example.TestClass";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/example/TestClass.class");
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void saveClassFileHandlesClassNameWithNumbers(@TempDir Path tempDir) throws IOException {
        byte[] classBytes = new byte[]{0x01};
        String className = "com.test.Test123Class456";

        ClassFileUtil.saveClassFile(classBytes, tempDir.toString(), className);

        Path expectedPath = tempDir.resolve("com/test/Test123Class456.class");
        assertTrue(Files.exists(expectedPath));
    }

    @Test
    void saveClassFileThrowsIOExceptionForInvalidPath() {
        byte[] classBytes = new byte[]{0x01};
        String className = "com.test.Test";
        String invalidPath = "\0invalid";

        assertThrows(Exception.class,
            () -> ClassFileUtil.saveClassFile(classBytes, invalidPath, className)
        );
    }
}
