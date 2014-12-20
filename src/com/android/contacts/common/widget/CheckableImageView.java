package com.android.contacts.common.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.ImageView;

import com.android.contacts.common.R;

public class CheckableImageView extends ImageView implements Checkable {
    private boolean mChecked = false;
    private int mCheckMarkBackgroundColor;
    private CheckableFlipDrawable mDrawable;

    public CheckableImageView(Context context) {
        super(context);
        init(context);
    }

    public CheckableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CheckableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public CheckableImageView(Context context, AttributeSet attrs,
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
                int oldWidth = mDrawable.getIntrinsicWidth();
                int oldHeight = mDrawable.getIntrinsicHeight();
                mDrawable.setFront(d);
                if (oldWidth != mDrawable.getIntrinsicWidth()
                        || oldHeight != mDrawable.getIntrinsicHeight()) {
                    // enforce drawable size update + layout
                    super.setImageDrawable(null);
                }
            }
            d = mDrawable;
        }
        super.setImageDrawable(d);
    }
}
