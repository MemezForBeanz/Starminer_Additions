package net.memezforbeanz.starminerodyssey.blocks.client.ui;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Segmented FE buffer gauge: green while the generator can pay its upkeep, red when
 * starved. Reads its suppliers live and repaints itself a few times a second while
 * attached, so it tracks the block entity without outside plumbing.
 */
public class EnergyGaugeView extends View {

    private static final int SEGMENTS = 16;
    private static final long REFRESH_MS = 100;

    private final LongSupplier stored;
    private final long capacity;
    private final BooleanSupplier powered;
    /** Runs on each refresh so sibling readouts (labels, statusline) stay live too. */
    private final Runnable onRefresh;

    private final Runnable ticker = this::tick;

    public EnergyGaugeView(Context context, LongSupplier stored, long capacity,
                           BooleanSupplier powered, Runnable onRefresh) {
        super(context);
        this.stored = stored;
        this.capacity = capacity;
        this.powered = powered;
        this.onRefresh = onRefresh;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(ticker, REFRESH_MS);
    }

    private void tick() {
        if (isAttachedToWindow()) {
            invalidate();
            onRefresh.run();
            postDelayed(ticker, REFRESH_MS);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float inset = dp(1);
        float fraction = capacity <= 0 ? 0
                : Math.min(1.0f, Math.max(0.0f, stored.getAsLong() / (float) capacity));

        Paint paint = Paint.obtain();
        paint.setColor(OneDark.BG_DARK);
        canvas.drawRect(0, 0, width, height, paint);

        float filled = (width - 2 * inset) * fraction;
        if (filled > 0) {
            paint.setColor(powered.getAsBoolean() ? OneDark.GREEN : OneDark.RED);
            canvas.drawRect(inset, inset, inset + filled, height - inset, paint);
        }

        paint.setColor(OneDark.BG);
        for (int i = 1; i < SEGMENTS; i++) {
            float tickX = inset + (width - 2 * inset) * i / SEGMENTS;
            canvas.drawRect(tickX, inset, tickX + dp(1), height - inset, paint);
        }

        paint.setColor(OneDark.BORDER);
        canvas.drawRect(0, 0, width, dp(1), paint);
        canvas.drawRect(0, height - dp(1), width, height, paint);
        canvas.drawRect(0, 0, dp(1), height, paint);
        canvas.drawRect(width - dp(1), 0, width, height, paint);
        paint.recycle();
    }
}
