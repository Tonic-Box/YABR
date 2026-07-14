package com.tonic.analysis.frame;

import com.tonic.analysis.ClassFactory;
import com.tonic.parser.ClassFile;
import com.tonic.testutil.TestUtils;
import org.junit.jupiter.api.Test;

/**
 * Regression: JVMS 4.10.1.9 says invokespecial &lt;init&gt; on uninitializedThis initializes {@code this}
 * to the class being verified, not the invoked constructor's owner. A super() call names the superclass
 * as the methodref owner, so typing {@code this} from that owner declares {@code this} as the parent in a
 * StackMapTable frame at any target between super() and a use of {@code this} as the subclass (here a
 * field store after an if). That makes the field store fail verification with the superclass not
 * assignable to the subclass.
 */
public class UninitializedThisFrameTest {

    @Test
    public void uninitializedThisFrameUsesCurrentClass() throws Exception {
        ClassFile cf = TestUtils.loadTestFixture("UninitThisRepro");
        // Regenerate the StackMapTable, then force the JVM verifier over the whole class. Before the fix
        // the constructor's merge frame declares this as java/lang/Object (the super() owner) and the
        // this.x store throws VerifyError; after the fix it declares this as UninitThisRepro and verifies.
        ClassFactory.computeFrames(cf);
        TestUtils.linkAndVerify(cf);
    }
}
