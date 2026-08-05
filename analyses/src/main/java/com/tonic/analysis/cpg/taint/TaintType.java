package com.tonic.analysis.cpg.taint;

/**
 * Origin categories for tainted data.
 */
public enum TaintType
{
    /**
     * Data supplied by a person at the boundary - request parameters, headers, cookies,
     * console reads.
     */
    USER_INPUT,
    /**
     * Data read off the local filesystem through a stream or reader.
     */
    FILE_INPUT,
    /**
     * Data read from a socket or URL connection.
     */
    NETWORK,
    /**
     * Data read back out of a datastore, which may itself have been stored tainted.
     */
    DATABASE,
    /**
     * Data taken from environment variables or system properties.
     */
    ENVIRONMENT,
    /**
     * Data reconstructed from a serialized byte stream, so its type and contents are
     * attacker-controlled.
     */
    DESERIALIZATION,
    /**
     * Data obtained through reflective lookup, where the target member is chosen at run time.
     */
    REFLECTION,
    /**
     * Data from a source the caller registered themselves rather than one of the built-in
     * source definitions.
     */
    CUSTOM
}
