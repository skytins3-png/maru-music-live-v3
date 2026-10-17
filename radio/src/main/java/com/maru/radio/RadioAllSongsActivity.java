package com.maru.radio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen, independently scrolling catalog of ALL MARU-imported originals.
 * Unlike the broadcaster's playlist filter, default view never restricts by
 * current scheduled genre, search, or duplicate-title filter.
 * The source downloads are not read/mutated by listing the local catalog.
 */
public final class RadioAllSongsActivity extends Activity {
    private static final int BG = 0xFF110E21;
    private static final int WHITE = 0xFFF7F1FF;
    private final List<Originals.Track> all = new ArrayList<>();
    private final List<Originals.Track> visible = new ArrayList<>();
    private TextView summary;
    private EditText search;
    private BaseAdapter adapter;
    private int genre = 0; // all genres regardless of current broadcasting schedule
    private int ordering = 0; // most recent, title, oldest
    private Button genreButton;

    private int dp(int n) {
        return (int) (getResources().getDisplayMetrics().density * n + .5f);
    }

    private TextView label(String str, int sp) {
        TextView text = new TextView(this);
        text.setText(str);
        text.setTextSize(sp);
        text.setTextColor(WHITE);
        text.setPadding(dp(9), dp(7), dp(9), dp(7));
        return text;
    }

    private Button button(String title, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(14);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(BG);
        screen.setPadding(dp(10), dp(22), dp(10), dp(15));
        setContentView(screen);

        screen.addView(label("📋 보관 원곡 전체 목록", 24));
        screen.addView(button("☰ 전체 기능 목록 · 예약·방송·아환 등 모든 메뉴",
                () -> startActivity(new Intent(this, RadioFeatureMenuActivity.class))));

        // Keep the AI presenter reachable from the full-song library;
        // it is a broadcast function, not a music track/category.
        screen.addView(button("🎙 AI 아나운서 아환 · 방송 멘트/댓글/신청곡",
                () -> startActivity(new Intent(this, RadioDjActivity.class))));

        screen.addView(label("MARU에 등록된 모든 곡을 표시합니다. 방송 장르 선택과 무관하며 파일 원본은 변경되지 않습니다.", 13));
        summary = label("목록 불러오는 중", 16);
        screen.addView(summary);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(0xFFA79BB8);
        search.setHint("전체 목록에서 제목 검색");
        search.setTextSize(16);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateVisible();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        screen.addView(search);

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.HORIZONTAL);
        genreButton = button("분류: 전체", this::chooseGenre);
        filters.addView(genreButton, new LinearLayout.LayoutParams(0, dp(54), 1.2f));
        filters.addView(button("정렬", this::chooseSort),
                new LinearLayout.LayoutParams(0, dp(54), .7f));
        filters.addView(button("전체 보기", this::clearFilter),
                new LinearLayout.LayoutParams(0, dp(54), 1f));
        screen.addView(filters);

        ListView songs = new ListView(this);
        songs.setBackgroundColor(0xFF19152D);
        songs.setDividerHeight(dp(2));
        songs.setFastScrollEnabled(true);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int pos) { return visible.get(pos); }
            @Override public long getItemId(int pos) { return pos; }

            @Override public View getView(int pos, View converted, ViewGroup parent) {
                final Originals.Track track = visible.get(pos);
                LinearLayout row = new LinearLayout(RadioAllSongsActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(6), dp(8), dp(6));

                LinearLayout details = new LinearLayout(RadioAllSongsActivity.this);
                details.setOrientation(LinearLayout.VERTICAL);
                TextView title = label((pos + 1) + ". " + track.title, 16);
                // Never ellipsize: long Korean/Chinese/Suno titles wrap naturally.
                title.setMaxLines(Integer.MAX_VALUE);
                details.addView(title);
                int category = RadioProgram.category(RadioAllSongsActivity.this, track);
                TextView sub = label(RadioProgram.NAMES[category], 12);
                sub.setTextColor(0xFFB89BFF);
                details.addView(sub);
                row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                row.addView(button("▶", () -> play(track)),
                        new LinearLayout.LayoutParams(dp(55), dp(58)));
                row.setOnClickListener(v -> play(track));
                return row;
            }
        };
        songs.setAdapter(adapter);
        screen.addView(songs, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout controls = new LinearLayout(this);
        controls.addView(button("↻ 새로고침", this::reload),
                new LinearLayout.LayoutParams(0, dp(53), 1f));
        controls.addView(button("← 돌아가기", this::finish),
                new LinearLayout.LayoutParams(0, dp(53), 1f));
        screen.addView(controls);

        reload();
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null) reload();
    }

    private void reload() {
        all.clear();
        all.addAll(Originals.list(this));
        updateVisible();
    }

    private void updateVisible() {
        if (adapter == null) return;
        visible.clear();
        List<Originals.Track> ordered = new ArrayList<>(all);
        if (ordering == 0) Collections.reverse(ordered);
        else if (ordering == 1) ordered.sort((a, b) -> a.title.compareToIgnoreCase(b.title));
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (Originals.Track item : ordered) {
            if (genre != 0 && RadioProgram.category(this, item) != genre) continue;
            if (!item.title.toLowerCase(Locale.ROOT).contains(query)) continue;
            visible.add(item);
        }
        summary.setText("등록 원곡 " + all.size() + "곡 · 화면에 " + visible.size() + "곡 표시"
                + (visible.isEmpty() && all.isEmpty() ? "\n등록곡이 없습니다. 메인 화면에서 Suno 파일을 가져오세요." : ""));
        adapter.notifyDataSetChanged();
    }

    private void clearFilter() {
        genre = 0;
        ordering = 0;
        genreButton.setText("분류: 전체");
        search.setText("");
        updateVisible();
    }

    private void chooseGenre() {
        final String[] names = {"전체", "한국노래", "중국노래", "서양노래", "연주곡"};
        new AlertDialog.Builder(this).setTitle("목록 분류 보기")
                .setSingleChoiceItems(names, genre, (dialog, chosen) -> {
                    genre = chosen;
                    genreButton.setText("분류: " + names[chosen]);
                    dialog.dismiss();
                    updateVisible();
                }).setNegativeButton("닫기", null).show();
    }

    private void chooseSort() {
        final String[] choices = {"최근 추가순", "제목순", "오래된 순"};
        new AlertDialog.Builder(this).setTitle("전체 목록 정렬")
                .setSingleChoiceItems(choices, ordering, (dialog, chosen) -> {
                    ordering = chosen;
                    dialog.dismiss();
                    updateVisible();
                }).setNegativeButton("닫기", null).show();
    }

    private void play(Originals.Track track) {
        Intent play = new Intent(this, RadioPlaybackService.class)
                .setAction(RadioPlaybackService.ACTION_PLAY)
                .putExtra(RadioPlaybackService.EXTRA_HASH, track.hash);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(play);
            else startService(play);
            summary.setText("재생 요청: " + track.title + "\n등록 "
                    + all.size() + "곡 · 표시 " + visible.size() + "곡");
        } catch (RuntimeException error) {
            new AlertDialog.Builder(this).setMessage("원곡을 재생하지 못했어요. 메인 화면에서 다시 재생해 주세요.")
                    .setPositiveButton("확인", null).show();
        }
    }
}
