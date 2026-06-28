package com.tonic.parser.attribute.annotation;

/**
 * Represents an element-value pair in an annotation.
 */
public class ElementValuePair {
    private final int nameIndex;
    private final String elementName;
    private final ElementValue value;

    public ElementValuePair(int nameIndex, String elementName, ElementValue value) {
        this.nameIndex = nameIndex;
        this.elementName = elementName;
        this.value = value;
    }

    public int getNameIndex() {
        return nameIndex;
    }

    public String getElementName() {
        return elementName;
    }

    public ElementValue getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "{" +
                "elementName='" + elementName + '\'' +
                ", value=" + value +
                '}';
    }
}