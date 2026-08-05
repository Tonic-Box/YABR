package com.tonic.analysis.source.ast.expr;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.SourceLocation;
import com.tonic.analysis.source.ast.type.SourceType;
import com.tonic.analysis.source.visitor.SourceVisitor;
import java.util.ArrayList;
import java.util.List;

/**
 * A switch expression (Java 14, JEP 361): {@code switch (sel) { case L -> result; default -> r; }}.
 * Each arm yields a value; the expression's type is the common type of the arm results. Only the
 * arrow/yield value form is modeled (block-bodied arms reduce to a single result expression here).
 */
public final class SwitchExpr implements Expression
{

    /**
     * One arm. A constant-label arm has {@code labels} (empty when {@code isDefault}); a type-pattern
     * arm (Java 21) instead has a {@code patternType} and optional {@code patternBinding}, rendered as
     * {@code case Type binding -> result}.
     */
    public static final class Arm
    {
        private final List<Expression> labels;
        private final boolean isDefault;
        private final SourceType patternType;
        private final String patternBinding;
        private final List<Component> deconstructionComponents;
        /**
         * The {@code when} guard of a guarded pattern arm, or null.
         */
        private final Expression guard;
        private Expression result;

        public Arm(List<Expression> labels, boolean isDefault, Expression result)
        {
            this(labels, isDefault, null, null, null, null, result);
        }

        public Arm(List<Expression> labels, boolean isDefault, SourceType patternType, String patternBinding, Expression result)
        {
            this(labels, isDefault, patternType, patternBinding, null, null, result);
        }

        public Arm(List<Expression> labels, boolean isDefault, SourceType patternType, String patternBinding, List<Component> deconstructionComponents, Expression result)
        {
            this(labels, isDefault, patternType, patternBinding, deconstructionComponents, null, result);
        }

        public Arm(List<Expression> labels, boolean isDefault, SourceType patternType, String patternBinding, List<Component> deconstructionComponents, Expression guard, Expression result)
        {
            this.labels = labels != null ? labels : new ArrayList<>();
            this.isDefault = isDefault;
            this.patternType = patternType;
            this.patternBinding = patternBinding;
            this.deconstructionComponents = deconstructionComponents;
            this.guard = guard;
            this.result = result;
        }

        /**
         * @return the labels
         */
        public List<Expression> getLabels()
        {
            return labels;
        }

        /**
         * @return whether default
         */
        public boolean isDefault()
        {
            return isDefault;
        }

        /**
         * @return the pattern type
         */
        public SourceType getPatternType()
        {
            return patternType;
        }

        /**
         * @return the pattern binding
         */
        public String getPatternBinding()
        {
            return patternBinding;
        }

        /**
         * @return the deconstruction components
         */
        public List<Component> getDeconstructionComponents()
        {
            return deconstructionComponents;
        }

        /**
         * @return the guard
         */
        public Expression getGuard()
        {
            return guard;
        }

        /**
         * @return the result
         */
        public Expression getResult()
        {
            return result;
        }

        /**
         * @return true if the arm matches on a type pattern rather than constant labels
         */
        public boolean isTypePattern()
        {
            return patternType != null;
        }

        /**
         * @return true if the arm is a type pattern that also destructures record components
         */
        public boolean isRecordDeconstruction()
        {
            return patternType != null && deconstructionComponents != null
                    && !deconstructionComponents.isEmpty();
        }

        /**
         * Replaces the value the arm yields; the parent link is not updated.
         * @param result the new result expression
         */
        public void setResult(Expression result)
        {
            this.result = result;
        }
    }

    /**
     * One component of a record-deconstruction pattern: {@code type binding}.
     */
    public static final class Component
    {
        private final SourceType type;
        private final String binding;

        public Component(SourceType type, String binding)
        {
            this.type = type;
            this.binding = binding;
        }

        /**
         * @return the type
         */
        public SourceType getType()
        {
            return type;
        }

        /**
         * @return the binding
         */
        public String getBinding()
        {
            return binding;
        }
    }

    private Expression selector;
    private final List<Arm> arms;
    private final SourceType type;
    private final SourceLocation location;
    private ASTNode parent;

    /**
     * Creates a switch expression and adopts the selector, every label, and every arm result as children.
     * @param selector the value switched on
     * @param arms the arms, or null for none
     * @param type the type the expression yields
     * @param location the source position, or null for UNKNOWN
     */
    public SwitchExpr(Expression selector, List<Arm> arms, SourceType type, SourceLocation location)
    {
        this.selector = selector;
        this.arms = arms != null ? arms : new ArrayList<>();
        this.type = type;
        this.location = location != null ? location : SourceLocation.UNKNOWN;
        if (selector != null)
        {
            selector.setParent(this);
        }
        for (Arm arm : this.arms)
        {
            for (Expression label : arm.getLabels())
            {
                if (label != null) label.setParent(this);
            }
            if (arm.getResult() != null) arm.getResult().setParent(this);
        }
    }

    /**
     * Creates a switch expression with no source position.
     * @param selector the value switched on
     * @param arms the arms, or null for none
     * @param type the type the expression yields
     */
    public SwitchExpr(Expression selector, List<Arm> arms, SourceType type)
    {
        this(selector, arms, type, SourceLocation.UNKNOWN);
    }

    /**
     * @return the selector
     */
    public Expression getSelector()
    {
        return selector;
    }

    /**
     * Replaces the value switched on, adopting the new selector and releasing the old one.
     * @param selector the new selector, or null
     */
    public void setSelector(Expression selector)
    {
        ASTNode previous = this.selector;
        this.selector = selector;
        if (selector != null)
        {
            selector.setParent(this);
        }
        ASTNode.releaseFormerChild(previous, this);
    }

    /**
     * @return the arms
     */
    public List<Arm> getArms()
    {
        return arms;
    }

    /**
     * @return the location
     */
    public SourceLocation getLocation()
    {
        return location;
    }

    /**
     * @return the parent
     */
    public ASTNode getParent()
    {
        return parent;
    }

    /**
     * Records the node this expression hangs under.
     * @param parent the enclosing node
     */
    public void setParent(ASTNode parent)
    {
        this.parent = parent;
    }

    @Override
    public SourceType getType()
    {
        return type;
    }

    @Override
    public List<ASTNode> getChildren()
    {
        List<ASTNode> children = new ArrayList<>();
        if (selector != null) children.add(selector);
        for (Arm arm : arms)
        {
            children.addAll(arm.getLabels());
            if (arm.getGuard() != null) children.add(arm.getGuard());
            if (arm.getResult() != null) children.add(arm.getResult());
        }
        return children;
    }

    @Override
    public <T> T accept(SourceVisitor<T> visitor)
    {
        return visitor.visitSwitchExpr(this);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder("switch (").append(selector).append(") { ");
        for (Arm arm : arms)
        {
            if (arm.isDefault())
            {
                sb.append("default -> ").append(arm.getResult()).append("; ");
            }
            else
            {
                sb.append("case ").append(arm.getLabels()).append(" -> ").append(arm.getResult()).append("; ");
            }
        }
        return sb.append("}").toString();
    }
}
