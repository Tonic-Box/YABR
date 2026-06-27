package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class ClassBuilderTest {

    @Nested
    class CreateTests {

        @Test
        void createSetsClassName() {
            ClassFile cf = ClassBuilder.create("com/test/MyClass").build();
            assertEquals("com/test/MyClass", cf.getClassName());
        }

        @Test
        void createWithSimpleName() {
            ClassFile cf = ClassBuilder.create("SimpleClass").build();
            assertEquals("SimpleClass", cf.getClassName());
        }
    }

    @Nested
    class VersionTests {

        @Test
        void versionSetsMajorMinor() {
            ClassFile cf = ClassBuilder.create("com/test/VersionTest")
                .version(55, 0)
                .build();

            assertEquals(55, cf.getMajorVersion());
            assertEquals(0, cf.getMinorVersion());
        }

        @Test
        void defaultVersionIsJava11() {
            ClassFile cf = ClassBuilder.create("com/test/DefaultVersion").build();
            assertEquals(AccessFlags.V11, cf.getMajorVersion());
        }
    }

    @Nested
    class AccessTests {

        @Test
        void accessSetsFlags() {
            ClassFile cf = ClassBuilder.create("com/test/AccessTest")
                .access(AccessFlags.ACC_PUBLIC, AccessFlags.ACC_FINAL)
                .build();

            int flags = cf.getAccess();
            assertTrue((flags & AccessFlags.ACC_PUBLIC) != 0);
            assertTrue((flags & AccessFlags.ACC_FINAL) != 0);
            assertTrue((flags & AccessFlags.ACC_SUPER) != 0);
        }

        @Test
        void defaultAccessIsPublicSuper() {
            ClassFile cf = ClassBuilder.create("com/test/DefaultAccess").build();
            int flags = cf.getAccess();
            assertTrue((flags & AccessFlags.ACC_PUBLIC) != 0);
            assertTrue((flags & AccessFlags.ACC_SUPER) != 0);
        }
    }

    @Nested
    class SuperClassTests {

        @Test
        void superClassSetsSuperName() {
            ClassFile cf = ClassBuilder.create("com/test/SuperTest")
                .superClass("java/util/ArrayList")
                .build();

            assertEquals("java/util/ArrayList", cf.getSuperClassName());
        }

        @Test
        void defaultSuperClassIsObject() {
            ClassFile cf = ClassBuilder.create("com/test/DefaultSuper").build();
            assertEquals("java/lang/Object", cf.getSuperClassName());
        }
    }

    @Nested
    class InterfaceTests {

        @Test
        void interfacesAddsInterfaces() {
            ClassFile cf = ClassBuilder.create("com/test/InterfaceTest")
                .interfaces("java/io/Serializable", "java/lang/Cloneable")
                .build();

            assertTrue(cf.getInterfaces().size() >= 2);
        }

        @Test
        void singleInterface() {
            ClassFile cf = ClassBuilder.create("com/test/SingleInterface")
                .interfaces("java/lang/Runnable")
                .build();

            assertTrue(cf.getInterfaces().size() >= 1);
        }
    }

    @Nested
    class AddFieldTests {

        @Test
        void addFieldCreatesField() {
            ClassFile cf = ClassBuilder.create("com/test/FieldTest")
                .addField(AccessFlags.ACC_PRIVATE, "value", "I")
                .end()
                .build();

            boolean hasField = false;
            for (FieldEntry field : cf.getFields()) {
                if ("value".equals(field.getName())) {
                    hasField = true;
                    break;
                }
            }
            assertTrue(hasField);
        }
    }

    @Nested
    class AddMethodTests {

        @Test
        void addMethodCreatesMethod() {
            ClassFile cf = ClassBuilder.create("com/test/MethodTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "doSomething", "()V")
                .code()
                    .vreturn()
                .end()
                .end()
                .build();

            boolean hasMethod = false;
            for (MethodEntry method : cf.getMethods()) {
                if ("doSomething".equals(method.getName())) {
                    hasMethod = true;
                    break;
                }
            }
            assertTrue(hasMethod);
        }
    }

    @Nested
    class BuildTests {

        @Test
        void buildCreatesValidClassFile() {
            ClassFile cf = ClassBuilder.create("com/test/BuildTest")
                .addMethod(AccessFlags.ACC_PUBLIC, "<init>", "()V")
                .code()
                    .aload(0)
                    .invokespecial("java/lang/Object", "<init>", "()V")
                    .vreturn()
                .end()
                .end()
                .build();

            assertNotNull(cf);
            assertNotNull(cf.getConstPool());
        }
    }

    @Nested
    class ToByteArrayTests {

        @Test
        void toByteArrayProducesValidBytecode() {
            ClassBuilder builder = ClassBuilder.create("com/test/ByteArrayTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "getValue", "()I")
                .code()
                    .iconst(42)
                    .ireturn()
                .end()
                .end();

            byte[] bytes = builder.toByteArray();

            assertNotNull(bytes);
            assertTrue(bytes.length > 0);
            assertEquals((byte) 0xCA, bytes[0]);
            assertEquals((byte) 0xFE, bytes[1]);
            assertEquals((byte) 0xBA, bytes[2]);
            assertEquals((byte) 0xBE, bytes[3]);
        }

        @Test
        void toByteArrayCallsBuildIfNeeded() {
            ClassBuilder builder = ClassBuilder.create("com/test/AutoBuildTest")
                .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "test", "()V")
                .code()
                    .vreturn()
                .end()
                .end();

            byte[] bytes = builder.toByteArray();
            assertNotNull(bytes);
            assertTrue(bytes.length > 0);
        }
    }

    @Nested
    class BootstrapMethodTests {

        @Test
        void addBootstrapMethodReturnsIndex() {
            ClassBuilder builder = ClassBuilder.create("com/test/BootstrapTest");
            ClassFile cf = builder.build();

            java.util.List<Integer> args = new java.util.ArrayList<>();
            args.add(1);
            args.add(2);

            int index = builder.addBootstrapMethod(10, args);
            assertEquals(0, index);
        }

        @Test
        void multipleBootstrapMethodsIncreaseIndex() {
            ClassBuilder builder = ClassBuilder.create("com/test/BootstrapTest");
            ClassFile cf = builder.build();

            java.util.List<Integer> args = new java.util.ArrayList<>();

            int index1 = builder.addBootstrapMethod(10, args);
            int index2 = builder.addBootstrapMethod(20, args);

            assertEquals(0, index1);
            assertEquals(1, index2);
        }
    }

    @Nested
    class AccessFlagsEdgeCases {

        @Test
        void accessWithEmptyFlagsArrayRetainsSuper() {
            ClassFile cf = ClassBuilder.create("com/test/EmptyFlagsTest")
                .access()
                .build();

            int flags = cf.getAccess();
            assertTrue((flags & AccessFlags.ACC_SUPER) != 0);
        }

        @Test
        void accessCombinesMultipleFlags() {
            ClassFile cf = ClassBuilder.create("com/test/MultiFlagsTest")
                .access(AccessFlags.ACC_PUBLIC, AccessFlags.ACC_FINAL, AccessFlags.ACC_ABSTRACT)
                .build();

            int flags = cf.getAccess();
            assertTrue((flags & AccessFlags.ACC_PUBLIC) != 0);
            assertTrue((flags & AccessFlags.ACC_FINAL) != 0);
            assertTrue((flags & AccessFlags.ACC_ABSTRACT) != 0);
            assertTrue((flags & AccessFlags.ACC_SUPER) != 0);
        }
    }

    @Nested
    class SuperClassEdgeCases {

        @Test
        void customSuperClassIsNotObject() {
            ClassFile cf = ClassBuilder.create("com/test/CustomSuperTest")
                .superClass("java/util/HashMap")
                .build();

            assertEquals("java/util/HashMap", cf.getSuperClassName());
        }
    }

    @Nested
    class InterfacesEdgeCases {

        @Test
        void noInterfacesHasEmptyList() {
            ClassFile cf = ClassBuilder.create("com/test/NoInterfacesTest")
                .build();

            assertTrue(cf.getInterfaces().isEmpty() || cf.getInterfaces().size() == 0);
        }

        @Test
        void multipleInterfaceCallsAccumulate() {
            ClassFile cf = ClassBuilder.create("com/test/MultiInterfaceTest")
                .interfaces("java/io/Serializable")
                .interfaces("java/lang/Cloneable", "java/lang/Runnable")
                .build();

            assertTrue(cf.getInterfaces().size() >= 3);
        }
    }

    @Nested
    class FluentApiTests {

        @Test
        void fluentApiAllowsChaining() {
            ClassFile cf = ClassBuilder.create("com/test/FluentTest")
                .version(52, 0)
                .access(AccessFlags.ACC_PUBLIC)
                .superClass("java/lang/Object")
                .interfaces("java/io/Serializable")
                .addField(AccessFlags.ACC_PRIVATE, "field", "I").end()
                .addMethod(AccessFlags.ACC_PUBLIC, "<init>", "()V")
                .code()
                    .aload(0)
                    .invokespecial("java/lang/Object", "<init>", "()V")
                    .vreturn()
                .end()
                .end()
                .build();

            assertNotNull(cf);
            assertEquals("com/test/FluentTest", cf.getClassName());
        }
    }
}
