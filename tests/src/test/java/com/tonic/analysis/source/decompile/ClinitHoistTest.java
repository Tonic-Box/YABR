package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

public class ClinitHoistTest {

    private static String decompileMain() throws Exception {
        File jar = new File("DemoJar.jar");
        if (!jar.exists()) jar = new File("src/test/resources/DemoJar.jar");
        ClassPool pool = new ClassPool(true);
        try (JarFile jf = new JarFile(jar)) {
            pool.loadJar(jf);
        }
        ClassFile cf = pool.get("osrs/dev/Main");
        assertNotNull(cf, "Main not found");
        return new ClassDecompiler(cf).decompile();
    }

    @Test
    public void mainStaticFieldsHoist() throws Exception {
        String src = decompileMain();
        System.out.println(src);
        // pull just the field region for clarity
        int hdr = src.indexOf("class Main");
        int firstMethod = src.indexOf("public static void main");
        System.out.println("---- field region ----");
        System.out.println(src.substring(hdr, firstMethod));
        System.out.println("----------------------");

        assertTrue(src.contains("SOME_INT = 10"), "SOME_INT not inlined");
        assertTrue(src.replace(" ", "").contains("someArray=newint[]{0,1,2,3,4,5}"), "array not inlined as literal");
        // someString hoisted: it appears as a field initializer, and there is NO static block left.
        assertFalse(src.contains("static {"), "static block still present (not fully hoisted)");
        assertTrue(src.contains("someString = \"Hello, World!\""), "someString not inlined");
    }
}
