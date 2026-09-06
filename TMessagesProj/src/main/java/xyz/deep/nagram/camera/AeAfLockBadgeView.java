package xyz.deep.nagram.camera;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;

/**
 * AeAfLockBadgeView displays a bright yellow pill at the top of the viewfinder
 * indicating that Auto-Exposure and Auto-Focus are locked.
 */
public class AeAfLockBadgeView extends TextView {

    public AeAfLockBadgeView(Context context) {
        super(context);
        setText("AE/AF LOCK");
        setTextColor(Color.BLACK);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        setGravity(Gravity.CENTER);

        int padH = AndroidUtilities.dp(12);
        int padV = AndroidUtilities.dp(4);
        setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 215, 0)); // Gold / yellow badge
        bg.setCornerRadius(AndroidUtilities.dp(12));
        setBackground(bg);

        setVisibility(View.GONE);
        setAlpha(0.0f);
    }

    public void showLock() {
        setVisibility(View.VISIBLE);
        animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(180).start();
    }

    public void hideLock() {
        animate().alpha(0.0f).scaleX(0.85f).scaleY(0.85f).setDuration(150).withEndAction(() -> {
            setVisibility(View.GONE);
        }).start();
    }

    public static AeAfLockBadgeView attachTo(ViewGroup parent) {
        if (parent == null) return null;
        AeAfLockBadgeView badge = new AeAfLockBadgeView(parent.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = AndroidUtilities.dp(48);
        parent.addView(badge, lp);
        return badge;
    }
}
