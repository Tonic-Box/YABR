package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A switch expression (Java 14, JEP 361): {@code switch (sel) { case L -> result; default -> r; }}.
 * Each arm yields a value; the expression's type is the common type of the arm results. Only the
 * arrow/yield value form is modeled (block-bodied arms reduce to a single result expression here).
 */
@Getter
public final class SwitchExpr implements Expression {

    /**
     * One arm. A constant-label arm has {@code labels} (empty when {@code isDefault}); a type-pattern
     * arm (Java 21) instead has a {@code patternType} and optional {@code patternBinding}, rendered as
     * {@code case Type binding -> result}.
     */
    @Getter
    public static final class Arm {
        private final List<Expression> labels;
        private final boolean isDefault;
        private final com.tonic.analysis.source.ast.type.SourceType patternType;
        private final String patternBinding;
        private final List<Component> deconstructionComponents;
        /** The {@code when} guard of a guarded pattern arm, or null. */
        private final Expression guard;
        private Expression result;

        public Arm(List<Expression> labels, boolean isDefault, Expression result) {
            this(labels, isDefault, null, null, null, null, result);
        }

        public Arm(List<Expression> labels, boolean isDefault,
                   com.tonic.analysis.source.ast.type.SourceType patternType, String patternBinding,
                   Expression result) {
            this(labels, isDefault, patternType, patternBinding, null, null, result);
        }

        public Arm(List<Expression> labels, boolean isDefault,
                   com.tonic.analysis.source.ast.type.SourceType patternType, String patternBinding,
                   List<Component> deconstructionComponents, Expression result) {
            this(labels, isDefault, patternType, patternBinding, deconstructionComponents, null, result);
        }

        public Arm(List<Expression> labels, boolean isDefault,
                   com.tonic.analysis.source.ast.type.SourceType patternType, String patternBinding,
                   List<Component> deconstructionComponents, Expression guard, Expression result) {
            this.labels = labels != null ? labels : new ArrayList<>();
            this.isDefault = isDefault;
            this.patternType = patternType;
            this.patternBinding = patternBinding;
            this.deconstructionComponents = deconstructionComponents;
            this.guard = guard;
            this.result = result;
        }

        public boolean isTypePattern() {
            return patternType != null;
        }

        public boolean isRecordDeconstruction() {
            return patternType != null && deconstructionComponents != null
                    && !deconstructionComponents.isEmpty();
        }

        public void setResult(Expression result) {
            this.result = result;
        }
    }

    /** One component of a record-deconstruction pattern: {@code type binding}. */
    @Getter
    public static final class Component {
        private final SourceType type;
        private final String binding;

        public Component(SourceType type, String binding) {
            this.type = type;
            this.binding = binding;
        }
    }

    @Setter
    private Expression selector;
    private final List<Arm> arms;
    private final SourceType type;
    private final SourceLocation location;
    @Setter
    private ASTNode parent;

    public SwitchExpr(Expression selector, List<Arm> arms, SourceType type, SourceLocation location) {
        this.selector = selector;
        this.arms = arms != null ? arms : new ArrayList<>();
        this.type = type;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        if (selector != null) {
            selector.setParent(this);
        }
        for (Arm arm : this.arms) {
            for (Expression label : arm.getLabels()) {
                if (label != null) label.setParent(this);
            }
            if (arm.getResult() != null) arm.getResult().setParent(this);
        }
    }

    public SwitchExpr(Expression selector, List<Arm> arms, SourceType type) {
        this(selector, arms, type, SourceLocation.UNKNOWN);
    }

    @Override
    public SourceType getType() {
        return type;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>();
        if (selector != null) children.add(selector);
        for (Arm arm : arms) {
            children.addAll(arm.getLabels());
            if (arm.getGuard() != null) children.add(arm.getGuard());
            if (arm.getResult() != null) children.add(arm.getResult());
        }
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor) {
        return visitor.visitSwitchExpr(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("switch (").append(selector).append(") { ");
        for (Arm arm : arms) {
            if (arm.isDefault()) {
                sb.append("default -> ").append(arm.getResult()).append("; ");
            } else {
                sb.append("case ").append(arm.getLabels()).append(" -> ").append(arm.getResult()).append("; ");
            }
        }
        return sb.append("}").toString();
    }
}
