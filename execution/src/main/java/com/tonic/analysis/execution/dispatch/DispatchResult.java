package com.tonic.analysis.execution.dispatch;

public enum DispatchResult {
    CONTINUE,
    BRANCH,
    INVOKE,
    RETURN,
    THROW,
    ATHROW,
    FIELD_GET,
    FIELD_PUT,
    NEW_OBJECT,
    NEW_ARRAY,
    CHECKCAST,
    INSTANCEOF
}
