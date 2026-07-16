package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.parser.attribute.annotation.ElementValue;
import com.tonic.parser.attribute.annotation.ElementValuePair;
import com.tonic.parser.attribute.annotation.EnumConst;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.type.AccessFlags;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationBuilderTest {

    @Nested
    class TargetPlacement {

        @Test
        void classAnnotationIsEmitted() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/Marker").end()
                    .build();

            List<Annotation> anns = visibleAnnotations(cf);
            assertEquals(1, anns.size());
            assertEquals("Lcom/example/Marker;", type(cf.getConstPool(), anns.get(0)));
        }

        @Test
        void fieldAnnotationIsEmitted() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .addField(AccessFlags.ACC_PRIVATE, "value", "I")
                        .annotate("com/example/Marker").end()
                    .end()
                    .build();

            FieldEntry field = field(cf, "value");
            List<Annotation> anns = visibleAnnotations(field);
            assertEquals(1, anns.size());
            assertEquals("Lcom/example/Marker;", type(cf.getConstPool(), anns.get(0)));
        }

        @Test
        void methodAnnotationIsEmitted() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .addMethod(AccessFlags.ACC_PUBLIC, "run", "()V")
                        .annotate("com/example/Marker").end()
                        .code().vreturn().end()
                    .end()
                    .build();

            MethodEntry method = method(cf, "run");
            List<Annotation> anns = visibleAnnotations(method);
            assertEquals(1, anns.size());
            assertEquals("Lcom/example/Marker;", type(cf.getConstPool(), anns.get(0)));
        }
    }

    @Nested
    class ValueKinds {

        @Test
        void everyElementValueKindGetsTheCorrectTag() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/All")
                        .value("i", 7)
                        .value("l", 9L)
                        .value("f", 1.5f)
                        .value("d", 2.5)
                        .value("z", true)
                        .byteValue("b", (byte) 3)
                        .charValue("c", 'x')
                        .shortValue("s", (short) 4)
                        .stringValue("str", "hello")
                        .classValue("cls", "java/lang/String")
                        .enumValue("en", "java/lang/annotation/RetentionPolicy", "RUNTIME")
                        .annotationValue("nested", AnnotationBuilder.of("com/example/Marker"))
                        .intArray("ints", 1, 2, 3)
                        .stringArray("strs", "a", "b")
                        .enumArray("policies", "java/lang/annotation/RetentionPolicy", "SOURCE", "RUNTIME")
                    .end()
                    .build();

            ConstPool pool = cf.getConstPool();
            Annotation ann = visibleAnnotations(cf).get(0);

            assertEquals('I', value(ann, "i").getTag());
            assertEquals('J', value(ann, "l").getTag());
            assertEquals('F', value(ann, "f").getTag());
            assertEquals('D', value(ann, "d").getTag());
            assertEquals('Z', value(ann, "z").getTag());
            assertEquals('B', value(ann, "b").getTag());
            assertEquals('C', value(ann, "c").getTag());
            assertEquals('S', value(ann, "s").getTag());
            assertEquals('s', value(ann, "str").getTag());
            assertEquals('c', value(ann, "cls").getTag());
            assertEquals('e', value(ann, "en").getTag());
            assertEquals('@', value(ann, "nested").getTag());
            assertEquals('[', value(ann, "ints").getTag());
            assertEquals('[', value(ann, "strs").getTag());
            assertEquals('[', value(ann, "policies").getTag());
        }

        @Test
        void scalarValuesResolveThroughTheConstantPool() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/All")
                        .value("i", 42)
                        .stringValue("str", "hello")
                        .classValue("cls", "java/lang/String")
                        .enumValue("en", "java/lang/annotation/RetentionPolicy", "RUNTIME")
                    .end()
                    .build();

            ConstPool pool = cf.getConstPool();
            Annotation ann = visibleAnnotations(cf).get(0);

            assertEquals(Integer.valueOf(42), ((IntegerItem) pool.getItem((Integer) value(ann, "i").getValue())).getValue());
            assertEquals("hello", utf8(pool, (Integer) value(ann, "str").getValue()));
            assertEquals("Ljava/lang/String;", utf8(pool, (Integer) value(ann, "cls").getValue()));

            EnumConst en = (EnumConst) value(ann, "en").getValue();
            assertEquals("Ljava/lang/annotation/RetentionPolicy;", utf8(pool, en.getTypeNameIndex()));
            assertEquals("RUNTIME", utf8(pool, en.getConstNameIndex()));
        }

        @Test
        void arrayValuesCarryTheElementTag() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/All")
                        .stringArray("strs", "a", "b", "c")
                    .end()
                    .build();

            ConstPool pool = cf.getConstPool();
            ElementValue array = value(visibleAnnotations(cf).get(0), "strs");

            @SuppressWarnings("unchecked")
            List<ElementValue> items = (List<ElementValue>) array.getValue();
            assertEquals(3, items.size());
            assertEquals('s', items.get(0).getTag());
            assertEquals("a", utf8(pool, (Integer) items.get(0).getValue()));
            assertEquals("c", utf8(pool, (Integer) items.get(2).getValue()));
        }

        @Test
        void typeNamesAndDescriptorsBothNormalize() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com.example.Dotted").end()
                    .annotate("Lcom/example/Descriptor;").end()
                    .build();

            List<Annotation> anns = visibleAnnotations(cf);
            ConstPool pool = cf.getConstPool();
            assertEquals("Lcom/example/Dotted;", type(pool, anns.get(0)));
            assertEquals("Lcom/example/Descriptor;", type(pool, anns.get(1)));
        }
    }

    @Nested
    class Visibility {

        @Test
        void visibleAndInvisibleAreSeparateAttributes() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/Seen").end()
                    .annotate("com/example/Hidden").visible(false).end()
                    .build();

            assertEquals(1, visibleAnnotations(cf).size());
            RuntimeVisibleAnnotationsAttribute invisible = annotationsAttribute(cf.getClassAttributes(), false);
            assertNotNull(invisible);
            assertEquals(1, invisible.getAnnotations().size());
        }

        @Test
        void multipleAnnotationsAppendToOneAttribute() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/A").end()
                    .annotate("com/example/B").end()
                    .build();

            long visibleAttrs = cf.getClassAttributes().stream()
                    .filter(a -> a instanceof RuntimeVisibleAnnotationsAttribute)
                    .filter(a -> ((RuntimeVisibleAnnotationsAttribute) a).isVisible())
                    .count();
            assertEquals(1, visibleAttrs);
            assertEquals(2, visibleAnnotations(cf).size());
        }
    }

    @Nested
    class ParameterAnnotations {

        @Test
        void parameterAnnotationsAreIndexedByPosition() {
            ClassFile cf = ClassBuilder.create("com/test/Anno")
                    .addMethod(AccessFlags.ACC_PUBLIC, "op", "(ILjava/lang/String;)V")
                        .annotateParameter(1, "com/example/NotNull").end()
                        .code().vreturn().end()
                    .end()
                    .build();

            MethodEntry method = method(cf, "op");
            RuntimeVisibleParameterAnnotationsAttribute attr = parameterAttribute(method);
            assertNotNull(attr);
            List<List<Annotation>> params = attr.getParameterAnnotations();
            assertEquals(2, params.size(), "table sized to parameter count");
            assertTrue(params.get(0).isEmpty());
            assertEquals(1, params.get(1).size());
            assertEquals("Lcom/example/NotNull;", type(cf.getConstPool(), params.get(1).get(0)));
        }

        @Test
        void outOfRangeParameterIndexThrows() {
            MethodBuilder mb = ClassBuilder.create("com/test/Anno")
                    .addMethod(AccessFlags.ACC_PUBLIC, "op", "(I)V");
            assertThrows(IllegalArgumentException.class, () -> mb.annotateParameter(2, "com/example/X"));
        }
    }

    @Nested
    class StandaloneAttach {

        @Test
        void attachToAppendsToExistingAttributeWithoutDuplicating() throws IOException {
            ClassFile cf = reparse(ClassBuilder.create("com/test/Anno")
                    .addMethod(AccessFlags.ACC_PUBLIC, "run", "()V")
                        .code().vreturn().end()
                    .end());

            MethodEntry method = method(cf, "run");
            ConstPool pool = cf.getConstPool();

            AnnotationBuilder.of("java/lang/Deprecated").attachTo(method, pool);
            AnnotationBuilder.of("com/example/Marker").attachTo(method, pool);

            long attrs = method.getAttributes().stream()
                    .filter(a -> a instanceof RuntimeVisibleAnnotationsAttribute).count();
            assertEquals(1, attrs, "second attach appends, not duplicates");
            assertEquals(2, visibleAnnotations(method).size());
        }
    }

    @Nested
    class RoundTrip {

        @Test
        void annotationsSurviveSerialization() throws IOException {
            ClassFile cf = reparse(ClassBuilder.create("com/test/Anno")
                    .annotate("com/example/Marker")
                        .stringValue("name", "hi")
                        .value("n", 5)
                    .end()
                    .addField(AccessFlags.ACC_PRIVATE, "f", "I")
                        .annotate("com/example/FieldMarker").end()
                    .end());

            Annotation classAnn = visibleAnnotations(cf).get(0);
            ConstPool pool = cf.getConstPool();
            assertEquals("Lcom/example/Marker;", type(pool, classAnn));
            assertEquals("hi", utf8(pool, (Integer) value(classAnn, "name").getValue()));
            assertEquals(Integer.valueOf(5), ((IntegerItem) pool.getItem((Integer) value(classAnn, "n").getValue())).getValue());

            assertEquals("Lcom/example/FieldMarker;", type(pool, visibleAnnotations(field(cf, "f")).get(0)));
        }
    }

    // Helpers -------------------------------------------------------------------------------------

    private static ClassFile reparse(ClassBuilder builder) throws IOException {
        return new ClassFile(new ByteArrayInputStream(builder.build().write()));
    }

    private static List<Annotation> visibleAnnotations(ClassFile cf) {
        RuntimeVisibleAnnotationsAttribute attr = annotationsAttribute(cf.getClassAttributes(), true);
        assertNotNull(attr, "expected a RuntimeVisibleAnnotations attribute");
        return attr.getAnnotations();
    }

    private static List<Annotation> visibleAnnotations(MemberEntry member) {
        RuntimeVisibleAnnotationsAttribute attr = annotationsAttribute(member.getAttributes(), true);
        assertNotNull(attr, "expected a RuntimeVisibleAnnotations attribute");
        return attr.getAnnotations();
    }

    private static RuntimeVisibleAnnotationsAttribute annotationsAttribute(List<Attribute> attributes, boolean visible) {
        for (Attribute attribute : attributes) {
            if (attribute instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute a = (RuntimeVisibleAnnotationsAttribute) attribute;
                if (a.isVisible() == visible) {
                    return a;
                }
            }
        }
        return null;
    }

    private static RuntimeVisibleParameterAnnotationsAttribute parameterAttribute(MethodEntry method) {
        for (Attribute attribute : method.getAttributes()) {
            if (attribute instanceof RuntimeVisibleParameterAnnotationsAttribute) {
                return (RuntimeVisibleParameterAnnotationsAttribute) attribute;
            }
        }
        return null;
    }

    private static ElementValue value(Annotation annotation, String name) {
        for (ElementValuePair pair : annotation.getElementValuePairs()) {
            if (pair.getElementName().equals(name)) {
                return pair.getValue();
            }
        }
        throw new AssertionError("no element named " + name);
    }

    private static String type(ConstPool pool, Annotation annotation) {
        return utf8(pool, annotation.getTypeIndex());
    }

    private static String utf8(ConstPool pool, int index) {
        return ((Utf8Item) pool.getItem(index)).getValue();
    }

    private static FieldEntry field(ClassFile cf, String name) {
        for (FieldEntry f : cf.getFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        throw new AssertionError("no field " + name);
    }

    private static MethodEntry method(ClassFile cf, String name) {
        for (MethodEntry m : cf.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError("no method " + name);
    }
}
