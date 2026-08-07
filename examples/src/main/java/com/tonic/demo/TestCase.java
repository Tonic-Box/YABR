package com.tonic.demo;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo fixture exercising a spread of bytecode shapes: constants, loops, switches, and listener stubs.
 */
public class TestCase implements MouseListener
{
    // A static final int constant
    private static final int MY_CONST = 42;

    // A protected static field (String)
    protected static String fieldTest = "Test string";

    // A regular instance field
    private final int instanceField;

    /**
     * Initializes the instance field from the class constant.
     */
    public TestCase()
    {
        this.instanceField = MY_CONST - 10;
    }

    /**
     * Exercises Java 11 features such as var locals and new String methods.
     * @param args unused
     */
    public static void _main(String[] args)
    {
        // Java 11 local variable type inference with 'var'
        var localVar = MY_CONST + 1;
        System.out.println("localVar = " + localVar);

        // A simple for-loop using 'var'
        for (var i = 0; i < 3; i++)
        {
            var s = fieldTest + " " + i;
            System.out.println("Loop output: " + s);
        }

        // Using a method that returns a Class<?> reference
        var testObj = new TestCase();
        Class<?> result = testObj.dummyMethod_ayo(10, true, (byte)5);
        System.out.println("dummyMethod returned: " + result);

        // Java 11 added string methods (strip, isBlank, lines) for demonstration
        var spaced = "  Hello from Java 11  ".strip();
        if (!spaced.isBlank())
        {
            spaced.lines().forEach(line -> System.out.println("Line: " + line));
        }
    }

    /**
     * Exercises boolean and byte arguments plus a switch that selects a class literal.
     * @param x the switch selector
     * @param b guards the early null return
     * @param c unused byte argument
     * @return a class literal chosen by the arguments, or null on the early exit
     */
    public Class<?> dummyMethod_ayo(int x, boolean b, byte c)
    {
        var testLocal = "someValue";
        if (testLocal.contains("some") && b)
        {
            System.out.println("dummyMethod: condition met");
            return null;
        }
        switch(x)
        {
            case 0:
            {
                return Byte.class;
            }
            case 1:
            {
                return Integer.class;
            }
            case 2:
            {
                return long.class;
            }
        }
        System.out.println("dummyMethod: returning the class reference");
        return TestCase.class;
    }

    /**
     * Multiplies the argument and throws when the product is at most twelve.
     * @param arg the value to scale
     * @throws RuntimeException if the scaled value is not greater than twelve
     */
    public void testMethod(int arg)
    {
        int i = arg * 1234321;
        if(i <= 12)
        {
            throw new RuntimeException();
        }
        Object test = Class.class.getClassLoader();
    }

    /**
     * @return always null
     */
    public Object objReturn()
    {
        return null;
    }

    /**
     * Exercises list iteration and loop arithmetic to produce varied stack shapes.
     */
    public void stackDemoMethod()
    {
        List<String> list = new ArrayList<>();
        list.add("Java");
        list.add("ClassFile");
        list.add("Parser");

        for (String item : list)
        {
            System.out.println("Item: " + item);
        }

        // Example of simple arithmetic to create stack frames
        int sum = 0;
        for (int i = 0; i < 3; i++)
        {
            sum += i;
        }
        System.out.println("Sum in stackDemoMethod = " + sum);
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {

    }

    @Override
    public void mousePressed(MouseEvent e)
    {

    }

    @Override
    public void mouseReleased(MouseEvent e)
    {

    }

    @Override
    public void mouseEntered(MouseEvent e)
    {

    }

    @Override
    public void mouseExited(MouseEvent e)
    {

    }
}
