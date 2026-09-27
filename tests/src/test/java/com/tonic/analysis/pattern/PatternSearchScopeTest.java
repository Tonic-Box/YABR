package com.tonic.analysis.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassPool;
import com.tonic.testutil.TestUtils;
import com.tonic.type.AccessFlags;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternSearchScopeTest
{

    @Test
    void allClassesScopeSearchesEveryClassInThePool() throws Exception
    {
        ClassPool pool = TestUtils.emptyPool();
        for (String name : new String[]{"a/One", "a/Two"})
        {
            pool.loadClass(new ByteArrayInputStream(ClassBuilder.create(name).access(AccessFlags.ACC_PUBLIC)
                    .addMethod(AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC, "run", "()V").code().invokestatic("a/Target", "hit", "()V").vreturn().end().end()
                    .toByteArray()));
        }

        List<SearchResult> results = new PatternSearch(pool).inAllClasses().findMethodCalls("a/Target", "hit");

        assertEquals(2, results.size());
    }
}
