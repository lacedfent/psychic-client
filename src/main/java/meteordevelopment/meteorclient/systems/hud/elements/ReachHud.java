/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ReachHud extends HudElement {
    public static final HudElementInfo<ReachHud> INFO = new HudElementInfo<>(Hud.GROUP, "reach", "Displays the distance of your last attack.", ReachHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgScale = settings.createGroup("Scale");
    private final SettingGroup sgBackground = settings.createGroup("Background");

    // General

    private final Setting<Boolean> shadow = sgGeneral.add(new BoolSetting.Builder()
        .name("shadow")
        .description("Text shadow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> textColor = sgGeneral.add(new ColorSetting.Builder()
        .name("text-color")
        .description("Color of the text.")
        .defaultValue(new SettingColor())
        .build()
    );

    private final Setting<SettingColor> distanceColor = sgGeneral.add(new ColorSetting.Builder()
        .name("distance-color")
        .description("Color of the distance value.")
        .defaultValue(new SettingColor(235, 158, 52))
        .build()
    );

    // Scale

    private final Setting<Boolean> customScale = sgScale.add(new BoolSetting.Builder()
        .name("custom-scale")
        .description("Applies a custom scale to this hud element.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> scale = sgScale.add(new DoubleSetting.Builder()
        .name("scale")
        .description("Custom scale.")
        .visible(customScale::get)
        .defaultValue(1)
        .min(0.5)
        .sliderRange(0.5, 3)
        .build()
    );

    // Background

    private final Setting<Boolean> background = sgBackground.add(new BoolSetting.Builder()
        .name("background")
        .description("Displays background.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> backgroundColor = sgBackground.add(new ColorSetting.Builder()
        .name("background-color")
        .description("Color used for the background.")
        .visible(background::get)
        .defaultValue(new SettingColor(25, 25, 25, 50))
        .build()
    );

    private double lastReach = 0;

    public ReachHud() {
        super(INFO);
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || event.entity == null) return;
        lastReach = Math.round(mc.player.distanceTo(event.entity) * 100.0) / 100.0;
    }

    @Override
    public void render(HudRenderer renderer) {
        double reach = isInEditor() ? 3.24 : lastReach;

        double x2 = renderer.text("Reach ", x, y, textColor.get(), shadow.get(), getScale());
        x2 = renderer.text(String.format("%.2f", reach), x2, y, distanceColor.get(), shadow.get(), getScale());

        setSize(x2 - x, renderer.textHeight(shadow.get(), getScale()));

        if (background.get()) {
            renderer.quad(x, y, getWidth(), getHeight(), backgroundColor.get());
        }
    }

    private double getScale() {
        return customScale.get() ? scale.get() : Hud.get().getTextScale();
    }
}
