package com.tonic.analysis.common;

import com.tonic.utill.ClassNameUtil;
import com.tonic.utill.DescriptorUtil;

import java.util.Objects;

/**
 * Represents a reference to a method identified by owner class, name, and descriptor.
 * Used as a key in call graphs, cross-references, and other analysis APIs.
 *
 * <p>This class consolidates functionality previously split between:
 * <ul>
 *   <li>{@code com.tonic.analysis.callgraph.MethodReference}</li>
 *   <li>{@code com.tonic.analysis.xref.MethodReference}</li>
 * </ul>
 */
public class MethodReference {

    private final String owner;
    private final String name;
    private final String descriptor;

    /**
     * Creates a new method reference.
     *
     * @param owner      the internal name of the class that owns this method (e.g., "java/lang/String")
     * @param name       the method name (e.g., "toString", "&lt;init&gt;")
     * @param descriptor the method descriptor (e.g., "()Ljava/lang/String;")
     */
    public MethodReference(String owner, String name, String descriptor) {
        this.owner = owner != null ? owner : "";
        this.name = name != null ? name : "";
        this.descriptor = descriptor != null ? descriptor : "";
    }

    /**
     * Gets the owner class internal name.
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Gets the method name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the method descriptor.
     */
    public String getDescriptor() {
        return descriptor;
    }

    // ========== From callgraph.MethodReference ==========

    /**
     * Returns a fully qualified method signature: "owner.name descriptor"
     */
    public String getFullSignature() {
        return owner + "." + name + descriptor;
    }

    /**
     * Checks if this is a constructor (&lt;init&gt;).
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Checks if this is a static initializer (&lt;clinit&gt;).
     */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }

    // ========== From xref.MethodReference ==========

    /**
     * Returns a human-readable display string like "com.example.MyClass.methodName()"
     */
    public String getDisplayName() {
        String className = ClassNameUtil.toSourceName(owner);
        return className + "." + name + parseDescriptorForDisplay();
    }

    /**
     * Returns a short display name without the full class path.
     */
    public String getShortDisplayName() {
        String simpleClass = ClassNameUtil.getSimpleNameWithInnerClasses(owner);
        return simpleClass + "." + name + parseDescriptorForDisplay();
    }

    /**
     * Returns a fully qualified reference string including descriptor.
     * Alias for {@link #getFullSignature()} for backwards compatibility.
     */
    public String getFullReference() {
        return getFullSignature();
    }

    /**
     * Parse the method descriptor and return a simplified parameter representation.
     */
    private String parseDescriptorForDisplay() {
        if (descriptor == null || descriptor.isEmpty() || !descriptor.startsWith("(")) {
            return "()";
        }

        int paramCount = DescriptorUtil.countParameters(descriptor);

        if (paramCount == 0) {
            return "()";
        } else if (paramCount == 1) {
            return "(...)";
        } else {
            return "(" + paramCount + " params)";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodReference)) return false;
        MethodReference that = (MethodReference) o;
        return Objects.equals(owner, that.owner) &&
               Objects.equals(name, that.name) &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, descriptor);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
