package com.tonic.analysis.cpg.taint;

import lombok.Builder;
import lombok.Getter;
import java.util.regex.Pattern;

@Getter
@Builder
public class TaintSource {

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

    public static TaintSource httpParameter() {
        return TaintSource.builder()
            .name("HTTP Parameter")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getParameter|getParameterValues|getParameterMap")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource httpHeader() {
        return TaintSource.builder()
            .name("HTTP Header")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getHeader|getHeaders")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource httpCookie() {
        return TaintSource.builder()
            .name("HTTP Cookie")
            .ownerPattern("javax/servlet/http/HttpServletRequest")
            .methodPattern("getCookies")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource consoleInput() {
        return TaintSource.builder()
            .name("Console Input")
            .ownerPattern("java/util/Scanner")
            .methodPattern("next.*|hasNext.*")
            .taintsReturnValue(true)
            .taintType(TaintType.USER_INPUT)
            .build();
    }

    public static TaintSource fileRead() {
        return TaintSource.builder()
            .name("File Read")
            .ownerPattern("java/io/(FileInputStream|BufferedReader|FileReader)")
            .methodPattern("read.*")
            .taintsReturnValue(true)
            .taintType(TaintType.FILE_INPUT)
            .build();
    }

    public static TaintSource environmentVariable() {
        return TaintSource.builder()
            .name("Environment Variable")
            .ownerPattern("java/lang/System")
            .methodPattern("getenv|getProperty")
            .taintsReturnValue(true)
            .taintType(TaintType.ENVIRONMENT)
            .build();
    }

    public static TaintSource databaseQuery() {
        return TaintSource.builder()
            .name("Database Query")
            .ownerPattern("java/sql/ResultSet")
            .methodPattern("get.*")
            .taintsReturnValue(true)
            .taintType(TaintType.DATABASE)
            .build();
    }

    public static TaintSource networkInput() {
        return TaintSource.builder()
            .name("Network Input")
            .ownerPattern("java/net/(Socket|URLConnection)")
            .methodPattern("getInputStream")
            .taintsReturnValue(true)
            .taintType(TaintType.NETWORK)
            .build();
    }

    public static TaintSource custom(String name, String owner, String method, TaintType type) {
        return TaintSource.builder()
            .name(name)
            .ownerPattern(owner)
            .methodPattern(method)
            .taintsReturnValue(true)
            .taintType(type)
            .build();
    }

    @Override
    public String toString() {
        return String.format("TaintSource[%s: %s.%s]", name, ownerPattern, methodPattern);
    }
}
