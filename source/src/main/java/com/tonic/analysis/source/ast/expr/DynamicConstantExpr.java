package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an unresolved dynamic constant (condy) with bootstrap method information.
 * <p>
 * Dynamic constants are loaded via ldc from CONSTANT_Dynamic entries (Java 11+).
 * When the bootstrap method is not a recognized pattern (like ConstantBootstraps.invoke),
 * this expression preserves the bootstrap information for display.
 * <p>
 * Output format: {@code /* condy:"name" descriptor @bsm owner.method *\/}
 */
@Getter
public final class DynamicConstantExpr implements Expression {

    /** The name of the dynamic constant. */
    private final String name;

    /** The type descriptor of the constant. */
    private final String descriptor;

    /** The bootstrap method index in the BootstrapMethods attribute. */
    private final int bootstrapMethodIndex;

    /** The owner class of the bootstrap method. */
    private final String bootstrapOwner;

    /** The name of the bootstrap method. */
    private final String bootstrapName;

    /** The descriptor of the bootstrap method. */
    private final String bootstrapDescriptor;

    /** The inferred type. */
    @Setter
    private SourceType type;

    /** Source location. */
    private final SourceLocation location;

    /** Parent AST node. */
    @Setter
    private ASTNode parent;

    /**
     * Creates a dynamic constant expression with full bootstrap information.
     */
    public DynamicConstantExpr(String name, String descriptor, int bootstrapMethodIndex,
                                String bootstrapOwner, String bootstrapName, String bootstrapDescriptor,
                                SourceType type) {
        this.name = name != null ? name : "<unknown>";
        this.descriptor = descriptor != null ? descriptor : "Ljava/lang/Object;";
        this.bootstrapMethodIndex = bootstrapMethodIndex;
        this.bootstrapOwner = bootstrapOwner != null ? bootstrapOwner : "unknown";
        this.bootstrapName = bootstrapName != null ? bootstrapName : "unknown";
        this.bootstrapDescriptor = bootstrapDescriptor != null ? bootstrapDescriptor : "";
        this.type = type != null ? type : ReferenceSourceType.OBJECT;
        this.location = SourceLocation.UNKNOWN;
    }

    /**
     * Creates a dynamic constant expression with minimal information.
     */
    public DynamicConstantExpr(String name, String descriptor, int bootstrapMethodIndex, SourceType type) {
        this(name, descriptor, bootstrapMethodIndex, "unknown", "unknown", "", type);
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitDynamicConstant(this);
    }

    /**
     * Gets a formatted string for the bootstrap method reference.
     */
    public String getFormattedBootstrapMethod() {
        return bootstrapOwner.replace('/', '.') + "." + bootstrapName;
    }

    @Override
    public String toString() {
        return String.format("/* condy:\"%s\" %s @bsm %s */",
                name, descriptor, getFormattedBootstrapMethod());
    }
}
