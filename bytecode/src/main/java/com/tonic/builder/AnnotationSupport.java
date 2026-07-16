package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MemberEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import com.tonic.parser.attribute.annotation.Annotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Shared materialization/attachment helpers for {@link AnnotationBuilder}. Centralizes the
 * "append to the existing annotations attribute of matching visibility, or create one" logic so the
 * fluent builders and standalone attach share a single implementation and never emit a duplicate
 * attribute.
 */
final class AnnotationSupport {

    private AnnotationSupport() {
    }

    static void appendAnnotation(MemberEntry member, ConstPool pool, Annotation annotation, boolean visible) {
        String name = attributeName(visible);
        append(member.getAttributes(), pool, annotation, visible,
                nameIndex -> new RuntimeVisibleAnnotationsAttribute(name, member, visible, nameIndex, 0));
    }

    static void appendAnnotation(ClassFile classFile, ConstPool pool, Annotation annotation, boolean visible) {
        String name = attributeName(visible);
        append(classFile.getClassAttributes(), pool, annotation, visible,
                nameIndex -> new RuntimeVisibleAnnotationsAttribute(name, classFile, visible, nameIndex, 0));
    }

    /**
     * Emits a parameter-annotations attribute on a freshly-built method. {@code byIndex} maps a
     * parameter position to its annotations; positions with no entry get an empty list, and the
     * table is sized to the method's parameter count.
     */
    static void setParameterAnnotations(MethodEntry method, ConstPool pool,
                                        Map<Integer, List<Annotation>> byIndex, int paramCount, boolean visible) {
        List<List<Annotation>> parameters = new ArrayList<>(paramCount);
        for (int i = 0; i < paramCount; i++) {
            List<Annotation> annotations = byIndex.get(i);
            parameters.add(annotations != null ? annotations : new ArrayList<>());
        }

        String name = visible ? "RuntimeVisibleParameterAnnotations" : "RuntimeInvisibleParameterAnnotations";
        int nameIndex = pool.getIndexOf(pool.findOrAddUtf8(name));
        RuntimeVisibleParameterAnnotationsAttribute attribute =
                new RuntimeVisibleParameterAnnotationsAttribute(name, method, visible, nameIndex, 0);
        attribute.setParameterAnnotations(parameters);
        attribute.updateLength();
        method.getAttributes().add(attribute);
    }

    private static void append(List<Attribute> attributes, ConstPool pool, Annotation annotation, boolean visible,
                               IntFunction<RuntimeVisibleAnnotationsAttribute> create) {
        for (Attribute attribute : attributes) {
            if (attribute instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute existing = (RuntimeVisibleAnnotationsAttribute) attribute;
                if (existing.isVisible() == visible) {
                    if (existing.getAnnotations() == null) {
                        existing.setAnnotations(new ArrayList<>());
                    }
                    existing.getAnnotations().add(annotation);
                    return;
                }
            }
        }

        int nameIndex = pool.getIndexOf(pool.findOrAddUtf8(attributeName(visible)));
        RuntimeVisibleAnnotationsAttribute created = create.apply(nameIndex);
        List<Annotation> annotations = new ArrayList<>();
        annotations.add(annotation);
        created.setAnnotations(annotations);
        created.updateLength();
        attributes.add(created);
    }

    private static String attributeName(boolean visible) {
        return visible ? "RuntimeVisibleAnnotations" : "RuntimeInvisibleAnnotations";
    }
}
