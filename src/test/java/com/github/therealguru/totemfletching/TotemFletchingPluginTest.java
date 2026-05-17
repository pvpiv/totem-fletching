package com.github.therealguru.totemfletching;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import org.slf4j.LoggerFactory;

public class TotemFletchingPluginTest {
    public static void main(String[] args) throws Exception {
        // Enable debug logging for the plugin so [EntVisit] lines appear in the log
        Logger pluginLogger = (Logger) LoggerFactory.getLogger(
                "com.github.therealguru.totemfletching");
        pluginLogger.setLevel(Level.DEBUG);

        ExternalPluginManager.loadBuiltin(TotemFletchingPlugin.class);
        RuneLite.main(args);
    }
}
