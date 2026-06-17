package com.tonic.analysis;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.testutil.TestUtils;
import com.tonic.type.MethodHandle;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-tests the shared bootstrap resolver against hand-built BootstrapMethods, covering the JDK
 * bootstrap families that the Java 11 test compiler cannot emit directly (switch/record) plus the
 * recipe formatter.
 */
class BootstrapsTest {

    private static ClassFile emptyClass() {
        try {
            return TestUtils.emptyPool().createNewClass("BsmHost", new AccessBuilder().setPublic().build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int addBootstrap(ClassFile cf, int refKind, String owner, String name, String desc, List<Integer> args) {
        ConstPool cp = cf.getConstPool();
        int handle = cp.getIndexOf(cp.findOrAddMethodHandle(refKind, owner, name, desc));
        return cf.addBootstrapMethod(handle, args);
    }

    @Test
    void resolvesSwitchBootstrap() {
        ClassFile cf = emptyClass();
        int idx = addBootstrap(cf, MethodHandle.H_INVOKESTATIC, "java/lang/runtime/SwitchBootstraps",
                "typeSwitch",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                List.of());

        Bootstraps.BootstrapRef ref = Bootstraps.resolve(cf, idx);
        assertEquals("java/lang/runtime/SwitchBootstraps", ref.getOwner());
        assertEquals("typeSwitch", ref.getName());
        assertEquals("invokeStatic", ref.getKind());
        assertEquals("switch", ref.category());
    }

    @Test
    void resolvesRecordBootstrap() {
        ClassFile cf = emptyClass();
        int idx = addBootstrap(cf, MethodHandle.H_INVOKESTATIC, "java/lang/runtime/ObjectMethods",
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;",
                List.of());

        assertEquals("record", Bootstraps.resolve(cf, idx).category());
    }

    @Test
    void resolvesStringConcatAndReadsRecipe() {
        ClassFile cf = emptyClass();
        ConstPool cp = cf.getConstPool();
        int recipeArg = cp.getIndexOf(cp.findOrAddString("abc"));
        int idx = addBootstrap(cf, MethodHandle.H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory",
                "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
                List.of(recipeArg));

        Bootstraps.BootstrapRef ref = Bootstraps.resolve(cf, idx);
        assertEquals("stringconcat", ref.category());
        assertEquals(List.of(recipeArg), ref.getArgCpIndices());
    }

    @Test
    void readableRecipeRendersMarkersAndEscapes() {
        String readable = Bootstraps.readableRecipe("ab");
        assertTrue(readable.contains("{arg}"), readable);
        assertTrue(readable.contains("{const}"), readable);
        assertTrue(readable.contains("\\u0007"), readable);
        assertEquals("a{arg}b{const}\\u0007", readable);
    }

    @Test
    void invalidIndexResolvesNull() {
        assertNull(Bootstraps.resolve(emptyClass(), 0));
        assertNull(Bootstraps.resolve(emptyClass(), 99));
    }
}
