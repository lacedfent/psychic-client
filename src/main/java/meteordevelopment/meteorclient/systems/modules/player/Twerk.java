/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.player;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;

public class Twerk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("How often to press the sneak key in ticks.")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private int timer;

    public Twerk() {
        super(Categories.Player, "twerk", "Rapidly presses the sneak key. Useful for auto farming sugar cane.");
    }

    @Override
    public void onDeactivate() {
        mc.options.keyShift.setDown(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate() || mc.gui.screen() != null) return;

        timer++;
        if (timer >= delay.get()) {
            timer = 0;
            mc.options.keyShift.setDown(!mc.options.keyShift.isDown());
        }
    }
}