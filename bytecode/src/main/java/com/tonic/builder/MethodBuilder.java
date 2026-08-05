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

/**
 * Fluent builder for one method of a ClassBuilder, holding its code, thrown types, frame sizes, and annotations
 * until the enclosing class is built.
 */
public class MethodBuilder
{

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

    MethodBuilder(ClassBuilder parent, int access, String name, String descriptor)
    {
        this.parent = parent;
        this.access = access;
        this.name = name;
        this.descriptor = descriptor;
    }

    /**
     * Opens the method body, creating the code builder on first call and returning the same one afterwards.
     * @return the code builder for this method
     */
    public CodeBuilder code()
    {
        if (codeBuilder == null)
        {
            codeBuilder = new CodeBuilder(this);
        }
        return codeBuilder;
    }

    /**
     * Opens an annotation on this method; call {@link AnnotationBuilder#end()} to return here.
     * @param type the annotation type, as an internal or descriptor name
     * @return the annotation builder
     */
    public AnnotationBuilder<MethodBuilder> annotate(String type)
    {
        AnnotationBuilder<MethodBuilder> annotation = AnnotationBuilder.forParent(this, type);
        annotations.add(annotation);
        return annotation;
    }

    /**
     * Opens an annotation on one parameter; call {@link AnnotationBuilder#end()} to return here.
     * @param index 0-based parameter position
     * @param type the annotation type, as an internal or descriptor name
     * @return the annotation builder
     * @throws IllegalArgumentException if the position is not valid for this method's descriptor
     */
    public AnnotationBuilder<MethodBuilder> annotateParameter(int index, String type)
    {
        int paramCount = DescriptorUtil.countParameters(descriptor);
        if (index < 0 || index >= paramCount)
        {
            throw new IllegalArgumentException("Parameter index " + index + " out of range for "
                    + descriptor + " (" + paramCount + " parameter(s))");
        }
        AnnotationBuilder<MethodBuilder> annotation = AnnotationBuilder.forParent(this, type);
        parameterAnnotations.add(new ParamAnnotation(index, annotation));
        return annotation;
    }

    /**
     * Adds types to the Exceptions attribute emitted for this method.
     * @param exceptionTypes internal names of the declared thrown types
     * @return this builder
     */
    public MethodBuilder exceptions(String... exceptionTypes)
    {
        Collections.addAll(exceptions, exceptionTypes);
        return this;
    }

    /**
     * Overrides the computed max_stack of the Code attribute.
     * @param maxStack the operand stack size to write
     * @return this builder
     */
    public MethodBuilder maxStack(int maxStack)
    {
        this.maxStack = maxStack;
        return this;
    }

    /**
     * Overrides the computed max_locals of the Code attribute.
     * @param maxLocals the local slot count to write
     * @return this builder
     */
    public MethodBuilder maxLocals(int maxLocals)
    {
        this.maxLocals = maxLocals;
        return this;
    }

    /**
     * Closes this method.
     * @return the enclosing class builder
     */
    public ClassBuilder end()
    {
        return parent;
    }

    ClassBuilder getParent()
    {
        return parent;
    }

    int getAccess()
    {
        return access;
    }

    String getName()
    {
        return name;
    }

    String getDescriptor()
    {
        return descriptor;
    }

    void buildMethod(ClassFile classFile, ConstPool constPool) throws IOException
    {
        MethodEntry method = classFile.createNewMethodWithDescriptor(access, name, descriptor);

        if (codeBuilder != null)
        {
            codeBuilder.buildCode(method, constPool);
        }

        if(!exceptions.isEmpty())
        {
            int nameIndex = constPool.utf8Index("Exceptions");
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

        if (maxStack != null && method.getCodeAttribute() != null)
        {
            method.getCodeAttribute().setMaxStack(maxStack);
        }
        if (maxLocals != null && method.getCodeAttribute() != null)
        {
            method.getCodeAttribute().setMaxLocals(maxLocals);
        }

        for (AnnotationBuilder<MethodBuilder> annotation : annotations)
        {
            annotation.attachTo(method, constPool);
        }

        if (!parameterAnnotations.isEmpty())
        {
            int paramCount = DescriptorUtil.countParameters(descriptor);
            Map<Integer, List<Annotation>> visibleByIndex = new HashMap<>();
            Map<Integer, List<Annotation>> invisibleByIndex = new HashMap<>();
            for (ParamAnnotation pa : parameterAnnotations)
            {
                Annotation annotation = pa.builder.build(constPool);
                Map<Integer, List<Annotation>> target = pa.builder.isVisible() ? visibleByIndex : invisibleByIndex;
                target.computeIfAbsent(pa.index, k -> new ArrayList<>()).add(annotation);
            }
            if (!visibleByIndex.isEmpty())
            {
                AnnotationSupport.setParameterAnnotations(method, constPool, visibleByIndex, paramCount, true);
            }
            if (!invisibleByIndex.isEmpty())
            {
                AnnotationSupport.setParameterAnnotations(method, constPool, invisibleByIndex, paramCount, false);
            }
        }
    }

    private static final class ParamAnnotation
    {
        final int index;
        final AnnotationBuilder<MethodBuilder> builder;

        ParamAnnotation(int index, AnnotationBuilder<MethodBuilder> builder)
        {
            this.index = index;
            this.builder = builder;
        }
    }
}
