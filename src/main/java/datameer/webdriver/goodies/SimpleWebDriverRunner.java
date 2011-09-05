package datameer.webdriver.goodies;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxBinary;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.ie.InternetExplorerDriver;
import org.openqa.selenium.remote.CommandExecutor;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.thoughtworks.selenium.DefaultSelenium;
import com.thoughtworks.selenium.Selenium;

import datameer.webdriver.goodies.SimpleWebDriverRunner.WebDriverDefinition.DriverKey;

/**
 * A test runner allowing to run "functional unit tests" in different browsers
 * using {@link WebDriver}.
 * @author Marc Guillemot
 * @version $Revision:  $
 */
public class SimpleWebDriverRunner extends Suite {
    /**
     * The number of tries that test will be executed.
     * The test will fail if and only if all trials failed.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface Retry {
        /**
         * The value.
         */
        int value() default 1;
    }
	
    private final ArrayList<Runner> _runners = new ArrayList<Runner>();
    private final static List<WebDriverDefinition> _driverDefinitions = createDriversList();
    private static CurrentExecutionInfo _currentExecutionInfo; // as thread local when we want to run parallel tests

    public SimpleWebDriverRunner(final Class<?> klass) throws Throwable {
        super(klass, new ArrayList<Runner>());

        initCurrentExecutionInfo();

        for (final WebDriverDefinition def : _driverDefinitions) {
            _runners.add(buildClassRunner(klass, def));
        }
        getChildren().addAll(_runners);
        
        
	}

    /**
     * Builds the runner used to run the class' tests for one WebDriver. 
     * @param testClass the test class
     * @param def the WebDriver definition
     * @return the runner
     * @throws InitializationError
     */
    protected WebDriverClassRunner buildClassRunner(final Class<?> testClass, final WebDriverDefinition def) throws InitializationError {
        return new WebDriverClassRunner(testClass, def.getKey());
    }

    protected void initCurrentExecutionInfo() {
        if (_currentExecutionInfo == null) {
            _currentExecutionInfo = new CurrentExecutionInfo();
        }
        _currentExecutionInfo.setRunner(this);
    }

	private static List<WebDriverDefinition> createDriversList() {
	    final Map<String, Map<String, String>> driverSettings = TestsConfiguration.getInstance().getDriverSettings();
	    
        final List<WebDriverDefinition> definitions = new ArrayList<WebDriverDefinition>();
	    if (driverSettings.isEmpty()) {
			// default drivers: the one that may be available on any OS
			definitions.add(new WebDriverDefinition("FF"));
			definitions.add(new WebDriverDefinition("Chrome"));
			definitions.add(new WebDriverDefinition("HU_FF"));
			definitions.add(new WebDriverDefinition("HU_IE"));
		}
		else {
			for (final Entry<String, Map<String, String>> setting : driverSettings.entrySet()) {
                final String driverName = setting.getKey();
                final Map<String, String> options = setting.getValue();
                definitions.add(new WebDriverDefinition(driverName, options));
			}
		}
		
		if (definitions.isEmpty()) {
			throw new Error("No driver defined to run tests!");
		}
		return definitions;
	}

    /**
     * Gets the {@link WebDriver} instance currently used
     * or build a new one if none already exists.
     * @return an instance
     */
	public static WebDriver getDriver() {
		return _currentExecutionInfo.getDriverInternal();
	}

	/**
	 * Gets the {@link WebDriver} instance currently used
	 * @return <code>null</code> if none
	 */
    public static WebDriver getCurrentDriver() {
        return _currentExecutionInfo.getCurrentDriver();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filter(final Filter filter) throws NoTestsRemainException {
        boolean atLeastOne = false;
        for (final Runner runner : getChildren()) {
            final BlockJUnit4ClassRunner junit4Runner = (BlockJUnit4ClassRunner) runner;
            try {
                junit4Runner.filter(filter);
                atLeastOne = true;
            }
            catch (final NoTestsRemainException e) {
                // nothing
            }
        }

        if (!atLeastOne) {
            throw new NoTestsRemainException();
        }
    }

    @Override
    public void run(final RunNotifier notifier) {

        final RunListener listener = new RunListener() {
            private final Map<Description, Failure> failures = new HashMap<Description, Failure>();

            @Override
            public void testStarted(final Description description) throws Exception {
                final WebDriverDefinition def = getDriverDefinition(description);
                _currentExecutionInfo.setNextDriver(def);
                notifyTestStarted(description.getTestClass(), description.getMethodName(), def);
            }

            private WebDriverDefinition getDriverDefinition(final Description description) {
                final DriverKey driverKey = description.getAnnotation(DriverKey.class);
                return SimpleWebDriverRunner.this.getDriverDefinition(driverKey);
            }

            @Override
            public void testFailure(final Failure failure) throws Exception {
                failures.put(failure.getDescription(), failure);
            }
            
            @Override
            public void testIgnored(final Description description) throws Exception {
                final String ignoreReason = description.getAnnotation(Ignore.class).value();
                notifyTestIgnored(description.getTestClass(), description.getMethodName(), ignoreReason, getDriverDefinition(description));
            }

            @Override
            public void testFinished(final Description description) throws Exception {
                final Failure failure = failures.get(description);
                final Throwable failureCause = failure != null ? failure.getException() : null;
                notifyTestFinished(description.getTestClass(), description.getMethodName(), failureCause);

                _currentExecutionInfo.release(getDriverDefinition(description));
            }
        };
        
        notifier.addListener(listener);
        super.run(notifier);
    	
		_currentExecutionInfo.closeDriverQuietly();
    }

    protected void notifyTestStarted(final Class<?> testClass, final String methodName, final WebDriverDefinition def) {
        // nothing, just for subclasses
    }

    /**
     * Gets called when a single test is ignored (due to @Ignore)
     * @param testClass
     * @param methodName
     * @param ignoreReason
     * @param driverDefinition
     */
    protected void notifyTestIgnored(final Class<?> testClass, final String methodName, final String ignoreReason, final WebDriverDefinition driverDefinition) {
        // nothing, just for subclasses
    }
    
    /**
     * 
     * @param testClass
     * @param methodName
     * @param failureCause <code>null</code> if the test was successful
     */
    protected void notifyTestFinished(final Class<?> testClass, final String methodName, final Throwable failureCause) {
        // nothing, just for subclasses
    }

    protected static class WebDriverDefinition {
    	private static final String KEY_BINARY = "bin";
        private static final Object KEY_REMOTEURL = "remoteDriverUrl";
        private final String _name;
    	private final Map<String, String> _options;
        private File _firefoxEmptyBookmarksFile;
        private final DriverKey _driverKey;
        
        /**
         * Used to add information to the JUnit test {@link Description}.
         * Annotations are the only things that can be attached there.
         * @author Marc Guillemot
         * @version $Revision:  $
         */
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.METHOD})
        public static @interface DriverKey {
            String getName();
        }

        private static class DriverKeyImpl implements DriverKey {
            private final String _key;
            DriverKeyImpl(final String key) {
                _key = key;
            }
            @Override
            public Class<? extends Annotation> annotationType() {
                return DriverKey.class;
            }
            @Override
            public String getName() {
                return _key;
            }
        }

    	public WebDriverDefinition(final String name) {
    	    this(name, new HashMap<String, String>());
    	}
    	
        public WebDriverDefinition(final String name, final Map<String, String> options) {
            _name = name;
            _options = options;
            _driverKey = new DriverKeyImpl(name);
            
            if (isFirefox()) {
                try {
                    _firefoxEmptyBookmarksFile = File.createTempFile("empty-bookmarks", ".html");
                    _firefoxEmptyBookmarksFile.createNewFile();
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public DriverKey getKey() {
            return _driverKey;
        }
        
        protected WebDriver buildDriver() {
            final WebDriver driver;
            if (isRemote()) {
                driver = buildRemoteDriver();
            }
            else if ("Chrome".equalsIgnoreCase(getName())) {
                driver = new ChromeDriver(); 
            }
            else if ("HU_FF".equalsIgnoreCase(getName())) {
                driver = new BetterHtmlUnitDriver(BrowserVersion.FIREFOX_3_6);
            }
            else if ("HU_IE".equalsIgnoreCase(getName())) {
                driver = new BetterHtmlUnitDriver(BrowserVersion.INTERNET_EXPLORER_8);
            }
            else if (isFirefox()) {
                final File pathToFirefoxBinary;
                if (_options.containsKey(KEY_BINARY)) {
                    pathToFirefoxBinary = new File(_options.get(KEY_BINARY));
                }
                else {
                    pathToFirefoxBinary = null;
                }
                final FirefoxBinary ffBin = new FirefoxBinary(pathToFirefoxBinary);
                ffBin.setEnvironmentProperty("DISPLAY", ":0.0");
                final FirefoxProfile profile = new FirefoxProfile();
//                profile.setEnableNativeEvents(true);
                
                // set empty bookmarks file to avoid Browser to make requests to some RSS feed defined in the default bookmarks
                profile.setPreference("browser.bookmarks.file", _firefoxEmptyBookmarksFile.getAbsolutePath());

                driver = new FirefoxDriver(ffBin, profile);
//              profile.setPreference("network.proxy.http_port", 9090);
//              profile.setPreference("network.proxy.http", "localhost");
//                profile.setPreference("network.proxy.type", 1);
//                profile.setPreference("network.proxy.no_proxies_on", "");
            }
            else if ("IE".equalsIgnoreCase(getName())) {
                driver = new InternetExplorerDriver();
            }
            else if ("safari".equalsIgnoreCase(getName())) {
                final String baseURL = TestsConfiguration.getInstance().getTestServer();
                final Selenium sel = new DefaultSelenium("localhost", 4444, "*safari", baseURL);

                final CommandExecutor executor = new BetterSeleneseCommandExecutor(sel);
                DesiredCapabilities dc = new DesiredCapabilities();
                driver = new RemoteWebDriver(executor, dc);
            }
            else {
                throw new RuntimeException("Unknown webdriver: " + getName());
            }
            
            return driver;
        }

        private boolean isFirefox() {
            return getName().toUpperCase().startsWith("FF");
        }

    	/**
    	 * Used to build the {@link RemoteWebDriver} with the right settings to run the tests on a remote server.
    	 * @return the driver
    	 */
        private WebDriver buildRemoteDriver() {
            final DesiredCapabilities capabilities;
            if ("IE".equalsIgnoreCase(getName())) {
                capabilities = DesiredCapabilities.internetExplorer();
            }
            else {
                throw new RuntimeException("Not yet supported: remote " + getName());
            }

            final WebDriver driver = new ExtendedRemoteWebDriver(getRemoteDriverUrl(), capabilities);
            return driver;
        }

        private URL getRemoteDriverUrl() {
            try {
                return new URL(_options.get(KEY_REMOTEURL));
            } 
            catch (final MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Indicate if this defines a {@link RemoteWebDriver}.
         * @return true/false
         */
        private boolean isRemote() {
            return _options.containsKey(KEY_REMOTEURL);
        }

        public String getName() {
    		return _name;
    	}

        @Override
        protected void finalize() throws Throwable {
            if (_firefoxEmptyBookmarksFile != null) {
                _firefoxEmptyBookmarksFile.delete();
            }
        }
	}

	public static class CurrentExecutionInfo {
	    private WebDriver _driver;
        private WebDriverDefinition _driverDefinition, _nextDriverDefinition;
        private SimpleWebDriverRunner _webDriverRunner;

        void setRunner(final SimpleWebDriverRunner webDriverRunner) {
            _webDriverRunner = webDriverRunner;
        }

        private WebDriver getDriverInternal() {
            if (_driver == null || _driverDefinition != _nextDriverDefinition) {
                if (_driver != null) {
                    try {
                        _driver.quit();
                    }
                    catch (final Exception e) {
                        // ignore
                    }
                }
                _driver = _webDriverRunner.buildDriver(_nextDriverDefinition);
                TestsConfiguration.getInstance().setCurrentDriver(_nextDriverDefinition.getName());
                
                _driverDefinition = _nextDriverDefinition;
            }

            return _driver;
        }

        void setNextDriver(final WebDriverDefinition driverDefinition) {
            _nextDriverDefinition = driverDefinition;
        }

        protected void closeDriverQuietly() {
            if (_driver != null) {
                try {
                    _driver.quit();
                }
                catch (final Exception e) {
                    // ignore it, happens for instance when a @Test(timeout=...) has failed timed out
                    // see http://code.google.com/p/selenium/issues/detail?id=1998
                    System.err.println("Error quitting the driver:");
                    e.printStackTrace(System.err);
                }
                _driver = null;
            }
        }

        void release(final WebDriverDefinition browserVersion) {
            closeDriverQuietly();
        }

        /**
         * Gets the current driver or <code>null</code> if {@link #getDriver()} has not
         * been called by the currently executed test.
         * @return the driver for the current test
         */
        WebDriver getCurrentDriver() {
            return _driver;
        }
	}

    protected WebDriver buildDriver(final WebDriverDefinition driverDefinition) {
        return driverDefinition.buildDriver();
    }

    private WebDriverDefinition getDriverDefinition(final DriverKey driverKey) {
        for (final WebDriverDefinition driverDefinition : _driverDefinitions) {
            if (driverDefinition.getKey().equals(driverKey)) {
                return driverDefinition;
            }
        }
        throw new RuntimeException("No WebDriverDefinition found for " + driverKey.getName());
    }
}

