package com.tonic.demo;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;

import static com.tonic.type.AccessFlags.*;

/**
 * Demo showing ClassBuilder assembling a hello-world class that is then defined and run.
 */
public class ScratchTest
{
    /**
     * Builds a hello-world class with ClassBuilder, defines it, and invokes its main method.
     * @param args unused
     * @throws Exception if class definition or reflective invocation fails
     */
    public static void main(String[] args) throws Exception
    {
        ClassFile cf = ClassBuilder.create("com/tonic/HelloWorld")
                .addMethod(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V")
                    .exceptions("java/lang/Exception")
                    .code()
                        .getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
                        .ldc("Hello world!")
                        .invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
                        .vreturn()
                    .end()
                .end()
                .build();

        byte[] bytes = cf.write();

        Class<?> clazz = new ClassLoader() {
            Class<?> define(byte[] b)
            {
                return defineClass(null, b, 0, b.length);
            }
        }.define(bytes);
        clazz.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
    }
}
