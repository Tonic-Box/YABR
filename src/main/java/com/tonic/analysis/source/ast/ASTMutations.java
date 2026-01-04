package com.tonic.analysis.source.ast;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for AST mutation operations including deep cloning,
 * node replacement, and node removal.
 */
public final class ASTMutations {

    private ASTMutations() {}

    /**
     * Creates a deep clone of an AST node and its subtree.
     * The cloned tree has no parent (root of a new tree).
     */
    @SuppressWarnings("unchecked")
    public static <T extends ASTNode> T deepClone(T node) {
        if (node == null) return null;

        ASTNode cloned = cloneNode(node);
        return (T) cloned;
    }

    private static ASTNode cloneNode(ASTNode node) {
        if (node == null) return null;

        if (node instanceof Expression) {
            return cloneExpression((Expression) node);
        } else if (node instanceof Statement) {
            return cloneStatement((Statement) node);
        }
        throw new UnsupportedOperationException("Cannot clone node of type: " + node.getClass().getName());
    }

    private static Expression cloneExpression(Expression expr) {
        if (expr instanceof BinaryExpr) {
            BinaryExpr e = (BinaryExpr) expr;
            return new BinaryExpr(
                e.getOperator(),
                deepClone(e.getLeft()),
                deepClone(e.getRight()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof UnaryExpr) {
            UnaryExpr e = (UnaryExpr) expr;
            return new UnaryExpr(
                e.getOperator(),
                deepClone(e.getOperand()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof LiteralExpr) {
            LiteralExpr e = (LiteralExpr) expr;
            return new LiteralExpr(e.getValue(), e.getType(), e.getLocation());
        } else if (expr instanceof VarRefExpr) {
            VarRefExpr e = (VarRefExpr) expr;
            return new VarRefExpr(e.getName(), e.getType(), e.getSsaValue(), e.getLocation());
        } else if (expr instanceof MethodCallExpr) {
            MethodCallExpr e = (MethodCallExpr) expr;
            return new MethodCallExpr(
                deepClone(e.getReceiver()),
                e.getMethodName(),
                e.getOwnerClass(),
                cloneExprList(e.getArguments()),
                e.isStatic(),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof FieldAccessExpr) {
            FieldAccessExpr e = (FieldAccessExpr) expr;
            return new FieldAccessExpr(
                deepClone(e.getReceiver()),
                e.getFieldName(),
                e.getOwnerClass(),
                e.isStatic(),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof ArrayAccessExpr) {
            ArrayAccessExpr e = (ArrayAccessExpr) expr;
            return new ArrayAccessExpr(
                deepClone(e.getArray()),
                deepClone(e.getIndex()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof TernaryExpr) {
            TernaryExpr e = (TernaryExpr) expr;
            return new TernaryExpr(
                deepClone(e.getCondition()),
                deepClone(e.getThenExpr()),
                deepClone(e.getElseExpr()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof CastExpr) {
            CastExpr e = (CastExpr) expr;
            return new CastExpr(
                e.getTargetType(),
                deepClone(e.getExpression()),
                e.getLocation()
            );
        } else if (expr instanceof InstanceOfExpr) {
            InstanceOfExpr e = (InstanceOfExpr) expr;
            return new InstanceOfExpr(
                deepClone(e.getExpression()),
                e.getCheckType(),
                e.getPatternVariable(),
                e.getLocation()
            );
        } else if (expr instanceof NewExpr) {
            NewExpr e = (NewExpr) expr;
            return new NewExpr(
                e.getClassName(),
                cloneExprList(e.getArguments()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof NewArrayExpr) {
            NewArrayExpr e = (NewArrayExpr) expr;
            return new NewArrayExpr(
                e.getElementType(),
                cloneExprList(e.getDimensions()),
                deepClone(e.getInitializer()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof ArrayInitExpr) {
            ArrayInitExpr e = (ArrayInitExpr) expr;
            return new ArrayInitExpr(
                cloneExprList(e.getElements()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr e = (LambdaExpr) expr;
            return new LambdaExpr(
                e.getParameters(),
                deepClone(e.getBody()),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof MethodRefExpr) {
            MethodRefExpr e = (MethodRefExpr) expr;
            return new MethodRefExpr(
                deepClone(e.getReceiver()),
                e.getMethodName(),
                e.getOwnerClass(),
                e.getKind(),
                e.getType(),
                e.getLocation()
            );
        } else if (expr instanceof ClassExpr) {
            ClassExpr e = (ClassExpr) expr;
            return new ClassExpr(e.getClassType(), e.getLocation());
        } else if (expr instanceof ThisExpr) {
            ThisExpr e = (ThisExpr) expr;
            return new ThisExpr(e.getType(), e.getLocation());
        }

        throw new UnsupportedOperationException("Cannot clone expression of type: " + expr.getClass().getName());
    }

    private static Statement cloneStatement(Statement stmt) {
        if (stmt instanceof BlockStmt) {
            BlockStmt s = (BlockStmt) stmt;
            return new BlockStmt(cloneStmtList(s.getStatements()), s.getLocation());
        } else if (stmt instanceof IfStmt) {
            IfStmt s = (IfStmt) stmt;
            return new IfStmt(
                deepClone(s.getCondition()),
                deepClone(s.getThenBranch()),
                deepClone(s.getElseBranch()),
                s.getLocation()
            );
        } else if (stmt instanceof WhileStmt) {
            WhileStmt s = (WhileStmt) stmt;
            return new WhileStmt(
                deepClone(s.getCondition()),
                deepClone(s.getBody()),
                s.getLabel(),
                s.getLocation()
            );
        } else if (stmt instanceof DoWhileStmt) {
            DoWhileStmt s = (DoWhileStmt) stmt;
            return new DoWhileStmt(
                deepClone(s.getBody()),
                deepClone(s.getCondition()),
                s.getLabel(),
                s.getLocation()
            );
        } else if (stmt instanceof ForStmt) {
            ForStmt s = (ForStmt) stmt;
            return new ForStmt(
                cloneStmtList(s.getInit()),
                deepClone(s.getCondition()),
                cloneExprList(s.getUpdate()),
                deepClone(s.getBody()),
                s.getLabel(),
                s.getLocation()
            );
        } else if (stmt instanceof ForEachStmt) {
            ForEachStmt s = (ForEachStmt) stmt;
            return new ForEachStmt(
                deepClone(s.getVariable()),
                deepClone(s.getIterable()),
                deepClone(s.getBody()),
                s.getLabel(),
                s.getLocation()
            );
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt s = (ReturnStmt) stmt;
            return new ReturnStmt(deepClone(s.getValue()), s.getLocation());
        } else if (stmt instanceof ThrowStmt) {
            ThrowStmt s = (ThrowStmt) stmt;
            return new ThrowStmt(deepClone(s.getException()), s.getLocation());
        } else if (stmt instanceof BreakStmt) {
            BreakStmt s = (BreakStmt) stmt;
            return new BreakStmt(s.getTargetLabel(), s.getLocation());
        } else if (stmt instanceof ContinueStmt) {
            ContinueStmt s = (ContinueStmt) stmt;
            return new ContinueStmt(s.getTargetLabel(), s.getLocation());
        } else if (stmt instanceof ExprStmt) {
            ExprStmt s = (ExprStmt) stmt;
            return new ExprStmt(deepClone(s.getExpression()), s.getLocation());
        } else if (stmt instanceof VarDeclStmt) {
            VarDeclStmt s = (VarDeclStmt) stmt;
            return new VarDeclStmt(
                s.getType(),
                s.getName(),
                deepClone(s.getInitializer()),
                s.isUseVarKeyword(),
                s.isFinal(),
                s.getLocation()
            );
        } else if (stmt instanceof LabeledStmt) {
            LabeledStmt s = (LabeledStmt) stmt;
            return new LabeledStmt(s.getLabel(), deepClone(s.getStatement()), s.getLocation());
        } else if (stmt instanceof SynchronizedStmt) {
            SynchronizedStmt s = (SynchronizedStmt) stmt;
            return new SynchronizedStmt(
                deepClone(s.getLock()),
                deepClone(s.getBody()),
                s.getLocation()
            );
        } else if (stmt instanceof TryCatchStmt) {
            TryCatchStmt s = (TryCatchStmt) stmt;
            List<CatchClause> clonedCatches = s.getCatches().stream()
                .map(c -> new CatchClause(c.exceptionTypes(), c.variableName(), deepClone(c.body())))
                .collect(Collectors.toList());
            return new TryCatchStmt(
                deepClone(s.getTryBlock()),
                clonedCatches,
                deepClone(s.getFinallyBlock()),
                cloneExprList(s.getResources()),
                s.getLocation()
            );
        } else if (stmt instanceof SwitchStmt) {
            SwitchStmt s = (SwitchStmt) stmt;
            List<SwitchCase> clonedCases = s.getCases().stream()
                .map(c -> new SwitchCase(c.labels(), cloneExprList(c.expressionLabels()), c.isDefault(), cloneStmtList(c.statements())))
                .collect(Collectors.toList());
            return new SwitchStmt(deepClone(s.getSelector()), clonedCases, s.getLocation());
        }

        throw new UnsupportedOperationException("Cannot clone statement of type: " + stmt.getClass().getName());
    }

    private static List<Expression> cloneExprList(List<? extends Expression> exprs) {
        return exprs.stream()
            .map(ASTMutations::deepClone)
            .collect(Collectors.toList());
    }

    private static List<Statement> cloneStmtList(List<? extends Statement> stmts) {
        return stmts.stream()
            .map(ASTMutations::deepClone)
            .collect(Collectors.toList());
    }

    /**
     * Replaces a node in its parent with a new node.
     * Returns true if replacement was successful.
     */
    public static boolean replace(ASTNode oldNode, ASTNode newNode) {
        if (oldNode == null || newNode == null) return false;

        ASTNode parent = oldNode.getParent();
        if (parent == null) return false;

        if (parent instanceof BinaryExpr) {
            BinaryExpr p = (BinaryExpr) parent;
            if (p.getLeft() == oldNode && newNode instanceof Expression) {
                p.withLeft((Expression) newNode);
                return true;
            } else if (p.getRight() == oldNode && newNode instanceof Expression) {
                p.withRight((Expression) newNode);
                return true;
            }
        } else if (parent instanceof UnaryExpr) {
            UnaryExpr p = (UnaryExpr) parent;
            if (p.getOperand() == oldNode && newNode instanceof Expression) {
                p.withOperand((Expression) newNode);
                return true;
            }
        } else if (parent instanceof IfStmt) {
            IfStmt p = (IfStmt) parent;
            if (p.getCondition() == oldNode && newNode instanceof Expression) {
                p.withCondition((Expression) newNode);
                return true;
            } else if (p.getThenBranch() == oldNode && newNode instanceof Statement) {
                p.withThenBranch((Statement) newNode);
                return true;
            } else if (p.getElseBranch() == oldNode && newNode instanceof Statement) {
                p.withElseBranch((Statement) newNode);
                return true;
            }
        } else if (parent instanceof WhileStmt) {
            WhileStmt p = (WhileStmt) parent;
            if (p.getCondition() == oldNode && newNode instanceof Expression) {
                p.withCondition((Expression) newNode);
                return true;
            } else if (p.getBody() == oldNode && newNode instanceof Statement) {
                p.withBody((Statement) newNode);
                return true;
            }
        } else if (parent instanceof BlockStmt) {
            BlockStmt p = (BlockStmt) parent;
            List<Statement> stmts = p.getStatements();
            for (int i = 0; i < stmts.size(); i++) {
                if (stmts.get(i) == oldNode && newNode instanceof Statement) {
                    stmts.set(i, (Statement) newNode);
                    return true;
                }
            }
        } else if (parent instanceof ReturnStmt) {
            ReturnStmt p = (ReturnStmt) parent;
            if (p.getValue() == oldNode && newNode instanceof Expression) {
                p.withValue((Expression) newNode);
                return true;
            }
        } else if (parent instanceof ExprStmt) {
            ExprStmt p = (ExprStmt) parent;
            if (p.getExpression() == oldNode && newNode instanceof Expression) {
                p.withExpression((Expression) newNode);
                return true;
            }
        } else if (parent instanceof VarDeclStmt) {
            VarDeclStmt p = (VarDeclStmt) parent;
            if (p.getInitializer() == oldNode && newNode instanceof Expression) {
                p.withInitializer((Expression) newNode);
                return true;
            }
        } else if (parent instanceof CastExpr) {
            CastExpr p = (CastExpr) parent;
            if (p.getExpression() == oldNode && newNode instanceof Expression) {
                p.withExpression((Expression) newNode);
                return true;
            }
        } else if (parent instanceof TernaryExpr) {
            TernaryExpr p = (TernaryExpr) parent;
            if (p.getCondition() == oldNode && newNode instanceof Expression) {
                p.withCondition((Expression) newNode);
                return true;
            } else if (p.getThenExpr() == oldNode && newNode instanceof Expression) {
                p.withThenExpr((Expression) newNode);
                return true;
            } else if (p.getElseExpr() == oldNode && newNode instanceof Expression) {
                p.withElseExpr((Expression) newNode);
                return true;
            }
        } else if (parent instanceof ArrayAccessExpr) {
            ArrayAccessExpr p = (ArrayAccessExpr) parent;
            if (p.getArray() == oldNode && newNode instanceof Expression) {
                p.withArray((Expression) newNode);
                return true;
            } else if (p.getIndex() == oldNode && newNode instanceof Expression) {
                p.withIndex((Expression) newNode);
                return true;
            }
        } else if (parent instanceof MethodCallExpr) {
            MethodCallExpr p = (MethodCallExpr) parent;
            if (p.getReceiver() == oldNode && newNode instanceof Expression) {
                p.withReceiver((Expression) newNode);
                return true;
            }
            List<Expression> args = p.getArguments();
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i) == oldNode && newNode instanceof Expression) {
                    args.set(i, (Expression) newNode);
                    return true;
                }
            }
        } else if (parent instanceof NewExpr) {
            NewExpr p = (NewExpr) parent;
            List<Expression> args = p.getArguments();
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i) == oldNode && newNode instanceof Expression) {
                    args.set(i, (Expression) newNode);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Removes a node from its parent.
     * Returns true if removal was successful.
     * Only works for nodes in lists (e.g., statements in blocks, arguments in calls).
     */
    public static boolean remove(ASTNode node) {
        if (node == null) return false;

        ASTNode parent = node.getParent();
        if (parent == null) return false;

        if (parent instanceof BlockStmt && node instanceof Statement) {
            return ((BlockStmt) parent).removeStatement((Statement) node);
        } else if (parent instanceof MethodCallExpr && node instanceof Expression) {
            return ((MethodCallExpr) parent).getArguments().remove(node);
        } else if (parent instanceof NewExpr && node instanceof Expression) {
            return ((NewExpr) parent).getArguments().remove(node);
        } else if (parent instanceof ForStmt) {
            ForStmt p = (ForStmt) parent;
            if (node instanceof Statement) {
                return p.getInit().remove(node);
            } else if (node instanceof Expression) {
                return p.getUpdate().remove(node);
            }
        }

        return false;
    }
}
