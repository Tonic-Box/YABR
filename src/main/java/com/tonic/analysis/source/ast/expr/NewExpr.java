package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.NodeList;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.ReferenceSourceType;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import com.tonic.utill.ClassNameUtil;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Objects;

/**
 * Represents a new object expression: new Type(args) or outer.new Inner(args)
 */
@Getter
public final class NewExpr implements Expression {

    /**
     * The enclosing instance for inner class creation (e.g., outer.new Inner()).
     * Null for regular class instantiation.
     */
    @Setter
    private Expression enclosingInstance;
    /**
     * The class being instantiated (in internal format).
     */
    private final String className;
    private final NodeList<Expression> arguments;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public NewExpr(Expression enclosingInstance, String className, List<Expression> arguments,
                   SourceType type, SourceLocation location) {
        this.arguments = new NodeList<>(this);
        this.enclosingInstance = enclosingInstance;
        this.className = Objects.requireNonNull(className, "className cannot be null");
        this.type = type != null ? type : new ReferenceSourceType(className);
        this.location = location != null ? location : SourceLocation.UNKNOWN;

        if (enclosingInstance != null) {
            enclosingInstance.setParent(this);
        }
        if (arguments != null) {
            this.arguments.addAll(arguments);
        }
    }

    public NewExpr(String className, List<Expression> arguments, SourceType type, SourceLocation location) {
        this(null, className, arguments, type, location);
    }

    public NewExpr(String className, List<Expression> arguments, SourceType type) {
        this(className, arguments, type, SourceLocation.UNKNOWN);
    }

    public NewExpr(String className, List<Expression> arguments) {
        this(className, arguments, null, SourceLocation.UNKNOWN);
    }

    public NewExpr(String className) {
        this(className, List.of(), null, SourceLocation.UNKNOWN);
    }

    /**
     * Adds an argument to this constructor call.
     */
    public void addArgument(Expression arg) {
        arguments.add(arg);
    }

    /**
     * Gets the number of arguments.
     */
    public int getArgumentCount() {
        return arguments.size();
    }

    /**
     * Gets the simple class name.
     */
    public String getSimpleName() {
        return ClassNameUtil.getSimpleNameWithInnerClasses(className);
    }

    /**
     * Returns true if this is an inner class creation with an enclosing instance.
     */
    public boolean isInnerClassCreation() {
        return enclosingInstance != null;
    }

    public NewExpr withEnclosingInstance(Expression enclosingInstance) {
        if (this.enclosingInstance != null) this.enclosingInstance.setParent(null);
        this.enclosingInstance = enclosingInstance;
        if (enclosingInstance != null) enclosingInstance.setParent(this);
        return this;
    }

    @Override
    public java.util.List<ASTNode> getChildren() {
        java.util.List<ASTNode> children = new java.util.ArrayList<>();
        if (enclosingInstance != null) children.add(enclosingInstance);
        children.addAll(arguments);
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitNew(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (enclosingInstance != null) {
            sb.append(enclosingInstance).append(".");
        }
        sb.append("new ").append(getSimpleName()).append("(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(arguments.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
