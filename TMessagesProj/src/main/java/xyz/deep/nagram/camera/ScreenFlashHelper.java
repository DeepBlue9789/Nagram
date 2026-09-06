package xyz.deep.nagram.camera;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.telegram.messenger.FileLog;

/**
 * ScreenFlashHelper provides front-camera software flash via high-intensity UI screen illumination.
 * Temporarily overrides window brightness to 100% and displays a warm-white RingFlashOverlayView
 * directly over the camera preview to illuminate selfies in low-light conditions.
 */
public class ScreenFlashHelper {

    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static RingFlashOverlayView activeFlashOverlay = null;
    private static float originalBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

    public interface FlashCallback {
        void onFlashReady();
    }

    /**
     * Triggers screen flash illumination on the given parent container (e.g. CameraView) and activity window.
     */
    public static void illuminateScreen(ViewGroup cameraContainer, Activity activity, final FlashCallback callback) {
        uiHandler.post(() -> {
            try {
                if (activity != null && !activity.isFinishing()) {
                    WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                    originalBrightness = lp.screenBrightness;
                    lp.screenBrightness = 1.0f; // Maximum screen brightness
                    activity.getWindow().setAttributes(lp);
                }

                // Attach RingFlashOverlayView directly to cameraContainer (or activity decorView if container is null)
                ViewGroup targetParent = cameraContainer;
                if (targetParent == null && activity != null) {
                    targetParent = (ViewGroup) activity.getWindow().getDecorView();
                }

                if (targetParent != null) {
                    if (activeFlashOverlay == null) {
                        activeFlashOverlay = new RingFlashOverlayView(targetParent.getContext());
                        activeFlashOverlay.setElevation(9999f);

                        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        );
                        targetParent.addView(activeFlashOverlay, params);
                    } else {
                        activeFlashOverlay.setVisibility(View.VISIBLE);
                        activeFlashOverlay.bringToFront();
                    }
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
        uiHandler.post(() -> {
            try {
                if (activeFlashOverlay != null) {
                    ViewGroup parent = (ViewGroup) activeFlashOverlay.getParent();
                    if (parent != null) {
                        parent.removeView(activeFlashOverlay);
                    }
                    activeFlashOverlay = null;
                }

                if (activity != null && !activity.isFinishing()) {
                    WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                    lp.screenBrightness = originalBrightness;
                    activity.getWindow().setAttributes(lp);
                }
            } catch (Exception e) {
                FileLog.e("ScreenFlashHelper dismissScreen error", e);
            }
        });
    }
}
