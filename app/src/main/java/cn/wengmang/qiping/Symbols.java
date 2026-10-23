package cn.wengmang.qiping;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Original icon silhouettes cached as filled vector paths for the Android 9+ glass renderer. */
final class Symbols {
    private Symbols() {}
    private static final java.util.Map<String,Path> SHAPES=new java.util.HashMap<String,Path>();
    static Path shape(String kind) {
        Path cached=SHAPES.get(kind);
        if(cached==null){Outline outline=new Outline();build(outline,kind,new Paint(Paint.ANTI_ALIAS_FLAG));cached=outline.result;SHAPES.put(kind,cached);}
        return cached;
    }
    static void draw(Canvas canvas,String kind,Paint paint){paint.setStyle(Paint.Style.FILL);canvas.drawPath(shape(kind),paint);}
    private static void build(Outline c, String kind, Paint p) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        if("film".equals(kind)) {
            p.setStyle(Paint.Style.FILL);
            Path a=new Path(); a.moveTo(14,6); a.quadTo(11,4,11,9); a.lineTo(11,39);
            a.quadTo(11,43,15,41); a.lineTo(39,27); a.quadTo(43,24,39,21); a.close(); c.drawPath(a,p);
        } else if("live".equals(kind)) {
            c.drawRoundRect(new RectF(5,12,43,37),4,4,p);
            c.drawLine(16,4,24,11,p); c.drawLine(32,4,24,11,p); c.drawLine(16,41,32,41,p);
        } else if("music".equals(kind)) {
            p.setStyle(Paint.Style.FILL);
            Path a=new Path(); a.moveTo(18,10); a.lineTo(39,5); a.lineTo(39,35); a.lineTo(35,35);
            a.lineTo(35,15); a.lineTo(22,18); a.lineTo(22,39); a.lineTo(18,39); a.close(); c.drawPath(a,p);
            c.drawOval(new RectF(7,33,22,44),p); c.drawOval(new RectF(24,29,39,40),p);
        } else if("album".equals(kind)) {
            c.drawRoundRect(new RectF(3,7,45,41),4,4,p);
            p.setStyle(Paint.Style.FILL); c.drawCircle(34,17,4,p);
            Path a=new Path(); a.moveTo(5,35); a.lineTo(17,21); a.lineTo(28,32);
            a.lineTo(34,26); a.lineTo(43,36); a.lineTo(43,39); a.lineTo(5,39); a.close(); c.drawPath(a,p);
        } else if("grid".equals(kind)) {
            p.setStyle(Paint.Style.FILL);
            for(int y=0;y<2;y++) for(int x=0;x<2;x++) c.drawRoundRect(new RectF(5+x*23,5+y*23,21+x*23,21+y*23),4,4,p);
        } else if("network".equals(kind)||"offline".equals(kind)) {
            c.drawArc(new RectF(3,7,45,49),223,94,false,p);
            c.drawArc(new RectF(11,16,37,42),223,94,false,p);
            c.drawArc(new RectF(18,25,30,37),223,94,false,p);
            p.setStyle(Paint.Style.FILL); c.drawCircle(24,36,2,p);
            if("offline".equals(kind)){p.setStyle(Paint.Style.STROKE);c.drawLine(7,7,41,42,p);}
        } else if("settings".equals(kind)) {
            Path a=new Path();
            for(int i=0;i<32;i++) {
                double angle=i*Math.PI/16; float rad=(i%4==0||i%4==3)?19:15;
                float x=24+(float)Math.cos(angle)*rad,y=24+(float)Math.sin(angle)*rad;
                if(i==0)a.moveTo(x,y);else a.lineTo(x,y);
            }
            a.close();c.drawPath(a,p);c.drawCircle(24,24,6.5f,p);
        } else if("down".equals(kind)) {
            c.drawLine(12,19,24,31,p); c.drawLine(24,31,36,19,p);
        } else if("back".equals(kind)) {
            c.drawLine(28,11,15,24,p);c.drawLine(15,24,28,37,p);
        } else if("next".equals(kind)) {
            c.drawLine(20,11,33,24,p);c.drawLine(33,24,20,37,p);
        } else if("refresh".equals(kind)) {
            c.drawArc(new RectF(8,8,40,40),55,290,false,p);
            c.drawLine(40,9,40,21,p);c.drawLine(40,21,28,21,p);
        } else if("home".equals(kind)) {
            Path a=new Path();a.moveTo(5,23);a.lineTo(24,7);a.lineTo(43,23);c.drawPath(a,p);
            a=new Path();a.moveTo(10,20);a.lineTo(10,41);a.lineTo(20,41);a.lineTo(20,29);a.lineTo(29,29);a.lineTo(29,41);a.lineTo(38,41);a.lineTo(38,20);c.drawPath(a,p);
        } else if("motion".equals(kind)) {
            Path a=new Path();a.moveTo(25,5);a.lineTo(29,19);a.lineTo(43,23);a.lineTo(29,27);a.lineTo(25,41);a.lineTo(21,27);a.lineTo(7,23);a.lineTo(21,19);a.close();c.drawPath(a,p);
            c.drawLine(39,3,39,11,p);c.drawLine(35,7,43,7,p);
        } else if("margin".equals(kind)) {
            c.drawLine(5,16,5,6,p);c.drawLine(5,6,16,6,p);c.drawLine(32,6,43,6,p);c.drawLine(43,6,43,16,p);
            c.drawLine(5,32,5,42,p);c.drawLine(5,42,16,42,p);c.drawLine(32,42,43,42,p);c.drawLine(43,42,43,32,p);
            c.drawRoundRect(new RectF(13,14,35,34),3,3,p);
        } else if("text".equals(kind)) {
            c.drawLine(6,39,19,8,p);c.drawLine(19,8,32,39,p);c.drawLine(11,28,27,28,p);
            c.drawLine(33,23,43,23,p);c.drawLine(38,23,38,39,p);
        } else if("desktop".equals(kind)) {
            c.drawRoundRect(new RectF(4,7,44,34),4,4,p);c.drawLine(24,34,24,41,p);c.drawLine(14,42,34,42,p);
        } else if("history".equals(kind)) {
            c.drawArc(new RectF(7,7,41,41),205,300,false,p);c.drawLine(5,8,5,20,p);c.drawLine(5,20,17,20,p);
            c.drawLine(24,14,24,25,p);c.drawLine(24,25,32,29,p);
        } else if("export".equals(kind)||"import".equals(kind)) {
            c.drawRoundRect(new RectF(8,4,37,44),3,3,p);c.drawLine(14,35,30,35,p);
            boolean outward="export".equals(kind);float from=outward?29:13,to=outward?13:29;
            c.drawLine(23,from,23,to,p);c.drawLine(17,outward?19:23,23,to,p);c.drawLine(23,to,29,outward?19:23,p);
        } else if("lock".equals(kind)) {
            c.drawRoundRect(new RectF(10,22,38,43),4,4,p);c.drawArc(new RectF(15,4,33,34),180,180,false,p);c.drawLine(24,29,24,35,p);
        } else if("info".equals(kind)) {
            c.drawCircle(24,24,19,p);c.drawLine(24,22,24,35,p);p.setStyle(Paint.Style.FILL);c.drawCircle(24,14,1.8f,p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    /** Converts stroked primitives once, retaining rounded ends and hollow centers. */
    private static final class Outline {
        final Path result=new Path();
        private void add(Path path,Paint paint){Path filled=new Path();if(paint.getStyle()==Paint.Style.FILL)filled.set(path);else paint.getFillPath(path,filled);result.op(filled,Path.Op.UNION);}
        void drawPath(Path path,Paint paint){add(path,paint);}
        void drawLine(float x,float y,float ex,float ey,Paint paint){Path p=new Path();p.moveTo(x,y);p.lineTo(ex,ey);add(p,paint);}
        void drawRoundRect(RectF r,float rx,float ry,Paint paint){Path p=new Path();p.addRoundRect(r,rx,ry,Path.Direction.CW);add(p,paint);}
        void drawCircle(float x,float y,float radius,Paint paint){Path p=new Path();p.addCircle(x,y,radius,Path.Direction.CW);add(p,paint);}
        void drawOval(RectF r,Paint paint){Path p=new Path();p.addOval(r,Path.Direction.CW);add(p,paint);}
        void drawArc(RectF r,float start,float sweep,boolean center,Paint paint){Path p=new Path();p.addArc(r,start,sweep);add(p,paint);}
    }

}
