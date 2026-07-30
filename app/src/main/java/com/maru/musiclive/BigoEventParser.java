package com.maru.musiclive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BigoEventParser {
    private static final int MAX_NAME = 48;
    private static final int MAX_DETAIL = 60;

    private static final Rule[] RULES = {
            // Korean
            suffix(EventType.JOIN, "ko",
                    "님이 입장했습니다", "님이 입장하였습니다", "님이 입장",
                    " 라이브에 입장했습니다", " 라이브에 입장하였습니다",
                    " 라이브 방송에 입장했습니다", " 라이브 방송에 입장하였습니다",
                    " 방에 들어왔습니다", "입장했습니다", "입장하셨습니다"),
            suffix(EventType.LIKE, "ko", "님이 좋아요를 눌렀습니다", "님이 좋아요를 보냈습니다", "좋아요를 눌렀습니다", "좋아요했습니다"),
            suffix(EventType.FOLLOW, "ko", "님이 팔로우했습니다", "님이 팔로우하였습니다", "팔로우했습니다"),
            contains(EventType.GIFT, "ko", "님이 선물을 보냈습니다", "선물을 보냈습니다", "선물했습니다"),

            // English
            suffix(EventType.JOIN, "en", " joined the live", " entered the live", " joined"),
            suffix(EventType.LIKE, "en", " liked the live", " liked the stream", " sent likes", " liked"),
            suffix(EventType.FOLLOW, "en", " followed you", " started following", " followed"),
            contains(EventType.GIFT, "en", " sent a gift", " sent gift", " gifted "),

            // Chinese
            suffix(EventType.JOIN, "zh", "进入了直播间", "进入直播间", "进来了"),
            suffix(EventType.LIKE, "zh", "点赞了直播", "点了赞", "点赞"),
            suffix(EventType.FOLLOW, "zh", "关注了主播", "关注了你", "关注"),
            contains(EventType.GIFT, "zh", "送出了礼物", "赠送了礼物", "送出"),

            // Japanese
            suffix(EventType.JOIN, "ja", "さんが入室しました", "が入室しました", "参加しました"),
            suffix(EventType.LIKE, "ja", "さんがいいねしました", "いいねしました", "いいね"),
            suffix(EventType.FOLLOW, "ja", "さんがフォローしました", "フォローしました"),
            contains(EventType.GIFT, "ja", "さんがギフトを贈りました", "ギフトを贈りました", "ギフト"),

            // Spanish
            suffix(EventType.JOIN, "es", " se unió al directo", " entró al directo", " se unió"),
            suffix(EventType.LIKE, "es", " le gustó el directo", " dio me gusta", " le gustó"),
            suffix(EventType.FOLLOW, "es", " empezó a seguirte", " te siguió", " siguió"),
            contains(EventType.GIFT, "es", " envió un regalo", " mandó un regalo"),

            // French
            suffix(EventType.JOIN, "fr", " a rejoint le live", " a rejoint le direct", " a rejoint"),
            suffix(EventType.LIKE, "fr", " a aimé le live", " a aimé le direct", " a aimé"),
            suffix(EventType.FOLLOW, "fr", " s'est abonné", " vous suit", " a suivi"),
            contains(EventType.GIFT, "fr", " a envoyé un cadeau", " a offert un cadeau"),

            // German
            suffix(EventType.JOIN, "de", " ist dem live beigetreten", " ist beigetreten"),
            suffix(EventType.LIKE, "de", " gefällt der livestream", " hat den livestream geliked", " gefällt"),
            suffix(EventType.FOLLOW, "de", " folgt dir jetzt", " folgt dir", " hat abonniert"),
            contains(EventType.GIFT, "de", " hat ein geschenk gesendet", " sendete ein geschenk"),

            // Italian
            suffix(EventType.JOIN, "it", " è entrato nella live", " è entrata nella live", " è entrato", " è entrata"),
            suffix(EventType.LIKE, "it", " ha messo mi piace", " ha apprezzato la live"),
            suffix(EventType.FOLLOW, "it", " ha iniziato a seguirti", " ti segue"),
            contains(EventType.GIFT, "it", " ha inviato un regalo", " ha mandato un regalo"),

            // Portuguese
            suffix(EventType.JOIN, "pt", " entrou na live", " entrou na transmissão", " entrou"),
            suffix(EventType.LIKE, "pt", " curtiu a live", " deu gostei", " curtiu"),
            suffix(EventType.FOLLOW, "pt", " começou a seguir você", " seguiu você", " seguiu"),
            contains(EventType.GIFT, "pt", " enviou um presente", " mandou um presente"),

            // Russian
            suffix(EventType.JOIN, "ru", " присоединился к эфиру", " присоединилась к эфиру", " вошел в эфир", " вошла в эфир"),
            suffix(EventType.LIKE, "ru", " поставил лайк", " поставила лайк", " понравился эфир"),
            suffix(EventType.FOLLOW, "ru", " подписался", " подписалась"),
            contains(EventType.GIFT, "ru", " отправил подарок", " отправила подарок")
    };

    public List<LiveEvent> parseAll(String raw) {
        List<LiveEvent> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        String normalized = TextNormalizer.normalize(raw);
        String[] lines = normalized.split("\\n");
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            LiveEvent event = parseLine(line);
            if (event != null && seen.add(event.fingerprint())) out.add(event);
        }
        // 좋아요·선물·팔로우는 오탐 방지를 우선해 한 줄 시스템 문구만 처리한다.
        // 닉네임과 일반 댓글이 서로 다른 줄에 보이는 BIGO 채팅을 합치지 않는다.
        return out;
    }

    public LiveEvent parseLine(String rawLine) {
        String line = cleanLine(rawLine);
        if (line.isEmpty() || line.length() > 180) return null;
        String lower = line.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            for (String trigger : rule.triggers) {
                String triggerLower = trigger.toLowerCase(Locale.ROOT);
                int index = lower.indexOf(triggerLower);
                if (index < 0) continue;
                if (rule.mode == Mode.SUFFIX) {
                    String tail = lower.substring(index + triggerLower.length()).trim();
                    if (!tail.isEmpty() && !tail.matches("^[.!?。！？]+$")) continue;
                }
                String nickname = extractNickname(line, index, trigger.length(), rule.mode);
                if (!validNickname(nickname)) continue;
                String detail = rule.type == EventType.GIFT
                        ? extractGiftDetail(line, index, trigger.length(), rule.mode)
                        : "";
                return new LiveEvent(
                        rule.type,
                        nickname,
                        detail,
                        line,
                        rule.language,
                        System.currentTimeMillis());
            }
        }
        return null;
    }

    public static String guessNicknameByTrigger(String line, String trigger) {
        if (line == null || trigger == null) return "청취자";
        String clean = cleanLine(line);
        int index = clean.toLowerCase(Locale.ROOT)
                .indexOf(trigger.toLowerCase(Locale.ROOT));
        if (index < 0) return fallbackNickname(clean);
        String before = clean.substring(0, index).trim();
        String after = clean.substring(Math.min(clean.length(), index + trigger.length())).trim();
        String candidate = before.isEmpty() ? after : before;
        candidate = stripUiPrefix(candidate);
        candidate = stripSuffixParticles(candidate);
        if (!validNickname(candidate)) return fallbackNickname(clean);
        return clip(candidate, MAX_NAME);
    }

    private static String extractNickname(String line, int index, int length, Mode mode) {
        String before = line.substring(0, index).trim();
        String after = line.substring(Math.min(line.length(), index + length)).trim();
        String candidate = mode == Mode.PREFIX ? after : before;
        if (candidate.isEmpty()) candidate = before.isEmpty() ? after : before;
        candidate = stripUiPrefix(candidate);
        candidate = stripSuffixParticles(candidate);
        return clip(candidate, MAX_NAME);
    }

    private static String extractGiftDetail(String line, int index, int length, Mode mode) {
        String after = line.substring(Math.min(line.length(), index + length)).trim();
        if (mode == Mode.CONTAINS && !after.isEmpty()) {
            after = after.replaceFirst("^[：:xX×*\\-]+", "").trim();
            return clip(after, MAX_DETAIL);
        }
        Matcher matcher = Pattern.compile("[xX×*]\\s*([0-9]{1,5})").matcher(line);
        return matcher.find() ? "x" + matcher.group(1) : "";
    }

    private static String cleanLine(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ')
                .replace('\t', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String stripUiPrefix(String value) {
        return value.replaceFirst(
                "(?i)^(BIGO LIVE|BIGO|LIVE|알림|공지|system|notification|消息|通知)\\s*[:：\\-]?\\s*",
                "").trim();
    }

    private static String stripSuffixParticles(String value) {
        return value.replaceFirst("(?i)(님이|님|さんが|さん|が)$", "").trim();
    }

    private static boolean validNickname(String value) {
        if (value == null) return false;
        String clean = value.trim();
        if (clean.length() < 1 || clean.length() > MAX_NAME) return false;
        String lower = clean.toLowerCase(Locale.ROOT);
        // A colon normally marks a viewer chat line (nickname: message), not a BIGO system event.
        if (clean.contains(":") || clean.contains("：")) return false;
        if (lower.equals("i") || lower.equals("we") || lower.equals("you")
                || lower.equals("he") || lower.equals("she")
                || lower.equals("they") || lower.equals("나")
                || lower.equals("저")) return false;
        return !lower.equals("bigo")
                && !lower.equals("bigo live")
                && !lower.equals("live")
                && !lower.equals("notification")
                && !lower.equals("system")
                && !lower.matches("^[0-9:./\\-]+$");
    }

    private static String fallbackNickname(String line) {
        if (line == null || line.trim().isEmpty()) return "청취자";
        String clean = stripUiPrefix(line);
        String[] parts = clean.split("\\s+", 2);
        String first = parts.length == 0 ? "청취자" : parts[0];
        return validNickname(first) ? clip(first, MAX_NAME) : "청취자";
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private static Rule suffix(EventType type, String language, String... triggers) {
        return new Rule(type, language, Mode.SUFFIX, triggers);
    }

    private static Rule contains(EventType type, String language, String... triggers) {
        return new Rule(type, language, Mode.CONTAINS, triggers);
    }

    private enum Mode { SUFFIX, PREFIX, CONTAINS }

    private static final class Rule {
        final EventType type;
        final String language;
        final Mode mode;
        final String[] triggers;

        Rule(EventType type, String language, Mode mode, String[] triggers) {
            this.type = type;
            this.language = language;
            this.mode = mode;
            this.triggers = triggers;
        }
    }
}
