package datameer.webdriver.goodies;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a test method indicating that the test is expected to fail
 * and therefore that the result should be inverted:
 * <ul>
 * <li>a failure will be reported as success: it is what we expect as some functionality is not yet implemented.</li>
 * <li>a success will be reported as failure: this will indicate that the functionality has been implemented
 * and that the &#064;NotYetImplemented annotation should be removed</li>
 * </ul>
 * but a success 
 * @author Marc Guillemot
 * @version $Revision:  $
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface NotYetImplemented {
    /**
     * The exception thrown by the test runner when a test marked as NotYetImplemented is already working fine.
     */
    public static class WorksAlreadyException extends RuntimeException {
        public WorksAlreadyException() {
            super("Test is marked as Not Yet Implemented but is already working");
        }
    }

    /**
     * Information about the reason why it is not implemented.
     */
    String value() default "";
}
