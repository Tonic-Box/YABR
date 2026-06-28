package com.tonic.util;

/**
 * Simple logging utility for information and error messages.
 */
public class Logger
{
    private static boolean log = false;

    public static void setLog(boolean log)
    {
        Logger.log = log;
    }

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
