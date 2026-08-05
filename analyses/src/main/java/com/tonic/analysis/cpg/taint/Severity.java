package com.tonic.analysis.cpg.taint;

/**
 * Severity ranking for taint findings, from informational to critical.
 */
public enum Severity
{
    /**
     * No action needed; a taint path that passes through a sanitizer is reported at this level
     * whatever its sink is rated.
     */
    INFO,
    /**
     * Reaches a sink whose misuse has limited impact, worth recording but not worth blocking on.
     */
    LOW,
    /**
     * Reaches a sink that is only exploitable in some contexts, such as log injection.
     */
    MEDIUM,
    /**
     * Reaches a directly exploitable sink such as path traversal, cross-site scripting,
     * LDAP or XPath injection, server-side request forgery, or unsafe reflection.
     */
    HIGH,
    /**
     * Reaches a sink that yields code or query execution outright - SQL injection, command
     * injection, or insecure deserialization; these paths are summarized first.
     */
    CRITICAL
}
