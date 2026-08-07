package com.tonic.analysis.query.planner.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static filter keeping classes and methods whose names match a regex, tested
 * with both full match and substring find.
 */
public class PatternFilter implements StaticFilter
{

    private final Pattern classPattern;
    private final Pattern methodPattern;

    private PatternFilter(Pattern classPattern, Pattern methodPattern)
    {
        this.classPattern = classPattern;
        this.methodPattern = methodPattern;
    }

    /**
     * Builds a filter matching on the owning class name.
     * @param regex the pattern to compile
     * @return a filter with no method constraint
     * @throws java.util.regex.PatternSyntaxException if the regex is malformed
     */
    public static PatternFilter classMatching(String regex)
    {
        return new PatternFilter(Pattern.compile(regex), null);
    }

    /**
     * Builds a filter matching on the method signature or bare method name.
     * @param regex the pattern to compile
     * @return a filter with no class constraint
     * @throws java.util.regex.PatternSyntaxException if the regex is malformed
     */
    public static PatternFilter methodMatching(String regex)
    {
        return new PatternFilter(null, Pattern.compile(regex));
    }

    /**
     * Builds a filter matching static initializers.
     * @return a filter whose method pattern is ".*\\.&lt;clinit&gt;\\(\\)V"
     */
    public static PatternFilter clinitMethods()
    {
        return methodMatching(".*\\.<clinit>\\(\\)V");
    }

    @Override
    public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods)
    {
        return methods
            .filter(m -> {
                if (classPattern != null)
                {
                    String className = m.getOwnerName();
                    if (!classPattern.matcher(className).matches() && !classPattern.matcher(className).find())
                    {
                        return false;
                    }
                }
                if (methodPattern != null)
                {
                    String methodSig = m.getOwnerName() + "." + m.getName() + m.getDesc();
                    String methodName = m.getName();
                    return methodPattern.matcher(methodSig).matches() ||
                            methodPattern.matcher(methodSig).find() ||
                            methodPattern.matcher(methodName).matches() ||
                            methodPattern.matcher(methodName).find();
                }
                return true;
            })
            .collect(Collectors.toSet());
    }

    @Override
    public Set<ClassFile> filterClasses(Stream<ClassFile> classes)
    {
        if (classPattern == null)
        {
            return classes.collect(Collectors.toSet());
        }

        return classes
            .filter(cf -> {
                String className = cf.getClassName();
                return classPattern.matcher(className).matches() ||
                       classPattern.matcher(className).find();
            })
            .collect(Collectors.toSet());
    }
}
