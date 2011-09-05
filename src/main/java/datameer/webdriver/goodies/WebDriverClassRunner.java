package datameer.webdriver.goodies;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ListIterator;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import datameer.webdriver.goodies.SimpleWebDriverRunner.Retry;
import datameer.webdriver.goodies.SimpleWebDriverRunner.WebDriverDefinition.DriverKey;

/**
 * Runs the tests of a class with the specified browser.
 * @author Marc Guillemot
 * @version $Revision:  $
 */
public class WebDriverClassRunner extends BlockJUnit4ClassRunner {
    private List<FrameworkMethod> _testMethods;
    private final DriverKey _driverKey;
	
    public WebDriverClassRunner(final Class<?> testClass, final DriverKey driverKey) throws InitializationError {
        super(testClass);
        _driverKey = driverKey;
    }

    @Override
    protected String getName() {
    	return _driverKey.getName();
    }
    
    @Override
    protected String testName(final FrameworkMethod method) {
        String suffix = "";
        final NotYetImplemented nyiAnnotation = readAnnotation(method.getMethod(), NotYetImplemented.class);
        if (nyiAnnotation != null) {
            suffix += " [NYI]";
        }
    	return super.testName(method) + " [" + _driverKey.getName() + "]" + suffix;
    }

    private <T extends Annotation> T readAnnotation(final Method method, final Class<T> annotationClass) {
        T annotation = method.getAnnotation(annotationClass);
        // look at the class if not on the method
        if (annotation == null) {
            annotation = getTestClass().getJavaClass().getAnnotation(annotationClass);
        }
        return annotation;
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        if (_testMethods != null) {
            return _testMethods;
        }
        _testMethods = super.computeTestMethods();
        return _testMethods;
    }

    /**
     * Build a description containing additional annotations holding custom information for the runner.
     */
    @Override
    protected Description describeChild(final FrameworkMethod method) {
        final Annotation[] originalAnnotations = method.getAnnotations();
        final Annotation[] newAnnotations = new Annotation[originalAnnotations.length + 1];
        System.arraycopy(originalAnnotations, 0, newAnnotations, 0, originalAnnotations.length);
        newAnnotations[newAnnotations.length - 1] = _driverKey;
        return Description.createTestDescription(getTestClass().getJavaClass(),
                testName(method), newAnnotations);
    }
    
    @Override
    protected Statement methodBlock(final FrameworkMethod method) {
        Statement statement = super.methodBlock(method);
        
        final NotYetImplemented annotation = readAnnotation(method.getMethod(), NotYetImplemented.class);
        if (annotation != null) {
            statement = new StatementForNotYetImplemented(statement);
        }

    	final int tries = getTries(method);
    	if (tries != 1) {
    		return new StatementWithRetry(statement, tries);
    	}
    	return statement;
    }

    private int getTries(final FrameworkMethod method) {
        final Retry tries = method.getAnnotation(Retry.class);
        return tries != null ? tries.value() : 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filter(final Filter filter) throws NoTestsRemainException {
        computeTestMethods();

        for (final ListIterator<FrameworkMethod> iter = _testMethods.listIterator(); iter.hasNext();) {
            final FrameworkMethod method = iter.next();
            // compute 2 descriptions to verify if it is the intended test:
            // - one "normal", this is what Eclipse's filter awaits when typing Ctrl+X T
            //   when cursor is located on a test method
            // - one with browser nickname, this is what is needed when re-running a test from
            //   the JUnit view
            // as the list of methods is cached, this is what will be returned when computeTestMethods() is called
            final Description description = Description.createTestDescription(getTestClass().getJavaClass(),
                method.getName());
            final Description description2 = Description.createTestDescription(getTestClass().getJavaClass(),
                testName(method));
            if (!filter.shouldRun(description) && !filter.shouldRun(description2)) {
                iter.remove();
            }
        }
    }
}

class StatementWithRetry extends Statement {
	private final Statement _wrapped;
	private final int _maxRetries;
	public StatementWithRetry(final Statement statement, final int tries) {
		_wrapped = statement;
		_maxRetries = tries;
	}

	@Override
	public void evaluate() throws Throwable {
		for (int i=0; i<_maxRetries-1; ++i) {
			try {
				_wrapped.evaluate();
				return;
			}
			catch (final Throwable t) {
				// ignore it
			}
		}
		_wrapped.evaluate();
	}
}

class StatementForNotYetImplemented extends Statement {
    private final Statement _wrapped;

    public StatementForNotYetImplemented(final Statement statement) {
        _wrapped = statement;
    }

    @Override
    public void evaluate() throws Throwable {
        try {
            _wrapped.evaluate();
        }
        catch (final Throwable t) {
            // nothing, this is as expected
            return;
        }
        throw new NotYetImplemented.WorksAlreadyException();
    }
}