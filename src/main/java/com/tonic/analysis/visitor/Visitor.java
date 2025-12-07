package com.tonic.analysis.visitor;

import java.io.IOException;

/**
 * Base visitor interface for processing various bytecode elements.
 *
 * @param <T> the type of element to visit
 */
public interface Visitor<T> {
    /**
     * Processes the given element.
     *
     * @param t the element to process
     * @throws IOException if an I/O error occurs during processing
     */
    void process(T t) throws IOException;
}
