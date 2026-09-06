package xyz.deep.nagram.camera;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

/**
 * Modern floating lens switcher pill for switching between Ultrawide (0.5x),
 * Wide (1x), and Telephoto (2x) camera lenses.
 */
public class CameraLensSwitcherWidget extends LinearLayout {

    private final TextView btnUltraWide;
    private final TextView btnWide;
    private final TextView btnTelephoto;
    private CameraXSession cameraXSession;

    public CameraLensSwitcherWidget(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Glassmorphic dark rounded pill background
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(160, 20, 20, 25));
        background.setCornerRadius(AndroidUtilities.dp(20));
        background.setStroke(AndroidUtilities.dp(1), Color.argb(40, 255, 255, 255));
        setBackground(background);

        int paddingH = AndroidUtilities.dp(6);
        int paddingV = AndroidUtilities.dp(4);
        setPadding(paddingH, paddingV, paddingH, paddingV);

        btnUltraWide = createButton(context, "0.5x", EnhancedCameraSettings.LENS_REAR_ULTRAWIDE);
        btnWide = createButton(context, "1x", EnhancedCameraSettings.LENS_REAR_WIDE);
        btnTelephoto = createButton(context, "2x", EnhancedCameraSettings.LENS_REAR_TELEPHOTO);

        addView(btnUltraWide);
        addView(btnWide);
        addView(btnTelephoto);

        updateActiveLens(EnhancedCameraSettings.getSelectedLens());
    }

    public void setCameraXSession(CameraXSession session) {
        this.cameraXSession = session;
        if (session != null && session.isFront()) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    private TextView createButton(Context context, String label, int lens) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tv.setGravity(Gravity.CENTER);

        int size = AndroidUtilities.dp(34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.leftMargin = AndroidUtilities.dp(2);
        lp.rightMargin = AndroidUtilities.dp(2);
        tv.setLayoutParams(lp);

        tv.setOnClickListener(v -> {
            updateActiveLens(lens);
            if (cameraXSession != null) {
                cameraXSession.switchLens(lens);
            }
        });
        return tv;
    }

    public void updateActiveLens(int lens) {
        setButtonSelected(btnUltraWide, lens == EnhancedCameraSettings.LENS_REAR_ULTRAWIDE);
        setButtonSelected(btnWide, lens == EnhancedCameraSettings.LENS_REAR_WIDE);
        setButtonSelected(btnTelephoto, lens == EnhancedCameraSettings.LENS_REAR_TELEPHOTO);
    }

    private void setButtonSelected(TextView btn, boolean selected) {
        if (selected) {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setShape(GradientDrawable.OVAL);
            activeBg.setColor(Color.argb(230, 255, 215, 0)); // Warm gold / yellow indicator
            btn.setBackground(activeBg);
            btn.setTextColor(Color.BLACK);
        } else {
            btn.setBackground(null);
            btn.setTextColor(Color.argb(220, 255, 255, 255));
        }
    }

    public static CameraLensSwitcherWidget attachTo(ViewGroup parent) {
        if (parent == null) return null;
        CameraLensSwitcherWidget widget = new CameraLensSwitcherWidget(parent.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = AndroidUtilities.dp(85);
        parent.addView(widget, lp);
        return widget;
    }
}
