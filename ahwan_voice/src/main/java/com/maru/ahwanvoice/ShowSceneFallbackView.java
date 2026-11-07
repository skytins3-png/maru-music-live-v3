package com.maru.ahwanvoice;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Original vector-only visual fallback for a local stage preview.
 * Each cue is visually distinguishable even when no video was uploaded.
 * NOT generated performance footage or a synchronized speaking avatar.
 */
public final class ShowSceneFallbackView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ShowScenePlan.Mode mode = ShowScenePlan.Mode.BRIDGE;
    private String[] people = {"아환"};
    private int currentCue = -1;

    public ShowSceneFallbackView(Context context) {super(context);}
    public void showCue(int cue, String singer, int singerIndex) {
        currentCue = cue;
        mode = ShowScenePlan.modeForCue(cue);
        people = ShowScenePlan.peopleForCue(cue,singer,singerIndex);
        invalidate();
    }
    public String sceneModeForTest() {return mode.name();}

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(getWidth()<=0 || getHeight()<=0) return;
        canvas.save();
        canvas.scale(getWidth()/400f,getHeight()/800f);
        int top = mode == ShowScenePlan.Mode.STAGE ? 0xFF45336F :
                mode == ShowScenePlan.Mode.TALK ? 0xFF51425C : 0xFF253D5C;
        gradient(canvas,top,0xFF111523);
        if(mode == ShowScenePlan.Mode.STAGE) drawStage(canvas);
        else if(mode == ShowScenePlan.Mode.TALK) drawLounge(canvas);
        else drawPresenterBackground(canvas);
        if(people.length>1) {
            drawPerson(canvas,114f,505f,0.76f,people[0],0);
            drawPerson(canvas,290f,505f,0.76f,people[1],1);
        } else {
            drawPerson(canvas,200f,545f,1.1f,people[0],currentCue<0?0:currentCue);
        }
        text(canvas,mode == ShowScenePlan.Mode.STAGE ? "MARU CONCERT STAGE" :
                        mode == ShowScenePlan.Mode.TALK ? "MARU GUEST TALK" : "AHWAN RADIO LIVE",
                200,92,19,0xFFEDE5FF);
        text(canvas,mode == ShowScenePlan.Mode.STAGE ? "노래 무대 · 가상 캐릭터" :
                        mode == ShowScenePlan.Mode.TALK ? "초대석 · 사연 · 대화" : "인사 · 곡 소개 · 진행",
                200,128,15,0xFFEBD5E9);
        text(canvas,"실제 공연 영상은 별도로 등록해야 합니다",200,702,13,0xFFE9D7C8);
        canvas.restore();
    }
    private void gradient(Canvas canvas, int top,int bottom) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,0,0,800,top,bottom,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,400,800,p);
        p.setShader(null);
    }
    private void fill(int color) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setShader(null);
    }
    private void drawStage(Canvas c) {
        for(int i=0;i<5;i++) {
            Path beam=new Path();
            beam.moveTo(55+i*74,0);
            beam.lineTo(55+i*74-95,690);
            beam.lineTo(55+i*74+105,690);
            beam.close();
            fill(i%2==0?0x20FCE3FF:0x24F8C1D9);
            c.drawPath(beam,p);
        }
        fill(0xFF392C4C);c.drawOval(-80,603,480,840,p);
        fill(0xFF746E9A);c.drawRoundRect(new RectF(22,625,378,641),8,8,p);
    }
    private void drawLounge(Canvas c) {
        fill(0x22FFFFFF);
        c.drawRoundRect(new RectF(25,185,375,535),33,33,p);
        fill(0xFF7F6078);
        c.drawRoundRect(new RectF(32,525,368,625),45,45,p);
        fill(0xFF413852);
        c.drawRoundRect(new RectF(25,590,375,639),20,20,p);
        fill(0xFFBE9A76);
        c.drawRoundRect(new RectF(146,612,258,634),10,10,p);
    }
    private void drawPresenterBackground(Canvas c) {
        fill(0x27F4F2FF);c.drawOval(65,165,335,565,p);
        fill(0xFF544C6C);c.drawRoundRect(new RectF(36,613,364,648),18,18,p);
    }
    private void drawPerson(Canvas c,float x,float baseline,float size,String name,int index) {
        c.save();c.translate(x,baseline);c.scale(size,size);
        int coat=colorFor(name,index);
        fill(0xFF201B30);c.drawOval(-94,-17,94,20,p);
        fill(coat);c.drawRoundRect(new RectF(-95,-137,95,4),48,48,p);
        fill(0xFFFEF3E2);
        Path shirt=new Path();shirt.moveTo(-32,-139);shirt.lineTo(0,-69);
        shirt.lineTo(32,-139);shirt.close();c.drawPath(shirt,p);
        fill(0xFFE7B99C);c.drawRoundRect(new RectF(-18,-176,18,-122),10,10,p);
        fill(0xFF322B3D);c.drawOval(-70,-308,70,-161,p);
        fill(0xFFE7B99C);c.drawOval(-56,-286,56,-168,p);
        fill(0xFF322B3D);
        Path fringe=new Path();fringe.moveTo(-57,-256);fringe.quadTo(-46,-335,46,-307);
        fringe.quadTo(70,-275,25,-271);fringe.lineTo(-34,-267);
        fringe.close();c.drawPath(fringe,p);
        fill(0xFF493438);
        c.drawCircle(-22,-228,3.2f,p);c.drawCircle(22,-228,3.2f,p);
        fill(0xFF994F66);c.drawRoundRect(new RectF(-11,-201,11,-197),4,4,p);
        c.restore();
        text(c,name,x,baseline+44,18,0xFFFFFFFF);
    }
    private int colorFor(String name,int index) {
        if("아환".equals(name))return 0xFF294667;
        if("윤솔".equals(name))return 0xFF7C69A7;
        if("강태준".equals(name))return 0xFF794059;
        if("청호".equals(name))return 0xFFC1A479;
        if("서아".equals(name))return 0xFF357D81;
        if("린아".equals(name))return 0xFFAD4661;
        if("민혁".equals(name))return 0xFF657B92;
        if("하린".equals(name))return 0xFFB56E89;
        return new int[]{0xFF4A6299,0xFF90647F,0xFF597F78,0xFF987754}
                [Math.floorMod(index,4)];
    }
    private void text(Canvas c,String text,float x,float y,int size,int color){
        fill(color);p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(3f,0f,2f,Color.BLACK);
        c.drawText(text,x,y,p);p.clearShadowLayer();
    }
}
