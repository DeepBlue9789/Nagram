package xyz.deep.nagram.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;

/**
 * Modern persistent vertical zoom slider on the right edge of the camera viewfinder.
 * Allows smooth, fluid dragging between minimum and maximum zoom ratios with
 * interactive badge display and haptic feedback at major optical thresholds.
 */
public class CameraZoomSliderWidget extends View {

    private CameraXSession cameraXSession;
    private CameraLensSwitcherWidget lensSwitcherWidget;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF trackRect = new RectF();
    private final RectF thumbRect = new RectF();

    private float minZoom = 1.0f;
    private float maxZoom = 10.0f;
    private float currentZoom = 1.0f;

    private boolean isDragging = false;
    private float lastHapticZoom = 1.0f;
    private Vibrator vibrator;

    public CameraZoomSliderWidget(Context context) {
        super(context);

        trackPaint.setColor(Color.argb(150, 15, 15, 20));
        trackPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(Color.argb(120, 255, 215, 0));
        fillPaint.setStyle(Paint.Style.FILL);

        thumbPaint.setColor(Color.rgb(255, 215, 0)); // Gold / yellow thumb
        thumbPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(AndroidUtilities.dp(10));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        symbolPaint.setColor(Color.argb(200, 255, 255, 255));
        symbolPaint.setTextSize(AndroidUtilities.dp(12));
        symbolPaint.setTextAlign(Paint.Align.CENTER);
        symbolPaint.setAntiAlias(true);

        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignore) {}
    }

    public void setCameraXSession(CameraXSession session) {
        this.cameraXSession = session;
        if (session != null) {
            this.minZoom = session.getMinZoomRatio();
            this.maxZoom = session.getMaxZoomRatio();
            this.currentZoom = session.getCurrentZoomRatio();
            invalidate();
        }
    }

    public void setLensSwitcherWidget(CameraLensSwitcherWidget switcher) {
        this.lensSwitcherWidget = switcher;
    }

    public void updateZoom(float zoomRatio) {
        if (!isDragging) {
            this.currentZoom = Math.max(minZoom, Math.min(zoomRatio, maxZoom));
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = AndroidUtilities.dp(38);
        int height = AndroidUtilities.dp(180);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float cornerRadius = width / 2.0f;
        trackRect.set(0, 0, width, height);

        // Draw track background
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint);

        // Draw + at top and - at bottom
        canvas.drawText("+", width / 2.0f, AndroidUtilities.dp(14), symbolPaint);
        canvas.drawText("−", width / 2.0f, height - AndroidUtilities.dp(6), symbolPaint);

        // Compute thumb vertical position (top = maxZoom, bottom = minZoom)
        float usableTop = AndroidUtilities.dp(24);
        float usableBottom = height - AndroidUtilities.dp(24);
        float progress = (currentZoom - minZoom) / Math.max(0.001f, maxZoom - minZoom);
        progress = Math.max(0.0f, Math.min(progress, 1.0f));

        float thumbY = usableBottom - progress * (usableBottom - usableTop);
        float thumbRadius = AndroidUtilities.dp(15);

        // Draw filled track up to thumb
        RectF fillRect = new RectF(AndroidUtilities.dp(16), thumbY, width - AndroidUtilities.dp(16), usableBottom);
        canvas.drawRoundRect(fillRect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), fillPaint);

        // Draw interactive thumb circle
        thumbRect.set(width / 2.0f - thumbRadius, thumbY - thumbRadius, width / 2.0f + thumbRadius, thumbY + thumbRadius);
        canvas.drawCircle(width / 2.0f, thumbY, thumbRadius, thumbPaint);

        // Draw text badge on thumb (e.g. "1.0x" or "3x")
        String label;
        if (currentZoom >= 10.0f || (int) currentZoom == currentZoom) {
            label = String.format(Locale.US, "%.0fx", currentZoom);
        } else {
            label = String.format(Locale.US, "%.1fx", currentZoom);
        }
        float textY = thumbY - ((textPaint.descent() + textPaint.ascent()) / 2.0f);
        canvas.drawText(label, width / 2.0f, textY, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                isDragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                handleTouchPosition(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    handleTouchPosition(event.getY());
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    handleTouchPosition(event.getY());
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    private void handleTouchPosition(float y) {
        int height = getHeight();
        float usableTop = AndroidUtilities.dp(24);
        float usableBottom = height - AndroidUtilities.dp(24);

        float progress = (usableBottom - y) / (usableBottom - usableTop);
        progress = Math.max(0.0f, Math.min(progress, 1.0f));

        float newZoom = minZoom + progress * (maxZoom - minZoom);
        this.currentZoom = newZoom;
        invalidate();

        if (cameraXSession != null) {
            cameraXSession.setZoomRatio(newZoom);
        }

        // Haptic feedback when crossing integer zoom steps (1x, 2x, 3x, etc.)
        if (Math.abs(Math.round(newZoom) - newZoom) < 0.15f && Math.abs(Math.round(newZoom) - lastHapticZoom) >= 1.0f) {
            lastHapticZoom = Math.round(newZoom);
            triggerHaptic();
        }

        // Synchronize with lens switcher widget
        if (lensSwitcherWidget != null) {
            lensSwitcherWidget.notifyZoomChanged(newZoom);
        }
    }

    private void triggerHaptic() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                } else {
                    vibrator.vibrate(10);
                }
            } catch (Exception ignore) {}
        }
    }

    public static CameraZoomSliderWidget attachTo(ViewGroup parent) {
        if (parent == null) return null;
        CameraZoomSliderWidget widget = new CameraZoomSliderWidget(parent.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        lp.rightMargin = AndroidUtilities.dp(14);
        parent.addView(widget, lp);
        return widget;
    }
}
