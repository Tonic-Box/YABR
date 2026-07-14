package com.tonic.demo;

public class DemoClass {
    public static void test(int[] arr) {
        for (int i : arr)
        {
            if(i == 0)
            {
                System.out.println("Case: 0");
            }
            else if(i == 1)
            {
                System.out.println("Case: 1");
            }
            else if(i == 2)
            {
                System.out.println("Case: 2");
            }
            else if(i == 3)
            {
                System.out.println("Case: 3");
            }
            else if(i == 4)
            {
                System.out.println("Case: 4");
            }
            else if(i > 4 && i <= 10)
            {
                if(i == 5)
                {
                    System.out.println("Case: 5");
                }
                else if(i == 6)
                {
                    System.out.println("Case: 6");
                }
                else if(i == 7)
                {
                    System.out.println("Case: 7");
                }
                else if(i == 8)
                {
                    System.out.println("Case: 8");
                }
                else if(i == 9)
                {
                    System.out.println("Case: 9");
                }
                else if(i == 10)
                {
                    System.out.println("Case: 10");
                }
            }
            else if(i == 11)
            {
                System.out.println("Case: 11");
            }
        }
    }
}
