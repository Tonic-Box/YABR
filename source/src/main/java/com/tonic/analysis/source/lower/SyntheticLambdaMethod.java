package com.tonic.analysis.source.lower;

import com.tonic.analysis.source.ast.ASTNode;
import com.tonic.analysis.source.ast.expr.LambdaParameter;
import com.tonic.analysis.source.ast.type.SourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * A lambda body pending materialization as a synthetic class method, with its captured
 * variables and declared parameters.
 */
public class SyntheticLambdaMethod
{

    private final String name;
    private final String descriptor;
    private final boolean isStatic;
    private final List<CapturedVariable> captures;
    private final ASTNode body;
    private final List<LambdaParameter> parameters;
    private final SourceType returnType;

    /**
     * Creates the synthetic lambda method description.
     * @param name the synthetic method name
     * @param descriptor the method descriptor
     * @param isStatic whether the synthetic method is static
     * @param captures the captured variables; null means none
     * @param body the lambda body node
     * @param parameters the declared lambda parameters; null means none
     * @param returnType the lambda return type
     */
    public SyntheticLambdaMethod(String name, String descriptor, boolean isStatic, List<CapturedVariable> captures, ASTNode body, List<LambdaParameter> parameters, SourceType returnType)
    {
        this.name = name;
        this.descriptor = descriptor;
        this.isStatic = isStatic;
        this.captures = captures != null ? new ArrayList<>(captures) : new ArrayList<>();
        this.body = body;
        this.parameters = parameters != null ? new ArrayList<>(parameters) : new ArrayList<>();
        this.returnType = returnType;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the descriptor
     */
    public String getDescriptor()
    {
        return descriptor;
    }

    /**
     * @return whether static
     */
    public boolean isStatic()
    {
        return isStatic;
    }

    /**
     * @return the captures
     */
    public List<CapturedVariable> getCaptures()
    {
        return captures;
    }

    /**
     * @return the body
     */
    public ASTNode getBody()
    {
        return body;
    }

    /**
     * @return the parameters
     */
    public List<LambdaParameter> getParameters()
    {
        return parameters;
    }

    /**
     * @return the return type
     */
    public SourceType getReturnType()
    {
        return returnType;
    }

    /**
     * @return the number of captures plus declared parameters
     */
    public int getTotalParameterCount()
    {
        return captures.size() + parameters.size();
    }

    /**
     * A variable captured from the enclosing scope, by name and type.
     */
    public static class CapturedVariable
    {
        private final String name;
        private final SourceType type;

        public CapturedVariable(String name, SourceType type)
        {
            this.name = name;
            this.type = type;
        }

        /**
         * @return the name
         */
        public String getName()
        {
            return name;
        }

        /**
         * @return the type
         */
        public SourceType getType()
        {
            return type;
        }
    }
}
