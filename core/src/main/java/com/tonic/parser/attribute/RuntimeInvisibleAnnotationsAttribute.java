package com.tonic.parser.attribute;

import com.tonic.parser.MemberEntry;

/**
 * The RuntimeInvisibleAnnotations attribute: annotations not retained for runtime reflection.
 */
public class RuntimeInvisibleAnnotationsAttribute extends RuntimeVisibleAnnotationsAttribute
{

    /**
     * Creates the attribute shell for parsing, attached to a member, with visibility fixed to false.
     * @param name the attribute name
     * @param parent the member the attribute belongs to
     * @param nameIndex constant-pool index of the name Utf8
     * @param length the attribute length in bytes
     */
    public RuntimeInvisibleAnnotationsAttribute(String name, MemberEntry parent, int nameIndex, int length)
    {
        super(name, parent, false, nameIndex, length);
    }
}