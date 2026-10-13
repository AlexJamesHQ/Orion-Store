package com.orion.store;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

@CapacitorPlugin(name = "MaterialYou")
public class MaterialYouPlugin extends Plugin {
    private static final String TAG = "MaterialYouPlugin";

    @PluginMethod
    public void getMaterialYouPalette(PluginCall call) {
        JSObject ret = new JSObject();
        Context context = getContext();

        if (context == null) {
            ret.put("supported", false);
            call.resolve(ret);
            return;
        }

        boolean isNightMode = false;
        try {
            isNightMode = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {}

        ret.put("isNightMode", isNightMode);

        JSObject palette = new JSObject();
        boolean found = false;

        // Strategy 1: Parse Android Secure Settings (theme_customization_overlay_packages)
        // Supported across Android 12+ OEM skins (Pixel, Transsion/Infinix/Tecno, Samsung, Xiaomi, OnePlus, Motorola)
        try {
            String themeJson = Settings.Secure.getString(context.getContentResolver(), "theme_customization_overlay_packages");
            if (themeJson != null && !themeJson.isEmpty()) {
                Log.d(TAG, "Found theme_customization_overlay_packages: " + themeJson);
                JSONObject json = new JSONObject(themeJson);
                String systemPalette = json.optString("android.theme.customization.system_palette", null);
                String accentColor = json.optString("android.theme.customization.accent_color", null);
                String rawSeed = (systemPalette != null && !systemPalette.isEmpty()) ? systemPalette : accentColor;

                if (rawSeed != null && !rawSeed.isEmpty()) {
                    String cleanSeed = rawSeed.replace("#", "").trim();
                    if (cleanSeed.length() == 6 || cleanSeed.length() == 8) {
                        String seedHex = "#" + cleanSeed.substring(cleanSeed.length() - 6);
                        int seedColor = Color.parseColor(seedHex);
                        generateTonalPalette(palette, seedColor);
                        found = true;
                        Log.i(TAG, "Extracted Monet seed from Secure Settings: " + seedHex);
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Secure settings parsing error", t);
        }

        // Strategy 2: Android 12+ system color resource tokens (AOSP / Pixel / standard ROMs)
        if (!found && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                int accent1_500 = context.getColor(android.R.color.system_accent1_500);
                if (accent1_500 != 0) {
                    generateTonalPalette(palette, accent1_500);
                    found = true;
                    Log.i(TAG, "Extracted Monet from system_accent1_500");
                }
            } catch (Throwable ignored) {}

            String[] shades = new String[] {
                "accent1_100", "accent1_200", "accent1_300", "accent1_500", "accent1_600", "accent1_800",
                "accent2_100", "accent2_200", "accent2_500",
                "accent3_100", "accent3_200", "accent3_500", "accent3_600",
                "neutral1_50", "neutral1_100", "neutral1_700", "neutral1_800", "neutral1_900",
                "neutral2_100", "neutral2_200", "neutral2_500", "neutral2_700", "neutral2_800"
            };
            for (String shade : shades) {
                String hex = resolveSystemColorHex(context, "system_" + shade);
                if (hex != null) {
                    palette.put(shade, hex);
                    found = true;
                }
            }
        }

        // Strategy 3: WallpaperColors API (Android 8.1+)
        if (!found && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(context);
                if (wm != null) {
                    WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                    if (wc != null && wc.getPrimaryColor() != null) {
                        int primInt = wc.getPrimaryColor().toArgb();
                        generateTonalPalette(palette, primInt);
                        found = true;
                        Log.i(TAG, "Extracted Monet from WallpaperColors");
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "WallpaperColors error", t);
            }
        }

        ret.put("supported", found);
        ret.put("palette", palette);

        String primaryHex;
        String accentHex;
        if (isNightMode) {
            primaryHex = palette.optString("accent1_200", palette.optString("accent1_300", palette.optString("accent1_500", "#d0bcff")));
            accentHex = palette.optString("accent3_200", palette.optString("accent2_200", palette.optString("accent3_500", "#efb8c8")));
        } else {
            primaryHex = palette.optString("accent1_600", palette.optString("accent1_500", palette.optString("accent1_800", "#6750a4")));
            accentHex = palette.optString("accent3_600", palette.optString("accent3_500", "#7d5260"));
        }

        ret.put("primary", primaryHex);
        ret.put("primaryRgb", hexToRgb(primaryHex));
        ret.put("accent", accentHex);
        ret.put("accentRgb", hexToRgb(accentHex));

        Log.i(TAG, "Resolved Material You: supported=" + found + ", primary=" + primaryHex + ", accent=" + accentHex);
        call.resolve(ret);
    }

    private void generateTonalPalette(JSObject palette, int seedColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(seedColor, hsv);
        float h = hsv[0];
        // Use full saturation — no desaturation. Preserve vivid wallpaper color.
        float s = Math.min(1.0f, hsv[1]);

        // Store the raw seed hex for JS to use as direct primary if needed
        palette.put("seed", String.format("#%06X", (0xFFFFFF & seedColor)));

        // Primary tones (accent1) — full saturation maintained
        palette.put("accent1_100", hsvToHex(h, s * 0.30f, 0.97f));  // very light tint
        palette.put("accent1_200", hsvToHex(h, s * 0.50f, 0.92f));  // light
        palette.put("accent1_300", hsvToHex(h, s * 0.75f, 0.86f));  // medium light
        palette.put("accent1_500", hsvToHex(h, s,          0.72f)); // vibrant mid
        palette.put("accent1_600", hsvToHex(h, s,          0.58f)); // vibrant dark (light mode primary)
        palette.put("accent1_800", hsvToHex(h, s,          0.36f)); // deep dark

        // Secondary tones (accent2 - slightly desaturated variant)
        palette.put("accent2_100", hsvToHex(h, s * 0.25f, 0.96f));
        palette.put("accent2_200", hsvToHex(h, s * 0.38f, 0.90f));
        palette.put("accent2_500", hsvToHex(h, s * 0.52f, 0.68f));

        // Tertiary tones (accent3 - hue shifted +60° for complementary pop)
        float h3 = (h + 60.0f) % 360.0f;
        palette.put("accent3_100", hsvToHex(h3, s * 0.35f, 0.96f));
        palette.put("accent3_200", hsvToHex(h3, s * 0.52f, 0.90f));
        palette.put("accent3_500", hsvToHex(h3, s * 0.72f, 0.68f));
        palette.put("accent3_600", hsvToHex(h3, s * 0.78f, 0.55f));
    }


    private String hsvToHex(float h, float s, float v) {
        int color = Color.HSVToColor(new float[] { h, Math.max(0f, Math.min(1f, s)), Math.max(0f, Math.min(1f, v)) });
        return String.format("#%06X", (0xFFFFFF & color));
    }

    private String resolveSystemColorHex(Context context, String resourceName) {
        try {
            Resources res = context.getResources();
            int resId = res.getIdentifier(resourceName, "color", "android");
            if (resId != 0) {
                int color = context.getColor(resId);
                return String.format("#%06X", (0xFFFFFF & color));
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private String hexToRgb(String hex) {
        if (hex == null || hex.isEmpty()) return "99 102 241";
        String clean = hex.replace("#", "").trim();
        try {
            if (clean.length() >= 6) {
                int r = Integer.parseInt(clean.substring(0, 2), 16);
                int g = Integer.parseInt(clean.substring(2, 4), 16);
                int b = Integer.parseInt(clean.substring(4, 6), 16);
                return r + " " + g + " " + b;
            }
        } catch (Throwable ignored) {}
        return "99 102 241";
    }
}
