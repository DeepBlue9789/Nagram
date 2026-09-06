package xyz.deep.nagram.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * RingFlashOverlayView displays a high-intensity warm-white border lighting
 * with a centered transparent cutout so the user can clearly see their face
 * in the camera preview while being brightly illuminated.
 */
public class RingFlashOverlayView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ovalRect = new RectF();

    public RingFlashOverlayView(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);

        // Soft warm white for flattering skin tones (#FFFDF5)
        fillPaint.setColor(Color.rgb(255, 253, 245));
        fillPaint.setStyle(Paint.Style.FILL);

        // Clear paint for cutting out the central preview window
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        clearPaint.setAntiAlias(true);

        // Requires software or layer for PorterDuff.Mode.CLEAR to work properly
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        int saveCount = canvas.saveLayer(0, 0, width, height, null);

        // 1. Fill full screen with warm white light
        canvas.drawRect(0, 0, width, height, fillPaint);

        // 2. Cut out a smooth portrait oval in the center-top where face sits
        float cx = width / 2.0f;
        float cy = height * 0.42f; // slightly above vertical center
        float radiusX = width * 0.36f;
        float radiusY = width * 0.46f;

        ovalRect.set(cx - radiusX, cy - radiusY, cx + radiusX, cy + radiusY);
        canvas.drawOval(ovalRect, clearPaint);

        canvas.restoreToCount(saveCount);
    }
}
