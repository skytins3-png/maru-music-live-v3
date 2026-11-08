package com.maru.media;
import android.view.*;
import android.graphics.*;
import android.graphics.drawable.*;
/** Drawn broadcast-studio hero: offline, royalty-free, no stock or web downloads. */
public final class MicHeroView extends View {
 private final Paint p=new Paint(3);
 public MicHeroView(android.content.Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
 private void color(int c){p.reset();p.setAntiAlias(true);p.setColor(c);}
 private void stroke(int c,float width){color(c);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);}
 @Override protected void onDraw(Canvas nativeCanvas){
  super.onDraw(nativeCanvas);Canvas c=nativeCanvas;
  float w=getWidth(),h=getHeight();if(w==0||h==0)return;
  c.save();c.scale(w/400f,h/250f);
  p.setShader(new LinearGradient(0,0,400,250,new int[]{0xff041022,0xff122c4c,0xff040d1b},null,Shader.TileMode.CLAMP));
  c.drawRect(0,0,400,250,p);p.setShader(null);
  // Bokeh from the studio windows. Keep left side dark for readable header.
  for(int i=0;i<21;i++){
    float x=10+(i*73)%390,y=96+(i*41)%130;
    int col=(i%3==0)?0xff2e80b8:(i%3==1)?0xff865bb7:0xff2a7ba6;
    color(col);p.setAlpha(25+(i%4)*15);p.setMaskFilter(new BlurMaskFilter(18+(i%4)*5,BlurMaskFilter.Blur.NORMAL));
    c.drawCircle(x,y,5+(i%4)*4,p);
  }
  p.setMaskFilter(null);
  // Microphone arm/shock mount.
  stroke(0xffa0b4c9,2.6f);
  c.drawLine(326,140,313,238,p);c.drawLine(350,143,372,238,p);
  c.drawLine(308,164,369,184,p);c.drawLine(369,160,307,187,p);
  c.drawLine(340,142,340,239,p);
  // Glossy silver capsule with metallic highlights.
  color(0xffd8e3ee);
  p.setShader(new LinearGradient(318,0,359,0,new int[]{0xff7a8eaa,0xffe2eff8,0xff8197ad,0xff405e7b},null,Shader.TileMode.CLAMP));
  c.drawRoundRect(318,60,361,181,20,20,p);p.setShader(null);
  stroke(0xff375875,2);c.drawRoundRect(318,60,361,181,20,20,p);
  for(int i=0;i<33;i++){
    stroke(i%4==0?0xff607e94:0xff97b0c3,.75f);
    float yy=70+i*2.7f;c.drawLine(324,yy,355,yy,p);
  }
  for(int i=0;i<13;i++){stroke(0xff819caf,.65f);float xx=325+i*2.45f;c.drawLine(xx,74,xx,155,p);}
  color(0xff3c5166);c.drawRect(318,157,361,175,p);
  stroke(0xffd6e6f2,2.8f);c.drawLine(315,170,364,170,p);
  stroke(0xff7289a5,3.4f);c.drawLine(340,183,340,250,p);
  color(0xff0c1e31);p.setAlpha(108);c.drawRect(0,0,260,250,p);
  c.restore();
 }
}