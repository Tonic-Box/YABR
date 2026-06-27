package com.tonic.analysis.bytecode;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the {@code tableswitch}/{@code lookupswitch} builder ops: the offset-dependent
 * {@code SizedOp.getSizeAt} sizing (4-byte-aligned padding) and label resolution, validated by loading
 * the built class (the verifier rejects a mis-padded or mis-targeted switch) and running each arm.
 */
class SwitchBuilderTest {

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void tableswitchDispatches() throws Exception {
        ClassFile cf = ClassBuilder.create("TS")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "pick", "(I)I")
                .code()
                    .iload(0)
                    .tableswitch(0, 2, "d", "c0", "c1", "c2")
                    .label("c0").iconst(10).ireturn()
                    .label("c1").iconst(20).ireturn()
                    .label("c2").iconst(30).ireturn()
                    .label("d").iconst(-1).ireturn()
                .end().end().build();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(10, (int) clazz.getMethod("pick", int.class).invoke(null, 0));
        assertEquals(20, (int) clazz.getMethod("pick", int.class).invoke(null, 1));
        assertEquals(30, (int) clazz.getMethod("pick", int.class).invoke(null, 2));
        assertEquals(-1, (int) clazz.getMethod("pick", int.class).invoke(null, 7));
    }

    @Test
    void lookupswitchDispatches() throws Exception {
        Map<Integer, String> cases = new LinkedHashMap<>();
        cases.put(100, "lb");   // intentionally out of order — lookupswitch sorts keys
        cases.put(1, "la");

        ClassFile cf = ClassBuilder.create("LS")
                .version(AccessFlags.V11, 0).access(AccessFlags.ACC_PUBLIC)
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "lpick", "(I)I")
                .code()
                    .iload(0)
                    .lookupswitch("ld", cases)
                    .label("la").iconst(1).ireturn()
                    .label("lb").iconst(2).ireturn()
                    .label("ld").iconst(0).ireturn()
                .end().end().build();

        Class<?> clazz = TestUtils.loadAndVerify(cf);
        assertEquals(1, (int) clazz.getMethod("lpick", int.class).invoke(null, 1));
        assertEquals(2, (int) clazz.getMethod("lpick", int.class).invoke(null, 100));
        assertEquals(0, (int) clazz.getMethod("lpick", int.class).invoke(null, 7));
    }
}
