package com.belote.i18n;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Lightweight localisation manager.
 *
 * <p>Kept free of any JavaFX dependency so the rules engine and the tests can
 * use it. Listeners let the UI re-translate itself in place when the language
 * changes, rather than having to be rebuilt.
 */
public final class I18n {

    public static final Locale ENGLISH = Locale.forLanguageTag("en");
    public static final Locale BULGARIAN = Locale.forLanguageTag("bg");

    private static final String BUNDLE = "i18n.messages";

    private static Locale locale = ENGLISH;
    private static ResourceBundle bundle = load(ENGLISH);
    private static final List<Runnable> listeners = new ArrayList<>();

    private I18n() {}

    private static ResourceBundle load(Locale l) {
        // Java 9+ reads .properties bundles as UTF-8, so the Cyrillic strings
        // need no escaping.
        return ResourceBundle.getBundle(BUNDLE, l);
    }

    public static Locale locale() { return locale; }

    public static void setLocale(Locale l) {
        if (l.equals(locale)) return;
        locale = l;
        bundle = load(l);
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    public static void toggleLocale() {
        setLocale(locale.equals(ENGLISH) ? BULGARIAN : ENGLISH);
    }

    public static void addListener(Runnable r) { listeners.add(r); }

    public static void removeListener(Runnable r) { listeners.remove(r); }

    /** Translates a key, returning the key itself if it is missing. */
    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static String t(String key, Object... args) {
        return MessageFormat.format(t(key), args);
    }

    /** For tests: restore the default language. */
    public static void reset() { setLocale(ENGLISH); }
}
