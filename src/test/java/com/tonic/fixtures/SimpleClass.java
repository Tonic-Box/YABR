package com.tonic.fixtures;

/**
 * Simple test fixture class with basic field and method patterns.
 * Used for: parsing tests, field access tests, method decompilation tests.
 */
public class SimpleClass {

    private int value;
    private String name;
    public static int counter = 0;

    public SimpleClass() {
        this.value = 0;
        this.name = "default";
    }

    public SimpleClass(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static int getCounter() {
        return counter;
    }

    public static void incrementCounter() {
        counter++;
    }

    public static void resetCounter() {
        counter = 0;
    }

    public boolean isEmpty() {
        return value == 0 && name == null;
    }

    @Override
    public String toString() {
        return "SimpleClass{value=" + value + ", name='" + name + "'}";
    }
}
