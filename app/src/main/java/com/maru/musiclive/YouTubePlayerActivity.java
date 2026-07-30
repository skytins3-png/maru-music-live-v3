package com.maru.musiclive;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;


public final class YouTubePlayerActivity extends ComponentActivity {
    private WebView webView;
    private EditText input;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(14));
        root.setBackgroundColor(Color.rgb(16, 16, 24));

        TextView title = new TextView(this);
        title.setText("YouTube 공식 연결");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView guide = new TextView(this);
        guide.setText(
                "YouTube 링크를 붙여 넣으면 공식 내장 플레이어로 재생합니다.\n"
                        + "영상·음원을 다운로드하거나 MP3로 가져오지는 않습니다.");
        guide.setTextSize(15);
        guide.setTextColor(Color.LTGRAY);
        guide.setPadding(0, dp(8), 0, dp(8));
        root.addView(guide, matchWrap());

        input = new EditText(this);
        input.setHint("YouTube 링크 또는 검색어");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button play = new Button(this);
        play.setText("링크 재생");
        play.setAllCaps(false);
        play.setOnClickListener(v -> loadLink());
        buttons.addView(play, weighted());

        Button search = new Button(this);
        search.setText("YouTube 검색");
        search.setAllCaps(false);
        search.setOnClickListener(v -> openSearch());
        buttons.addView(search, weighted());
        root.addView(buttons, matchWrap());

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        setContentView(root);
    }

    private void loadLink() {
        String id = YoutubeUrlParser.videoId(input.getText().toString());
        if (id.isEmpty()) {
            toast("정상적인 YouTube 영상 링크를 붙여 넣으세요.");
            return;
        }
        String url = "https://www.youtube.com/embed/" + id
                + "?playsinline=1&controls=1&rel=0";
        webView.loadUrl(url);
    }

    private void openSearch() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) query = "음악";
        String url = "https://www.youtube.com/results?search_query="
                + Uri.encode(query);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            toast("YouTube 또는 웹브라우저를 열 수 없습니다.");
        }
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(52), 1f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
