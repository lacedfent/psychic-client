/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.ModuleListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.List;

public class Panic extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Module>> blacklist = sgGeneral.add(new ModuleListSetting.Builder()
        .name("blacklist")
        .description("Modules to not disable.")
        .build()
    );

    public Panic() {
        super(Categories.Misc, "panic", "Disables all active modules.");
    }

    @Override
    public void onActivate() {
        for (Module module : Modules.get().getAll()) {
            if (module == this || blacklist.get().contains(module)) continue;
            if (module.isActive()) module.toggle();
        }
    }
}
