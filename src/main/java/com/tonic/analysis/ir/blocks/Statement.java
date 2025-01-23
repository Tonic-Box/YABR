package com.tonic.analysis.ir.blocks;

import com.tonic.analysis.ir.types.StatementType;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a statement block, typically involving actions like storing values or method invocations.
 */
@Getter
@Setter
public class Statement extends Block {
    private StatementType type;

    @Override
    public String toString() {
        return "Statement [" + type + "] from " + getStartOffset() + " to " + getEndOffset();
    }
}