package com.tonic.util;

/**
 * Console logger for informational and error messages, silent unless logging is switched on.
 */
public class Logger
{
    private static boolean log = false;

    /**
     * Switches logging on or off process-wide; when off, info and error calls print nothing.
     * @param log true to emit messages
     */
    public static void setLog(boolean log)
    {
        Logger.log = log;
    }

    /**
     * Logs an informational message to standard output.
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
     * @param message the error message to log
     */
    public static void error(String message)
    {
        if(!log)
            return;
        System.err.println("[ERROR] " + message);
    }
}
