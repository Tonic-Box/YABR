package com.tonic.analysis.common;

import com.tonic.util.ClassNameUtil;
import com.tonic.util.DescriptorUtil;

import java.util.Objects;

/**
 * An immutable method identity of owner class, name, and descriptor.
 */
public class MethodReference
{

    private final String owner;
    private final String name;
    private final String descriptor;

    /**
     * Creates a new method reference.
     * @param owner      the internal name of the class that owns this method (e.g., "java/lang/String")
     * @param name       the method name (e.g., "toString", "&lt;init&gt;")
     * @param descriptor the method descriptor (e.g., "()Ljava/lang/String;")
     */
    public MethodReference(String owner, String name, String descriptor)
    {
        this.owner = owner != null ? owner : "";
        this.name = name != null ? name : "";
        this.descriptor = descriptor != null ? descriptor : "";
    }

    /**
     * @return the internal name of the owning class
     */
    public String getOwner()
    {
        return owner;
    }

    /**
     * @return the method name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the method descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    // From callgraph.MethodReference

    /**
     * Concatenates owner, name and descriptor into a single identifying string.
     *
     * @return the signature, such as "java/lang/String.toString()Ljava/lang/String;"
     */
    public String getFullSignature()
    {
        return owner + "." + name + descriptor;
    }

    /**
     * Tests whether the name is the constructor name.
     *
     * @return true if the name is &lt;init&gt;
     */
    public boolean isConstructor()
    {
        return "<init>".equals(name);
    }

    /**
     * Tests whether the name is the static initializer name.
     *
     * @return true if the name is &lt;clinit&gt;
     */
    public boolean isStaticInitializer()
    {
        return "<clinit>".equals(name);
    }

    // From xref.MethodReference

    /**
     * Renders the reference with the dotted class name and a summarized parameter list.
     *
     * @return the display string, such as "com.example.MyClass.methodName()"
     */
    public String getDisplayName()
    {
        String className = ClassNameUtil.toSourceName(owner);
        return className + "." + name + parseDescriptorForDisplay();
    }

    /**
     * Renders the reference with the simple class name and a summarized parameter list.
     *
     * @return the short display string
     */
    public String getShortDisplayName()
    {
        String simpleClass = ClassNameUtil.getSimpleNameWithInnerClasses(owner);
        return simpleClass + "." + name + parseDescriptorForDisplay();
    }

    /**
     * Alias for {@link #getFullSignature()}.
     *
     * @return the fully qualified signature
     */
    public String getFullReference()
    {
        return getFullSignature();
    }

    /**
     * Parse the method descriptor and return a simplified parameter representation.
     */
    private String parseDescriptorForDisplay()
    {
        if (descriptor == null || descriptor.isEmpty() || !descriptor.startsWith("("))
        {
            return "()";
        }

        int paramCount = DescriptorUtil.countParameters(descriptor);

        if (paramCount == 0)
        {
            return "()";
        }
        else if (paramCount == 1)
        {
            return "(...)";
        }
        else
        {
            return "(" + paramCount + " params)";
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof MethodReference)) return false;
        MethodReference that = (MethodReference) o;
        return Objects.equals(owner, that.owner) &&
               Objects.equals(name, that.name) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public String toString()
    {
        return getDisplayName();
    }
}
