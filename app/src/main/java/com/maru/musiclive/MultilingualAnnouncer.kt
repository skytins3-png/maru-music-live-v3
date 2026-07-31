package com.maru.musiclive

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Android 표준 TextToSpeech API만 사용합니다.
 * 접근성 서비스나 다른 앱 화면 읽기는 전혀 필요 없습니다.
 *
 * 곡 제목은 매번 바뀌므로 사전 녹음 대신 TTS로 실시간 생성합니다.
 */
class MultilingualAnnouncer(context: Context) {

    private data class LangPhrase(val locale: Locale, val template: (String) -> String)

    private val languages = listOf(
        LangPhrase(Locale.KOREAN) { title -> "다음 곡은 $title 입니다." },
        LangPhrase(Locale.US) { title -> "The next song is $title." },
        LangPhrase(Locale.SIMPLIFIED_CHINESE) { title -> "下一首歌是 $title。" },
        LangPhrase(Locale.JAPANESE) { title -> "次の曲は $title です。" },
        LangPhrase(Locale("ru", "RU")) { title -> "Следующая песня: $title." }
    )

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
        }
    }

    fun announceNextSong(songTitle: String, onDone: () -> Unit) {
        if (!isReady || tts == null) {
            onDone()
            return
        }
        speakQueue(songTitle, index = 0, onAllDone = onDone)
    }

    private fun speakQueue(songTitle: String, index: Int, onAllDone: () -> Unit) {
        if (index >= languages.size) {
            onAllDone()
            return
        }
        val phrase = languages[index]
        val engine = tts ?: run { onAllDone(); return }

        engine.language = phrase.locale
        val utteranceId = UUID.randomUUID().toString()

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                speakQueue(songTitle, index + 1, onAllDone)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                speakQueue(songTitle, index + 1, onAllDone)
            }
        })

        engine.speak(phrase.template(songTitle), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
