package com.tonic.renamer.exception;

/**
 * Signals that a rename was rejected because the new name already belongs to
 * something else at the target location.
 */
public class NameConflictException extends RenameException
{

    private final String conflictingName;
    private final String existingName;
    private final String location;

    /**
     * Creates a conflict report, building the message from the three parts.
     * @param conflictingName the name the rename tried to introduce
     * @param existingName the name already in use
     * @param location where the clash occurs
     */
    public NameConflictException(String conflictingName, String existingName, String location)
    {
        super("Name conflict: '" + conflictingName + "' conflicts with existing '" + existingName + "' in " + location);
        this.conflictingName = conflictingName;
        this.existingName = existingName;
        this.location = location;
    }

    /**
     * @return the conflicting name
     */
    public String getConflictingName()
    {
        return conflictingName;
    }

    /**
     * @return the existing name
     */
    public String getExistingName()
    {
        return existingName;
    }

    /**
     * @return the location
     */
    public String getLocation()
    {
        return location;
    }
}
