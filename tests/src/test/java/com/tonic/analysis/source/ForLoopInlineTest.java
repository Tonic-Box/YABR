package com.tonic.analysis.source;

import com.tonic.analysis.source.decompile.ClassDecompiler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ForLoopInlineTest {

    @Test
    void testForLoopDeclarationIsInlined() throws IOException {
        Path fixtureFile = Path.of("build/classes/java/test/com/tonic/fixtures/ControlFlow.class");
        if (!Files.exists(fixtureFile)) {
            System.err.println("Fixture file not found at: " + fixtureFile.toAbsolutePath());
            System.err.println("Skipping test - run 'gradle compileTestJava' first");
            return;
        }

        ClassPool pool = new ClassPool();
        byte[] classBytes = Files.readAllBytes(fixtureFile);
        ClassFile cf = pool.loadClass(classBytes);
        assertNotNull(cf, "ControlFlow class should be loaded");

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        System.out.println("=== Decompiled ControlFlow ===");
        System.out.println(result);
        System.out.println("=== End ===");

        assertTrue(result.contains("for (int "), "For loop should have inlined declaration, got:\n" + result);

        assertFalse(result.contains("int local") && result.contains("for (local"),
            "Declaration should be inlined into for-loop, not separate");
    }
}
