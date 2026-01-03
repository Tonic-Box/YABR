package com.tonic.analysis.source.ast.transform;

import com.tonic.analysis.source.ast.expr.*;
import com.tonic.analysis.source.ast.stmt.*;
import com.tonic.analysis.source.visitor.AbstractSourceVisitor;

import java.util.*;

/**
 * Moves variable declarations closer to their first use.
 *
 * This transform identifies variable declarations at the start of a method
 * that are initialized with default values (0, null, etc.) and moves them
 * to just before their first use. This produces more natural-looking code.
 *
 * Example before:
 *   long local3 = 0L;
 *   long local5 = 0L;
 *   int local7 = 0;
 *   while (local3 < arg0) { ... }
 *   local5 = System.nanoTime();
 *
 * Example after:
 *   long local3 = 0L;
 *   while (local3 < arg0) { ... }
 *   long local5 = System.nanoTime();
 */
public class DeclarationHoister implements ASTTransform {

    @Override
    public String getName() {
        return "DeclarationHoister";
    }

    @Override
    public boolean transform(BlockStmt block) {
        return hoistDeclarations(block.getStatements());
    }

    private boolean hoistDeclarations(List<Statement> stmts) {
        if (stmts.isEmpty()) {
            return false;
        }

        boolean changed = false;

        List<VarDeclStmt> frontDeclarations = new ArrayList<>();
        int firstNonDeclIndex = 0;

        for (int i = 0; i < stmts.size(); i++) {
            Statement stmt = stmts.get(i);
            if (stmt instanceof VarDeclStmt) {
                VarDeclStmt decl = (VarDeclStmt) stmt;
                if (isDefaultInitializer(decl.getInitializer())) {
                    frontDeclarations.add(decl);
                } else {
                    break;
                }
            } else {
                firstNonDeclIndex = i;
                break;
            }
            firstNonDeclIndex = i + 1;
        }

        if (frontDeclarations.isEmpty() || firstNonDeclIndex >= stmts.size()) {
            changed |= transformNested(stmts);
            return changed;
        }

        List<Statement> nonDeclStatements = new ArrayList<>(
            stmts.subList(firstNonDeclIndex, stmts.size())
        );

        Map<String, Integer> firstUseIndex = new LinkedHashMap<>();
        Map<String, Boolean> usedInNestedScope = new HashMap<>();

        for (VarDeclStmt decl : frontDeclarations) {
            String varName = decl.getName();
            int firstUse = findFirstUseIndex(nonDeclStatements, varName);
            firstUseIndex.put(varName, firstUse);
            if (firstUse >= 0 && firstUse < nonDeclStatements.size()) {
                usedInNestedScope.put(varName, isUsedInNestedScope(nonDeclStatements.get(firstUse), varName));
            }
        }

        Map<String, Expression> firstAssignments = new HashMap<>();
        for (VarDeclStmt decl : frontDeclarations) {
            String varName = decl.getName();
            int useIdx = firstUseIndex.getOrDefault(varName, -1);
            if (useIdx >= 0 && useIdx < nonDeclStatements.size()) {
                Statement firstUseStmt = nonDeclStatements.get(useIdx);
                Expression assignedValue = getAssignmentValue(firstUseStmt, varName);
                if (assignedValue != null && !usedInNestedScope.getOrDefault(varName, false)) {
                    if (!referencesVar(assignedValue, varName)) {
                        firstAssignments.put(varName, assignedValue);
                    }
                }
            }
        }

        for (int i = 0; i < frontDeclarations.size(); i++) {
            stmts.remove(0);
        }
        firstNonDeclIndex = 0;

        List<VarDeclStmt> declsToInsert = new ArrayList<>(frontDeclarations);
        declsToInsert.sort((a, b) -> {
            int idxA = firstUseIndex.getOrDefault(a.getName(), Integer.MAX_VALUE);
            int idxB = firstUseIndex.getOrDefault(b.getName(), Integer.MAX_VALUE);
            return Integer.compare(idxA, idxB);
        });

        int offset = 0;
        Set<String> inlinedVars = new HashSet<>();

        for (VarDeclStmt decl : declsToInsert) {
            String varName = decl.getName();
            int useIdx = firstUseIndex.getOrDefault(varName, -1);

            if (useIdx < 0) {
                stmts.add(offset, decl);
                offset++;
                changed = true;
                continue;
            }

            Expression inlineValue = firstAssignments.get(varName);
            if (inlineValue != null) {
                VarDeclStmt newDecl = new VarDeclStmt(decl.getType(), varName, inlineValue);
                int insertPos = useIdx + offset;
                stmts.set(insertPos, newDecl);
                inlinedVars.add(varName);
                changed = true;
            } else {
                int insertPos = useIdx + offset;
                stmts.add(insertPos, decl);
                offset++;
                changed = true;
            }
        }

        changed |= transformNested(stmts);

        return changed;
    }

    private boolean transformNested(List<Statement> stmts) {
        boolean changed = false;
        for (Statement stmt : stmts) {
            if (stmt instanceof WhileStmt) {
                WhileStmt whileStmt = (WhileStmt) stmt;
                if (whileStmt.getBody() instanceof BlockStmt) {
                    changed |= hoistDeclarations(((BlockStmt) whileStmt.getBody()).getStatements());
                }
            } else if (stmt instanceof DoWhileStmt) {
                DoWhileStmt doWhile = (DoWhileStmt) stmt;
                if (doWhile.getBody() instanceof BlockStmt) {
                    changed |= hoistDeclarations(((BlockStmt) doWhile.getBody()).getStatements());
                }
            } else if (stmt instanceof ForStmt) {
                ForStmt forStmt = (ForStmt) stmt;
                if (forStmt.getBody() instanceof BlockStmt) {
                    changed |= hoistDeclarations(((BlockStmt) forStmt.getBody()).getStatements());
                }
            } else if (stmt instanceof IfStmt) {
                IfStmt ifStmt = (IfStmt) stmt;
                if (ifStmt.getThenBranch() instanceof BlockStmt) {
                    changed |= hoistDeclarations(((BlockStmt) ifStmt.getThenBranch()).getStatements());
                }
                if (ifStmt.hasElse() && ifStmt.getElseBranch() instanceof BlockStmt) {
                    changed |= hoistDeclarations(((BlockStmt) ifStmt.getElseBranch()).getStatements());
                }
            } else if (stmt instanceof TryCatchStmt) {
                TryCatchStmt tryCatch = (TryCatchStmt) stmt;
                if (tryCatch.getTryBlock() instanceof BlockStmt) {
                    changed |= hoistDeclarations(((BlockStmt) tryCatch.getTryBlock()).getStatements());
                }
                for (CatchClause clause : tryCatch.getCatches()) {
                    if (clause.body() instanceof BlockStmt) {
                        changed |= hoistDeclarations(((BlockStmt) clause.body()).getStatements());
                    }
                }
            } else if (stmt instanceof BlockStmt) {
                changed |= hoistDeclarations(((BlockStmt) stmt).getStatements());
            }
        }
        return changed;
    }

    private boolean isDefaultInitializer(Expression init) {
        if (init == null) {
            return true;
        }
        if (init instanceof LiteralExpr) {
            LiteralExpr lit = (LiteralExpr) init;
            Object val = lit.getValue();
            if (val == null) return true;
            if (val instanceof Number) {
                return ((Number) val).doubleValue() == 0.0;
            }
            if (val instanceof Boolean) {
                return !(Boolean) val;
            }
            if (val instanceof Character) {
                return (Character) val == '\0';
            }
        }
        return false;
    }

    private int findFirstUseIndex(List<Statement> stmts, String varName) {
        for (int i = 0; i < stmts.size(); i++) {
            if (usesVariable(stmts.get(i), varName)) {
                return i;
            }
        }
        return -1;
    }

    private boolean usesVariable(Statement stmt, String varName) {
        VariableUsageChecker checker = new VariableUsageChecker(varName);
        stmt.accept(checker);
        return checker.found;
    }

    private boolean isUsedInNestedScope(Statement stmt, String varName) {
        if (stmt instanceof WhileStmt) {
            WhileStmt whileStmt = (WhileStmt) stmt;
            if (usesVariable(whileStmt.getBody(), varName)) {
                return true;
            }
            return usesVariableInExpr(whileStmt.getCondition(), varName);
        } else if (stmt instanceof DoWhileStmt) {
            return usesVariable(((DoWhileStmt) stmt).getBody(), varName);
        } else if (stmt instanceof ForStmt) {
            return usesVariable(((ForStmt) stmt).getBody(), varName);
        } else if (stmt instanceof IfStmt) {
            IfStmt ifStmt = (IfStmt) stmt;
            return usesVariable(ifStmt.getThenBranch(), varName) ||
                   (ifStmt.hasElse() && usesVariable(ifStmt.getElseBranch(), varName));
        }
        return false;
    }

    private boolean usesVariableInExpr(Expression expr, String varName) {
        VariableUsageChecker checker = new VariableUsageChecker(varName);
        expr.accept(checker);
        return checker.found;
    }

    private Expression getAssignmentValue(Statement stmt, String varName) {
        if (stmt instanceof ExprStmt) {
            Expression expr = ((ExprStmt) stmt).getExpression();
            if (expr instanceof BinaryExpr) {
                BinaryExpr binary = (BinaryExpr) expr;
                if (binary.getOperator() == BinaryOperator.ASSIGN) {
                    if (binary.getLeft() instanceof VarRefExpr) {
                        VarRefExpr varRef = (VarRefExpr) binary.getLeft();
                        if (varRef.getName().equals(varName)) {
                            return binary.getRight();
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean referencesVar(Expression expr, String varName) {
        VariableUsageChecker checker = new VariableUsageChecker(varName);
        expr.accept(checker);
        return checker.found;
    }

    private static class VariableUsageChecker extends AbstractSourceVisitor<Void> {
        private final String varName;
        boolean found = false;

        VariableUsageChecker(String varName) {
            this.varName = varName;
        }

        @Override
        public Void visitVarRef(VarRefExpr expr) {
            if (expr.getName().equals(varName)) {
                found = true;
            }
            return super.visitVarRef(expr);
        }
    }
}
