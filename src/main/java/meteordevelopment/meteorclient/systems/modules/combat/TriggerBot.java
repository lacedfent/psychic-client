/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.EntityHitResult;

import java.util.Set;

public class TriggerBot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("When to attack.")
        .defaultValue(Mode.Click)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your hand when attacking.")
        .defaultValue(true)
        .build()
    );

    public TriggerBot() {
        super(Categories.Combat, "trigger-bot", "Attacks the entity your crosshair is on.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mode.get() != Mode.Hold) return;
        if (!mc.options.keyAttack.isDown()) return;

        attack();
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (mode.get() != Mode.Click) return;
        if (event.action != KeyAction.Press || event.button() != 0) return;

        attack();
    }

    private void attack() {
        if (!Utils.canUpdate() || mc.gui.screen() != null) return;

        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() != null) {
            Entity target = hit.getEntity();

            if (target.isAlive() && !target.isRemoved()
                && EntityUtils.isAttackable(target.getType())
                && entities.get().contains(target.getType())
                && mc.player.getAttackStrengthScale(0) >= 1) {
                mc.gameMode.attack(mc.player, target);
                if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    public enum Mode {
        Click,
        Hold
    }
}