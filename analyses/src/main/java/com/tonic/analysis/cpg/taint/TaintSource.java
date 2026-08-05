package com.tonic.analysis.cpg.taint;

import java.util.regex.Pattern;

/**
 * A regex-matched method call site that introduces tainted data, either through its return value
 * or through one argument.
 */
public class TaintSource
{

    private final String name;
    private final String ownerPattern;
    private final String methodPattern;
    private final String descriptorPattern;
    private final int taintedArgumentIndex;
    private final boolean taintsReturnValue;
    private final TaintType taintType;

    private transient Pattern compiledOwnerPattern;
    private transient Pattern compiledMethodPattern;
    private transient Pattern compiledDescriptorPattern;

    private TaintSource(Builder builder)
    {
        this.name = builder.name;
        this.ownerPattern = builder.ownerPattern;
        this.methodPattern = builder.methodPattern;
        this.descriptorPattern = builder.descriptorPattern;
        this.taintedArgumentIndex = builder.taintedArgumentIndex;
        this.taintsReturnValue = builder.taintsReturnValue;
        this.taintType = builder.taintType;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the owner pattern
     */
    public String getOwnerPattern()
    {
        return ownerPattern;
    }

    /**
     * @return the method pattern
     */
    public String getMethodPattern()
    {
        return methodPattern;
    }

    /**
     * @return the descriptor pattern
     */
    public String getDescriptorPattern()
    {
        return descriptorPattern;
    }

    /**
     * @return the tainted argument index
     */
    public int getTaintedArgumentIndex()
    {
        return taintedArgumentIndex;
    }

    /**
     * @return whether taints return value
     */
    public boolean isTaintsReturnValue()
    {
        return taintsReturnValue;
    }

    /**
     * @return the taint type
     */
    public TaintType getTaintType()
    {
        return taintType;
    }

    /**
     * Tests an owner against the owner pattern, compiling and caching it on first use.
     * @param owner internal class name to test
     * @return true if the pattern is absent or matches in full
     */
    public boolean matchesOwner(String owner)
    {
        if (ownerPattern == null) return true;
        if (compiledOwnerPattern == null)
        {
            compiledOwnerPattern = Pattern.compile(ownerPattern);
        }
        return compiledOwnerPattern.matcher(owner).matches();
    }

    /**
     * Tests a method name against the method pattern, compiling and caching it on first use.
     * @param method method name to test
     * @return true if the pattern is absent or matches in full
     */
    public boolean matchesMethod(String method)
    {
        if (methodPattern == null) return true;
        if (compiledMethodPattern == null)
        {
            compiledMethodPattern = Pattern.compile(methodPattern);
        }
        return compiledMethodPattern.matcher(method).matches();
    }

    /**
     * Tests a descriptor against the descriptor pattern, compiling and caching it on first use.
     * @param descriptor method descriptor to test
     * @return true if the pattern is absent or matches in full
     */
    public boolean matchesDescriptor(String descriptor)
    {
        if (descriptorPattern == null) return true;
        if (compiledDescriptorPattern == null)
        {
            compiledDescriptorPattern = Pattern.compile(descriptorPattern);
        }
        return compiledDescriptorPattern.matcher(descriptor).matches();
    }

    /**
     * Tests a full call target against all three patterns.
     * @param owner internal class name to test
     * @param method method name to test
     * @param descriptor method descriptor to test
     * @return true if every present pattern matches
     */
    public boolean matches(String owner, String method, String descriptor)
    {
        return matchesOwner(owner) && matchesMethod(method) && matchesDescriptor(descriptor);
    }

    /**
     * @return a USER_INPUT source for servlet request parameter reads
     */
    public static TaintSource httpParameter()
    {
        return TaintSource.builder()
            .name("HTTP Parameter")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getParameter|getParameterValues|getParameterMap")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    /**
     * @return a USER_INPUT source for servlet request header reads
     */
    public static TaintSource httpHeader()
    {
        return TaintSource.builder()
            .name("HTTP Header")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getHeader|getHeaders")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    /**
     * @return a USER_INPUT source for servlet request cookie reads
     */
    public static TaintSource httpCookie()
    {
        return TaintSource.builder()
            .name("HTTP Cookie")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getCookies")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    /**
     * @return a USER_INPUT source for Scanner reads
     */
    public static TaintSource consoleInput()
    {
        return TaintSource.builder()
            .name("Console Input")
            .ownerPattern("java/util/Scanner")
            .methodPattern("next.*|hasNext.*")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    /**
     * @return a FILE_INPUT source for java.io stream and reader reads
     */
    public static TaintSource fileRead()
    {
        return TaintSource.builder()
            .name("File Read")
            .ownerPattern("java/io/(FileInputStream|BufferedReader|FileReader)")
            .methodPattern("read.*")
            .taintsReturnValue(true)
            .taintType(TaintType.FILE_INPUT)
            .build();
    }

    /**
     * @return an ENVIRONMENT source for System.getenv and System.getProperty
     */
    public static TaintSource environmentVariable()
    {
        return TaintSource.builder()
            .name("Environment Variable")
            .ownerPattern("java/lang/System")
            .methodPattern("getenv|getProperty")
            .taintsReturnValue(true)
            .taintType(TaintType.ENVIRONMENT)
            .build();
    }

    /**
     * @return a DATABASE source for ResultSet column getters
     */
    public static TaintSource databaseQuery()
    {
        return TaintSource.builder()
            .name("Database Query")
            .ownerPattern("java/sql/ResultSet")
            .methodPattern("get.*")
            .taintsReturnValue(true)
            .taintType(TaintType.DATABASE)
            .build();
    }

    /**
     * @return a NETWORK source for socket and connection input streams
     */
    public static TaintSource networkInput()
    {
        return TaintSource.builder()
            .name("Network Input")
            .ownerPattern("java/net/(Socket|URLConnection)")
            .methodPattern("getInputStream")
            .taintsReturnValue(true)
            .taintType(TaintType.NETWORK)
            .build();
    }

    /**
     * Builds a return-value-tainting source from caller-supplied patterns.
     * @param name display name
     * @param owner owner regex
     * @param method method name regex
     * @param type taint kind the source introduces
     * @return the configured source
     */
    public static TaintSource custom(String name, String owner, String method, TaintType type)
    {
        return TaintSource.builder()
            .name(name)
            .ownerPattern(owner)
            .methodPattern(method)
            .taintsReturnValue(true)
            .taintType(type)
            .build();
    }

    /**
     * @return a new empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    @Override
    public String toString()
    {
        return String.format("TaintSource[%s: %s.%s]", name, ownerPattern, methodPattern);
    }

    /**
     * Mutable accumulator for the fields of a {@link TaintSource}.
     */
    public static class Builder
    {
        private String name;
        private String ownerPattern;
        private String methodPattern;
        private String descriptorPattern;
        private int taintedArgumentIndex;
        private boolean taintsReturnValue;
        private TaintType taintType;

        /**
         * @param name display name
         * @return this builder
         */
        public Builder name(String name)
        {
            this.name = name;
            return this;
        }

        /**
         * @param ownerPattern regex matched in full against the internal owner name
         * @return this builder
         */
        public Builder ownerPattern(String ownerPattern)
        {
            this.ownerPattern = ownerPattern;
            return this;
        }

        /**
         * @param methodPattern regex matched in full against the method name
         * @return this builder
         */
        public Builder methodPattern(String methodPattern)
        {
            this.methodPattern = methodPattern;
            return this;
        }

        /**
         * @param descriptorPattern regex matched in full against the method descriptor
         * @return this builder
         */
        public Builder descriptorPattern(String descriptorPattern)
        {
            this.descriptorPattern = descriptorPattern;
            return this;
        }

        /**
         * @param taintedArgumentIndex zero-based argument the call taints
         * @return this builder
         */
        public Builder taintedArgumentIndex(int taintedArgumentIndex)
        {
            this.taintedArgumentIndex = taintedArgumentIndex;
            return this;
        }

        /**
         * @param taintsReturnValue whether the call taints its return value
         * @return this builder
         */
        public Builder taintsReturnValue(boolean taintsReturnValue)
        {
            this.taintsReturnValue = taintsReturnValue;
            return this;
        }

        /**
         * @param taintType taint kind the source introduces
         * @return this builder
         */
        public Builder taintType(TaintType taintType)
        {
            this.taintType = taintType;
            return this;
        }

        /**
         * @return a source holding the accumulated fields
         */
        public TaintSource build()
        {
            return new TaintSource(this);
        }
    }
}
