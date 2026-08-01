package com.maru.musiclive;

import java.text.Normalizer;
import java.util.Locale;

public final class ConversationEngine {
    public static final class Reply {
        public final ConversationIntent intent;
        public final String text;
        public final String language;
        public final float confidence;

        Reply(ConversationIntent intent, String text, String language, float confidence) {
            this.intent = intent;
            this.text = text == null ? "" : text;
            this.language = GreetingLanguage.normalize(language);
            this.confidence = confidence;
        }

        public boolean shouldSpeak() {
            return intent != ConversationIntent.UNKNOWN && !text.isEmpty() && confidence >= 0.60f;
        }
    }

    private ConversationEngine() {}

    public static Reply reply(String nickname, String message, String language) {
        String lang = GreetingLanguage.normalize(language);
        ConversationIntent intent = classify(message, lang);
        String name = nickname == null || nickname.trim().isEmpty() ? "" : nickname.trim();
        return new Reply(intent, response(intent, name, lang, 1), lang,
                intent == ConversationIntent.UNKNOWN ? 0.20f : 0.82f);
    }

    /**
     * Fixed safety policy for song requests. Learning can improve language,
     * recognition and repeat-aware wording, but it can never change this
     * broadcast from original-songs-only into accepting requests.
     */
    public static Reply songRequestRefusal(
            String nickname,
            String language,
            int requestCount) {
        String lang = GreetingLanguage.normalize(language);
        String name = nickname == null || nickname.trim().isEmpty()
                ? "" : nickname.trim();
        return new Reply(
                ConversationIntent.SONG_REQUEST,
                response(ConversationIntent.SONG_REQUEST, name, lang,
                        Math.max(1, requestCount)),
                lang,
                0.96f);
    }

    public static ConversationIntent classify(String message, String language) {
        if (message == null) return ConversationIntent.UNKNOWN;
        String text = strip(message).toLowerCase(Locale.ROOT);
        if (containsAny(text,
                "hello", "hi", "hey", "안녕", "반가", "你好", "您好", "こんにちは", "こんばんは",
                "hola", "bonjour", "hallo", "ciao", "ola", "привет")) return ConversationIntent.HELLO;
        if (containsAny(text,
                "how are you", "잘 지내", "어떻게 지내", "你好吗", "元気", "como estas", "comment ca va",
                "wie geht", "come stai", "como voce", "как дела")) return ConversationIntent.HOW_ARE_YOU;
        if (containsAny(text,
                "thank", "thanks", "고마", "감사", "谢谢", "ありがとう", "gracias", "merci", "danke",
                "grazie", "obrig", "спасибо")) return ConversationIntent.THANKS;
        if (containsAny(text,
                "good song", "nice song", "love this", "노래 좋", "음악 좋", "好听", "いい曲", "buena cancion",
                "belle chanson", "gutes lied", "bella canzone", "boa musica", "хорошая песня")) return ConversationIntent.PRAISE;
        if (containsAny(text,
                "play ", "please play", "can you play", "play me", "song request", "request song",
                "틀어", "들려줘", "들려 주세요", "신청곡", "곡 요청", "노래 신청",
                "播放", "点歌", "放一首", "リクエスト", "曲をかけて", "流して",
                "pon la cancion", "joue ", "spiel ", "metti ", "toque ", "включи"))
            return ConversationIntent.SONG_REQUEST;
        if (containsAny(text,
                "bye", "good night", "잘가", "안녕히", "再见", "晚安", "またね", "adios", "au revoir",
                "tschuss", "arrivederci", "tchau", "пока")) return ConversationIntent.GOODBYE;
        return ConversationIntent.UNKNOWN;
    }

    private static String response(ConversationIntent intent, String name, String lang, int requestCount) {
        String prefix = name.isEmpty() ? "" : name;
        switch (lang) {
            case GreetingLanguage.KOREAN:
                switch (intent) {
                    case HELLO: return prefix + "님, 반가워요. 오늘도 편안하게 음악 듣고 가세요.";
                    case HOW_ARE_YOU: return "저는 좋은 음악과 함께 잘 지내고 있어요. " + prefix + "님도 좋은 시간 보내세요.";
                    case THANKS: return prefix + "님, 제가 더 고마워요.";
                    case PRAISE: return prefix + "님, 노래를 좋아해 주셔서 감사합니다.";
                    case SONG_REQUEST:
                        return requestCount > 1
                                ? prefix + "님, 앞서 안내드린 것처럼 이 방송은 진행자가 직접 만든 자작곡만 소개해서 신청곡은 받지 않습니다. 이해해 주셔서 감사합니다."
                                : prefix + "님, 신청해 주셔서 감사합니다. 이 방송은 진행자가 직접 만든 자작곡만 들려드리는 방송이라 신청곡은 받지 않습니다. 편하게 자작곡을 함께 들어 주세요.";
                    case GOODBYE: return prefix + "님, 함께해 주셔서 감사합니다. 다음에 또 만나요.";
                    default: return "";
                }
            case GreetingLanguage.CHINESE:
                switch (intent) {
                    case HELLO: return prefix + "，你好，欢迎来到直播间，请轻松享受音乐。";
                    case HOW_ARE_YOU: return "我很好，正在和大家一起听音乐。也祝你今天愉快。";
                    case THANKS: return prefix + "，我也非常感谢你。";
                    case PRAISE: return prefix + "，谢谢你喜欢这首歌。";
                    case SONG_REQUEST:
                        return requestCount > 1
                                ? prefix + "，正如刚才说明的，这里只播放主播原创歌曲，因此不接受点歌。谢谢理解。"
                                : prefix + "，谢谢你的点歌。不过这里专门播放主播原创歌曲，因此不接受点歌。感谢理解，也请继续欣赏原创音乐。";
                    case GOODBYE: return prefix + "，谢谢你的陪伴，下次再见。";
                    default: return "";
                }
            case GreetingLanguage.JAPANESE:
                switch (intent) {
                    case HELLO: return prefix + "さん、こんにちは。どうぞゆっくり音楽を楽しんでください。";
                    case HOW_ARE_YOU: return "元気です。みなさんと一緒に音楽を楽しんでいます。";
                    case THANKS: return prefix + "さん、こちらこそありがとうございます。";
                    case PRAISE: return prefix + "さん、曲を気に入ってくれてありがとうございます。";
                    case SONG_REQUEST:
                        return requestCount > 1
                                ? prefix + "さん、先ほどご案内した通り、この配信は配信者のオリジナル曲だけを紹介しているため、リクエストは受け付けていません。ご理解ありがとうございます。"
                                : prefix + "さん、リクエストありがとうございます。この配信は配信者が作ったオリジナル曲だけをお届けしているため、リクエストは受け付けていません。オリジナル曲をゆっくりお楽しみください。";
                    case GOODBYE: return prefix + "さん、来てくれてありがとうございました。また会いましょう。";
                    default: return "";
                }
            case GreetingLanguage.SPANISH:
                return latinResponse(intent, prefix, requestCount,
                        "Hola, bienvenido. Relájate y disfruta de la música.",
                        "Estoy muy bien, disfrutando de la música con todos.",
                        "Muchas gracias a ti.",
                        "Gracias por disfrutar de la canción.",
                        "Gracias por la sugerencia, pero esta transmisión presenta solo canciones originales del anfitrión y no acepta solicitudes.",
                        "Gracias por acompañarnos. Hasta la próxima.");
            case GreetingLanguage.FRENCH:
                return latinResponse(intent, prefix, requestCount,
                        "Bonjour et bienvenue. Détendez-vous et profitez de la musique.",
                        "Je vais très bien et je profite de la musique avec tout le monde.",
                        "Merci beaucoup à vous aussi.",
                        "Merci d'apprécier cette chanson.",
                        "Merci pour la suggestion, mais cette émission diffuse uniquement les chansons originales de l’hôte et n’accepte pas les demandes.",
                        "Merci d'être venu. À la prochaine fois.");
            case GreetingLanguage.GERMAN:
                return latinResponse(intent, prefix, requestCount,
                        "Hallo und willkommen. Entspann dich und genieße die Musik.",
                        "Mir geht es gut. Ich genieße die Musik mit allen zusammen.",
                        "Vielen Dank auch an dich.",
                        "Danke, dass dir das Lied gefällt.",
                        "Danke für den Wunsch. Diese Sendung spielt nur eigene Lieder des Gastgebers und nimmt keine Musikwünsche an.",
                        "Danke fürs Zuschauen. Bis zum nächsten Mal.");
            case GreetingLanguage.ITALIAN:
                return latinResponse(intent, prefix, requestCount,
                        "Ciao e benvenuto. Rilassati e goditi la musica.",
                        "Sto molto bene e mi godo la musica con tutti.",
                        "Grazie mille anche a te.",
                        "Grazie per aver apprezzato la canzone.",
                        "Grazie per la richiesta. Questa diretta presenta solo brani originali dell’host e non accetta richieste musicali.",
                        "Grazie per essere stato con noi. Alla prossima.");
            case GreetingLanguage.PORTUGUESE:
                return latinResponse(intent, prefix, requestCount,
                        "Olá e bem-vindo. Relaxe e aproveite a música.",
                        "Estou muito bem, curtindo a música com todos.",
                        "Muito obrigado também.",
                        "Obrigado por gostar da música.",
                        "Obrigado pelo pedido. Esta transmissão toca apenas músicas autorais do anfitrião e não aceita pedidos de músicas.",
                        "Obrigado pela companhia. Até a próxima.");
            case GreetingLanguage.RUSSIAN:
                return latinResponse(intent, prefix, requestCount,
                        "Привет и добро пожаловать. Расслабьтесь и наслаждайтесь музыкой.",
                        "У меня всё хорошо, я слушаю музыку вместе со всеми.",
                        "Большое спасибо и вам.",
                        "Спасибо, что вам понравилась песня.",
                        "Спасибо за просьбу. В этой трансляции звучат только авторские песни ведущего, поэтому заявки на другие песни не принимаются.",
                        "Спасибо, что были с нами. До следующей встречи.");
            default:
                return latinResponse(intent, prefix, requestCount,
                        "Hello and welcome. Please relax and enjoy the music.",
                        "I am doing well and enjoying the music with everyone.",
                        "Thank you very much too.",
                        "Thank you for enjoying the song.",
                        "Thank you for the request. This broadcast plays only the host’s original songs, so song requests are not accepted. Please enjoy the original music.",
                        "Thank you for spending time with us. See you again.");
        }
    }

    private static String latinResponse(
            ConversationIntent intent,
            String name,
            int requestCount,
            String hello,
            String how,
            String thanks,
            String praise,
            String request,
            String goodbye) {
        String prefix = name.isEmpty() ? "" : name + ", ";
        switch (intent) {
            case HELLO: return prefix + hello;
            case HOW_ARE_YOU: return prefix + how;
            case THANKS: return prefix + thanks;
            case PRAISE: return prefix + praise;
            case SONG_REQUEST: return prefix + request;
            case GOODBYE: return prefix + goodbye;
            default: return "";
        }
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(strip(value).toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static String strip(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").trim();
    }
}
