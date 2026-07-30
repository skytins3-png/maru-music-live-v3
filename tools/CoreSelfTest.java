import com.maru.musiclive.*;
import java.util.*;

public final class CoreSelfTest {
    private static int checks;
    private static void ok(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        BigoJoinParser parser = new BigoJoinParser();
        List<JoinEvent> ko = parser.parseAll("봄날 님이 입장했습니다");
        ok(ko.size() == 1, "ko size");
        ok("봄날".equals(ko.get(0).nickname), "ko name");
        List<JoinEvent> spaced = parser.parseAll("테스트청취자 님이 입 장 했 습 니 다");
        ok(spaced.size() == 1, "ocr spaced size");
        ok("테스트청취자".equals(spaced.get(0).nickname), "ocr spaced name");
        List<JoinEvent> room = parser.parseAll("하늘별 님이 라이브 방송에 입장했습니다");
        ok(room.size() == 1, "room size");
        ok("하늘별".equals(room.get(0).nickname), "room name");
        List<JoinEvent> zh = parser.parseAll("小雨进入了你的直播间");
        ok(zh.size() == 1, "zh size");
        ok("小雨".equals(zh.get(0).nickname), "zh name");
        List<JoinEvent> en = parser.parseAll("Blue Moon joined the live");
        ok(en.size() == 1, "en size");
        ok("Blue Moon".equals(en.get(0).nickname), "en name");
        ok(parser.parseAll("오늘도 좋은 음악과 함께하세요").isEmpty(), "no false positive");
        ok(parser.parseAll("Alice: 게임 좋아하세요?").isEmpty(), "question comment ignored");
        ok(parser.parseAll("Alice: I joined yesterday").isEmpty(), "ordinary joined comment ignored");
        ok(parser.parseAll("Alice\nI joined yesterday").isEmpty(),
                "split ordinary joined comment ignored");
        ok(parser.parseAll("Alice\n게임 좋아하세요?").isEmpty(),
                "split question comment ignored");
        List<JoinEvent> isolatedJoin = parser.parseAll(
                "Alice: 게임 좋아하세요?\n봄날 님이 입장했습니다");
        ok(isolatedJoin.size() == 1, "comment plus join size");
        ok("봄날".equals(isolatedJoin.get(0).nickname), "comment not nickname");
        ok("dQw4w9WgXcQ".equals(YoutubeUrlParser.videoId(
                "https://youtu.be/dQw4w9WgXcQ?t=5")), "youtube short");
        ok("dQw4w9WgXcQ".equals(YoutubeUrlParser.videoId(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ")), "youtube watch");
        ok(YoutubeUrlParser.videoId("invalid").isEmpty(), "youtube invalid");
        SeenCache seen = new SeenCache(20);
        ok(seen.markIfNew("a", 1000, 60000), "seen first");
        ok(!seen.markIfNew("a", 2000, 60000), "seen duplicate");
        ok(seen.markIfNew("a", 62000, 60000), "seen expiry");
        VolumeDucking duck = new VolumeDucking();
        duck.setGuidance(0.18f);
        ok(Math.abs(duck.effective() - 0.18f) < 0.001f, "duck");
        duck.setGuidance(1f);
        ok(Math.abs(duck.effective() - 1f) < 0.001f, "restore");
        ok(GreetingLanguage.KOREAN.equals(
                GreetingLanguage.normalize("ko-KR")), "greeting ko");
        ok(GreetingLanguage.ENGLISH.equals(
                GreetingLanguage.normalize("en-US")), "greeting en");
        ok(GreetingLanguage.CHINESE.equals(
                GreetingLanguage.normalize("zh-CN")), "greeting zh");
        ok(GreetingLanguage.JAPANESE.equals(
                GreetingLanguage.normalize("ja-JP")), "greeting ja");
        ok(GreetingLanguage.ENGLISH.equals(
                GreetingLanguage.normalize("vi")), "greeting fallback");
        ok("영어 음성".equals(
                GreetingLanguage.koreanLabel("en")), "greeting label");
        ok(GreetingLanguage.KOREAN.equals(
                GreetingLanguage.detectFromText("별빛 아래서")), "title ko");
        ok(GreetingLanguage.JAPANESE.equals(
                GreetingLanguage.detectFromText("さくら")), "title ja");
        ok(TtsAnnouncementText.greeting(
                "John", "en").contains("welcome"), "tts en");
        ok(TtsAnnouncementText.greeting(
                "小雨", "zh").contains("欢迎"), "tts zh");
        ok(TtsAnnouncementText.greeting(
                "さくら", "ja").contains("いらっしゃいませ"), "tts ja");
        ok("별빛 아래서".equals(
                SongTitleFormatter.clean(
                        "01 - 별빛_아래서.mp3")), "song title clean");
        List<JoinEvent> noSpace =
                parser.parseAll("홍길동님이 방에 들어왔습니다");
        ok(noSpace.size() == 1, "ko no space");
        ok("홍길동".equals(
                noSpace.get(0).nickname), "ko no space name");
        List<JoinEvent> split =
                parser.parseAll("Blue Moon\njoined the live");
        ok(split.size() == 1, "en split");
        List<LyricsCore.Line> finalLine =
                Collections.singletonList(
                        new LyricsCore.Line(
                                25_000L,
                                "마지막 줄"));
        ok("마지막 줄".equals(
                LyricsCore.twoLines(
                        finalLine,
                        30_000L)), "lyric visible");
        ok(LyricsCore.twoLines(
                finalLine,
                32_000L).isEmpty(), "lyric expires");
        BigoEventParser eventParser = new BigoEventParser();
        ok(eventParser.parseLine("민지 라이브에 입장하였습니다").type == EventType.JOIN,
                "event join current BIGO");
        ok(eventParser.parseLine("민지님이 좋아요를 눌렀습니다").type == EventType.LIKE,
                "event like");
        ok(eventParser.parseLine("민지님이 선물을 보냈습니다 Rose x10").type == EventType.GIFT,
                "event gift");
        ok(eventParser.parseLine("민지님이 팔로우했습니다").type == EventType.FOLLOW,
                "event follow");
        ok(eventParser.parseLine("Alice: liked the live") == null,
                "viewer chat not system like");
        ok(eventParser.parseLine("I liked the live") == null,
                "pronoun comment not system like");
        ok("민지님, 좋아요 감사합니다".equals(
                EventOverlayText.format(new LiveEvent(
                        EventType.LIKE, "민지", "", "", "ko", 1L))),
                "small overlay text");
        ok(!EventOverlayText.format(new LiveEvent(
                EventType.GIFT, "민지", "Rose x10", "", "ko", 1L)).contains("\n"),
                "overlay one line");
        ok(EventType.LIKE.overlayDurationMs == 2_500L, "like duration");
        ok(EventType.GIFT.overlayDurationMs == 4_000L, "gift duration");
        ok(EventType.FOLLOW.overlayDurationMs == 3_000L, "follow duration");
        ok(eventParser.parseLine("... 라이브에 입장하였습니다") == null,
                "punctuation nickname rejected");
        ok(GreetingLanguage.SPANISH.equals(
                GreetingLanguage.detectFromText("hola gracias")), "language es");
        ok(GreetingLanguage.RUSSIAN.equals(
                GreetingLanguage.detectFromText("Привет")), "language ru");
        ok(TtsAnnouncementText.event(
                EventType.GIFT, "Ana", "Rose", "es").contains("regalo"), "gift tts es");
        ok(ConversationEngine.reply(
                "John", "hello", "en").shouldSpeak(), "conversation known");
        ok(!ConversationEngine.reply(
                "John", "zxqv 123", "en").shouldSpeak(), "conversation unknown safe");
        LiveEventCooldown cooldown = new LiveEventCooldown();
        LiveEvent like = new LiveEvent(
                EventType.LIKE, "A", "", "", "en", 1L);
        ok(cooldown.markIfNew(like, 1_000L), "like first");
        ok(!cooldown.markIfNew(like, 2_000L), "like cooldown");
        ok(Arrays.equals(
                new String[]{"ko", "en", "zh", "ja", "ru"},
                IntermissionAnnouncementText.orderedLanguages()),
                "intermission fixed five-language order");
        List<String> gifts = Arrays.asList("민지", "Sky");
        List<String> follows = Collections.singletonList("小雨");
        String interKo = IntermissionAnnouncementText.build("별빛 아래서", "ko", gifts, follows);
        ok(interKo.contains("오신 분들 모두 환영합니다"), "intermission ko welcome");
        ok(interKo.contains("좋아요 한 번 눌러 주세요"), "intermission ko like");
        ok(interKo.contains("선물 감사합니다"), "intermission ko gift");
        ok(interKo.contains("팔로우 감사합니다"), "intermission ko follow");
        ok(interKo.contains("다음 노래는 별빛 아래서입니다"), "intermission ko title");
        String interEn = IntermissionAnnouncementText.build("Starlight", "en", gifts, follows);
        ok(interEn.contains("Welcome, everyone"), "intermission en welcome");
        ok(interEn.contains("thank you for the gifts"), "intermission en gift");
        String interZh = IntermissionAnnouncementText.build("星光之下", "zh", gifts, follows);
        ok(interZh.contains("欢迎来到直播间"), "intermission zh welcome");
        ok(interZh.contains("下一首歌是《星光之下》"), "intermission zh title");
        String interJa = IntermissionAnnouncementText.build("星空の下で", "ja", gifts, follows);
        ok(interJa.contains("皆さん、ようこそ"), "intermission ja welcome");
        ok(interJa.contains("次の曲は「星空の下で」"), "intermission ja title");
        String interRu = IntermissionAnnouncementText.build("Под звёздами", "ru", gifts, follows);
        ok(interRu.contains("Добро пожаловать на трансляцию"), "intermission ru welcome");
        ok(interRu.contains("Следующая песня — «Под звёздами»"), "intermission ru title");
        ok(!interKo.contains("\n") && !interEn.contains("\n")
                && !interZh.contains("\n") && !interJa.contains("\n")
                && !interRu.contains("\n"),
                "intermission single line");
        System.out.println("CORE-SELF-TEST: " + checks + "/" + checks);
    }
}
