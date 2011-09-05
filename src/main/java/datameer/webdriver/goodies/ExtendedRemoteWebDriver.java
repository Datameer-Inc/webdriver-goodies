package datameer.webdriver.goodies;

import java.net.URL;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Workaround for the fact that {@link RemoteWebDriver} doesn't currently implement
 * {@link TakesScreenshot}.
 * @see <a href="http://code.google.com/p/selenium/issues/detail?id=306">WebDriver bug 306</a>
 * @author Marc Guillemot
 * @version $Revision:  $
 */
public class ExtendedRemoteWebDriver extends RemoteWebDriver implements TakesScreenshot {

    public ExtendedRemoteWebDriver(final URL url, final DesiredCapabilities capabilities) {
        super(url, capabilities);
    }

    @Override
    public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
        final String base64 = execute(DriverCommand.SCREENSHOT).getValue().toString();
        return target.convertFromBase64Png(base64);
    }
}
