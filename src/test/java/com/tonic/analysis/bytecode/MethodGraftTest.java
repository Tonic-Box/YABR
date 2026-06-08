package com.tonic.analysis.bytecode;

import com.tonic.analysis.CodeWriter;
import com.tonic.analysis.MethodGrafter;
import com.tonic.analysis.source.ast.decl.ClassDecl;
import com.tonic.analysis.source.ast.decl.CompilationUnit;
import com.tonic.analysis.source.ast.decl.MethodDecl;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.parser.JavaParser;
import com.tonic.analysis.ssa.SSA;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-{@link ClassFile} method grafting with constant-pool remapping ({@link MethodGrafter}) plus
 * owner redirection ({@link ClassFile#redirectOwner}): two methods are moved from {@code Sound} into
 * {@code Game} — one calling the other — then the call's owner is repointed {@code Sound -> Game}, and
 * the self-contained {@code Game} is loaded and run.
 */
class MethodGraftTest {

    @Test
    void graftMethodsAndRedirectOwnerRunsOnTarget() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        for (String cn : new String[]{"java/lang/Object", "java/lang/String"}) {
            pool.loadPlatformClass(cn + ".class");
        }

        // Source class with vol() and loud()=vol()+3, compiled via the YABR front end.
        String src = "public class Sound { public static int vol() { return 7; }"
                + " public static int loud() { return vol() + 3; } }";
        JavaParser parser = JavaParser.create();
        CompilationUnit cu = parser.parse(src);
        ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
        ClassFile sound = pool.createNewClass("Sound", new AccessBuilder().setPublic().build());
        ASTLowerer lowerer = new ASTLowerer(sound.getConstPool(), pool);
        lowerer.setCurrentClassDecl(cls);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(sound.getConstPool());
        for (MethodDecl m : cls.getMethods()) {
            if (m.getBody() == null) {
                continue;
            }
            sound.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(), m.getName(), "()I");
            ssa.lower(lowerer.lower(m, "Sound"), method(sound, m.getName()));
        }

        ClassFile game = pool.createNewClass("Game", new AccessBuilder().setPublic().build());
        MethodGrafter.graftMethod(sound, method(sound, "vol"), game);
        MethodGrafter.graftMethod(sound, method(sound, "loud"), game);

        // Before redirect, the grafted loud() still calls Sound.vol; repoint it to Game.vol.
        int repointed = game.redirectOwner("Sound", "Game");
        assertEquals(1, repointed, "expected the Sound.vol call to be repointed to Game.vol");

        Class<?> clazz = TestUtils.loadAndVerify(game);
        assertEquals(7, (int) clazz.getMethod("vol").invoke(null));
        assertEquals(10, (int) clazz.getMethod("loud").invoke(null));
    }

    @Test
    void graftsInvokeDynamicByCopyingBootstrap() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                com.tonic.testutil.ModernJdk.available(17), "JDK 17 not installed");
        // javac compiles `"v" + n` to a StringConcatFactory invokedynamic; graft must copy + remap its
        // bootstrap method into the target's BootstrapMethods. (Loaded on the Java 11 test JVM, which
        // has StringConcatFactory.)
        java.util.Map<String, byte[]> javac = com.tonic.testutil.ModernJdk.compile(
                17, "Sound", "public class Sound { public static String tag(int n) { return \"v\" + n; } }");
        ClassPool pool = TestUtils.emptyPool();
        for (String cn : new String[]{"java/lang/Object", "java/lang/String"}) {
            pool.loadPlatformClass(cn + ".class");
        }
        ClassFile sound = new ClassFile(new java.io.ByteArrayInputStream(javac.get("Sound")));
        ClassFile game = pool.createNewClass("Game", new AccessBuilder().setPublic().build());

        MethodGrafter.graftMethod(sound, method(sound, "tag"), game);

        Class<?> clazz = TestUtils.loadAndVerify(game);
        assertEquals("v5", clazz.getMethod("tag", int.class).invoke(null, 5));
    }

    @Test
    void constPoolRemapperSplicesBodyIntoExistingMethod() throws Exception {
        // The graftMethod path can't help when the target already owns the method (e.g. merging into an
        // existing <clinit>). Here we drive ConstPoolRemapper + cloneRangeWithTargets directly to splice
        // a source body into an existing target method, relocating its String constant across pools.
        ClassPool pool = TestUtils.emptyPool();
        for (String cn : new String[]{"java/lang/Object", "java/lang/String"}) {
            pool.loadPlatformClass(cn + ".class");
        }
        ClassFile sound = compile(pool, "Sound",
                "public class Sound { public static String src() { return \"spliced-7\"; } }");
        ClassFile game = compile(pool, "Game",
                "public class Game { public static String run() { return \"old\"; } }");

        com.tonic.analysis.ConstPoolRemapper remapper =
                new com.tonic.analysis.ConstPoolRemapper(sound, game);
        CodeWriter srcWriter = new CodeWriter(method(sound, "src"));
        java.util.List<com.tonic.analysis.instruction.Instruction> body = new java.util.ArrayList<>();
        srcWriter.getInstructions().forEach(body::add);
        CodeWriter.ClonedRange cloned = srcWriter.cloneRangeWithTargets(
                body.get(0), body.get(body.size() - 1), 0, game.getConstPool(), remapper::remap);

        CodeWriter gameWriter = new CodeWriter(method(game, "run"));
        gameWriter.replaceBody(cloned);
        gameWriter.write();

        Class<?> clazz = TestUtils.loadAndVerify(game);
        assertEquals("spliced-7", clazz.getMethod("run").invoke(null));
    }

    /** Compiles a single-method class onto the given pool via the YABR front end. */
    private static ClassFile compile(ClassPool pool, String name, String src) throws Exception {
        CompilationUnit cu = JavaParser.create().parse(src);
        ClassDecl cls = (ClassDecl) cu.getTypes().get(0);
        ClassFile cf = pool.createNewClass(name, new AccessBuilder().setPublic().build());
        ASTLowerer lowerer = new ASTLowerer(cf.getConstPool(), pool);
        lowerer.setCurrentClassDecl(cls);
        lowerer.setImports(cu.getImports());
        SSA ssa = new SSA(cf.getConstPool());
        for (MethodDecl m : cls.getMethods()) {
            if (m.getBody() == null) {
                continue;
            }
            cf.createNewMethodWithDescriptor(new AccessBuilder().setPublic().setStatic().build(),
                    m.getName(), "()" + m.getReturnType().toIRType().getDescriptor());
            ssa.lower(lowerer.lower(m, name), method(cf, m.getName()));
        }
        return cf;
    }

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }
}
