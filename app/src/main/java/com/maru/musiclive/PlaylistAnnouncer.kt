package com.maru.musiclive

import android.content.Context
import android.media.MediaPlayer

/**
 * 곡이 끝날 때마다 호출됩니다.
 * 접근성 서비스, 화면 읽기, 다른 앱 이벤트 감지 없이
 * 순수하게 "내 앱 안에서 곡 재생이 끝났다"는 콜백에만 반응합니다.
 *
 * 사용 예:
 *   playlistAnnouncer.onSongCompleted(nextSongTitle = "봄날")
 */
class PlaylistAnnouncer(
    private val context: Context,
    private val announcer: MultilingualAnnouncer = MultilingualAnnouncer(context),
    private val onNextSongReady: () -> Unit
) {

    // 기존에 사용 중이던 고정 인사 오디오 (문제 없는 자산이라 재사용)
    // res/raw 에 default_male_greeting_ko.mp3, _en.mp3, _zh.mp3, _ja.mp3, _ru.mp3 형태로 배치
    private val fixedGreetingResByLang = mapOf(
        "ko" to R.raw.default_male_greeting_ko,
        "en" to R.raw.default_male_greeting_en,
        "zh" to R.raw.default_male_greeting_zh,
        "ja" to R.raw.default_male_greeting_ja,
        "ru" to R.raw.default_male_greeting_ru
    )

    fun onSongCompleted(nextSongTitle: String) {
        // 1) 고정 인사(선택) 재생 -> 2) 다음 곡 제목을 5개 언어로 TTS 안내 -> 3) 다음 곡 재생
        playFixedGreeting(index = 0) {
            announcer.announceNextSong(nextSongTitle) {
                onNextSongReady()
            }
        }
    }

    private fun playFixedGreeting(index: Int, onDone: () -> Unit) {
        val langs = fixedGreetingResByLang.keys.toList()
        if (index >= langs.size) {
            onDone()
            return
        }
        val resId = fixedGreetingResByLang[langs[index]] ?: run {
            playFixedGreeting(index + 1, onDone)
            return
        }
        val mp = MediaPlayer.create(context, resId)
        mp.setOnCompletionListener {
            it.release()
            playFixedGreeting(index + 1, onDone)
        }
        mp.start()
    }
}
