package com.tonic.analysis.ssa.llvm;

import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.BytecodeBuilder;
import com.tonic.testutil.BytecodeBuilder.Label;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lowers methods that exercise the object/reference/runtime model with the {@code RUNTIME_ABI}
 * object model, asserting the emitted LLVM IR uses the documented {@code jvm_*} ABI, static-field
 * globals, opaque {@code ptr}s, and EH landingpads. Validates IR shape (the deliverable is lowering
 * against the ABI, not execution).
 */
class SsaToLlvmObjectModelTest {

    @BeforeEach
    void setUp() {
        TestUtils.resetSSACounters();
    }

    private String lower(ClassFile cf, String name) {
        MethodEntry method = find(cf, name);
        IRMethod ir = TestUtils.liftMethod(method);
        return new LlvmLowering(LlvmLoweringConfig.fullObjectModel()).lower(ir);
    }

    private MethodEntry find(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("method not found: " + name);
    }

    @Test
    void staticPrimitiveFieldBecomesDefinedGlobal() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("g", "()I")
            .getstatic("T", "F", "I").ireturn().build();
        String ll = lower(cf, "g");
        assertTrue(ll.contains("@\"T.F\" = global i32 zeroinitializer"), ll);
        assertTrue(ll.contains("load i32, ptr @\"T.F\""), ll);
    }

    @Test
    void externalStaticFieldIsExternalGlobal() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("g", "()I")
            .getstatic("java/lang/System", "x", "I").ireturn().build();
        String ll = lower(cf, "g");
        assertTrue(ll.contains("@\"java/lang/System.x\" = external global i32"), ll);
    }

    @Test
    void putStaticStoresToGlobal() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("s", "(I)V")
            .iload(0).putstatic("T", "F", "I").vreturn().build();
        String ll = lower(cf, "s");
        assertTrue(ll.contains("store i32 %v0, ptr @\"T.F\""), ll);
    }

    @Test
    void instanceMethodAndInstanceFieldUseAbi() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicMethod("getX", "()I")
            .aload(0).getfield("T", "x", "I").ireturn().build();
        String ll = lower(cf, "getX");
        assertTrue(ll.contains("define i32 @\"T.getX()I\"(ptr %v0)"), ll);
        assertTrue(ll.contains("call i32 @\"jvm.gf T.x I\"(ptr %v0)"), ll);
    }

    @Test
    void arrayLoadUsesKindedAbi() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("a", "([II)I")
            .aload(0).iload(1).iaload().ireturn().build();
        String ll = lower(cf, "a");
        assertTrue(ll.contains("call i32 @jvm_aload_i(ptr %v0, i32 %v1)"), ll);
    }

    @Test
    void arrayLengthUsesAbi() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("len", "([I)I")
            .aload(0).arraylength().ireturn().build();
        assertTrue(lower(cf, "len").contains("call i32 @jvm_arraylength(ptr %v0)"));
    }

    @Test
    void newAndConstructorCall() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("n", "()Ljava/lang/Object;")
            .new_("java/lang/Object").dup()
            .invokespecial("java/lang/Object", "<init>", "()V")
            .areturn().build();
        String ll = lower(cf, "n");
        assertTrue(ll.contains("call ptr @jvm_new(ptr "), ll);
        assertTrue(ll.contains("@\"java/lang/Object.<init>()V\"(ptr "), ll);
    }

    @Test
    void virtualDispatchLooksUpThenCallsIndirectly() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("h", "(Ljava/lang/Object;)I")
            .aload(0).invokevirtual("java/lang/Object", "hashCode", "()I").ireturn().build();
        String ll = lower(cf, "h");
        assertTrue(ll.contains("call ptr @jvm_vtable_lookup(ptr %v0, ptr "), ll);
        assertTrue(ll.contains("= call i32 %t"), ll);
    }

    @Test
    void checkcastAndInstanceofUseAbi() throws IOException {
        ClassFile cast = BytecodeBuilder.forClass("T").publicStaticMethod("c", "(Ljava/lang/Object;)Ljava/lang/String;")
            .aload(0).checkcast("java/lang/String").areturn().build();
        assertTrue(lower(cast, "c").contains("call ptr @jvm_checkcast(ptr %v0, ptr "));

        ClassFile io = BytecodeBuilder.forClass("T").publicStaticMethod("io", "(Ljava/lang/Object;)I")
            .aload(0).instanceof_("java/lang/String").ireturn().build();
        assertTrue(lower(io, "io").contains("call i32 @jvm_instanceof(ptr %v0, ptr "));
    }

    @Test
    void stringConstantIsInterned() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("s", "()Ljava/lang/String;")
            .ldc("hi").areturn().build();
        String ll = lower(cf, "s");
        assertTrue(ll.contains("private constant ["), ll);
        assertTrue(ll.contains("call ptr @jvm_intern_string(ptr @.str.0, i32 2)"), ll);
    }

    @Test
    void nullCheckComparesPointer() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("nz", "(Ljava/lang/Object;)I");
        Label nullLabel = mb.newLabel();
        ClassFile cf = mb
            .aload(0).ifnull(nullLabel)
            .iconst(1).ireturn()
            .label(nullLabel).iconst(0).ireturn()
            .build();
        String ll = lower(cf, "nz");
        assertTrue(ll.contains("icmp eq ptr %v0, null"), ll);
    }

    @Test
    void athrowCallsRuntimeThenUnreachable() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("t", "(Ljava/lang/Throwable;)V")
            .aload(0).athrow().build();
        String ll = lower(cf, "t");
        assertTrue(ll.contains("call void @jvm_throw(ptr %v0)"), ll);
        assertTrue(ll.contains("unreachable"), ll);
    }

    @Test
    void tryCatchEmitsInvokeAndLandingpad() throws IOException {
        BytecodeBuilder.MethodBuilder mb = BytecodeBuilder.forClass("T").publicStaticMethod("tc", "(I)I");
        Label tryStart = mb.newLabel();
        Label tryEnd = mb.newLabel();
        Label handler = mb.newLabel();
        ClassFile cf = mb
            .label(tryStart)
            .iload(0).invokestatic("T", "helper", "(I)I").istore(1)
            .label(tryEnd)
            .iload(1).ireturn()
            .label(handler)
            .astore(1).iconst(0).ireturn()
            .tryCatch(tryStart, tryEnd, handler, "java/lang/Exception")
            .build();
        String ll = lower(cf, "tc");
        assertTrue(ll.contains("personality ptr @jvm_personality"), ll);
        assertTrue(ll.contains("invoke i32 @\"T.helper(I)I\""), ll);
        assertTrue(ll.contains("landingpad { ptr, i32 } cleanup"), ll);
        assertTrue(ll.contains("call i1 @jvm_match_catch("), ll);
        assertTrue(ll.contains("call ptr @jvm_current_exception()"), ll);
    }

    @Test
    void outputIsDeterministic() throws IOException {
        ClassFile cf = BytecodeBuilder.forClass("T").publicStaticMethod("h", "(Ljava/lang/Object;)I")
            .aload(0).invokevirtual("java/lang/Object", "hashCode", "()I").ireturn().build();
        assertEquals(lower(cf, "h"), lower(cf, "h"));
    }
}
