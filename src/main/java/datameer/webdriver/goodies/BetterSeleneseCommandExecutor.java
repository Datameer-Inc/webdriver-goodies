package datameer.webdriver.goodies;

import static org.openqa.selenium.remote.DriverCommand.SEND_KEYS_TO_ELEMENT;

import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.SeleneseCommandExecutor;
import org.openqa.selenium.internal.selenesedriver.SendKeys;

import com.thoughtworks.selenium.Selenium;

/**
 * Extension of {@link SeleneseCommandExecutor} trying to insert workarounds for existing issues.
 * @author Marc Guillemot
 * @version $Revision:  $
 */
public class BetterSeleneseCommandExecutor extends SeleneseCommandExecutor {

    public BetterSeleneseCommandExecutor(final Selenium sel) {
        super(sel);
        
        addCommand(SEND_KEYS_TO_ELEMENT, new BetterSendKeys());
    }
    
}

/**
 * Workaround for http://jira.openqa.org/browse/SEL-519
 * Some keys like "." disappear and aren't set.
 */
class BetterSendKeys extends SendKeys {
    private static final String setTextJs =
        "(function setText() {"
        + "  selenium.browserbot.findElement('LOCATOR').value = 'VALUE';"
        + "})();";

    @Override
    public Void apply(Selenium selenium, final Map<String, ?> args) {
        final CharSequence[] allKeys = (CharSequence[]) args.get("value");
        final StringBuilder builder = new StringBuilder();
        for (final CharSequence seq : allKeys) {
            builder.append(seq);
        }
        String value = builder.toString();
        
        if (value.contains(".")) {
            
            final String locator = getLocator(args);

            value = value.replace("\\", "\\\\");
            final String js = setTextJs.replace("LOCATOR", locator).replace("VALUE", value);
            selenium.getEval(js);

            final Map<String, Object> newArgs = new HashMap<String, Object>(args);
            newArgs.put("value", new String[] { "" });
            super.apply(selenium, newArgs);
        }
        else {
            super.apply(selenium, args);
        }

        return null;
    }
}