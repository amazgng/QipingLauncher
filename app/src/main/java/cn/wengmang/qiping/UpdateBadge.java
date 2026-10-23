package cn.wengmang.qiping;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Small upper-left update marker, distinct from the upper-right pinned dot. */
final class UpdateBadge {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds=new RectF();
    void draw(Canvas canvas,float x,float y,float scale) {
        bounds.set(x,y,x+34*scale,y+18*scale);paint.setColor(0xFFF2746C);
        canvas.drawRoundRect(bounds,6*scale,6*scale,paint);
        paint.setColor(0xFFFFFFFF);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(11*scale);paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("更新",bounds.centerX(),bounds.centerY()-(paint.ascent()+paint.descent())/2,paint);
    }
}
