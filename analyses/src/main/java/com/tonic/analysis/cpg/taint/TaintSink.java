package com.tonic.analysis.cpg.taint;

import java.util.regex.Pattern;

public class TaintSink {

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

    private TaintSink(Builder builder) {
        this.name = builder.name;
        this.ownerPattern = builder.ownerPattern;
        this.methodPattern = builder.methodPattern;
        this.descriptorPattern = builder.descriptorPattern;
        this.sensitiveArgumentIndex = builder.sensitiveArgumentIndex;
        this.vulnerabilityType = builder.vulnerabilityType;
        this.severity = builder.severity;
    }

    public String getName() {
        return name;
    }

    public String getOwnerPattern() {
        return ownerPattern;
    }

    public String getMethodPattern() {
        return methodPattern;
    }

    public String getDescriptorPattern() {
        return descriptorPattern;
    }

    public int getSensitiveArgumentIndex() {
        return sensitiveArgumentIndex;
    }

    public VulnerabilityType getVulnerabilityType() {
        return vulnerabilityType;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean matchesOwner(String owner) {
        if (ownerPattern == null) return true;
        if (compiledOwnerPattern == null) {
            compiledOwnerPattern = Pattern.compile(ownerPattern);
        }
        return compiledOwnerPattern.matcher(owner).matches();
    }

    public boolean matchesMethod(String method) {
        if (methodPattern == null) return true;
        if (compiledMethodPattern == null) {
            compiledMethodPattern = Pattern.compile(methodPattern);
        }
        return compiledMethodPattern.matcher(method).matches();
    }

    public boolean matchesDescriptor(String descriptor) {
        if (descriptorPattern == null) return true;
        if (compiledDescriptorPattern == null) {
            compiledDescriptorPattern = Pattern.compile(descriptorPattern);
        }
        return compiledDescriptorPattern.matcher(descriptor).matches();
    }

    public boolean matches(String owner, String method, String descriptor) {
        return matchesOwner(owner) && matchesMethod(method) && matchesDescriptor(descriptor);
    }

    public static TaintSink sqlInjection() {
        return TaintSink.builder()
            .name("SQL Injection")
            .ownerPattern("java/sql/(Statement|PreparedStatement|Connection)")
            .methodPattern("execute.*|prepareStatement|prepareCall")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.SQL_INJECTION)
            .severity(Severity.CRITICAL)
            .build();
    }

    public static TaintSink commandInjection() {
        return TaintSink.builder()
            .name("Command Injection")
            .ownerPattern("java/lang/Runtime|java/lang/ProcessBuilder")
            .methodPattern("exec|command|start")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.COMMAND_INJECTION)
            .severity(Severity.CRITICAL)
            .build();
    }

    public static TaintSink pathTraversal() {
        return TaintSink.builder()
            .name("Path Traversal")
            .ownerPattern("java/io/(File|FileInputStream|FileOutputStream|FileReader|FileWriter)")
            .methodPattern("<init>")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.PATH_TRAVERSAL)
            .severity(Severity.HIGH)
            .build();
    }

    public static TaintSink xss() {
        return TaintSink.builder()
            .name("Cross-Site Scripting")
            .ownerPattern("javax/servlet/(http/HttpServletResponse|ServletResponse)|java/io/PrintWriter")
            .methodPattern("write|print|println|getWriter")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.XSS)
            .severity(Severity.HIGH)
            .build();
    }

    public static TaintSink ldapInjection() {
        return TaintSink.builder()
            .name("LDAP Injection")
            .ownerPattern("javax/naming/directory/DirContext")
            .methodPattern("search")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.LDAP_INJECTION)
            .severity(Severity.HIGH)
            .build();
    }

    public static TaintSink xpathInjection() {
        return TaintSink.builder()
            .name("XPath Injection")
            .ownerPattern("javax/xml/xpath/XPath")
            .methodPattern("evaluate|compile")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.XPATH_INJECTION)
            .severity(Severity.HIGH)
            .build();
    }

    public static TaintSink logInjection() {
        return TaintSink.builder()
            .name("Log Injection")
            .ownerPattern("(java/util/logging/Logger|org/slf4j/Logger|org/apache/log4j/Logger)")
            .methodPattern("info|debug|warn|error|trace|log")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.LOG_INJECTION)
            .severity(Severity.MEDIUM)
            .build();
    }

    public static TaintSink ssrf() {
        return TaintSink.builder()
            .name("Server-Side Request Forgery")
            .ownerPattern("java/net/(URL|HttpURLConnection|URLConnection)")
            .methodPattern("<init>|openConnection")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.SSRF)
            .severity(Severity.HIGH)
            .build();
    }

    public static TaintSink deserializationSink() {
        return TaintSink.builder()
            .name("Insecure Deserialization")
            .ownerPattern("java/io/ObjectInputStream")
            .methodPattern("readObject|readUnshared")
            .sensitiveArgumentIndex(-1)
            .vulnerabilityType(VulnerabilityType.INSECURE_DESERIALIZATION)
            .severity(Severity.CRITICAL)
            .build();
    }

    public static TaintSink reflectionSink() {
        return TaintSink.builder()
            .name("Unsafe Reflection")
            .ownerPattern("java/lang/(Class|reflect/Method|reflect/Constructor)")
            .methodPattern("forName|invoke|newInstance")
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(VulnerabilityType.UNSAFE_REFLECTION)
            .severity(Severity.HIGH)
            .build();
    }

    public static TaintSink custom(String name, String owner, String method,
                                   VulnerabilityType vulnType, Severity severity) {
        return TaintSink.builder()
            .name(name)
            .ownerPattern(owner)
            .methodPattern(method)
            .sensitiveArgumentIndex(0)
            .vulnerabilityType(vulnType)
            .severity(severity)
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("TaintSink[%s: %s.%s (%s)]",
            name, ownerPattern, methodPattern, severity);
    }

    public static class Builder {
        private String name;
        private String ownerPattern;
        private String methodPattern;
        private String descriptorPattern;
        private int sensitiveArgumentIndex;
        private VulnerabilityType vulnerabilityType;
        private Severity severity;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ownerPattern(String ownerPattern) {
            this.ownerPattern = ownerPattern;
            return this;
        }

        public Builder methodPattern(String methodPattern) {
            this.methodPattern = methodPattern;
            return this;
        }

        public Builder descriptorPattern(String descriptorPattern) {
            this.descriptorPattern = descriptorPattern;
            return this;
        }

        public Builder sensitiveArgumentIndex(int sensitiveArgumentIndex) {
            this.sensitiveArgumentIndex = sensitiveArgumentIndex;
            return this;
        }

        public Builder vulnerabilityType(VulnerabilityType vulnerabilityType) {
            this.vulnerabilityType = vulnerabilityType;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public TaintSink build() {
            return new TaintSink(this);
        }
    }
}
