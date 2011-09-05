package datameer.webdriver.goodies;

import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;

/**
 * An {@link HtmlUnitDriver} that uses the possibilities of <a href="http://htmlunit.sf.net">HtmlUnit</a>
 * to detect potential problems rather than hiding this information.
 * For example this driver will throw:
 * - on HTTP error codes
 * - on JavaScript errors (caution: this may be real ones or erros due to HtmlUnit uncomplete JS simulation)
 * 
 * @author Marc Guillemot
 * @version $Revision:  $
 */
public class BetterHtmlUnitDriver extends HtmlUnitDriver {
	
	public BetterHtmlUnitDriver(final BrowserVersion browserVersion) {
		super(browserVersion);
	}
	
	@Override
	protected WebClient modifyWebClient(final WebClient client) {
	    client.setThrowExceptionOnFailingStatusCode(true);
	    client.setJavaScriptEnabled(true);
	    client.setThrowExceptionOnScriptError(true);

	    return client;
	}
	
	@Override
	public WebClient getWebClient() {
	    return super.getWebClient();
	}
}
