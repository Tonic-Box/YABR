package com.tonic.renamer.mapping;

/**
 * Interface for all rename mapping types.
 */
public interface RenameMapping {

    /**
     * Gets the old (original) name being renamed.
     */
    String getOldName();

    /**
     * Gets the new name to rename to.
     */
    String getNewName();
}
