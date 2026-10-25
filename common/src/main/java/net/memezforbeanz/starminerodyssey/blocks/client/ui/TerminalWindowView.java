package net.memezforbeanz.starminerodyssey.blocks.client.ui;

import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.FrameLayout;

/** The window frame: a One Dark panel revealed by two accent brackets sliding apart. Content is clipped to the reveal and input is swallowed until it is fully open. */
public class TerminalWindowView extends FrameLayout {

    private static final long OPEN_DELAY_MS = 120;
    private static final long OPEN_MS = 480;

    private float openProgress = 0.0f;
    private boolean entrancePlayed = false;

    public TerminalWindowView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!entrancePlayed) {
            entrancePlayed = true;
            ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
            animator.setStartDelay(OPEN_DELAY_MS);
            animator.setDuration(OPEN_MS);
            animator.setInterpolator(TimeInterpolator.DECELERATE_CUBIC);
            animator.addUpdateListener(a -> {
                openProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    public boolean isFullyOpen() {
        return openProgress >= 1.0f;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return !isFullyOpen();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Clicks on the window itself (or during the entrance) never fall through
        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float centerX = getWidth() / 2.0f;
        float closedHalf = dp(5);
        float halfGap = closedHalf + (centerX + dp(4) - closedHalf) * openProgress;
        float clipLeft = centerX - halfGap;
        float clipRight = centerX + halfGap;

        canvas.save();
        canvas.clipRect(clipLeft, 0, clipRight, getHeight());

        Paint paint = Paint.obtain();
        paint.setColor(OneDark.BG);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        super.dispatchDraw(canvas);

        // Top/bottom rails complete the frame between the brackets
        paint.setColor(OneDark.BORDER);
        canvas.drawRect(clipLeft, 0, clipRight, dp(1), paint);
        canvas.drawRect(clipLeft, getHeight() - dp(1), clipRight, getHeight(), paint);
        paint.recycle();

        canvas.restore();

        drawBracket(canvas, clipLeft, true);
        drawBracket(canvas, clipRight, false);
    }

    /** One "[" / "]" bar with inward stubs, in the accent blue with a soft glow. */
    private void drawBracket(Canvas canvas, float edgeX, boolean left) {
        float barWidth = dp(2.5f);
        float stub = dp(7);
        float overhang = dp(5);
        float top = -overhang;
        float bottom = getHeight() + overhang;
        float barX = left ? edgeX - dp(2) - barWidth : edgeX + dp(2);

        Paint paint = Paint.obtain();
        paint.setColor(0x3861AFEF);
        canvas.drawRect(barX - dp(1), top - dp(1), barX + barWidth + dp(1), bottom + dp(1), paint);
        paint.setColor(OneDark.BLUE);
        canvas.drawRect(barX, top, barX + barWidth, bottom, paint);
        float stubStart = left ? barX : barX + barWidth - stub;
        float stubEnd = left ? barX + stub : barX + barWidth;
        canvas.drawRect(stubStart, top, stubEnd, top + barWidth, paint);
        canvas.drawRect(stubStart, bottom - barWidth, stubEnd, bottom, paint);
        paint.recycle();
    }
}
