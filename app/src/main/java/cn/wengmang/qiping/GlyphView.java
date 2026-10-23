package cn.wengmang.qiping;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Legacy unused glyph view; active UI uses GlassSymbol. */
final class GlyphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private final int color;

    GlyphView(Context context, String kind, int color) {
        super(context);
        this.kind = kind;
        this.color = color;
        setFocusable(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        canvas.save();
        canvas.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
        canvas.scale(size / 48f, size / 48f);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.6f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if ("plus".equals(kind)) {
            canvas.drawCircle(24, 24, 17, paint);
            canvas.drawLine(24, 16, 24, 32, paint);
            canvas.drawLine(16, 24, 32, 24, paint);
        } else if ("grid".equals(kind)) {
            for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++)
                canvas.drawRoundRect(new RectF(7+c*20, 7+r*20, 21+c*20, 21+r*20), 3, 3, paint);
        } else if ("network".equals(kind)) {
            canvas.drawArc(new RectF(4, 10, 44, 50), 220, 100, false, paint);
            canvas.drawArc(new RectF(11, 18, 37, 44), 220, 100, false, paint);
            canvas.drawArc(new RectF(18, 26, 30, 38), 220, 100, false, paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(24, 37, 2, paint);
        } else if ("palette".equals(kind)) {
            canvas.drawCircle(24, 24, 17, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(17, 17, 3, paint); canvas.drawCircle(30, 17, 3, paint);
            canvas.drawCircle(17, 30, 3, paint); canvas.drawCircle(30, 30, 3, paint);
        } else if ("text".equals(kind)) {
            canvas.drawLine(10, 11, 38, 11, paint); canvas.drawLine(24, 11, 24, 38, paint);
            canvas.drawLine(17, 38, 31, 38, paint);
        } else if ("settings".equals(kind)) {
            for (int i=0; i<3; i++) {
                float y=12+i*12, x=i==1?30:18;
                canvas.drawLine(7,y,x-4,y,paint); canvas.drawLine(x+4,y,41,y,paint);
                canvas.drawCircle(x,y,4,paint);
            }
        } else if ("info".equals(kind)) {
            canvas.drawCircle(24,24,17,paint); canvas.drawLine(24,22,24,33,paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(24,15,1.8f,paint);
        } else if ("margin".equals(kind)) {
            canvas.drawRoundRect(new RectF(5,10,43,38),3,3,paint);
            canvas.drawRoundRect(new RectF(13,17,35,31),2,2,paint);
        } else {
            canvas.drawRoundRect(new RectF(5,8,43,34),5,5,paint);
            canvas.drawLine(24,34,24,41,paint); canvas.drawLine(16,41,32,41,paint);
        }
        canvas.restore();
    }
}
