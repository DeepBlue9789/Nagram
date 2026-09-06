package xyz.deep.nagram.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Size;
import android.view.Surface;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
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
import androidx.camera.extensions.ExtensionMode;
import androidx.camera.extensions.ExtensionsManager;
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
import java.util.concurrent.TimeUnit;

/**
 * CameraXSession handles modern camera streaming and high-resolution photo capture using
 * AndroidX CameraX. Supports multi-lens selection, HDR extensions, optical zoom, AF/AE lock,
 * and front-camera ring flash.
 */
public class CameraXSession {

    public static final String FLASH_MODE_OFF = "off";
    public static final String FLASH_MODE_ON = "on";
    public static final String FLASH_MODE_AUTO = "auto";

    public interface ResolutionListener {
        void onResolutionReady(int width, int height);
    }

    private final boolean isFront;
    private int viewWidth;
    private int viewHeight;

    private ProcessCameraProvider cameraProvider;
    private ExtensionsManager extensionsManager;
    private androidx.camera.core.Camera activeCamera;
    private Preview previewUseCase;
    private ImageCapture imageCaptureUseCase;

    private SurfaceTexture surfaceTexture;
    private Surface activeSurface;
    private boolean isInitiated = false;
    private String currentFlashMode = FLASH_MODE_OFF;

    private int actualPreviewWidth = 1920;
    private int actualPreviewHeight = 1080;
    private ResolutionListener resolutionListener;

    private float currentZoomRatio = 1.0f;
    private float minZoomRatio = 0.6f;
    private float maxZoomRatio = 10.0f;

    private boolean isAeAfLocked = false;
    private boolean isHdrEnabled = false;
    private boolean isHdrSupported = false;

    private ViewGroup cameraContainer;
    private Runnable onReadyCallback;

    public CameraXSession(boolean isFront, int viewWidth, int viewHeight) {
        this.isFront = isFront;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
    }

    public static CameraXSession create(boolean front, int viewWidth, int viewHeight) {
        return new CameraXSession(front, viewWidth, viewHeight);
    }

    public void setCameraContainer(ViewGroup container) {
        this.cameraContainer = container;
    }

    public void setResolutionListener(ResolutionListener listener) {
        this.resolutionListener = listener;
        if (isInitiated && actualPreviewWidth > 0 && actualPreviewHeight > 0 && listener != null) {
            listener.onResolutionReady(actualPreviewWidth, actualPreviewHeight);
        }
    }

    public boolean isInitiated() {
        return isInitiated;
    }

    public boolean isFront() {
        return isFront;
    }

    public int getPreviewWidth() {
        return actualPreviewWidth;
    }

    public int getPreviewHeight() {
        return actualPreviewHeight;
    }

    public int getWorldAngle() {
        return isFront ? 270 : 90;
    }

    public int getDisplayOrientation() {
        return 0;
    }

    public int getCurrentOrientation() {
        return isFront ? 270 : 90;
    }

    public float getMinZoomRatio() {
        return minZoomRatio;
    }

    public float getMaxZoomRatio() {
        return maxZoomRatio;
    }

    public float getCurrentZoomRatio() {
        return currentZoomRatio;
    }

    public boolean isHdrSupported() {
        return isHdrSupported;
    }

    public boolean isAeAfLocked() {
        return isAeAfLocked;
    }

    public void updateViewDimensions(int width, int height) {
        if (width > 0 && height > 0) {
            this.viewWidth = width;
            this.viewHeight = height;
        }
    }

    /**
     * Initializes CameraX and binds the preview stream to Telegram's SurfaceTexture.
     */
    public void open(final SurfaceTexture surfaceTexture, final Runnable onReady) {
        this.surfaceTexture = surfaceTexture;
        this.onReadyCallback = onReady;
        final Context context = ApplicationLoader.applicationContext;
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                // Bind immediately for instantaneous camera preview start
                bindCameraUseCases(onReadyCallback);

                // Initialize ExtensionsManager in background for HDR/Night query without blocking preview
                try {
                    ListenableFuture<ExtensionsManager> extensionsManagerFuture = ExtensionsManager.getInstanceAsync(context, cameraProvider);
                    extensionsManagerFuture.addListener(() -> {
                        try {
                            extensionsManager = extensionsManagerFuture.get();
                            checkExtensionsSupport();
                        } catch (Exception ignore) {}
                    }, ContextCompat.getMainExecutor(context));
                } catch (Throwable ignore) {}

            } catch (ExecutionException | InterruptedException e) {
                FileLog.e("CameraXSession init failed", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void checkExtensionsSupport() {
        if (extensionsManager == null || isFront) {
            isHdrSupported = false;
            return;
        }
        CameraSelector baseSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        isHdrSupported = extensionsManager.isExtensionAvailable(baseSelector, ExtensionMode.HDR);
    }

    public void toggleHdrMode(boolean enable) {
        if (isHdrEnabled == enable) return;
        this.isHdrEnabled = enable && isHdrSupported;
        bindCameraUseCases(onReadyCallback);
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

        // 1. Configure CameraSelector
        CameraSelector cameraSelector = isFront ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        if (isHdrEnabled && extensionsManager != null && extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.HDR)) {
            cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.HDR);
        }

        // 2. Compute matching aspect ratio to prevent stretching
        int targetAspectRatio = AspectRatio.RATIO_4_3;
        if (viewWidth > 0 && viewHeight > 0) {
            float ratio = (float) Math.max(viewWidth, viewHeight) / (float) Math.min(viewWidth, viewHeight);
            if (Math.abs(ratio - (16.0f / 9.0f)) < Math.abs(ratio - (4.0f / 3.0f))) {
                targetAspectRatio = AspectRatio.RATIO_16_9;
            }
        }

        // 3. Configure Preview
        previewUseCase = new Preview.Builder()
                .setTargetAspectRatio(targetAspectRatio)
                .build();

        previewUseCase.setSurfaceProvider(ContextCompat.getMainExecutor(context), request -> {
            if (surfaceTexture == null) {
                request.willNotProvideSurface();
                return;
            }
            Size resolution = request.getResolution();
            actualPreviewWidth = resolution.getWidth();
            actualPreviewHeight = resolution.getHeight();
            surfaceTexture.setDefaultBufferSize(actualPreviewWidth, actualPreviewHeight);

            if (resolutionListener != null) {
                AndroidUtilities.runOnUIThread(() -> resolutionListener.onResolutionReady(actualPreviewWidth, actualPreviewHeight));
            }

            activeSurface = new Surface(surfaceTexture);
            request.provideSurface(activeSurface, ContextCompat.getMainExecutor(context), result -> {
                if (activeSurface != null) {
                    activeSurface.release();
                    activeSurface = null;
                }
            });
        });

        // 4. Configure ImageCapture with MAXIMUM quality
        ImageCapture.Builder captureBuilder = new ImageCapture.Builder()
                .setTargetAspectRatio(targetAspectRatio)
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

            // Read zoom capabilities from active camera
            if (activeCamera != null && activeCamera.getCameraInfo() != null) {
                ZoomState zoomState = activeCamera.getCameraInfo().getZoomState().getValue();
                if (zoomState != null) {
                    minZoomRatio = zoomState.getMinZoomRatio();
                    maxZoomRatio = zoomState.getMaxZoomRatio();
                    currentZoomRatio = zoomState.getZoomRatio();
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
     * Tap to focus and meter, or long-press to lock AE/AF.
     */
    public void focusAndMeter(float x, float y, int viewW, int viewH, boolean lock) {
        if (activeCamera == null) return;
        try {
            int w = viewW > 0 ? viewW : (viewWidth > 0 ? viewWidth : 1080);
            int h = viewH > 0 ? viewH : (viewHeight > 0 ? viewHeight : 1920);
            MeteringPointFactory factory = new SurfaceOrientedMeteringPointFactory(w, h);
            MeteringPoint point = factory.createPoint(x, y);

            FocusMeteringAction.Builder builder = new FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
            );

            if (lock) {
                builder.disableAutoCancel(); // Lock focus and exposure indefinitely!
                isAeAfLocked = true;
            } else {
                builder.setAutoCancelDuration(3, TimeUnit.SECONDS);
                isAeAfLocked = false;
            }

            activeCamera.getCameraControl().startFocusAndMetering(builder.build());
        } catch (Exception e) {
            FileLog.e("CameraXSession focusAndMeter error", e);
        }
    }

    public void cancelFocusAndMetering() {
        if (activeCamera != null) {
            try {
                activeCamera.getCameraControl().cancelFocusAndMetering();
            } catch (Exception ignore) {}
        }
        isAeAfLocked = false;
    }

    public void focusToRect(Rect focusRect, Rect meteringRect) {
        if (focusRect != null) {
            focusAndMeter(focusRect.centerX(), focusRect.centerY(), viewWidth, viewHeight, false);
        }
    }

    /**
     * Set zoom ratio directly (e.g. 0.6x, 1.0x, 3.0x).
     */
    public void setZoomRatio(float ratio) {
        if (activeCamera == null) return;
        try {
            currentZoomRatio = Math.max(minZoomRatio, Math.min(ratio, maxZoomRatio));
            activeCamera.getCameraControl().setZoomRatio(currentZoomRatio);
        } catch (Exception e) {
            FileLog.e("CameraXSession setZoomRatio error", e);
        }
    }

    public void setZoom(float zoomRatioNormalized) {
        float targetZoom = minZoomRatio + (maxZoomRatio - minZoomRatio) * zoomRatioNormalized;
        setZoomRatio(targetZoom);
    }

    /**
     * Captures photo. Triggers ring flash if capturing with front camera and flash enabled.
     */
    public boolean takePicture(final File outputFile, final Utilities.Callback<Integer> callback) {
        if (imageCaptureUseCase == null || activeCamera == null) {
            return false;
        }

        boolean needScreenFlash = isFront && EnhancedCameraSettings.isFrontScreenFlashEnabled()
                && (FLASH_MODE_ON.equals(currentFlashMode) || FLASH_MODE_AUTO.equals(currentFlashMode));

        Activity currentActivity = LaunchActivity.instance;

        if (needScreenFlash && currentActivity != null) {
            ScreenFlashHelper.illuminateScreen(cameraContainer, currentActivity, () -> executeCapture(outputFile, callback, currentActivity));
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
                            AndroidUtilities.runOnUIThread(() -> callback.run(0));
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
