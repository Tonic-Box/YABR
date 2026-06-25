package com.tonic.analysis.source.decompile;

import com.tonic.analysis.source.recovery.EnumSwitchMapRegistry;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for enum-switch holder resolution: javac puts the {@code $SwitchMap$} array in a synthetic
 * sibling class, so decompiling a class on its own previously left raw switch-map indices ({@code case 1:})
 * on a switch over the enum variable, which does not recompile. With the holder reachable in the pool the
 * switch must emit constant-name labels.
 */
public class EnumSwitchHolderDecompileTest {

    @Test
    public void enumSwitchResolvesConstantsFromOwningPool() throws Exception {
        Path jar = Paths.get("src/test/resources/DemoJar.jar");
        Assumptions.assumeTrue(Files.exists(jar), "demo jar missing");
        byte[] loginDialog = entry(jar, "osrs/dev/auth/LoginDialog.class");
        byte[] holder = entry(jar, "osrs/dev/auth/LoginDialog$4.class");
        byte[] messageType = entry(jar, "osrs/dev/auth/LoginDialog$MessageType.class");
        Assumptions.assumeTrue(loginDialog != null && holder != null, "classes missing");

        // A project pool, as a host like JStudio builds - NOT the default pool. The decompiled ClassFile
        // must resolve its $SwitchMap$ holder from the pool that loaded it.
        ClassPool pool = new ClassPool(true);
        pool.loadClass(holder);
        if (messageType != null) {
            pool.loadClass(messageType);
        }
        ClassFile cf = pool.loadClass(loginDialog);
        EnumSwitchMapRegistry.getInstance().clear();

        String src = new ClassDecompiler(cf).decompile();
        int i = src.indexOf("updateStatus");
        String body = i < 0 ? src : src.substring(i, Math.min(src.length(), i + 700));

        // The switch is over a MessageType: it must use constant-name labels, never raw $SwitchMap$ indices.
        assertTrue(body.contains("case ERROR") || body.contains("case WARNING")
                        || body.contains("case SUCCESS") || body.contains("case INFO"),
                "enum switch must use constant-name labels:\n" + body);
        assertFalse(body.matches("(?s).*case \\d+ ?:.*"),
                "enum switch must not leave raw $SwitchMap$ indices:\n" + body);
    }

    private static byte[] entry(Path jar, String name) throws IOException {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            ZipEntry e = zf.getEntry(name);
            if (e == null) {
                return null;
            }
            try (InputStream is = zf.getInputStream(e)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    bos.write(buf, 0, n);
                }
                return bos.toByteArray();
            }
        }
    }
}
