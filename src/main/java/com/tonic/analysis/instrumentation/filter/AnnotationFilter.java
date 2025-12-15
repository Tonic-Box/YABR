package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;
import lombok.Builder;
import lombok.Getter;

/**
 * Filter based on annotation presence.
 * Matches classes/methods that have (or don't have) specific annotations.
 */
@Getter
@Builder
public class AnnotationFilter implements InstrumentationFilter {

    /** Annotation type descriptor to match (e.g., "Ljavax/inject/Inject;") */
    private final String annotationType;

    /** If true, matches when annotation is present; if false, matches when absent */
    @Builder.Default
    private final boolean matchPresent = true;

    /** Whether to check class annotations */
    @Builder.Default
    private final boolean checkClass = true;

    /** Whether to check method annotations */
    @Builder.Default
    private final boolean checkMethod = true;

    @Override
    public boolean matchesClass(ClassFile classFile) {
        if (!checkClass) return true;

        boolean hasAnnotation = hasAnnotation(classFile);
        return matchPresent == hasAnnotation;
    }

    @Override
    public boolean matchesMethod(MethodEntry method) {
        if (!checkMethod) return true;

        boolean hasAnnotation = hasAnnotation(method);
        return matchPresent == hasAnnotation;
    }

    @Override
    public boolean matchesField(String fieldOwner, String fieldName, String fieldDescriptor) {
        // Field annotation checking would require additional context
        return true;
    }

    @Override
    public boolean matchesMethodCall(String owner, String name, String descriptor) {
        // Method call annotation checking would require class resolution
        return true;
    }

    private boolean hasAnnotation(ClassFile classFile) {
        RuntimeVisibleAnnotationsAttribute annots = findAnnotationsAttribute(classFile.getClassAttributes());
        if (annots == null) return false;

        for (Annotation annotation : annots.getAnnotations()) {
            String type = getAnnotationType(annotation, classFile);
            if (annotationType.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(MethodEntry method) {
        RuntimeVisibleAnnotationsAttribute annots = findAnnotationsAttribute(method.getAttributes());
        if (annots == null) return false;

        ClassFile classFile = method.getClassFile();
        for (Annotation annotation : annots.getAnnotations()) {
            String type = getAnnotationType(annotation, classFile);
            if (annotationType.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private RuntimeVisibleAnnotationsAttribute findAnnotationsAttribute(java.util.List<Attribute> attributes) {
        if (attributes == null) return null;
        for (Attribute attr : attributes) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                return (RuntimeVisibleAnnotationsAttribute) attr;
            }
        }
        return null;
    }

    private String getAnnotationType(Annotation annotation, ClassFile classFile) {
        int typeIndex = annotation.getTypeIndex();
        var item = classFile.getConstPool().getItem(typeIndex);
        if (item instanceof Utf8Item) {
            return ((Utf8Item) item).getValue();
        }
        return "";
    }

    /**
     * Creates a filter that matches methods with the given annotation.
     */
    public static AnnotationFilter forAnnotation(String annotationType) {
        return AnnotationFilter.builder()
                .annotationType(annotationType)
                .matchPresent(true)
                .build();
    }

    /**
     * Creates a filter that matches methods without the given annotation.
     */
    public static AnnotationFilter withoutAnnotation(String annotationType) {
        return AnnotationFilter.builder()
                .annotationType(annotationType)
                .matchPresent(false)
                .build();
    }
}
