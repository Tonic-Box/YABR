package com.tonic.analysis.visitor;

import java.io.IOException;

public interface Visitor<T> {
    void process(T t) throws IOException;
}
