package com.tonic.analysis.instrumentation.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.parser.constpool.Utf8Item;

/**
 * Filter based on annotation presence.
 * Matches classes/methods that have (or don't have) specific annotations.
 */
public class AnnotationFilter implements InstrumentationFilter {

    private final String annotationType;
    private final boolean matchPresent;
    private final boolean checkClass;
    private final boolean checkMethod;

    private AnnotationFilter(Builder builder) {
        this.annotationType = builder.annotationType;
        this.matchPresent = builder.matchPresent;
        this.checkClass = builder.checkClass;
        this.checkMethod = builder.checkMethod;
    }

    /** Returns the annotation type descriptor to match (e.g. {@code "Ljavax/inject/Inject;"}). */
    public String getAnnotationType() {
        return annotationType;
    }

    /** Returns whether the filter matches when the annotation is present (false matches when absent). */
    public boolean isMatchPresent() {
        return matchPresent;
    }

    /** Returns whether class annotations are checked. */
    public boolean isCheckClass() {
        return checkClass;
    }

    /** Returns whether method annotations are checked. */
    public boolean isCheckMethod() {
        return checkMethod;
    }

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String annotationType;
        private boolean matchPresent = true;
        private boolean checkClass = true;
        private boolean checkMethod = true;

        public Builder annotationType(String annotationType) {
            this.annotationType = annotationType;
            return this;
        }

        public Builder matchPresent(boolean matchPresent) {
            this.matchPresent = matchPresent;
            return this;
        }

        public Builder checkClass(boolean checkClass) {
            this.checkClass = checkClass;
            return this;
        }

        public Builder checkMethod(boolean checkMethod) {
            this.checkMethod = checkMethod;
            return this;
        }

        public AnnotationFilter build() {
            return new AnnotationFilter(this);
        }
    }
}
