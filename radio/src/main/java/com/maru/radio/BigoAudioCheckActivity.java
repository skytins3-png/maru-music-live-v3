package com.maru.radio;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;

/**
 * No streaming, no listening or recording. A private local checklist that
 * requires the actual host/listener to verify audible sound on another device.
 */
public final class BigoAudioCheckActivity extends Activity {
    private static final int BG = 0xFF130F24, PANEL = 0xFF27203B;
    private static final int WHITE = 0xFFF7F2FF, PURPLE = 0xFFD2B9FF;
    private static final String PREFS = "maru_bigo_reception_self_report_v1";
    private final CheckBox[] boxes = new CheckBox[BigoReceptionChecklist.CHECKS];
    private TextView result;
    private TextView detail;
    private TextView timestamp;
    private SharedPreferences prefs;
    private boolean resetting;

    private int dp(int value) {return (int) (value * getResources().getDisplayMetrics().density + .5f);}
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setPadding(dp(3),dp(7),dp(3),dp(7));
        return t;
    }
    private Button button(String title, Runnable action) {
        Button b = new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(16);
        b.setTextColor(WHITE);b.setOnClickListener(v -> action.run());return b;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout p = new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(15),dp(15),dp(15),dp(15));
        GradientDrawable bg = new GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(14));
        p.setBackground(bg);LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(15);parent.addView(p,lp);return p;
    }
    private boolean[] current() {
        boolean[] flags=new boolean[boxes.length];
        for(int i=0;i<boxes.length;i++) flags[i]=boxes[i]!=null&&boxes[i].isChecked();
        return flags;
    }
    private String writtenTime() {
        long at=prefs.getLong("updated",0);
        return at==0?"이 기기에 저장된 테스트 기록 없음":
                "마지막 직접 기록: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(at));
    }
    private void save() {
        if(resetting)return;
        boolean[] flags=current();
        SharedPreferences.Editor editor=prefs.edit();
        for(int i=0;i<flags.length;i++)editor.putBoolean("check"+i,flags[i]);
        editor.putLong("updated",System.currentTimeMillis()).apply();
        refresh();
    }
    private void refresh() {
        boolean[] flags=current();
        int count=BigoReceptionChecklist.completed(flags);
        result.setText(BigoReceptionChecklist.acceptable(flags)?
                "기기 자기기록: 청취자 수신 5/5 확인 표시 (외부 자동검증 아님)":
                "청취자 수신 검사 진행: "+count+"/5 · 실제 방송 성공 미확인");
        result.setTextColor(BigoReceptionChecklist.acceptable(flags)?0xFFA3E9C6:PURPLE);
        detail.setText(BigoReceptionChecklist.diagnostic(flags));
        timestamp.setText(writtenTime());
    }
    private void openBigo() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("sg.bigo.live");
            if(intent == null) {
                detail.setText("BIGO 앱을 찾지 못했습니다. 공식 BIGO LIVE 설치·로그인을 먼저 확인해 주세요.");
                return;
            }
            startActivity(intent);
        }catch(ActivityNotFoundException | SecurityException err){
            detail.setText("BIGO 앱을 열지 못했습니다. 앱 설치 및 상태를 확인해 주세요.");
        }
    }
    private void openRadio() {
        Intent intent=new Intent(this, RadioActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }
    private void copyRecord() {
        boolean[] flags=current();
        String[] names={"두 번째 기기 입장","BIGO 실제 라이브 시작","진행자 멘트 수신",
                "첫 자작곡 수신","다음곡 전환 수신"};
        StringBuilder report=new StringBuilder("MARU RADIO · BIGO 오디오 청취자 수신 자기점검\n");
        report.append(writtenTime()).append("\n");
        for(int i=0;i<flags.length;i++) report.append(names[i]).append(": ")
                .append(flags[i]?"직접 확인":"미확인").append("\n");
        report.append(BigoReceptionChecklist.diagnostic(flags)).append("\n")
                .append("※ BIGO 서버 인증·자동 음성 분석 결과가 아닌 사용자 자기기록.");
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard!=null){
            clipboard.setPrimaryClip(ClipData.newPlainText("MARU BIGO audio listener checklist",report.toString()));
            detail.setText("점검 결과를 복사했어요. 개인정보/방송 ID/음원은 포함하지 않았습니다.");
        }
    }
    private void reset() {
        resetting=true;
        for(CheckBox b:boxes) b.setChecked(false);
        prefs.edit().clear().apply();resetting=false;
        refresh();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35){
            scroll.setOnApplyWindowInsetsListener((v,insets)->{
                Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                v.setPadding(0,safe.top,0,safe.bottom);
                return insets;
            });
        }
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17),dp(12),dp(17),dp(30));scroll.addView(root);
        root.addView(text("🎧 BIGO 실제 청취자 음향 검사",26,PURPLE));
        root.addView(text("MARU RADIO 앱에서 음악이 들리는 것과 BIGO 청취자에게 소리가 전달되는 것은 다릅니다.",15,WHITE));

        LinearLayout before=card(root);
        before.addView(text("① 방송 전 준비",20,PURPLE));
        before.addView(text("1. MARU RADIO에서 진행자 대화를 켜고 Suno 자작곡을 한 곡 이상 준비합니다.\n"
                +"2. BIGO LIVE를 열어 오디오라이브를 직접 시작합니다.\n"
                +"3. BIGO 방송 도구의 믹서, 마이크 1, 음악 입력을 확인합니다(메뉴는 앱 버전에 따라 달라질 수 있습니다).\n"
                +"4. 별도 휴대폰에서 청취자로 입장해 음악과 말소리를 직접 확인합니다.",15,WHITE));
        before.addView(button("📻 MARU RADIO 준비 화면",this::openRadio));
        before.addView(button("▶ BIGO LIVE 열기 (시작은 수동)",this::openBigo));

        LinearLayout check=card(root);
        check.addView(text("② 별도 청취 기기로 실제 수신 확인",20,PURPLE));
        check.addView(text("체크는 실제로 두 번째 기기에서 소리를 듣고 확인했을 때만 선택하세요. 이 앱이 BIGO 마이크 입력 또는 방송 상태를 읽는 것은 아닙니다.",13,0xFFFFDDB8));
        String[] descriptions={
            "별도 휴대폰에서 해당 BIGO 방송에 청취자로 입장했다.",
            "BIGO 오디오라이브가 시작된 것을 방송 화면과 청취자로 확인했다.",
            "별도 청취 기기에서 MARU 진행자의 첫 멘트가 들렸다.",
            "별도 청취 기기에서 Suno 자작곡의 보컬과 음악이 들렸다.",
            "첫 곡이 끝나 다음 곡으로 넘어간 뒤에도 소리가 들렸다."
        };
        for(int i=0;i<boxes.length;i++){
            final int n=i;
            CheckBox b=new CheckBox(this);
            b.setText(descriptions[i]);b.setTextColor(WHITE);
            b.setTextSize(15);b.setPadding(dp(6),dp(8),dp(4),dp(8));
            b.setChecked(prefs.getBoolean("check"+i,false));
            check.addView(b);boxes[n]=b;
            b.setOnCheckedChangeListener((buttonView,isChecked)->save());
        }
        result=text("",17,PURPLE);check.addView(result);
        detail=text("",15,WHITE);check.addView(detail);
        timestamp=text("",12,0xFFBEAEDA);check.addView(timestamp);
        check.addView(button("📋 점검 결과 복사",this::copyRecord));
        check.addView(button("↺ 새 테스트로 초기화",this::reset));

        LinearLayout help=card(root);
        help.addView(text("③ 소리가 안 들릴 때",20,PURPLE));
        help.addView(text("• 진행자·노래가 모두 안 들리면 BIGO가 외부 앱 음성을 입력으로 받고 있는지부터 확인하세요. 휴대폰 스피커에서만 소리가 나면 청취자는 듣지 못할 수 있습니다.\n"
                +"• 진행자만 들리면 음악 입력·믹서 설정을, 음악만 들리면 기기 TTS와 언어 음성 엔진을 확인하세요.\n"
                +"• 이어지는 곡이 끊기면 MARU의 다음 곡·오디오 포커스·백그라운드 설정을 검증하세요.\n"
                +"• BIGO 믹서로 타 앱 오디오를 받을 수 없다면 공식 지원 기능이나 호환되는 외부 오디오 장비가 필요할 수 있습니다. 임의 접근성/녹음 권한을 켜지 않습니다.\n"
                +"• BIGO의 실제 방송 시작/종료는 앱에서 직접 확인해야 합니다.",14,WHITE));
        help.addView(button("← MARU RADIO 돌아가기",this::openRadio));
        setContentView(scroll);
        refresh();
    }
}