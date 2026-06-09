package com.tonic.parser;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import com.tonic.utill.AccessBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code redirectClassReferences}/{@code redirectOwner} rewrites <i>every</i> reference to a
 * class — not just member-ref owners — so no live constant names the old class: bare
 * {@code CONSTANT_Class} (operands/catch_type), array class refs, and descriptors.
 */
class RedirectClassReferencesTest {

    @Test
    void rewritesEveryReferenceKind() throws Exception {
        ClassPool pool = TestUtils.emptyPool();
        ClassFile cf = pool.createNewClass("U", new AccessBuilder().setPublic().build());
        ConstPool cp = cf.getConstPool();

        cp.findOrAddClass("pkg/B");                 // bare CONSTANT_Class: new/checkcast/instanceof/catch_type
        cp.findOrAddClass("[Lpkg/B;");              // array class ref
        cp.findOrAddMethodRef("pkg/B", "m", "()V"); // member-ref owner
        cp.findOrAddUtf8("(Lpkg/B;)I");             // a method descriptor naming B
        cp.findOrAddUtf8("Lpkg/B;");                // a field descriptor naming B

        int rewritten = cf.redirectClassReferences("pkg/B", "pkg/A");
        assertTrue(rewritten >= 4, "expected every B reference rewritten, got " + rewritten);

        for (Item<?> item : cp.getItems()) {
            if (item instanceof ClassRefItem) {
                assertNotEquals("pkg/B", ((ClassRefItem) item).getClassName(),
                        "a class ref still resolves to pkg/B");
            } else if (item instanceof Utf8Item) {
                String v = ((Utf8Item) item).getValue();
                assertFalse(v != null && v.contains("Lpkg/B;"), "a descriptor still names B: " + v);
            }
        }
    }

    @Test
    void newAndInvokespecialOperandsRedirectedAtRuntime() throws Exception {
        // make(): new StringBuilder() returned as Object. After redirecting StringBuilder -> StringBuffer,
        // BOTH the `new` operand (a bare CONSTANT_Class) and the <init> member-ref owner must move, or
        // the verifier rejects `new StringBuilder; invokespecial StringBuffer.<init>`. The old
        // member-ref-only redirectOwner would leave the `new` operand naming StringBuilder.
        ClassFile cf = ClassBuilder.create("Make")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "make", "()Ljava/lang/Object;")
                .code()
                    .new_("java/lang/StringBuilder").dup()
                    .invokespecial("java/lang/StringBuilder", "<init>", "()V")
                    .areturn()
                .end().end().build();

        cf.redirectClassReferences("java/lang/StringBuilder", "java/lang/StringBuffer");

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        Object made = clazz.getMethod("make").invoke(null);
        assertEquals(StringBuffer.class, made.getClass());
    }
}
