package com.tonic.parser.attribute.annotation;

/**
 * An annotation element-value pair: the element name and its value.
 */
public class ElementValuePair
{
    private final int nameIndex;
    private final String elementName;
    private final ElementValue value;

    /**
     * Creates an element-value pair.
     * @param nameIndex constant-pool index of the element name Utf8
     * @param elementName the resolved element name
     * @param value the element value
     */
    public ElementValuePair(int nameIndex, String elementName, ElementValue value)
    {
        this.nameIndex = nameIndex;
        this.elementName = elementName;
        this.value = value;
    }

    /**
     * @return the name index
     */
    public int getNameIndex()
    {
        return nameIndex;
    }

    /**
     * @return the element name
     */
    public String getElementName()
    {
        return elementName;
    }

    /**
     * @return the value
     */
    public ElementValue getValue()
    {
        return value;
    }

    @Override
    public String toString()
    {
        return "{" +
                "elementName='" + elementName + '\'' +
                ", value=" + value +
                '}';
    }
}