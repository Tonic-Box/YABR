package com.tonic.analysis.cpg.taint;

import java.util.regex.Pattern;

/**
 * A regex-matched method call site where tainted data causes a vulnerability, carrying the
 * argument index that must stay untainted.
 */
public class TaintSink
{

    private final String name;
    private final String ownerPattern;
    private final String methodPattern;
    private final String descriptorPattern;
    private final int sensitiveArgumentIndex;
    private final VulnerabilityType vulnerabilityType;
    private final Severity severity;

    private transient Pattern compiledOwnerPattern;
    private transient Pattern compiledMethodPattern;
    private transient Pattern compiledDescriptorPattern;

    private TaintSink(Builder builder)
    {
        this.name = builder.name;
        this.ownerPattern = builder.ownerPattern;
        this.methodPattern = builder.methodPattern;
        this.descriptorPattern = builder.descriptorPattern;
        this.sensitiveArgumentIndex = builder.sensitiveArgumentIndex;
        this.vulnerabilityType = builder.vulnerabilityType;
        this.severity = builder.severity;
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
     * @return the sensitive argument index
     */
    public int getSensitiveArgumentIndex()
    {
        return sensitiveArgumentIndex;
    }

    /**
     * @return the vulnerability type
     */
    public VulnerabilityType getVulnerabilityType()
    {
        return vulnerabilityType;
    }

    /**
     * @return the severity
     */
    public Severity getSeverity()
    {
        return severity;
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
     * @return a CRITICAL sink for the JDBC statement execution family, argument 0 sensitive
     */
    public static TaintSink sqlInjection()
    {
        return TaintSink.builder()
            .name("SQL Injection")
            .ownerPattern("java/sql/(Statement|PreparedStatement|Connection)")
            .methodPattern("execute.*|prepareStatement|prepareCall")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.SQL_INJECTION)
            .severity(Severity.CRITICAL)
            .build();
    }

    /**
     * @return a CRITICAL sink for Runtime and ProcessBuilder process launches, argument 0 sensitive
     */
    public static TaintSink commandInjection()
    {
        return TaintSink.builder()
            .name("Command Injection")
            .ownerPattern("java/lang/Runtime|java/lang/ProcessBuilder")
            .methodPattern("exec|command|start")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.COMMAND_INJECTION)
            .severity(Severity.CRITICAL)
            .build();
    }

    /**
     * @return a HIGH sink for java.io file constructors, argument 0 sensitive
     */
    public static TaintSink pathTraversal()
    {
        return TaintSink.builder()
            .name("Path Traversal")
            .ownerPattern("java/io/(File|FileInputStream|FileOutputStream|FileReader|FileWriter)")
            .methodPattern("<init>")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.PATH_TRAVERSAL)
            .severity(Severity.HIGH)
            .build();
    }

    /**
     * @return a HIGH sink for servlet response and PrintWriter output, argument 0 sensitive
     */
    public static TaintSink xss()
    {
        return TaintSink.builder()
            .name("Cross-Site Scripting")
            .ownerPattern("javax/servlet/(http/HttpServletResponse|ServletResponse)|java/io/PrintWriter")
            .methodPattern("write|print|println|getWriter")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.XSS)
            .severity(Severity.HIGH)
            .build();
    }

    /**
     * @return a HIGH sink for DirContext searches, argument 0 sensitive
     */
    public static TaintSink ldapInjection()
    {
        return TaintSink.builder()
            .name("LDAP Injection")
            .ownerPattern("javax/naming/directory/DirContext")
            .methodPattern("search")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.LDAP_INJECTION)
            .severity(Severity.HIGH)
            .build();
    }

    /**
     * @return a HIGH sink for XPath evaluation and compilation, argument 0 sensitive
     */
    public static TaintSink xpathInjection()
    {
        return TaintSink.builder()
            .name("XPath Injection")
            .ownerPattern("javax/xml/xpath/XPath")
            .methodPattern("evaluate|compile")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.XPATH_INJECTION)
            .severity(Severity.HIGH)
            .build();
    }

    /**
     * @return a MEDIUM sink for JUL, slf4j and log4j logging calls, argument 0 sensitive
     */
    public static TaintSink logInjection()
    {
        return TaintSink.builder()
            .name("Log Injection")
            .ownerPattern("(java/util/logging/Logger|org/slf4j/Logger|org/apache/log4j/Logger)")
            .methodPattern("info|debug|warn|error|trace|log")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.LOG_INJECTION)
            .severity(Severity.MEDIUM)
            .build();
    }

    /**
     * @return a HIGH sink for URL construction and connection opening, argument 0 sensitive
     */
    public static TaintSink ssrf()
    {
        return TaintSink.builder()
            .name("Server-Side Request Forgery")
            .ownerPattern("java/net/(URL|HttpURLConnection|URLConnection)")
            .methodPattern("<init>|openConnection")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.SSRF)
            .severity(Severity.HIGH)
            .build();
    }

    /**
     * @return a CRITICAL sink for ObjectInputStream reads, with no sensitive argument (index -1)
     */
    public static TaintSink deserializationSink()
    {
        return TaintSink.builder()
            .name("Insecure Deserialization")
            .ownerPattern("java/io/ObjectInputStream")
            .methodPattern("readObject|readUnshared")
            .sensitiveArgumentIndex(-1)
            .vulnerabilityType(VulnerabilityType.INSECURE_DESERIALIZATION)
            .severity(Severity.CRITICAL)
            .build();
    }

    /**
     * @return a HIGH sink for reflective class lookup and invocation, argument 0 sensitive
     */
    public static TaintSink reflectionSink()
    {
        return TaintSink.builder()
            .name("Unsafe Reflection")
            .ownerPattern("java/lang/(Class|reflect/Method|reflect/Constructor)")
            .methodPattern("forName|invoke|newInstance")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.UNSAFE_REFLECTION)
            .severity(Severity.HIGH)
            .build();
    }

    /**
     * Builds a sink from caller-supplied patterns, fixing the sensitive argument at index 0.
     * @param name display name
     * @param owner owner regex
     * @param method method name regex
     * @param vulnType vulnerability the sink reports
     * @param severity severity the sink reports
     * @return the configured sink
     */
    public static TaintSink custom(String name, String owner, String method, VulnerabilityType vulnType, Severity severity)
    {
        return TaintSink.builder()
            .name(name)
            .ownerPattern(owner)
            .methodPattern(method)
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(vulnType)
            .severity(severity)
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
        return String.format("TaintSink[%s: %s.%s (%s)]", name, ownerPattern, methodPattern, severity);
    }

    /**
     * Mutable accumulator for the fields of a {@link TaintSink}.
     */
    public static class Builder
    {
        private String name;
        private String ownerPattern;
        private String methodPattern;
        private String descriptorPattern;
        private int sensitiveArgumentIndex;
        private VulnerabilityType vulnerabilityType;
        private Severity severity;

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
         * @param sensitiveArgumentIndex zero-based argument that must not be tainted, or -1 for none
         * @return this builder
         */
        public Builder sensitiveArgumentIndex(int sensitiveArgumentIndex)
        {
            this.sensitiveArgumentIndex = sensitiveArgumentIndex;
            return this;
        }

        /**
         * @param vulnerabilityType vulnerability reported when the sink is reached
         * @return this builder
         */
        public Builder vulnerabilityType(VulnerabilityType vulnerabilityType)
        {
            this.vulnerabilityType = vulnerabilityType;
            return this;
        }

        /**
         * @param severity severity reported when the sink is reached
         * @return this builder
         */
        public Builder severity(Severity severity)
        {
            this.severity = severity;
            return this;
        }

        /**
         * @return a sink holding the accumulated fields
         */
        public TaintSink build()
        {
            return new TaintSink(this);
        }
    }
}
