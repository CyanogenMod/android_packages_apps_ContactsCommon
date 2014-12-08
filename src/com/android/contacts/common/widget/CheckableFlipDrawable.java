package com.android.contacts.common.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.QuickContactBadge;

import com.android.contacts.common.R;

public class CheckableFlipDrawable extends FlipDrawable implements
        ValueAnimator.AnimatorUpdateListener {

    private final CheckmarkDrawable mCheckmarkDrawable;

    private final ValueAnimator mCheckmarkScaleAnimator;
    private final ValueAnimator mCheckmarkAlphaAnimator;

    private static final int POST_FLIP_DURATION_MS = 150;

    private static final float CHECKMARK_SCALE_BEGIN_VALUE = 0.2f;
    private static final float CHECKMARK_ALPHA_BEGIN_VALUE = 0f;

    /** Must be <= 1f since the animation value is used as a percentage. */
    private static final float END_VALUE = 1f;

    public CheckableFlipDrawable(Drawable front, final Resources res,
            final int checkBackgroundColor, final int flipDurationMs) {
        super(front, new CheckmarkDrawable(res, checkBackgroundColor),
                flipDurationMs, 0 /* preFlipDurationMs */, POST_FLIP_DURATION_MS);

        mCheckmarkDrawable = (CheckmarkDrawable) mBack;

        // We will create checkmark animations that are synchronized with the
        // flipping animation. The entire delay + duration of the checkmark animation
        // needs to equal the entire duration of the flip animation (where delay is 0).

        // The checkmark animation is in effect only when the back drawable is being shown.
        // For the flip animation duration    <pre>[_][]|[][_]<post>
        // The checkmark animation will be    |--delay--|-duration-|

        // Need delay to skip the first half of the flip duration.
        final long animationDelay = mPreFlipDurationMs + mFlipDurationMs / 2;
        // Actual duration is the second half of the flip duration.
        final long animationDuration = mFlipDurationMs / 2 + mPostFlipDurationMs;

        mCheckmarkScaleAnimator = ValueAnimator.ofFloat(CHECKMARK_SCALE_BEGIN_VALUE, END_VALUE)
                .setDuration(animationDuration);
        mCheckmarkScaleAnimator.setStartDelay(animationDelay);
        mCheckmarkScaleAnimator.addUpdateListener(this);

        mCheckmarkAlphaAnimator = ValueAnimator.ofFloat(CHECKMARK_ALPHA_BEGIN_VALUE, END_VALUE)
                .setDuration(animationDuration);
        mCheckmarkAlphaAnimator.setStartDelay(animationDelay);
        mCheckmarkAlphaAnimator.addUpdateListener(this);
    }

    public void setFront(Drawable front) {
        mFront.setCallback(null);

        mFront = front;

        mFront.setCallback(this);
        mFront.setBounds(getBounds());
        mFront.setAlpha(getAlpha());
        mFront.setColorFilter(getColorFilter());
        mFront.setLevel(getLevel());

        reset();
        invalidateSelf();
    }

    public void setCheckMarkBackgroundColor(int color) {
        mCheckmarkDrawable.setBackgroundColor(color);
        invalidateSelf();
    }

    @Override
    public void reset() {
        super.reset();
        if (mCheckmarkScaleAnimator == null) {
            // Call from super's constructor. Not yet initialized.
            return;
        }
        mCheckmarkScaleAnimator.cancel();
        mCheckmarkAlphaAnimator.cancel();
        boolean side = getSideFlippingTowards();
        mCheckmarkDrawable.setScaleAnimatorValue(side ? CHECKMARK_SCALE_BEGIN_VALUE : END_VALUE);
        mCheckmarkDrawable.setAlphaAnimatorValue(side ? CHECKMARK_ALPHA_BEGIN_VALUE : END_VALUE);
    }

    @Override
    public void flip() {
        super.flip();
        // Keep the checkmark animators in sync with the flip animator.
        if (mCheckmarkScaleAnimator.isStarted()) {
            mCheckmarkScaleAnimator.reverse();
            mCheckmarkAlphaAnimator.reverse();
        } else {
            if (!getSideFlippingTowards() /* front to back */) {
                mCheckmarkScaleAnimator.start();
                mCheckmarkAlphaAnimator.start();
            } else /* back to front */ {
                mCheckmarkScaleAnimator.reverse();
                mCheckmarkAlphaAnimator.reverse();
            }
        }
    }

    @Override
    public void onAnimationUpdate(final ValueAnimator animation) {
        //noinspection ConstantConditions
        final float value = (Float) animation.getAnimatedValue();

        if (animation == mCheckmarkScaleAnimator) {
            mCheckmarkDrawable.setScaleAnimatorValue(value);
        } else if (animation == mCheckmarkAlphaAnimator) {
            mCheckmarkDrawable.setAlphaAnimatorValue(value);
        }
    }

    private static class CheckmarkDrawable extends Drawable {
        private static Bitmap sCheckMark;

        private final Paint mPaint;

        private float mScaleFraction;
        private float mAlphaFraction;

        private static final Matrix sMatrix = new Matrix();

        public CheckmarkDrawable(final Resources res, int backgroundColor) {
            if (sCheckMark == null) {
                sCheckMark = BitmapFactory.decodeResource(res, R.drawable.ic_check_wht_24dp);
            }
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setFilterBitmap(true);
            mPaint.setColor(backgroundColor);
        }

        public void setBackgroundColor(int color) {
            mPaint.setColor(color);
        }

        @Override
        public void draw(final Canvas canvas) {
            final Rect bounds = getBounds();
            if (!isVisible() || bounds.isEmpty()) {
                return;
            }

            canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() / 2, mPaint);

            // Scale the checkmark.
            sMatrix.reset();
            sMatrix.setScale(mScaleFraction, mScaleFraction, sCheckMark.getWidth() / 2,
                    sCheckMark.getHeight() / 2);
            sMatrix.postTranslate(bounds.centerX() - sCheckMark.getWidth() / 2,
                    bounds.centerY() - sCheckMark.getHeight() / 2);

            // Fade the checkmark.
            final int oldAlpha = mPaint.getAlpha();
            // Interpolate the alpha.
            mPaint.setAlpha((int) (oldAlpha * mAlphaFraction));
            canvas.drawBitmap(sCheckMark, sMatrix, mPaint);
            // Restore the alpha.
            mPaint.setAlpha(oldAlpha);
        }

        @Override
        public void setAlpha(final int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(final ColorFilter cf) {
            mPaint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            // Always a gray background.
            return PixelFormat.OPAQUE;
        }

        /**
         * Set value as a fraction from 0f to 1f.
         */
        public void setScaleAnimatorValue(final float value) {
            final float old = mScaleFraction;
            mScaleFraction = value;
            if (old != mScaleFraction) {
                invalidateSelf();
            }
        }

        /**
         * Set value as a fraction from 0f to 1f.
         */
        public void setAlphaAnimatorValue(final float value) {
            final float old = mAlphaFraction;
            mAlphaFraction = value;
            if (old != mAlphaFraction) {
                invalidateSelf();
            }
        }
    }
}
