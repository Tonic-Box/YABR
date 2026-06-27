package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression for bug 6: generic type variables, class/method formal type parameters and wildcards. */
public class GenericSignatureDecompileTest {

    @Test
    public void classMethodTypeParametersAndWildcards() throws Exception {
        Path cls = Paths.get("stress-test/classes/S06_Generics.class");
        Assumptions.assumeTrue(Files.exists(cls), "stress-test class not compiled");

        byte[] bytes = Files.readAllBytes(cls);
        String src = new ClassDecompiler(new ClassFile(new ByteArrayInputStream(bytes))).decompile();
        String flat = src.replaceAll("\\s+", " ");

        assertFalse(src.contains("Object<T>"), "type variable must not be wrapped as Object<T>:\n" + src);
        assertTrue(flat.contains("class S06_Generics<T extends Comparable<T>>"),
                "class formal type parameters:\n" + src);
        assertTrue(flat.contains("List<T>"), "field/return type List<T>:\n" + src);
        assertTrue(flat.contains("<K, V>"), "method formal type parameters on invert:\n" + src);
        assertTrue(flat.contains("? extends") && flat.contains("? super"), "wildcards rendered:\n" + src);
    }
}
