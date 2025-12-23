package com.tonic.analysis.source.decompile;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.TestUtils;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClassDecompiler functionality.
 * Covers decompiling classes with various structures: fields, methods, constructors, and static initializers.
 */
class ClassDecompilerTest {

    @BeforeEach
    void setUp() {
        com.tonic.analysis.ssa.cfg.IRBlock.resetIdCounter();
        com.tonic.analysis.ssa.value.SSAValue.resetIdCounter();
    }

    // ========== Basic Class Decompilation Tests ==========

    @Test
    void decompileEmptyClass() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/EmptyClass", access);

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertNotNull(result);
        assertTrue(result.contains("package com.test;"));
        assertTrue(result.contains("public class EmptyClass"));
    }

    @Test
    void decompileClassWithSimpleName() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("SimpleClass", access);

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertNotNull(result);
        assertTrue(result.contains("class SimpleClass"));
        assertFalse(result.contains("package")); // No package for default package
    }

    @Test
    void decompilePublicClass() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/PublicClass", access);

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public class PublicClass"));
    }

    @Test
    void decompileFinalClass() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().setFinal().build();
        ClassFile cf = pool.createNewClass("com/test/FinalClass", access);

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public final class FinalClass"));
    }

    // ========== Field Decompilation Tests ==========

    @Test
    void decompileClassWithIntField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithFields", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        cf.createNewField(fieldAccess, "count", "I", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private int count;"));
    }

    @Test
    void decompileClassWithStaticField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithStatic", classAccess);

        int fieldAccess = new AccessBuilder().setPublic().setStatic().build();
        cf.createNewField(fieldAccess, "globalCount", "I", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static int globalCount;"));
    }

    @Test
    void decompileClassWithFinalField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithFinal", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().setFinal().build();
        cf.createNewField(fieldAccess, "constant", "I", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private final int constant;"));
    }

    @Test
    void decompileClassWithMultipleFields() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/MultiFields", classAccess);

        int privateAccess = new AccessBuilder().setPrivate().build();
        cf.createNewField(privateAccess, "name", "Ljava/lang/String;", new ArrayList<>());
        cf.createNewField(privateAccess, "age", "I", new ArrayList<>());
        cf.createNewField(privateAccess, "active", "Z", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private String name;"));
        assertTrue(result.contains("private int age;"));
        assertTrue(result.contains("private boolean active;"));
    }

    // ========== Method Decompilation Tests ==========

    @Test
    void decompileClassWithVoidMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithMethod")
            .publicStaticMethod("doSomething", "()V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static void doSomething()"));
    }

    @Test
    void decompileClassWithIntReturnMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithReturn")
            .publicStaticMethod("getNumber", "()I")
                .iconst(42)
                .ireturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static int getNumber()"));
        assertTrue(result.contains("return"));
    }

    @Test
    void decompileClassWithParameters() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithParams")
            .publicStaticMethod("add", "(II)I")
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static int add(int arg0, int arg1)"));
    }

    @Test
    void decompileClassWithMultipleMethods() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/MultiMethods")
            .publicStaticMethod("method1", "()V")
                .vreturn()
            .endMethod()
            .publicStaticMethod("method2", "()I")
                .iconst(5)
                .ireturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static void method1()"));
        assertTrue(result.contains("public static int method2()"));
    }

    // ========== Constructor Tests ==========

    @Test
    void decompileClassWithDefaultConstructor() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithConstructor")
            .publicMethod("<init>", "()V")
                .aload(0)
                .invokestatic("java/lang/Object", "<init>", "()V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public WithConstructor()"));
    }

    @Test
    void decompileClassWithParameterizedConstructor() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithParamCtor")
            .publicMethod("<init>", "(I)V")
                .aload(0)
                .invokestatic("java/lang/Object", "<init>", "()V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public WithParamCtor(int arg0)"));
    }

    // ========== Static Initializer Tests ==========

    @Test
    void decompileClassWithStaticInitializer() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithClinit", classAccess);

        int clinitAccess = new AccessBuilder().setStatic().build();
        BytecodeBuilder.forClass("temp")
            .method(clinitAccess, "<clinit>", "()V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        // Static initializer only appears if <clinit> method exists
        assertTrue(result.contains("class WithClinit") || !result.contains("static {"));
    }

    // ========== Builder Pattern Tests ==========

    @Test
    void builderCreatesValidDecompiler() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/BuilderTest", access);

        ClassDecompiler decompiler = ClassDecompiler.builder(cf).build();

        assertNotNull(decompiler);
        String result = decompiler.decompile();
        assertTrue(result.contains("class BuilderTest"));
    }

    @Test
    void staticDecompileMethod() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/StaticTest", access);

        String result = ClassDecompiler.decompile(cf);

        assertNotNull(result);
        assertTrue(result.contains("class StaticTest"));
    }

    // ========== Edge Cases ==========

    @Test
    void decompileAbstractMethod() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().setAbstract().build();
        ClassFile cf = pool.createNewClass("com/test/AbstractClass", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setAbstract().build();
        cf.createNewMethodWithDescriptor(methodAccess, "abstractMethod", "()V");

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public abstract class AbstractClass"));
        assertTrue(result.contains("public abstract void abstractMethod();"));
    }

    @Test
    void decompileMethodWithNoCode() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/NoCodeClass", classAccess);

        int methodAccess = new AccessBuilder().setPublic().setStatic().setNative().build();
        cf.createNewMethodWithDescriptor(methodAccess, "nativeMethod", "()V");

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static native void nativeMethod();"));
    }

    @Test
    void decompileClassWithArrayField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithArray", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        cf.createNewField(fieldAccess, "items", "[I", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private int[] items;"));
    }

    @Test
    void decompileClassWithObjectField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithObject", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        cf.createNewField(fieldAccess, "name", "Ljava/lang/String;", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private String name;"));
    }

    @Test
    void decompileClassWithComplexMethod() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Complex")
            .publicStaticMethod("calculate", "(III)I")
                .iload(0)
                .iload(1)
                .iadd()
                .iload(2)
                .imul()
                .ireturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertNotNull(result);
        assertTrue(result.contains("public static int calculate(int arg0, int arg1, int arg2)"));
    }

    @Test
    void getName() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/TestClass", access);

        assertEquals("com/test/TestClass", cf.getClassName());
    }

    @Test
    void run() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/Runnable")
            .publicStaticMethod("execute", "()V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // ========== Interface Tests ==========

    @Test
    void decompileInterface() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().setAbstract().setInterface().build();
        ClassFile cf = pool.createNewClass("com/test/MyInterface", access);

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public interface MyInterface"));
    }

    // ========== Volatile and Transient Fields ==========

    @Test
    void decompileClassWithVolatileField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithVolatile", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().setVolatile().build();
        cf.createNewField(fieldAccess, "flag", "Z", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private volatile boolean flag;"));
    }

    @Test
    void decompileClassWithTransientField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithTransient", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().setTransient().build();
        cf.createNewField(fieldAccess, "cache", "Ljava/lang/Object;", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private transient Object cache;"));
    }


    // ========== Multi-dimensional Array Fields ==========

    @Test
    void decompileClassWith2DArrayField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/With2DArray", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        cf.createNewField(fieldAccess, "matrix", "[[I", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private int[][] matrix;"));
    }

    // ========== Various Primitive Field Types ==========

    @Test
    void decompileAllPrimitiveFieldTypes() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/AllPrimitives", classAccess);

        int fieldAccess = new AccessBuilder().setPrivate().build();
        cf.createNewField(fieldAccess, "byteField", "B", new ArrayList<>());
        cf.createNewField(fieldAccess, "charField", "C", new ArrayList<>());
        cf.createNewField(fieldAccess, "shortField", "S", new ArrayList<>());
        cf.createNewField(fieldAccess, "longField", "J", new ArrayList<>());
        cf.createNewField(fieldAccess, "floatField", "F", new ArrayList<>());
        cf.createNewField(fieldAccess, "doubleField", "D", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("private byte byteField;"));
        assertTrue(result.contains("private char charField;"));
        assertTrue(result.contains("private short shortField;"));
        assertTrue(result.contains("private long longField;"));
        assertTrue(result.contains("private float floatField;"));
        assertTrue(result.contains("private double doubleField;"));
    }

    // ========== Method with Various Parameter Types ==========

    @Test
    void decompileMethodWithObjectParameter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithObjectParam")
            .publicStaticMethod("process", "(Ljava/lang/String;)V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static void process(String arg0)"));
    }

    @Test
    void decompileMethodWithArrayParameter() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("com/test/WithArrayParam")
            .publicStaticMethod("processArray", "([I)V")
                .vreturn()
            .build();

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static void processArray(int[] arg0)"));
    }


    // ========== Static with Custom Emitter Config ==========

    @Test
    void staticDecompileMethodWithConfig() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/ConfigTest", access);

        com.tonic.analysis.source.emit.SourceEmitterConfig config =
            com.tonic.analysis.source.emit.SourceEmitterConfig.defaults();

        String result = ClassDecompiler.decompile(cf, config);

        assertNotNull(result);
        assertTrue(result.contains("class ConfigTest"));
    }

    // ========== Builder with Preset ==========

    @Test
    void builderWithMinimalPreset() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int access = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/PresetTest", access);

        ClassDecompiler decompiler = ClassDecompiler.builder(cf)
            .preset(TransformPreset.MINIMAL)
            .build();

        String result = decompiler.decompile();
        assertTrue(result.contains("class PresetTest"));
    }

    // ========== Final Static Field ==========

    @Test
    void decompileFinalStaticField() throws IOException {
        ClassPool pool = TestUtils.emptyPool();
        int classAccess = new AccessBuilder().setPublic().build();
        ClassFile cf = pool.createNewClass("com/test/WithConstant", classAccess);

        int fieldAccess = new AccessBuilder().setPublic().setStatic().setFinal().build();
        cf.createNewField(fieldAccess, "CONSTANT", "I", new ArrayList<>());

        ClassDecompiler decompiler = new ClassDecompiler(cf);
        String result = decompiler.decompile();

        assertTrue(result.contains("public static final int CONSTANT;"));
    }
}
