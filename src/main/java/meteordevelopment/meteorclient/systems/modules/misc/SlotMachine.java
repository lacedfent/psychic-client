/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public class SlotMachine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far in front of you the slot machine spawns.")
        .defaultValue(3.0)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
        .name("sounds")
        .description("Whether to play sounds when spinning the machine.")
        .defaultValue(true)
        .build()
    );

    private static final String[] SYMBOL_NAMES = { "7", "bar", "cherry", "bell", "lemon", "skull" };
    private static final Color[] SYMBOL_COLORS = {
        new Color(255, 215, 0),
        new Color(220, 40, 40),
        new Color(255, 80, 120),
        new Color(255, 200, 60),
        new Color(140, 255, 90),
        new Color(200, 200, 210)
    };

    private Machine machine;
    private long spinStart = -1;
    private int[] spinResults;
    private long[] settleAt;
    private boolean evaluated;
    private long lastWinAt = -1;

    public SlotMachine() {
        super(Categories.Fun, "slot-machine", "Spawns a slot machine only you can see. Right click it to spin!");
    }

    @Override
    public void onActivate() {
        if (!Utils.canUpdate()) return;

        machine = Machine.create(distance.get());
        spinStart = -1;
        spinResults = new int[3];
        settleAt = new long[3];
        for (int i = 0; i < 3; i++) spinResults[i] = (int) (Math.random() * SYMBOL_NAMES.length);
        evaluated = false;

        info("A slot machine materializes in front of you... good luck!");
    }

    @Override
    public void onDeactivate() {
        machine = null;
        spinStart = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!Utils.canUpdate()) {
            machine = null;
            return;
        }
        if (machine == null || spinStart < 0 || evaluated) return;

        long now = System.currentTimeMillis();
        if (now < settleAt[2]) return;
        evaluated = true;

        if (spinResults[0] == spinResults[1] && spinResults[1] == spinResults[2]) {
            warning("JACKPOT!!! Three %ss!", SYMBOL_NAMES[spinResults[0]]);
            if (sounds.get()) mc.player.playSound(SoundEvents.TOTEM_USE, 1f, 1f);
            lastWinAt = now;
        }
        else if (spinResults[0] == spinResults[1] || spinResults[1] == spinResults[2] || spinResults[0] == spinResults[2]) {
            info("Two of a kind... so close!");
        }
        else {
            info("You got [%s] [%s] [%s]... no luck!", SYMBOL_NAMES[spinResults[0]], SYMBOL_NAMES[spinResults[1]], SYMBOL_NAMES[spinResults[2]]);
        }
    }

    @EventHandler
    private void onMouseClick(MouseClickEvent event) {
        if (event.action != KeyAction.Press || event.button() != 1) return;
        if (machine == null || !Utils.canUpdate() || mc.gui.screen() != null) return;

        if (machine.hit(mc.player.getEyePosition(), mc.player.getViewVector(1.0f))) spin();
    }

    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (machine == null || !Utils.canUpdate()) return;

        if (machine.hit(mc.player.getEyePosition(), mc.player.getViewVector(1.0f))) event.cancel();
    }

    private void spin() {
        long now = System.currentTimeMillis();
        if (spinStart >= 0 && now < settleAt[2]) return;

        spinStart = now;
        evaluated = false;

        spinResults = new int[3];
        settleAt = new long[3];
        for (int i = 0; i < 3; i++) {
            double roll = Math.random();
            spinResults[i] = roll < 0.1 ? 0 : roll < 0.25 ? 1 : 2 + (int) (Math.random() * 4);
            settleAt[i] = now + 700 + i * 450 + (long) (Math.random() * 500);
        }

        if (sounds.get()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
    }

    private boolean isSpinning(long now) {
        return spinStart >= 0 && now < settleAt[2];
    }

    private int currentSymbol(int reel, long now) {
        if (spinStart < 0) return spinResults[reel];
        if (now >= settleAt[reel]) return spinResults[reel];
        return (int) ((now - spinStart) / 75 + reel * 7) % SYMBOL_NAMES.length;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (machine == null) return;

        long now = System.currentTimeMillis();
        int[] symbols = { currentSymbol(0, now), currentSymbol(1, now), currentSymbol(2, now) };
        machine.render(event.renderer, now, isSpinning(now), now - lastWinAt < 2500, symbols);
    }

    private static class Machine {
        private final Vec3 center;
        private final Vec3 front;
        private final Vec3 right;

        private Machine(BlockPos base, Vec3 front) {
            this.center = new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            this.front = front;
            this.right = new Vec3(-front.z, 0, front.x);
        }

        public static Machine create(double distance) {
            Vec3 eye = MeteorClient.mc.player.getEyePosition();
            Vec3 look = MeteorClient.mc.player.getViewVector(1.0f);
            BlockPos pos = BlockPos.containing(eye.x + look.x * distance, eye.y + look.y * distance, eye.z + look.z * distance);

            for (int i = 0; i < 8; i++) {
                if (!MeteorClient.mc.level.getBlockState(pos.below()).isAir() || pos.getY() <= MeteorClient.mc.level.getMinY()) break;
                pos = pos.below();
            }

            double fx = -Math.sin(Math.toRadians(MeteorClient.mc.player.getYRot()));
            double fz = -Math.cos(Math.toRadians(MeteorClient.mc.player.getYRot()));
            Vec3 front = Math.abs(fx) > Math.abs(fz) ? new Vec3(Math.signum(fx), 0, 0) : new Vec3(0, 0, Math.signum(fz));

            return new Machine(pos, front);
        }

        public Vec3 local(double u, double y, double v) {
            return new Vec3(center.x + front.x * u + right.x * v, center.y + y, center.z + front.z * u + right.z * v);
        }

        public boolean hit(Vec3 origin, Vec3 dir) {
            Vec3 min = local(-0.37, 0, -0.48);
            Vec3 max = local(0.35, 2.1, 0.48);

            double tMin = 0;
            double tMax = 6;

            for (int i = 0; i < 3; i++) {
                double o = i == 0 ? origin.x : i == 1 ? origin.y : origin.z;
                double d = i == 0 ? dir.x : i == 1 ? dir.y : dir.z;
                double lo = i == 0 ? min.x : i == 1 ? min.y : min.z;
                double hi = i == 0 ? max.x : i == 1 ? max.y : max.z;

                if (Math.abs(d) < 1e-8) {
                    if (o < lo || o > hi) return false;
                }
                else {
                    double t1 = (lo - o) / d;
                    double t2 = (hi - o) / d;
                    if (t1 > t2) {
                        double tmp = t1;
                        t1 = t2;
                        t2 = tmp;
                    }
                    tMin = Math.max(tMin, t1);
                    tMax = Math.min(tMax, t2);
                    if (tMin > tMax) return false;
                }
            }

            return tMin <= tMax;
        }

        private void box(Renderer3D renderer, double u1, double y1, double v1, double u2, double y2, double v2, Color side, Color lines) {
            Vec3 min = local(u1, y1, v1);
            Vec3 max = local(u2, y2, v2);
            renderer.box(min.x, min.y, min.z, max.x, max.y, max.z, side, lines, ShapeMode.Both, 0);
        }

        private void renderSymbol(Renderer3D renderer, int symbol, double rc, double y0) {
            Color color = SYMBOL_COLORS[symbol];
            Color dark = new Color(Math.max(0, color.r - 60), Math.max(0, color.g - 60), Math.max(0, color.b - 60), 255);

            switch (symbol) {
                case 0 -> {
                    box(renderer, -0.345, y0 + 0.12, rc - 0.07, -0.325, y0 + 0.16, rc + 0.07, color, dark);
                    box(renderer, -0.345, y0 + 0.02, rc + 0.015, -0.325, y0 + 0.13, rc + 0.065, color, dark);
                }
                case 1 -> {
                    box(renderer, -0.345, y0 + 0.02, rc - 0.07, -0.325, y0 + 0.16, rc + 0.07, color, dark);
                    box(renderer, -0.345, y0 + 0.07, rc - 0.07, -0.325, y0 + 0.11, rc + 0.07, dark, dark);
                }
                case 2 -> {
                    box(renderer, -0.345, y0 + 0.02, rc - 0.06, -0.325, y0 + 0.10, rc + 0.06, color, dark);
                    box(renderer, -0.345, y0 + 0.10, rc - 0.01, -0.325, y0 + 0.14, rc + 0.01, new Color(60, 200, 80), dark);
                }
                case 3 -> {
                    box(renderer, -0.345, y0 + 0.03, rc - 0.05, -0.325, y0 + 0.15, rc + 0.05, color, dark);
                    box(renderer, -0.345, y0 + 0.12, rc - 0.05, -0.325, y0 + 0.17, rc + 0.05, dark, dark);
                }
                case 4 -> {
                    box(renderer, -0.345, y0 + 0.02, rc - 0.05, -0.325, y0 + 0.14, rc + 0.05, color, dark);
                    box(renderer, -0.345, y0 + 0.03, rc - 0.065, -0.325, y0 + 0.13, rc - 0.045, dark, dark);
                    box(renderer, -0.345, y0 + 0.03, rc + 0.045, -0.325, y0 + 0.13, rc + 0.065, dark, dark);
                }
                case 5 -> {
                    box(renderer, -0.345, y0 + 0.02, rc - 0.06, -0.325, y0 + 0.14, rc + 0.06, color, dark);
                    box(renderer, -0.345, y0 + 0.06, rc - 0.045, -0.325, y0 + 0.10, rc - 0.015, new Color(30, 30, 40), dark);
                    box(renderer, -0.345, y0 + 0.06, rc + 0.015, -0.325, y0 + 0.10, rc + 0.045, new Color(30, 30, 40), dark);
                }
            }
        }

        public void render(Renderer3D renderer, long now, boolean spinning, boolean celebrating, int[] symbols) {
            Color base = new Color(45, 20, 80, 230);
            Color baseLight = celebrating ? new Color(255, 215, 0, 230) : new Color(80, 40, 140, 230);
            Color lines = new Color(255, 215, 0, 255);

            box(renderer, -0.35, 0, -0.45, 0.35, 1.9, 0.45, celebrating ? baseLight : base, lines);
            box(renderer, -0.36, 0.9, -0.46, 0.36, 0.95, 0.46, lines, lines);

            double hue = (now / 8) % 360;
            Color marquee = celebrating ? new Color(255, 215, 0) : Color.fromHsv(hue, 1.0, 1.0);
            box(renderer, -0.30, 1.9, -0.40, 0.30, 2.05, 0.40, marquee, lines);

            box(renderer, -0.37, 0.75, -0.44, -0.33, 1.75, 0.44, new Color(12, 12, 20, 255), lines);

            box(renderer, -0.37, 1.10, -0.035, -0.36, 1.15, 0.035, new Color(255, 215, 0), lines);

            box(renderer, -0.10, 1.05, 0.44, 0.10, 1.30, 0.46, new Color(200, 40, 40), lines);
            box(renderer, -0.04, 1.28, 0.46, 0.04, 1.34, 0.48, new Color(255, 80, 80), lines);

            double[] slotV = { -0.28, 0.0, 0.28 };
            for (int i = 0; i < 3; i++) {
                renderSymbol(renderer, symbols[i], slotV[i], 0.82);
            }
        }
    }
}