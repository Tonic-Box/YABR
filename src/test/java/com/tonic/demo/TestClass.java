package com.tonic.demo;

public class TestClass {

    public int simpleMethod(int a) {
        int result = a + 10;
        return result;
    }

    public long longMethod(long x, long y) {
        return x + y;
    }

    public double doubleMethod(double value) {
        return value * 2.0;
    }

    public void voidMethod() {
        int temp = 42;
    }

    public String stringMethod(String input) {
        return input + " processed";
    }
}
