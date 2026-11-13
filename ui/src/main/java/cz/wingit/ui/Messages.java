package cz.wingit.ui;

import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * Access to localized UI strings ({@code cz/wingit/ui/messages*.properties}).
 */
public final class Messages {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("cz.wingit.ui.messages");

    private Messages() {
    }

    public static String get(String key) {
        return BUNDLE.getString(key);
    }

    public static String format(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }
}
