package com.tonic.renamer.exception;

/**
 * Exception thrown when a rename would cause a naming conflict.
 */
public class NameConflictException extends RenameException {

    private final String conflictingName;
    private final String existingName;
    private final String location;

    public NameConflictException(String conflictingName, String existingName, String location) {
        super("Name conflict: '" + conflictingName + "' conflicts with existing '" + existingName + "' in " + location);
        this.conflictingName = conflictingName;
        this.existingName = existingName;
        this.location = location;
    }

    public String getConflictingName() {
        return conflictingName;
    }

    public String getExistingName() {
        return existingName;
    }

    public String getLocation() {
        return location;
    }
}
