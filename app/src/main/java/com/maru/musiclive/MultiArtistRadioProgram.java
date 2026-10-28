package com.maru.musiclive;

import java.time.LocalDate;
import java.util.List;

/**
 * Scripted local radio with one DJ and two of 32 fictional AI singers per song.
 * The character talk is separate from sung tracks. No invented interviews,
 * requests, events, singing recordings or factual real-world biographies.
 */
public final class MultiArtistRadioProgram {
    private MultiArtistRadioProgram() {}

    public static RadioConversationScript.Line[] beforeSong(
            String title, int songIndex, LocalDate date,
            boolean dailyVariation, List<String> giftNames,
            List<String> followNames) {
        return beforeSong(title, songIndex, date, dailyVariation, giftNames, followNames, 0);
    }

    /** An optional user-selected singer introduces the next original track. */
    public static RadioConversationScript.Line[] beforeSong(
            String title, int songIndex, LocalDate date,
            boolean dailyVariation, List<String> giftNames,
            List<String> followNames, int assignedSinger) {
        if (date == null) throw new IllegalArgumentException("date required");
        VirtualArtistCatalog.Artist[] pair =
                VirtualArtistCatalog.pairFor(dailyVariation ? date.toEpochDay() : 0L, songIndex);
        VirtualArtistCatalog.Artist first = assignedSinger >= 1
                && assignedSinger <= VirtualArtistCatalog.all().size()
                ? VirtualArtistCatalog.at(assignedSinger - 1)
                : pair[0];
        VirtualArtistCatalog.Artist second = pair[1].id == first.id
                ? pair[0] : pair[1];
        if (second.id == first.id) second = VirtualArtistCatalog.at(first.id);
        String safeTitle = RadioConversationScript.cleanTitle(title);
        String intro = dailyVariation
                ? DailyRadioProgram.beforeSong(safeTitle, songIndex, date)[0].text
                : "아환의 음악다방입니다. 오늘은 서로 다른 음악 색깔을 가진 두 가상 AI 아티스트를 만납니다.";
        // An assigned singer is selected by the listener and is not proof that
        // a distinct singing model has actually been trained or generated.

        RadioConversationScript.Line[] base = new RadioConversationScript.Line[]{
                host(intro + " 이번에는 " + first.name + "님과 " + second.name + "님이 함께합니다."),
                guest(first, "반갑습니다. " + first.name + "입니다. 저는 "
                        + first.genre + "에 마음이 가요. " + first.motto),
                host(first.name + "님, 어떤 순간을 음악에 담고 싶나요?"),
                guest(first, first.viewpoint),
                host("그럼 " + second.name + "님의 이야기도 들어볼까요?"),
                guest(second, "안녕하세요, " + second.name + "입니다. 저는 "
                        + second.genre + "을 좋아해요. " + second.motto),
                guest(first, "다른 색깔의 음악 이야기를 들으니 저도 새롭게 느껴지네요."),
                guest(second, second.viewpoint),
                host("두 분 고맙습니다. 다음 음악은 " + safeTitle
                        + "입니다. 등록된 원본 음악으로 이어가겠습니다.")
        };

        boolean gifts = giftNames != null && !giftNames.isEmpty();
        boolean follows = followNames != null && !followNames.isEmpty();
        if (!gifts && !follows) return base;
        String thanks = gifts && follows
                ? "실제로 확인된 선물과 팔로우 응원에 감사드립니다."
                : gifts ? "실제로 확인된 선물 응원에 감사드립니다."
                        : "실제로 확인된 팔로우에 감사드립니다.";
        RadioConversationScript.Line[] withThanks =
                new RadioConversationScript.Line[base.length + 1];
        withThanks[0] = base[0];
        withThanks[1] = host(thanks);
        System.arraycopy(base, 1, withThanks, 2, base.length - 1);
        return withThanks;
    }

    private static RadioConversationScript.Line host(String text) {
        return new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN, text);
    }

    private static RadioConversationScript.Line guest(
            VirtualArtistCatalog.Artist artist, String text) {
        return new RadioConversationScript.Line(RadioConversationScript.Speaker.GUEST,
                text, artist);
    }
}
