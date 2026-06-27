package com.tonic.renamer.exception;

/**
 * Exception thrown when a name is not a valid Java identifier.
 */
public class InvalidNameException extends RenameException {

    private final String invalidName;
    private final String reason;

    public InvalidNameException(String invalidName, String reason) {
        super("Invalid name '" + invalidName + "': " + reason);
        this.invalidName = invalidName;
        this.reason = reason;
    }

    public String getInvalidName() {
        return invalidName;
    }

    public String getReason() {
        return reason;
    }
}
