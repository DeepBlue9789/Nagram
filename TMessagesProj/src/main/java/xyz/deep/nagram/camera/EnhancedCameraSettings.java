package xyz.deep.nagram.camera;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Settings preferences for the Enhanced CameraX engine, lens selection, and front screen flash.
 */
public class EnhancedCameraSettings {

    private static final String PREF_NAME = "deep_camerax_settings";
    private static final String KEY_CAMERAX_ENABLED = "enhanced_camerax_enabled";
    private static final String KEY_FRONT_FLASH_ENABLED = "camerax_front_screen_flash";
    private static final String KEY_SELECTED_LENS = "camerax_selected_lens";

    public static final int LENS_REAR_WIDE = 0;
    public static final int LENS_REAR_ULTRAWIDE = 1;
    public static final int LENS_REAR_TELEPHOTO = 2;
    public static final int LENS_FRONT = 3;

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnhancedCameraEnabled() {
        try {
            return xyz.nextalone.nagram.NaConfig.INSTANCE.getEnhancedCameraX().Bool();
        } catch (Throwable ignore) {
            return getPrefs().getBoolean(KEY_CAMERAX_ENABLED, true);
        }
    }

    public static void setEnhancedCameraEnabled(boolean enabled) {
        try {
            xyz.nextalone.nagram.NaConfig.INSTANCE.getEnhancedCameraX().setConfigBool(enabled);
        } catch (Throwable ignore) {}
        getPrefs().edit().putBoolean(KEY_CAMERAX_ENABLED, enabled).apply();
    }

    public static boolean isFrontScreenFlashEnabled() {
        try {
            return xyz.nextalone.nagram.NaConfig.INSTANCE.getFrontScreenFlash().Bool();
        } catch (Throwable ignore) {
            return getPrefs().getBoolean(KEY_FRONT_FLASH_ENABLED, true);
        }
    }

    public static void setFrontScreenFlashEnabled(boolean enabled) {
        try {
            xyz.nextalone.nagram.NaConfig.INSTANCE.getFrontScreenFlash().setConfigBool(enabled);
        } catch (Throwable ignore) {}
        getPrefs().edit().putBoolean(KEY_FRONT_FLASH_ENABLED, enabled).apply();
    }

    public static int getSelectedLens() {
        return getPrefs().getInt(KEY_SELECTED_LENS, LENS_REAR_WIDE);
    }

    public static void setSelectedLens(int lens) {
        getPrefs().edit().putInt(KEY_SELECTED_LENS, lens).apply();
    }
}
