package com.tonic.parser.attribute;

import com.tonic.parser.MemberEntry;

/**
 * Represents the RuntimeInvisibleAnnotations attribute.
 * Stores annotations that are invisible at runtime.
 */
public class RuntimeInvisibleAnnotationsAttribute extends RuntimeVisibleAnnotationsAttribute {

    public RuntimeInvisibleAnnotationsAttribute(String name, MemberEntry parent, int nameIndex, int length) {
        super(name, parent, false, nameIndex, length);
    }
}