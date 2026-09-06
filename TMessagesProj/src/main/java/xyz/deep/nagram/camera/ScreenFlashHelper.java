package xyz.deep.nagram.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

/**
 * ScreenFlashHelper provides front-camera software flash via high-intensity UI screen illumination.
 * Temporarily overrides window brightness to 100% and displays a warm-white overlay to illuminate
 * selfies in low-light conditions.
 */
public class ScreenFlashHelper {

    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static View activeFlashOverlay = null;
    private static float originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

    public interface FlashCallback {
        void onFlashReady();
    }

    /**
     * Triggers screen flash illumination on the given activity window.
     */
    public static void illuminateScreen(Activity activity, final FlashCallback callback) {
        if (activity == null || activity.isFinishing()) {
            if (callback != null) callback.onFlashReady();
            return;
        }

        uiHandler.post(() -> {
            try {
                WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                originalBrightness = lp.screenBrightness;
                lp.screenBrightness = 1.0f; // Maximum screen brightness
                activity.getWindow().setAttributes(lp);

                // Create or find full-screen white overlay
                ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                if (activeFlashOverlay == null) {
                    activeFlashOverlay = new View(activity);
                    // Soft warm white for natural skin tone (#FFFDF5)
                    activeFlashOverlay.setBackgroundColor(Color.argb(255, 255, 253, 245));
                    activeFlashOverlay.setElevation(9999f);
                    activeFlashOverlay.setClickable(false);
                    activeFlashOverlay.setFocusable(false);

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    decorView.addView(activeFlashOverlay, params);
                } else {
                    activeFlashOverlay.setVisibility(View.VISIBLE);
                    activeFlashOverlay.bringToFront();
                }

                // Wait 150ms for AE/AGC (auto-exposure) convergence before capturing
                uiHandler.postDelayed(() -> {
                    if (callback != null) {
                        callback.onFlashReady();
                    }
                }, 150);
            } catch (Exception e) {
                FileLog.e("ScreenFlashHelper illuminateScreen error", e);
                if (callback != null) callback.onFlashReady();
            }
        });
    }

    /**
     * Dismisses the screen flash overlay and restores original display brightness.
     */
    public static void dismissScreen(Activity activity) {
        if (activity == null) return;
        uiHandler.post(() -> {
            try {
                if (activeFlashOverlay != null) {
                    ViewGroup parent = (ViewGroup) activeFlashOverlay.getParent();
                    if (parent != null) {
                        parent.removeView(activeFlashOverlay);
                    }
                    activeFlashOverlay = null;
                }

                WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                lp.screenBrightness = originalBrightness;
                activity.getWindow().setAttributes(lp);
            } catch (Exception e) {
                FileLog.e("ScreenFlashHelper dismissScreen error", e);
            }
        });
    }
}
