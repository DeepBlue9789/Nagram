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

import java.util.Locale;

/**
 * Modern floating lens switcher pill for switching between device-specific
 * optical lenses (e.g. 0.6x Ultrawide, 1x Wide, 3x/2x Telephoto) and
 * HDR/Night light modes.
 */
public class CameraLensSwitcherWidget extends LinearLayout {

    private final TextView btnUltraWide;
    private final TextView btnWide;
    private final TextView btnTelephoto;
    private final TextView btnHdr;

    private CameraXSession cameraXSession;
    private CameraZoomSliderWidget zoomSliderWidget;

    private float ultraWideRatio = 0.6f;
    private float wideRatio = 1.0f;
    private float telephotoRatio = 3.0f;

    private int activeLensIndex = 1; // Default to 1x Wide
    private boolean isHdrEnabled = false;

    public CameraLensSwitcherWidget(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);

        // Glassmorphic dark rounded pill background
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(165, 18, 18, 22));
        background.setCornerRadius(AndroidUtilities.dp(22));
        background.setStroke(AndroidUtilities.dp(1), Color.argb(45, 255, 255, 255));
        setBackground(background);

        int paddingH = AndroidUtilities.dp(6);
        int paddingV = AndroidUtilities.dp(4);
        setPadding(paddingH, paddingV, paddingH, paddingV);

        btnUltraWide = createButton(context, "0.6x", 0);
        btnWide = createButton(context, "1x", 1);
        btnTelephoto = createButton(context, "3x", 2);
        btnHdr = createHdrButton(context);

        addView(btnUltraWide);
        addView(btnWide);
        addView(btnTelephoto);
        addView(btnHdr);

        updateActiveLensUI(1);
    }

    public void setCameraXSession(CameraXSession session) {
        this.cameraXSession = session;
        if (session != null) {
            if (session.isFront()) {
                setVisibility(GONE);
                return;
            }

            // Adapt button labels to hardware capabilities
            float minZoom = session.getMinZoomRatio();
            float maxZoom = session.getMaxZoomRatio();

            if (minZoom < 1.0f) {
                ultraWideRatio = minZoom;
                btnUltraWide.setText(String.format(Locale.US, "%.1fx", minZoom));
                btnUltraWide.setVisibility(VISIBLE);
            } else {
                btnUltraWide.setVisibility(GONE);
            }

            if (maxZoom >= 3.0f) {
                telephotoRatio = 3.0f;
                btnTelephoto.setText("3x");
                btnTelephoto.setVisibility(VISIBLE);
            } else if (maxZoom >= 2.0f) {
                telephotoRatio = 2.0f;
                btnTelephoto.setText("2x");
                btnTelephoto.setVisibility(VISIBLE);
            } else {
                btnTelephoto.setVisibility(GONE);
            }

            // Check HDR availability
            if (session.isHdrSupported()) {
                btnHdr.setVisibility(VISIBLE);
            } else {
                btnHdr.setVisibility(GONE);
            }

            setVisibility(VISIBLE);
        }
    }

    public void setZoomSliderWidget(CameraZoomSliderWidget slider) {
        this.zoomSliderWidget = slider;
    }

    private TextView createButton(Context context, String label, int lensIndex) {
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
            activeLensIndex = lensIndex;
            updateActiveLensUI(lensIndex);
            float targetZoom = wideRatio;
            if (lensIndex == 0) targetZoom = ultraWideRatio;
            else if (lensIndex == 2) targetZoom = telephotoRatio;

            if (cameraXSession != null) {
                cameraXSession.setZoomRatio(targetZoom);
            }
            if (zoomSliderWidget != null) {
                zoomSliderWidget.updateZoom(targetZoom);
            }
        });
        return tv;
    }

    private TextView createHdrButton(Context context) {
        TextView tv = new TextView(context);
        tv.setText("HDR");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tv.setGravity(Gravity.CENTER);

        int width = AndroidUtilities.dp(36);
        int height = AndroidUtilities.dp(34);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.leftMargin = AndroidUtilities.dp(4);
        lp.rightMargin = AndroidUtilities.dp(2);
        tv.setLayoutParams(lp);

        tv.setOnClickListener(v -> {
            isHdrEnabled = !isHdrEnabled;
            updateHdrButtonUI();
            if (cameraXSession != null) {
                cameraXSession.toggleHdrMode(isHdrEnabled);
            }
        });
        updateHdrButtonUI();
        return tv;
    }

    private void updateHdrButtonUI() {
        if (isHdrEnabled) {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setCornerRadius(AndroidUtilities.dp(16));
            activeBg.setColor(Color.rgb(255, 215, 0)); // Gold
            btnHdr.setBackground(activeBg);
            btnHdr.setTextColor(Color.BLACK);
        } else {
            btnHdr.setBackground(null);
            btnHdr.setTextColor(Color.argb(160, 255, 255, 255));
        }
    }

    public void notifyZoomChanged(float currentZoom) {
        if (Math.abs(currentZoom - ultraWideRatio) < 0.15f) {
            updateActiveLensUI(0);
        } else if (Math.abs(currentZoom - wideRatio) < 0.2f) {
            updateActiveLensUI(1);
        } else if (Math.abs(currentZoom - telephotoRatio) < 0.3f) {
            updateActiveLensUI(2);
        } else {
            clearSelection();
        }
    }

    private void updateActiveLensUI(int selectedIndex) {
        setButtonSelected(btnUltraWide, selectedIndex == 0);
        setButtonSelected(btnWide, selectedIndex == 1);
        setButtonSelected(btnTelephoto, selectedIndex == 2);
    }

    private void clearSelection() {
        setButtonSelected(btnUltraWide, false);
        setButtonSelected(btnWide, false);
        setButtonSelected(btnTelephoto, false);
    }

    private void setButtonSelected(TextView btn, boolean selected) {
        if (selected) {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setShape(GradientDrawable.OVAL);
            activeBg.setColor(Color.rgb(255, 215, 0)); // Warm gold / yellow indicator
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
        // Position comfortably above the shutter button (which extends up to ~118-126dp)
        lp.bottomMargin = AndroidUtilities.dp(140);
        parent.addView(widget, lp);
        return widget;
    }
}
