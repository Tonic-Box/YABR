package com.tonic.analysis.execution.state;

public enum ValueTag {
    INT(1),
    LONG(2),
    FLOAT(1),
    DOUBLE(2),
    REFERENCE(1),
    NULL(1),
    RETURN_ADDRESS(1);

    private final int category;

    ValueTag(int category) {
        this.category = category;
    }

    public int getCategory() {
        return category;
    }

    public boolean isWide() {
        return category == 2;
    }
}
