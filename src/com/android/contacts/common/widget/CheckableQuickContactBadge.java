package com.android.contacts.common.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.QuickContactBadge;

import com.android.contacts.common.R;

public class CheckableQuickContactBadge extends QuickContactBadge implements Checkable {
    private boolean mChecked = false;
    private int mCheckMarkBackgroundColor;
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

    public void setCheckMarkBackgroundColor(int color) {
        if (mDrawable != null) {
            throw new IllegalStateException(
                    "Need to set background color before assigning drawable");
        }
        mCheckMarkBackgroundColor = color;
    }

    private void init(Context context) {
        mCheckMarkBackgroundColor =
                context.getResources().getColor(R.color.people_app_theme_color);
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

        Drawable d = getDrawable();
        if (d instanceof CheckableFlipDrawable) {
            CheckableFlipDrawable cfd = (CheckableFlipDrawable) d;
            cfd.flipTo(!mChecked);
            if (!animate) {
                cfd.reset();
            }
        }
    }

    @Override
    public void setImageDrawable(Drawable d) {
        if (d != null) {
            if (mDrawable == null) {
                mDrawable = new CheckableFlipDrawable(d, getResources(),
                        mCheckMarkBackgroundColor, 150);
            } else {
                mDrawable.setFront(d);
            }
            d = mDrawable;
        }
        super.setImageDrawable(d);
    }
}
