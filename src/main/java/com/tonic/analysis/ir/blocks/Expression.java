package com.tonic.analysis.ir.blocks;

import com.tonic.analysis.ir.types.ExpressionType;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an expression block, typically involving branching or condition evaluation.
 */
/**
 * Represents an expression block, typically involving branching or condition evaluation.
 */
@Getter
@Setter
public class Expression extends Block {
    private ExpressionType type;

    @Override
    public String toString() {
        return "Expression [" + type + "] from " + getStartOffset() + " to " + getEndOffset();
    }
}