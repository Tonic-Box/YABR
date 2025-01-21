package com.tonic.utill;

import lombok.Setter;

public class Logger
{
    @Setter
    private static boolean log = false;

    public static void info(String message)
    {
        if(!log)
            return;
        System.out.println("[INFO] " + message);
    }

    public static void error(String message)
    {
        if(!log)
            return;
        System.err.println("[ERROR] " + message);
    }
}
