package com.tonic.builder;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.ExceptionsAttribute;
import com.tonic.parser.attribute.annotation.Annotation;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.util.DescriptorUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodBuilder {

    private final ClassBuilder parent;
    private final int access;
    private final String name;
    private final String descriptor;
    private CodeBuilder codeBuilder;
    private final List<String> exceptions = new ArrayList<>();
    private Integer maxStack;
    private Integer maxLocals;
    private final List<AnnotationBuilder<MethodBuilder>> annotations = new ArrayList<>();
    private final List<ParamAnnotation> parameterAnnotations = new ArrayList<>();

    MethodBuilder(ClassBuilder parent, int access, String name, String descriptor) {
        this.parent = parent;
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
    }

    public CodeBuilder code() {
        if (codeBuilder == null) {
            codeBuilder = new CodeBuilder(this);
        }
        return codeBuilder;
    }

    /** Opens an annotation on this method; call {@link AnnotationBuilder#end()} to return here. */
    public AnnotationBuilder<MethodBuilder> annotate(String type) {
        AnnotationBuilder<MethodBuilder> annotation = AnnotationBuilder.forParent(this, type);
        annotations.add(annotation);
        return annotation;
    }

    /**
     * Opens an annotation on the parameter at {@code index} (0-based); call
     * {@link AnnotationBuilder#end()} to return here.
     *
     * @throws IllegalArgumentException if {@code index} is not a valid parameter position for this
     *                                  method's descriptor
     */
    public AnnotationBuilder<MethodBuilder> annotateParameter(int index, String type) {
        int paramCount = DescriptorUtil.countParameters(descriptor);
        if (index < 0 || index >= paramCount) {
            throw new IllegalArgumentException("Parameter index " + index + " out of range for "
                    + descriptor + " (" + paramCount + " parameter(s))");
        }
        AnnotationBuilder<MethodBuilder> annotation = AnnotationBuilder.forParent(this, type);
        parameterAnnotations.add(new ParamAnnotation(index, annotation));
        return annotation;
    }

    public MethodBuilder exceptions(String... exceptionTypes) {
        Collections.addAll(exceptions, exceptionTypes);
        return this;
    }

    public MethodBuilder maxStack(int maxStack) {
        this.maxStack = maxStack;
        return this;
    }

    public MethodBuilder maxLocals(int maxLocals) {
        this.maxLocals = maxLocals;
        return this;
    }

    public ClassBuilder end() {
        return parent;
    }

    ClassBuilder getParent() {
        return parent;
    }

    int getAccess() {
        return access;
    }

    String getName() {
        return name;
    }

    String getDescriptor() {
        return descriptor;
    }

    void buildMethod(ClassFile classFile, ConstPool constPool) throws IOException {
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, name, descriptor);

        if (codeBuilder != null) {
            codeBuilder.buildCode(method, constPool);
        }

        if(!exceptions.isEmpty())
        {
            int nameIndex = constPool.getIndexOf(constPool.findOrAddUtf8("Exceptions"));
            ExceptionsAttribute exAttr = new ExceptionsAttribute("Exceptions", method, nameIndex, 0);
            ClassRefItem type;
            for(String ex : exceptions)
            {
                type = constPool.findOrAddClass(ex);
                exAttr.getExceptionIndexTable().add(constPool.getIndexOf(type));
            }
            exAttr.updateLength();
            method.getAttributes().add(exAttr);
        }

        if (maxStack != null && method.getCodeAttribute() != null) {
            method.getCodeAttribute().setMaxStack(maxStack);
        }
        if (maxLocals != null && method.getCodeAttribute() != null) {
            method.getCodeAttribute().setMaxLocals(maxLocals);
        }

        for (AnnotationBuilder<MethodBuilder> annotation : annotations) {
            annotation.attachTo(method, constPool);
        }

        if (!parameterAnnotations.isEmpty()) {
            int paramCount = DescriptorUtil.countParameters(descriptor);
            Map<Integer, List<Annotation>> visibleByIndex = new HashMap<>();
            Map<Integer, List<Annotation>> invisibleByIndex = new HashMap<>();
            for (ParamAnnotation pa : parameterAnnotations) {
                Annotation annotation = pa.builder.build(constPool);
                Map<Integer, List<Annotation>> target = pa.builder.isVisible() ? visibleByIndex : invisibleByIndex;
                target.computeIfAbsent(pa.index, k -> new ArrayList<>()).add(annotation);
            }
            if (!visibleByIndex.isEmpty()) {
                AnnotationSupport.setParameterAnnotations(method, constPool, visibleByIndex, paramCount, true);
            }
            if (!invisibleByIndex.isEmpty()) {
                AnnotationSupport.setParameterAnnotations(method, constPool, invisibleByIndex, paramCount, false);
            }
        }
    }

    private static final class ParamAnnotation {
        final int index;
        final AnnotationBuilder<MethodBuilder> builder;

        ParamAnnotation(int index, AnnotationBuilder<MethodBuilder> builder) {
            this.index = index;
            this.builder = builder;
        }
    }
}
