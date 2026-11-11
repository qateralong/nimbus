package com.qateralong.nimbus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

// A profile from the GPU name, on the careful side: integrated graphics never get more than Performance, laptop GPUs and
// 4K screens one step lower, unknown cards Balanced at most. Better to suggest less than to stutter.
public final class HardwareProfile {
    private static final String[] TIERS = {"max", "serious", "subtle", "none"};

    public record Pick(String profile, String gpu, Component reason) {
    }

    private HardwareProfile() {
    }

    public static Pick pick() {
        String gpu = GL11.glGetString(GL11.GL_RENDERER);
        gpu = gpu == null ? "?" : gpu.replaceAll("\\s*\\(.*?\\)", "").replace("/PCIe/SSE2", "").trim();
        String name = gpu.toLowerCase(Locale.ROOT);
        int tier;
        String kind;
        if (name.contains("llvmpipe") || name.contains("softpipe") || name.contains("microsoft basic") || name.contains("swiftshader")) {
            tier = 0;
            kind = "software";
        } else if (integrated(name)) {
            // old Intel HD/UHD are potatoes; recent APUs and Iris Xe hold Performance
            tier = name.matches(".*\\b(hd|uhd) graphics( \\d{3,4})?\\b.*") && !name.contains("iris") ? 0 : 1;
            kind = "integrated";
        } else {
            tier = discrete(name);
            kind = tier < 0 ? "unknown" : "discrete";
            if (tier < 0) {
                tier = 2;
            }
            if (name.contains("laptop") || name.contains("mobile") || name.contains("max-q") || name.matches(".*\\b\\d{3,4}m\\b.*")
                    || name.matches(".*\\brx\\s*\\d{4}s\\b.*")) {
                tier--;
                kind = "laptop";
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean huge = minecraft.getWindow().getWidth() * minecraft.getWindow().getHeight() > 2560 * 1440;
        if (huge) {
            tier--;
        }
        tier = Math.max(0, Math.min(TIERS.length - 1, tier));
        Component reason = Component.translatable("nimbus.auto.reason." + kind, gpu);
        if (huge) {
            reason = Component.translatable("nimbus.auto.reason.screen", reason);
        }
        Nimbus.LOGGER.info("Nimbus hardware profile: {} ({}, {}) -> {}", gpu, kind, huge ? "large screen" : "screen ok", TIERS[tier]);
        return new Pick(TIERS[tier], gpu, reason);
    }

    private static boolean integrated(String name) {
        return name.contains("intel") && !name.contains("arc")
                || name.contains("vega") && !name.contains("vega 56") && !name.contains("vega 64")
                || name.matches(".*radeon(\\(tm\\))? graphics.*") || name.matches(".*radeon \\d{3}m.*")
                || name.contains("mali") || name.contains("adreno") || name.contains("apple m");
    }

    // 1 = Performance, 2 = Balanced, 3 = Best quality; -1 when the name says nothing useful
    private static int discrete(String name) {
        Matcher nvidia = Pattern.compile("(rtx|gtx|gt)\\s*(\\d{3,4})").matcher(name);
        if (nvidia.find()) {
            String series = nvidia.group(1);
            int model = Integer.parseInt(nvidia.group(2));
            int generation = model / 100;
            int level = model % 100;
            if (series.equals("gt")) {
                return 0;
            }
            if (series.equals("rtx")) {
                // 2060/3050 Balanced, 2070+/3060+/4060+ Best quality
                if (generation >= 30) {
                    return level >= 60 ? 3 : 2;
                }
                return level >= 70 ? 3 : 2;
            }
            // GTX: 16xx and 1060+ Balanced, the rest Performance
            if (generation == 16) {
                return 2;
            }
            if (generation == 10) {
                return level >= 60 ? 2 : 1;
            }
            return 1;
        }
        Matcher amd = Pattern.compile("rx\\s*(\\d{3,4})").matcher(name);
        if (amd.find()) {
            int model = Integer.parseInt(amd.group(1));
            if (model >= 6600) {
                return 3;
            }
            if (model >= 5500) {
                return 2;
            }
            if (model >= 570 && model < 1000) {
                return 2;
            }
            return 1;
        }
        if (name.contains("arc")) {
            return name.matches(".*a[57]\\d0.*") ? 2 : 1;
        }
        return -1;
    }
}
