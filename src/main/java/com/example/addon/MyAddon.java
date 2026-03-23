// src/main/java/com/example/addon/MyAddon.java
package com.example.addon;

import com.example.addon.modules.AutoRegear;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("MyAddon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing My Custom Addon");

        // Register your module
        Modules.get().add(new AutoRegear());
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
