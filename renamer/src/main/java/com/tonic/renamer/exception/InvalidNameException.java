package com.tonic.renamer.exception;

/**
 * Exception thrown when a name is not a valid Java identifier.
 */
public class InvalidNameException extends RenameException
{

    private final String invalidName;
    private final String reason;

    /**
     * Creates the failure; the name and reason are folded into the message.
     *
     * @param invalidName the name that was rejected
     * @param reason why it was rejected
     */
    public InvalidNameException(String invalidName, String reason)
    {
        super("Invalid name '" + invalidName + "': " + reason);
        this.invalidName = invalidName;
        this.reason = reason;
    }

    /**
     * @return the invalid name
     */
    public String getInvalidName()
    {
        return invalidName;
    }

    /**
     * @return the reason
     */
    public String getReason()
    {
        return reason;
    }
}
