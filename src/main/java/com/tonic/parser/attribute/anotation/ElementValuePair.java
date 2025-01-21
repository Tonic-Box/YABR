package com.tonic.parser.attribute.anotation;

import lombok.Getter;

/**
 * Represents an element-value pair in an annotation.
 */
@Getter
public class ElementValuePair {
    private final int nameIndex;
    private final String elementName;
    private final ElementValue value;

    public ElementValuePair(int nameIndex, String elementName, ElementValue value) {
        this.nameIndex = nameIndex;
        this.elementName = elementName;
        this.value = value;
    }

    @Override
    public String toString() {
        return "{" +
                "elementName='" + elementName + '\'' +
                ", value=" + value +
                '}';
    }
}