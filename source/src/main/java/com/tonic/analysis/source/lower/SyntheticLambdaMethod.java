package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.LambdaParameter;
import com.tonic.analysis.source.ast.type.SourceType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class SyntheticLambdaMethod {

    private final String name;
    private final String descriptor;
    private final boolean isStatic;
    private final List<CapturedVariable> captures;
    private final ASTNode body;
    private final List<LambdaParameter> parameters;
    private final SourceType returnType;

    public SyntheticLambdaMethod(String name, String descriptor, boolean isStatic,
                                  List<CapturedVariable> captures, ASTNode body,
                                  List<LambdaParameter> parameters, SourceType returnType) {
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.captures = captures != null ? new ArrayList<>(captures) : new ArrayList<>();
        this.body = body;
        this.parameters = parameters != null ? new ArrayList<>(parameters) : new ArrayList<>();
        this.returnType = returnType;
    }

    public int getTotalParameterCount() {
        return captures.size() + parameters.size();
    }

    @Getter
    public static class CapturedVariable {
        private final String name;
        private final SourceType type;

        public CapturedVariable(String name, SourceType type) {
            this.name = name;
            this.type = type;
        }
    }
}
