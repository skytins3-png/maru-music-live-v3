package com.maru.radio;

/**
 * Local response cards: nothing is sent to BIGO or an external server.
 * The host can inspect and manually copy a line for a live comment.
 */
public final class RadioReplies {
    public static final String[] LANGUAGES = {"한국어", "中文", "English", "日本語", "Русский"};
    public static final String[] EVENTS = {"환영 인사", "댓글 답변", "선물 감사", "팔로우 감사", "다음 노래"};
    private static final String[][] TEXTS = {
        {
            "어서 오세요! 오늘도 좋은 음악과 함께해요. 🌹",
            "반가워요! 댓글 고마워요. 어떤 노래 좋아하세요?",
            "장미 한 송이도 큰 응원이에요. 선물 감사합니다! 🌹",
            "팔로우 감사합니다! 언제든 편하게 놀러 오세요.",
            "다음 곡 이어집니다. 즐겁게 들어 주세요! 🎵"
        }, {
            "欢迎来到直播间！一起听好音乐吧。🌹",
            "谢谢你的留言！你喜欢听什么样的歌？",
            "谢谢你的礼物！一朵玫瑰也让直播间更温暖。🌹",
            "感谢关注！欢迎常来听歌。",
            "下一首歌马上开始，祝你听得开心！🎵"
        }, {
            "Welcome in! Enjoy the music with us. 🌹",
            "Thanks for your comment! What kind of music do you like?",
            "Thank you for the gift! Every rose means a lot. 🌹",
            "Thanks for following! Come back anytime.",
            "Up next: another song. Enjoy! 🎵"
        }, {
            "ようこそ！今日も一緒に音楽を楽しみましょう。🌹",
            "コメントありがとう！どんな音楽が好きですか？",
            "プレゼントありがとう！とてもうれしいです。🌹",
            "フォローありがとうございます！また遊びに来てね。",
            "次の曲が始まります。お楽しみに！🎵"
        }, {
            "Добро пожаловать! Давайте слушать хорошую музыку. 🌹",
            "Спасибо за комментарий! Какую музыку вы любите?",
            "Спасибо за подарок! Ваша поддержка очень важна. 🌹",
            "Спасибо за подписку! Заходите ещё.",
            "Сейчас прозвучит следующая песня. Приятного прослушивания! 🎵"
        }
    };
    private RadioReplies() {}

    public static String message(int language, int event) {
        if (language < 0 || language >= TEXTS.length) language = 0;
        if (event < 0 || event >= TEXTS[language].length) event = 0;
        return TEXTS[language][event];
    }
}
