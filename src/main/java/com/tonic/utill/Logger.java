package com.tonic.utill;

import lombok.Setter;

/**
 * Simple logging utility for information and error messages.
 */
public class Logger
{
    @Setter
    private static boolean log = false;

    /**
     * Logs an informational message to standard output.
     *
     * @param message the message to log
     */
    public static void info(String message)
    {
        if(!log)
            return;
        System.out.println("[INFO] " + message);
    }

    /**
     * Logs an error message to standard error.
     *
     * @param message the error message to log
     */
    public static void error(String message)
    {
        if(!log)
            return;
        System.err.println("[ERROR] " + message);
    }
}
