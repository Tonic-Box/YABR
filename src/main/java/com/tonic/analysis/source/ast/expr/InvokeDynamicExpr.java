package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an unresolved invokedynamic call with bootstrap method information.
 * <p>
 * When the bootstrap method is not a recognized pattern (like LambdaMetafactory
 * or StringConcatFactory), this expression preserves the bootstrap information
 * and call arguments for display.
 * <p>
 * Output format: {@code invokedynamic("name", args) /* @bsm owner.method *\/}
 */
@Getter
public final class InvokeDynamicExpr implements Expression {

    /** The name of the invoked method. */
    private final String name;

    /** The method descriptor. */
    private final String descriptor;

    /** The arguments to the invokedynamic call. */
    private final List<Expression> arguments;

    /** The owner class of the bootstrap method. */
    private final String bootstrapOwner;

    /** The name of the bootstrap method. */
    private final String bootstrapName;

    /** The inferred return type. */
    @Setter
    private SourceType type;

    /** Source location. */
    private final SourceLocation location;

    /** Parent AST node. */
    @Setter
    private ASTNode parent;

    /**
     * Creates an invokedynamic expression with full bootstrap information.
     */
    public InvokeDynamicExpr(String name, String descriptor, List<Expression> arguments,
                              String bootstrapOwner, String bootstrapName, SourceType type) {
        this.name = name != null ? name : "<unknown>";
        this.descriptor = descriptor != null ? descriptor : "()V";
        this.arguments = arguments != null ? new ArrayList<>(arguments) : Collections.emptyList();
        this.bootstrapOwner = bootstrapOwner != null ? bootstrapOwner : "unknown";
        this.bootstrapName = bootstrapName != null ? bootstrapName : "unknown";
        this.type = type != null ? type : ReferenceSourceType.OBJECT;
        this.location = SourceLocation.UNKNOWN;
    }

    /**
     * Creates an invokedynamic expression with minimal information.
     */
    public InvokeDynamicExpr(String name, String descriptor, List<Expression> arguments, SourceType type) {
        this(name, descriptor, arguments, "unknown", "unknown", type);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitInvokeDynamic(this);
    }

    /**
     * Gets a formatted string for the bootstrap method reference.
     */
    public String getFormattedBootstrapMethod() {
        return bootstrapOwner.replace('/', '.') + "." + bootstrapName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("invokedynamic(\"").append(name).append("\"");
        if (!arguments.isEmpty()) {
            sb.append(", ");
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(arguments.get(i));
            }
        }
        sb.append(") /* @bsm ").append(getFormattedBootstrapMethod()).append(" */");
        return sb.toString();
    }
}
