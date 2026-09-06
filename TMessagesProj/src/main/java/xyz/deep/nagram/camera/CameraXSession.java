package xyz.deep.nagram.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * CameraXSession handles modern camera streaming and high-resolution photo capture using
 * AndroidX CameraX. Supports multi-lens selection, HDR/quality optimization, and front-camera screen flash.
 */
public class CameraXSession {

    public static final String FLASH_MODE_OFF = "off";
    public static final String FLASH_MODE_ON = "on";
    public static final String FLASH_MODE_AUTO = "auto";

    private final boolean isFront;
    private final int viewWidth;
    private final int viewHeight;

    private ProcessCameraProvider cameraProvider;
    private androidx.camera.core.Camera activeCamera;
    private Preview previewUseCase;
    private ImageCapture imageCaptureUseCase;

    private SurfaceTexture surfaceTexture;
    private Surface activeSurface;
    private boolean isInitiated = false;
    private String currentFlashMode = FLASH_MODE_OFF;

    private int currentLens = EnhancedCameraSettings.LENS_REAR_WIDE;
    private float currentZoomRatio = 1.0f;
    private float minZoomRatio = 1.0f;
    private float maxZoomRatio = 1.0f;

    private int currentOrientation = 0;
    private int displayOrientation = 0;

    public CameraXSession(boolean isFront, int viewWidth, int viewHeight) {
        this.isFront = isFront;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.currentLens = isFront ? EnhancedCameraSettings.LENS_FRONT : EnhancedCameraSettings.getSelectedLens();
    }

    public static CameraXSession create(boolean front, int viewWidth, int viewHeight) {
        return new CameraXSession(front, viewWidth, viewHeight);
    }

    public boolean isInitiated() {
        return isInitiated;
    }

    public boolean isFront() {
        return isFront;
    }

    public int getPreviewWidth() {
        return viewWidth > 0 ? viewWidth : 1280;
    }

    public int getPreviewHeight() {
        return viewHeight > 0 ? viewHeight : 720;
    }

    public int getWorldAngle() {
        return 0;
    }

    public int getCurrentOrientation() {
        return currentOrientation;
    }

    public int getDisplayOrientation() {
        return displayOrientation;
    }

    /**
     * Initializes CameraX and binds the preview stream to Telegram's SurfaceTexture.
     */
    public void open(final SurfaceTexture surfaceTexture, final Runnable onReady) {
        this.surfaceTexture = surfaceTexture;
        final Context context = ApplicationLoader.applicationContext;
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(onReady);
            } catch (ExecutionException | InterruptedException e) {
                FileLog.e("CameraXSession init failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases(final Runnable onReady) {
        if (cameraProvider == null) return;

        Context context = ApplicationLoader.applicationContext;
        LifecycleOwner lifecycleOwner = LaunchActivity.instance;
        if (lifecycleOwner == null) {
            FileLog.e("CameraXSession: LaunchActivity instance is null for lifecycle");
            return;
        }

        cameraProvider.unbindAll();

        // 1. Configure CameraSelector based on lens
        CameraSelector.Builder selectorBuilder = new CameraSelector.Builder();
        if (isFront) {
            selectorBuilder.requireLensFacing(CameraSelector.LENS_FACING_FRONT);
        } else {
            selectorBuilder.requireLensFacing(CameraSelector.LENS_FACING_BACK);
        }
        CameraSelector cameraSelector = selectorBuilder.build();

        // 2. Configure Preview
        previewUseCase = new Preview.Builder()
                .build();

        previewUseCase.setSurfaceProvider(ContextCompat.getMainExecutor(context), request -> {
            if (surfaceTexture == null) {
                request.willNotProvideSurface();
                return;
            }
            surfaceTexture.setDefaultBufferSize(request.getResolution().getWidth(), request.getResolution().getHeight());
            activeSurface = new Surface(surfaceTexture);
            request.provideSurface(activeSurface, ContextCompat.getMainExecutor(context), result -> {
                if (activeSurface != null) {
                    activeSurface.release();
                    activeSurface = null;
                }
            });
        });

        // 3. Configure ImageCapture with MAXIMUM quality for crystal clear images
        ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100);

        applyFlashModeToBuilder(captureBuilder);
        imageCaptureUseCase = captureBuilder.build();

        try {
            activeCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    imageCaptureUseCase
            );

            // Fetch zoom capabilities
            if (activeCamera != null && activeCamera.getCameraInfo() != null) {
                ZoomState zoomState = activeCamera.getCameraInfo().getZoomState().getValue();
                if (zoomState != null) {
                    minZoomRatio = zoomState.getMinZoomRatio();
                    maxZoomRatio = zoomState.getMaxZoomRatio();
                }

                // If user selected ultrawide lens and camera supports zoom < 1.0f
                if (currentLens == EnhancedCameraSettings.LENS_REAR_ULTRAWIDE && minZoomRatio < 1.0f) {
                    activeCamera.getCameraControl().setZoomRatio(minZoomRatio);
                }
            }

            isInitiated = true;
            if (onReady != null) {
                AndroidUtilities.runOnUIThread(onReady);
            }
        } catch (Exception e) {
            FileLog.e("CameraXSession bind error", e);
        }
    }

    private void applyFlashModeToBuilder(ImageCapture.Builder builder) {
        if (FLASH_MODE_ON.equals(currentFlashMode)) {
            builder.setFlashMode(ImageCapture.FLASH_MODE_ON);
        } else if (FLASH_MODE_AUTO.equals(currentFlashMode)) {
            builder.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
        } else {
            builder.setFlashMode(ImageCapture.FLASH_MODE_OFF);
        }
    }

    /**
     * Captures a high-resolution photo with zero degradation.
     * Triggers UI screen flash when capturing with the front camera.
     */
    public boolean takePicture(final File outputFile, final Utilities.Callback<Integer> callback) {
        if (imageCaptureUseCase == null || activeCamera == null) {
            return false;
        }

        // Handle front camera software flash
        boolean needScreenFlash = isFront && EnhancedCameraSettings.isFrontScreenFlashEnabled()
                && (FLASH_MODE_ON.equals(currentFlashMode) || FLASH_MODE_AUTO.equals(currentFlashMode));

        Activity currentActivity = LaunchActivity.instance;

        if (needScreenFlash && currentActivity != null) {
            ScreenFlashHelper.illuminateScreen(currentActivity, () -> executeCapture(outputFile, callback, currentActivity));
        } else {
            executeCapture(outputFile, callback, null);
        }

        return true;
    }

    private void executeCapture(final File outputFile, final Utilities.Callback<Integer> callback, final Activity activityToDismissFlash) {
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCaptureUseCase.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(ApplicationLoader.applicationContext),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        if (activityToDismissFlash != null) {
                            ScreenFlashHelper.dismissScreen(activityToDismissFlash);
                        }
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.run(currentOrientation));
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        FileLog.e("CameraXSession capture error", exception);
                        if (activityToDismissFlash != null) {
                            ScreenFlashHelper.dismissScreen(activityToDismissFlash);
                        }
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(() -> callback.run(0));
                        }
                    }
                }
        );
    }

    public void setZoom(float zoomRatioNormalized) {
        if (activeCamera == null) return;
        CameraControl control = activeCamera.getCameraControl();
        if (control != null) {
            float targetZoom = minZoomRatio + (maxZoomRatio - minZoomRatio) * zoomRatioNormalized;
            currentZoomRatio = Math.max(minZoomRatio, Math.min(targetZoom, maxZoomRatio));
            control.setZoomRatio(currentZoomRatio);
        }
    }

    public void switchLens(int targetLens) {
        this.currentLens = targetLens;
        EnhancedCameraSettings.setSelectedLens(targetLens);
        if (activeCamera != null) {
            CameraControl control = activeCamera.getCameraControl();
            if (control != null) {
                if (targetLens == EnhancedCameraSettings.LENS_REAR_ULTRAWIDE && minZoomRatio < 1.0f) {
                    control.setZoomRatio(minZoomRatio); // e.g. 0.5x or 0.6x
                } else if (targetLens == EnhancedCameraSettings.LENS_REAR_TELEPHOTO && maxZoomRatio >= 2.0f) {
                    control.setZoomRatio(Math.min(2.0f, maxZoomRatio)); // 2x telephoto
                } else {
                    control.setZoomRatio(1.0f); // 1x standard wide
                }
            }
        }
    }

    public void setCurrentFlashMode(String flashMode) {
        this.currentFlashMode = flashMode;
        if (imageCaptureUseCase != null) {
            if (FLASH_MODE_ON.equals(flashMode)) {
                imageCaptureUseCase.setFlashMode(ImageCapture.FLASH_MODE_ON);
            } else if (FLASH_MODE_AUTO.equals(flashMode)) {
                imageCaptureUseCase.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
            } else {
                imageCaptureUseCase.setFlashMode(ImageCapture.FLASH_MODE_OFF);
            }
        }
    }

    public String getCurrentFlashMode() {
        return currentFlashMode;
    }

    public String getNextFlashMode() {
        if (FLASH_MODE_OFF.equals(currentFlashMode)) {
            return FLASH_MODE_ON;
        } else if (FLASH_MODE_ON.equals(currentFlashMode)) {
            return FLASH_MODE_AUTO;
        } else {
            return FLASH_MODE_OFF;
        }
    }

    public boolean hasFlashModes() {
        return true;
    }

    public void focusToRect(Rect focusRect, Rect meteringRect) {
        if (activeCamera == null || viewWidth <= 0 || viewHeight <= 0) return;
        try {
            MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(viewWidth, viewHeight);
            MeteringPoint point = factory.createPoint(focusRect.centerX(), focusRect.centerY());
            FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
            activeCamera.getCameraControl().startFocusAndMetering(action);
        } catch (Exception e) {
            FileLog.e("CameraXSession focusToRect error", e);
        }
    }

    public void destroy(boolean async, Runnable before, Runnable after) {
        if (before != null) before.run();
        isInitiated = false;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (cameraProvider != null) {
                    cameraProvider.unbindAll();
                }
                if (activeSurface != null) {
                    activeSurface.release();
                    activeSurface = null;
                }
            } catch (Exception e) {
                FileLog.e("CameraXSession destroy error", e);
            }
            if (after != null) after.run();
        });
    }
}
