package com.android.contacts.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.QuickContactBadge;

import com.android.contacts.common.R;

public class CheckableQuickContactBadge extends QuickContactBadge implements Checkable {
    private boolean mChecked = false;
    private int mCheckMarkBackgroundColor;
    private int mCheckMarkCheckAlpha;
    private CheckableFlipDrawable mDrawable;

    public CheckableQuickContactBadge(Context context) {
        super(context);
        init(context);
    }

    public CheckableQuickContactBadge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CheckableQuickContactBadge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public CheckableQuickContactBadge(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        TypedArray a = context.obtainStyledAttributes(android.R.styleable.Theme);
        setCheckMarkBackgroundColor(a.getColor(android.R.styleable.Theme_colorPrimary,
                context.getResources().getColor(R.color.people_app_theme_color)));
        a.recycle();
    }

    public void setCheckMarkBackgroundColor(int color) {
        mCheckMarkBackgroundColor = color;
        if (mDrawable != null) {
            mDrawable.setCheckMarkBackgroundColor(color);
        }
    }

    public void setCheckMarkCheckAlpha(int alpha) {
        mCheckMarkCheckAlpha = alpha;
        if (mDrawable != null) {
            mDrawable.setCheckMarkCheckAlpha(alpha);
            mDrawable.setAlpha(alpha);
        }
    }

    public void toggle() {
        setChecked(!mChecked);
    }

    @Override
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animate) {
        if (mChecked == checked) {
            return;
        }

        mChecked = checked;
        if (mDrawable != null) {
            applyCheckState(animate);
        }
    }

    @Override
    public void setImageDrawable(Drawable d) {
        if (d != null) {
            if (mDrawable == null) {
                mDrawable = new CheckableFlipDrawable(d, getResources(),
                        mCheckMarkBackgroundColor, 150);
                mDrawable.setCheckMarkCheckAlpha(mCheckMarkCheckAlpha);
                mDrawable.setAlpha(mCheckMarkCheckAlpha);
                applyCheckState(false);
            } else {
                mDrawable.setFront(d);
            }
            d = mDrawable;
        }
        super.setImageDrawable(d);
    }

    private void applyCheckState(boolean animate) {
        mDrawable.flipTo(!mChecked);
        if (!animate) {
            mDrawable.reset();
        }
    }
}
