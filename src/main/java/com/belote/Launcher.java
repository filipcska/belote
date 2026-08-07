package com.belote;

import com.belote.ui.BeloteApp;

/**
 * Entry point for the shaded jar.
 *
 * <p>Deliberately does not extend {@code Application}: when the JavaFX classes
 * live on the class path rather than the module path, the JVM refuses to launch
 * a main class that does, so the fat jar needs this indirection.
 */
public final class Launcher {
    public static void main(String[] args) {
        BeloteApp.main(args);
    }
}
