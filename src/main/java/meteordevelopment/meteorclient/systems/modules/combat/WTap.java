/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class WTap extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ticks = sgGeneral.add(new IntSetting.Builder()
        .name("ticks")
        .description("How many ticks to release W for.")
        .defaultValue(1)
        .min(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> onlyWhileHoldingW = sgGeneral.add(new BoolSetting.Builder()
        .name("only-while-holding-w")
        .description("Only w-taps if you are holding W.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Minimum time in ticks between w-taps.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private int ticksLeft;
    private int cooldownLeft;
    private boolean wasDown;

    public WTap() {
        super(Categories.Combat, "w-tap", "Automatically w-taps for you, resetting your sprint to do more knockback.");
    }

    @Override
    public void onDeactivate() {
        if (wasDown) mc.options.keyUp.setDown(true);
        ticksLeft = 0;
        wasDown = false;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (cooldownLeft > 0) return;
        if (onlyWhileHoldingW.get() && !mc.options.keyUp.isDown()) return;
        if (!mc.player.onGround()) return;

        wasDown = mc.options.keyUp.isDown();
        mc.options.keyUp.setDown(false);
        ticksLeft = ticks.get();
        cooldownLeft = cooldown.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (cooldownLeft > 0) cooldownLeft--;

        if (ticksLeft > 0) {
            ticksLeft--;

            if (ticksLeft == 0 && wasDown) {
                mc.options.keyUp.setDown(true);
            }
        }
    }
}
