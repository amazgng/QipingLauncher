package cn.wengmang.qiping;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;

/** Positions the home UI on a 960 x 540 design canvas; no fixed device pixels. */
final class StageLayout extends ViewGroup {
    interface PageSwipe {void onPageSwipe(boolean right);}
    private PageSwipe pageSwipe;
    private float startX,startY;
    private boolean swiping;
    void setPageSwipe(PageSwipe callback){pageSwipe=callback;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(pageSwipe==null)return super.onInterceptTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();swiping=false;}
        else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&event.getPointerCount()==1) {
            float dx=Math.abs(event.getX()-startX),dy=Math.abs(event.getY()-startY);
            if(dx>getWidth()*.055f&&dx>dy*1.5f){swiping=true;return true;}
        }
        return swiping;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(pageSwipe==null)return super.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_UP) {
            if(swiping){boolean right=event.getX()<startX;swiping=false;pageSwipe.onPageSwipe(right);}
        }else if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)swiping=false;
        return true;
    }
    static final class Position extends ViewGroup.LayoutParams {
        final float x, y, w, h;
        Position(float x, float y, float w, float h) {
            super(0, 0); this.x=x; this.y=y; this.w=w; this.h=h;
        }
    }
    StageLayout(Context context) {
        super(context); setClipChildren(false); setClipToPadding(false);
    }
    void place(View child, float x, float y, float w, float h) {
        addView(child, new Position(x,y,w,h));
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec), height=MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(width,height);
        for(int i=0;i<getChildCount();i++) {
            View child=getChildAt(i); Position p=(Position)child.getLayoutParams();
            child.measure(MeasureSpec.makeMeasureSpec(Math.round(p.w*width/960f),MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(p.h*height/540f),MeasureSpec.EXACTLY));
        }
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b) {
        float sx=(r-l)/960f, sy=(b-t)/540f;
        for(int i=0;i<getChildCount();i++) {
            View child=getChildAt(i); Position p=(Position)child.getLayoutParams();
            int x=Math.round(p.x*sx), y=Math.round(p.y*sy);
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
        }
    }
}
