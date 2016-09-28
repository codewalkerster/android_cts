package android.uirendering.cts.util;

import android.view.View;
import android.view.ViewTreeObserver.OnPreDrawListener;

public class DrawCountDown implements OnPreDrawListener {
    private int mDrawCount;
    private View mTargetView;
    private Runnable mRunnable;

    private DrawCountDown(View targetView, int countFrames, Runnable countReachedListener) {
        mTargetView = targetView;
        mDrawCount = countFrames;
        mRunnable = countReachedListener;
    }

    @Override
    public boolean onPreDraw() {
        if (mDrawCount <= 0) {
            mTargetView.getViewTreeObserver().removeOnPreDrawListener(this);
            mRunnable.run();
        } else {
            mDrawCount--;
            mTargetView.postInvalidate();
        }
        return true;
 
    }

    public static void countDownDraws(View targetView, int countFrames,
            Runnable onDrawCountReachedListener) {
        DrawCountDown counter = new DrawCountDown(targetView, countFrames,
                onDrawCountReachedListener);
        targetView.getViewTreeObserver().addOnPreDrawListener(counter);
    }
}
