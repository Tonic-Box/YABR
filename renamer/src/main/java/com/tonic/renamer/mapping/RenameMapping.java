package com.tonic.renamer.mapping;

/**
 * Interface for all rename mapping types.
 */
public interface RenameMapping
{

    /**
     * @return the old (original) name being renamed
     */
    String getOldName();

    /**
     * @return the new name to rename to
     */
    String getNewName();
}
