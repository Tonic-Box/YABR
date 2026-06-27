package com.tonic.analysis.cpg.taint;

import lombok.Builder;
import lombok.Getter;

import java.util.regex.Pattern;

@Getter
@Builder
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

    @Override
    public String toString() {
        return String.format("TaintSink[%s: %s.%s (%s)]",
            name, ownerPattern, methodPattern, severity);
    }
}
