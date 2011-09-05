package datameer.webdriver.goodies;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.io.IOUtils;


/**
 * Holds custom settings from test.properties useful for the tests.
 * @author Marc Guillemot
 * @version $Revision:  $
 */
public class TestsConfiguration {
    private static final TestsConfiguration INSTANCE = new TestsConfiguration();
    private final Map<String, String> _rawProperties;
    private String _currentDriver;

    public static TestsConfiguration getInstance() {
        return INSTANCE;
    }

    private TestsConfiguration() {
        final InputStream is = TestsConfiguration.class.getClassLoader().getResourceAsStream("tests.properties");
        if (is == null) {
            _rawProperties = new HashMap<String, String>();
            return; // or should we throw?
        }

        _rawProperties = readProperties(is);
    }

    /**
     * Gets the drivers defined in the config file with the associated options.
     * The syntax will look like:
     * <pre>
     * drivers=ff,ie,hu_ff
     * ie.remoteUrl=http://foo:4444/wd/hub
     * </pre>
     * @return the settings
     */
    public Map<String, Map<String, String>> getDriverSettings() {
        final Map<String, Map<String, String>> settings = new HashMap<String, Map<String,String>>();
        final String[] drivers = _rawProperties.get("drivers").split(",");

        for (final String driver : drivers) {
            final String driverName = driver.trim();
            // read options for this driver
            final Map<String, String> options = new HashMap<String, String>();
            for (final Entry<String, String> entry : _rawProperties.entrySet()) {
                if (entry.getKey().startsWith(driverName + ".")) {
                    options.put(entry.getKey().substring(driverName.length() + 1), entry.getValue());
                }
            }
            settings.put(driverName, options);
        }
        return settings;
    }
  
    private static Map<String, String> readProperties(final InputStream is) {
        final Properties props = new Properties();
        try {
            props.load(is);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            IOUtils.closeQuietly(is);
        }
        
        final Map<String, String> map = new HashMap<String, String>();
        for (final Entry<Object, Object> entry : props.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return map;
    }


    /**
     * Return the configured url
     * @return
     */
    public String getTestServer() {
        return getAndExpand("config.baseUrl");
    }

    private String getAndExpand(final String key) {
        String value = _rawProperties.get(key);
        if (value != null && value.contains("$localhost")) {
            value = value.replace("$localhost", getHostAddress()); // TODO: use a cache
        }
        return value;
    }

    /**
     * Gets the first IPv4 address of current computer that is not 127.0.0.1, if any
     * otherwise "localhost".
     * @return
     */
    String getHostAddress() {
        try {
            for (final Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
                final NetworkInterface ni = e.nextElement();
                for (final InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    final InetAddress address = ia.getAddress();
                    if (address instanceof Inet4Address) {
                        final String hostAddress = address.getHostAddress();
                        if (!"127.0.0.1".equals(hostAddress)) {
                            return hostAddress;
                        }
                    }
                }
            }
        }
        catch (final SocketException e) {
            throw new RuntimeException(e);
        }
        return "localhost"; // can't do better
    }

    /**
     * Gets the property "resourceFolder" for the browser currently running.
     * @return <code>null</code> if none has been specified.
     */
    public String getCurrentResourceFolder() {
        return getCurrentProperty("resourceFolder");
    }

    private String getCurrentProperty(final String propertyName) {
        if (_currentDriver != null) {
            String s = _rawProperties.get(_currentDriver + "." + propertyName);
            if (s != null) {
                return s;
            }
        }
        return _rawProperties.get(propertyName);
    }

    public void setCurrentDriver(final String name) {
        _currentDriver = name;
    }
}
