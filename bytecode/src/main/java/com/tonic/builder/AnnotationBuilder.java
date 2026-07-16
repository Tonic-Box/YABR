package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.parser.attribute.annotation.ElementValue;
import com.tonic.parser.attribute.annotation.ElementValuePair;
import com.tonic.parser.attribute.annotation.EnumConst;
import com.tonic.util.DescriptorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for a single annotation. Accumulates element-value pairs and materializes a
 * {@link Annotation} against a constant pool, hiding the element-value tag rules and index
 * bookkeeping the class-file format requires.
 *
 * <p>Use {@link #of(String)} to build one standalone (for a nested value or to
 * {@link #attachTo(MemberEntry, ConstPool) attach} to an already-loaded member), or obtain one
 * bound to a parent via the {@code annotate(...)} method on {@link ClassBuilder},
 * {@link FieldBuilder}, or {@link MethodBuilder}; {@link #end()} returns to that parent.
 *
 * @param <P> the parent builder type returned by {@link #end()} ({@link Void} when standalone)
 */
public class AnnotationBuilder<P> {

    @FunctionalInterface
    private interface ValueFactory {
        ElementValue create(ConstPool pool);
    }

    private static final class Element {
        final String name;
        final ValueFactory factory;

        Element(String name, ValueFactory factory) {
            this.name = name;
            this.factory = factory;
        }
    }

    private final P parent;
    private final String typeDescriptor;
    private boolean visible = true;
    private final List<Element> elements = new ArrayList<>();

    private AnnotationBuilder(P parent, String type) {
        this.parent = parent;
        this.typeDescriptor = toTypeDescriptor(type);
    }

    /**
     * Starts a standalone annotation of the given type. The type may be an internal name
     * ({@code com/example/Foo}), a dotted name ({@code com.example.Foo}), or a field descriptor
     * ({@code Lcom/example/Foo;}).
     */
    public static AnnotationBuilder<Void> of(String type) {
        return new AnnotationBuilder<>(null, type);
    }

    static <P> AnnotationBuilder<P> forParent(P parent, String type) {
        return new AnnotationBuilder<>(parent, type);
    }

    /** Marks this annotation runtime-visible (default) or invisible. */
    public AnnotationBuilder<P> visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isVisible() {
        return visible;
    }

    /** Returns the parent builder this annotation was opened from ({@code null} when standalone). */
    public P end() {
        return parent;
    }

    // Scalar values -------------------------------------------------------------------------------

    public AnnotationBuilder<P> value(String name, int v) {
        return add(name, intFactory('I', v));
    }

    public AnnotationBuilder<P> value(String name, boolean v) {
        return add(name, intFactory('Z', v ? 1 : 0));
    }

    public AnnotationBuilder<P> value(String name, long v) {
        return add(name, longFactory(v));
    }

    public AnnotationBuilder<P> value(String name, float v) {
        return add(name, floatFactory(v));
    }

    public AnnotationBuilder<P> value(String name, double v) {
        return add(name, doubleFactory(v));
    }

    public AnnotationBuilder<P> byteValue(String name, byte v) {
        return add(name, intFactory('B', v));
    }

    public AnnotationBuilder<P> charValue(String name, char v) {
        return add(name, intFactory('C', v));
    }

    public AnnotationBuilder<P> shortValue(String name, short v) {
        return add(name, intFactory('S', v));
    }

    public AnnotationBuilder<P> stringValue(String name, String v) {
        return add(name, stringFactory(v));
    }

    /** A class literal value; accepts a class name (internal or dotted) or a raw type descriptor. */
    public AnnotationBuilder<P> classValue(String name, String type) {
        return add(name, classFactory(type));
    }

    public AnnotationBuilder<P> enumValue(String name, String enumType, String constant) {
        return add(name, enumFactory(enumType, constant));
    }

    public AnnotationBuilder<P> annotationValue(String name, AnnotationBuilder<?> annotation) {
        return add(name, annotationFactory(annotation));
    }

    // Array values --------------------------------------------------------------------------------

    public AnnotationBuilder<P> intArray(String name, int... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (int v : values) {
            items.add(intFactory('I', v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> booleanArray(String name, boolean... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (boolean v : values) {
            items.add(intFactory('Z', v ? 1 : 0));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> byteArray(String name, byte... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (byte v : values) {
            items.add(intFactory('B', v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> charArray(String name, char... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (char v : values) {
            items.add(intFactory('C', v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> shortArray(String name, short... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (short v : values) {
            items.add(intFactory('S', v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> longArray(String name, long... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (long v : values) {
            items.add(longFactory(v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> floatArray(String name, float... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (float v : values) {
            items.add(floatFactory(v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> doubleArray(String name, double... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (double v : values) {
            items.add(doubleFactory(v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> stringArray(String name, String... values) {
        List<ValueFactory> items = new ArrayList<>(values.length);
        for (String v : values) {
            items.add(stringFactory(v));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> classArray(String name, String... types) {
        List<ValueFactory> items = new ArrayList<>(types.length);
        for (String t : types) {
            items.add(classFactory(t));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> enumArray(String name, String enumType, String... constants) {
        List<ValueFactory> items = new ArrayList<>(constants.length);
        for (String c : constants) {
            items.add(enumFactory(enumType, c));
        }
        return add(name, arrayFactory(items));
    }

    public AnnotationBuilder<P> annotationArray(String name, AnnotationBuilder<?>... annotations) {
        List<ValueFactory> items = new ArrayList<>(annotations.length);
        for (AnnotationBuilder<?> a : annotations) {
            items.add(annotationFactory(a));
        }
        return add(name, arrayFactory(items));
    }

    // Materialization -----------------------------------------------------------------------------

    /** Materializes this specification into an {@link Annotation}, adding entries to {@code pool}. */
    public Annotation build(ConstPool pool) {
        int typeIndex = pool.getIndexOf(pool.findOrAddUtf8(typeDescriptor));
        List<ElementValuePair> pairs = new ArrayList<>(elements.size());
        for (Element element : elements) {
            int nameIndex = pool.getIndexOf(pool.findOrAddUtf8(element.name));
            ElementValue value = element.factory.create(pool);
            pairs.add(new ElementValuePair(nameIndex, element.name, value));
        }
        return new Annotation(pool, typeIndex, pairs.size(), pairs);
    }

    /**
     * Attaches this annotation to a field or method, appending to the existing
     * {@code Runtime[In]VisibleAnnotations} attribute of matching visibility when present, or
     * creating one otherwise.
     */
    public void attachTo(MemberEntry member, ConstPool pool) {
        AnnotationSupport.appendAnnotation(member, pool, build(pool), visible);
    }

    /** Attaches this annotation to a class. */
    public void attachTo(ClassFile classFile, ConstPool pool) {
        AnnotationSupport.appendAnnotation(classFile, pool, build(pool), visible);
    }

    // Internals -----------------------------------------------------------------------------------

    private AnnotationBuilder<P> add(String name, ValueFactory factory) {
        elements.add(new Element(name, factory));
        return this;
    }

    private static ValueFactory intFactory(char tag, int v) {
        return pool -> new ElementValue(tag, pool.getIndexOf(pool.findOrAddInteger(v)));
    }

    private static ValueFactory longFactory(long v) {
        return pool -> new ElementValue('J', pool.getIndexOf(pool.findOrAddLong(v)));
    }

    private static ValueFactory floatFactory(float v) {
        return pool -> new ElementValue('F', pool.getIndexOf(pool.findOrAddFloat(v)));
    }

    private static ValueFactory doubleFactory(double v) {
        return pool -> new ElementValue('D', pool.getIndexOf(pool.findOrAddDouble(v)));
    }

    private static ValueFactory stringFactory(String v) {
        return pool -> new ElementValue('s', pool.getIndexOf(pool.findOrAddUtf8(v)));
    }

    private static ValueFactory classFactory(String type) {
        String descriptor = toClassDescriptor(type);
        return pool -> new ElementValue('c', pool.getIndexOf(pool.findOrAddUtf8(descriptor)));
    }

    private static ValueFactory enumFactory(String enumType, String constant) {
        String descriptor = toTypeDescriptor(enumType);
        return pool -> {
            int typeNameIndex = pool.getIndexOf(pool.findOrAddUtf8(descriptor));
            int constNameIndex = pool.getIndexOf(pool.findOrAddUtf8(constant));
            return new ElementValue('e', new EnumConst(pool, typeNameIndex, constNameIndex));
        };
    }

    private static ValueFactory annotationFactory(AnnotationBuilder<?> annotation) {
        return pool -> new ElementValue('@', annotation.build(pool));
    }

    private static ValueFactory arrayFactory(List<ValueFactory> items) {
        return pool -> {
            List<ElementValue> values = new ArrayList<>(items.size());
            for (ValueFactory item : items) {
                values.add(item.create(pool));
            }
            return new ElementValue('[', values);
        };
    }

    /** Normalizes an annotation/enum type to a field descriptor ({@code Lcom/example/Foo;}). */
    private static String toTypeDescriptor(String type) {
        if (type.length() >= 2 && type.charAt(0) == 'L' && type.charAt(type.length() - 1) == ';') {
            return type;
        }
        return DescriptorUtil.toObjectDescriptor(type.replace('.', '/'));
    }

    /**
     * Normalizes a class-literal value to a type descriptor. Raw descriptors (array {@code [...},
     * primitive/{@code void} single letters, or an object {@code L...;}) pass through; anything else
     * is treated as a class name.
     */
    private static String toClassDescriptor(String type) {
        if (type.isEmpty()) {
            return type;
        }
        char first = type.charAt(0);
        if (first == '[') {
            return type;
        }
        if (type.length() == 1 && "VZBCSIJFD".indexOf(first) >= 0) {
            return type;
        }
        if (first == 'L' && type.charAt(type.length() - 1) == ';') {
            return type;
        }
        return DescriptorUtil.toObjectDescriptor(type.replace('.', '/'));
    }
}
