package com.example.addon;

import com.example.addon.modules.ShulkerBoxItemFetcher;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class AutoRegearAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("AutoRegear");

    @Override
    public void onInitialize() {
        Modules.get().add(new ShulkerBoxItemFetcher());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("00011152", "AutoRegear");
    }
}
