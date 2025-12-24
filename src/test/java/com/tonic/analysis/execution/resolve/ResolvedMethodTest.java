package com.tonic.analysis.execution.resolve;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedMethodTest {

    @Test
    void constructor_shouldInitializeAllFields() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Test")
            .publicMethod("example", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "example".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertNotNull(resolved);
        assertEquals(method, resolved.getMethod());
        assertEquals(classFile, resolved.getDeclaringClass());
        assertEquals(ResolvedMethod.InvokeKind.VIRTUAL, resolved.getKind());
    }

    @Test
    void getMethod_shouldReturnCorrectMethodEntry() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Calculator")
            .publicStaticMethod("add", "(II)I")
                .iload(0)
                .iload(1)
                .iadd()
                .ireturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "add".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.STATIC);

        assertEquals(method, resolved.getMethod());
    }

    @Test
    void getDeclaringClass_shouldReturnCorrectClassFile() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Service")
            .publicMethod("process", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "process".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertEquals(classFile, resolved.getDeclaringClass());
        assertEquals("com/example/Service", classFile.getClassName());
    }

    @Test
    void getKind_shouldReturnStaticForStaticInvoke() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Utils")
            .publicStaticMethod("helper", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "helper".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.STATIC);

        assertEquals(ResolvedMethod.InvokeKind.STATIC, resolved.getKind());
    }

    @Test
    void getKind_shouldReturnVirtualForVirtualInvoke() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Bean")
            .publicMethod("getValue", "()I")
                .iconst(42)
                .ireturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "getValue".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertEquals(ResolvedMethod.InvokeKind.VIRTUAL, resolved.getKind());
    }

    @Test
    void getKind_shouldReturnSpecialForSpecialInvoke() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/Constructor")
            .publicMethod("<init>", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "<init>".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.SPECIAL);

        assertEquals(ResolvedMethod.InvokeKind.SPECIAL, resolved.getKind());
    }

    @Test
    void getKind_shouldReturnInterfaceForInterfaceInvoke() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/MyInterface")
            .publicMethod("doSomething", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "doSomething".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.INTERFACE);

        assertEquals(ResolvedMethod.InvokeKind.INTERFACE, resolved.getKind());
    }

    @Test
    void isStatic_shouldReturnTrueForStaticMethod() throws IOException {
        int staticAccess = new AccessBuilder().setPublic().setStatic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/StaticTest")
            .method(staticAccess, "staticMethod", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "staticMethod".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.STATIC);

        assertTrue(resolved.isStatic());
    }

    @Test
    void isStatic_shouldReturnFalseForInstanceMethod() throws IOException {
        int instanceAccess = new AccessBuilder().setPublic().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/InstanceTest")
            .method(instanceAccess, "instanceMethod", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "instanceMethod".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertFalse(resolved.isStatic());
    }

    @Test
    void isNative_shouldReturnTrueForNativeMethod() throws IOException {
        int nativeAccess = new AccessBuilder().setPublic().setNative().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/NativeTest").build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(nativeAccess, "nativeMethod", "()V");

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.STATIC);

        assertTrue(resolved.isNative());
    }

    @Test
    void isNative_shouldReturnFalseForNonNativeMethod() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/RegularTest")
            .publicMethod("regularMethod", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "regularMethod".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertFalse(resolved.isNative());
    }

    @Test
    void isAbstract_shouldReturnTrueForAbstractMethod() throws IOException {
        int abstractAccess = new AccessBuilder().setPublic().setAbstract().build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/AbstractTest").build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(abstractAccess, "abstractMethod", "()V");

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertTrue(resolved.isAbstract());
    }

    @Test
    void isAbstract_shouldReturnFalseForConcreteMethod() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/ConcreteTest")
            .publicMethod("concreteMethod", "()V")
                .vreturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "concreteMethod".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        assertFalse(resolved.isAbstract());
    }

    @Test
    void toString_shouldIncludeMethodInfoAndKind() throws IOException {
        ClassFile classFile = BytecodeBuilder.forClass("com/example/ToStringTest")
            .publicMethod("testMethod", "(I)Ljava/lang/String;")
                .aconst_null()
                .areturn()
            .build();
        MethodEntry method = classFile.getMethods().stream()
            .filter(m -> "testMethod".equals(m.getName()))
            .findFirst()
            .orElseThrow();

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.VIRTUAL);

        String result = resolved.toString();

        assertTrue(result.contains("ResolvedMethod{"));
        assertTrue(result.contains("com/example/ToStringTest"));
        assertTrue(result.contains("testMethod"));
        assertTrue(result.contains("(I)Ljava/lang/String;"));
        assertTrue(result.contains("VIRTUAL"));
    }

    @Test
    void invokeKindEnum_shouldHaveAllExpectedValues() {
        ResolvedMethod.InvokeKind[] kinds = ResolvedMethod.InvokeKind.values();

        assertEquals(4, kinds.length);
        assertArrayEquals(
            new ResolvedMethod.InvokeKind[] {
                ResolvedMethod.InvokeKind.STATIC,
                ResolvedMethod.InvokeKind.VIRTUAL,
                ResolvedMethod.InvokeKind.SPECIAL,
                ResolvedMethod.InvokeKind.INTERFACE
            },
            kinds
        );
    }

    @Test
    void invokeKindEnum_shouldSupportValueOf() {
        assertEquals(ResolvedMethod.InvokeKind.STATIC, ResolvedMethod.InvokeKind.valueOf("STATIC"));
        assertEquals(ResolvedMethod.InvokeKind.VIRTUAL, ResolvedMethod.InvokeKind.valueOf("VIRTUAL"));
        assertEquals(ResolvedMethod.InvokeKind.SPECIAL, ResolvedMethod.InvokeKind.valueOf("SPECIAL"));
        assertEquals(ResolvedMethod.InvokeKind.INTERFACE, ResolvedMethod.InvokeKind.valueOf("INTERFACE"));
    }

    @Test
    void accessFlagCombinations_shouldWorkCorrectly() throws IOException {
        int combinedAccess = new AccessBuilder()
            .setPublic()
            .setStatic()
            .setNative()
            .build();
        ClassFile classFile = BytecodeBuilder.forClass("com/example/CombinedTest").build();
        MethodEntry method = classFile.createNewMethodWithDescriptor(combinedAccess, "combinedMethod", "()V");

        ResolvedMethod resolved = new ResolvedMethod(method, classFile, ResolvedMethod.InvokeKind.STATIC);

        assertTrue(resolved.isStatic());
        assertTrue(resolved.isNative());
        assertFalse(resolved.isAbstract());
    }
}
