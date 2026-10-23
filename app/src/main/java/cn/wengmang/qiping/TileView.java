package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.widget.LinearLayout;

/** A cheap, clearly focused card; no shaders, blur, or continuous animations. */
final class TileView extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final int accent;
    private final float unit;
    private boolean active;

    TileView(Context context, int accent, float unit) {
        super(context);
        this.accent = accent;
        this.unit = unit;
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setDefaultFocusHighlightEnabled(false);setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    void setActive(boolean value) { active = value; invalidate(); }

    @Override protected void onFocusChanged(boolean gain, int direction, Rect oldRect) {
        super.onFocusChanged(gain, direction, oldRect);
        invalidate();
    }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float inset = 2 * unit;
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isPressed() ? 0xFF354756 : isFocused() ? 0xFF293E4A
                : active ? 0xFF243541 : 0xFF192633);
        canvas.drawRoundRect(bounds, 14 * unit, 14 * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((isFocused() ? 3 : 1) * unit);
        paint.setColor(isFocused() ? accent : active ? 0x776EA79F : 0xFF30404C);
        canvas.drawRoundRect(bounds, 14 * unit, 14 * unit, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
