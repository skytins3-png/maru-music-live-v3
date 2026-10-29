package com.maru.musiclive;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Original fictional guest radio show, not a live AI model or actual interview.
 * A long talk section precedes each verified local original music track.
 * Optional audience stories and curated notes are operator-approved input only.
 */
public final class GuestTalkShow {
    private static final String[] THEMES = {
            "삶에서 가장 오래 남는 노래", "가족에게 전하지 못한 안부", "고향과 돌아가는 길",
            "바람이 불던 계절", "밤의 고요한 풍경", "친구와 웃었던 시간",
            "꿈을 다시 시작하는 용기", "중국과 한국의 서로 다른 풍경",
            "여행에서 들었던 멜로디", "책상 위 오래된 편지",
            "아침의 커피와 작은 약속", "악기가 들려주는 이야기"
    };
    private static final String[] QUESTIONS = {
            "오늘의 주제를 음악으로 표현한다면 첫 번째 악기는 무엇일까요?",
            "그 감정을 너무 과장하지 않고 전하려면 어떤 장면을 떠올리면 좋을까요?",
            "청취자가 가사 한 줄만 기억한다면 어떤 마음을 가져갔으면 하나요?",
            "아침에 듣는 노래와 밤에 듣는 노래는 감정이 다를까요?",
            "조용한 전주가 필요한 순간은 언제라고 생각하세요?",
            "가장 오래 남는 화음은 꼭 화려해야 할까요?",
            "서로 다른 나라의 악기가 만났을 때 어떤 풍경을 상상하나요?",
            "노래가 끝난 뒤 침묵을 남기는 방식은 어떤가요?",
            "가상의 무대에 한 사람을 초대한다면 어떤 이야기를 건네고 싶나요?",
            "이제 다음 곡을 들을 분들에게 어떤 한마디를 전하고 싶나요?"
    };
    private static final String[] FOLLOW_UPS = {
            "한 곡을 서두르지 않고 듣는 마음이 느껴지네요.",
            "같은 주제도 음악 색깔에 따라 전혀 달라지는군요.",
            "서로 의견이 다른 것도 라디오 초대석의 즐거움이지요.",
            "누군가에게는 평범한 이야기지만 다른 이에게는 큰 위로가 되겠어요.",
            "그 말에 잠깐 귀를 기울여 보고 싶네요.",
            "오늘 초대석에서 마음에 남는 표현이 하나 더 생겼습니다."
    };

    private GuestTalkShow() {}

    public static RadioConversationScript.Line[] beforeSong(
            String songTitle, int episodeIndex, LocalDate date, int assignedSinger,
            String nickname, String approvedStory, String approvedResearchNote,
            boolean listenerRequested) {
        if (date == null) throw new IllegalArgumentException("date required");
        List<RadioConversationScript.Line> script = new ArrayList<>();
        VirtualArtistCatalog.Artist[] pair = VirtualArtistCatalog.pairFor(
                date.toEpochDay(), episodeIndex);
        VirtualArtistCatalog.Artist a = assignedSinger >= 1
                && assignedSinger <= VirtualArtistCatalog.all().size()
                ? VirtualArtistCatalog.at(assignedSinger - 1) : pair[0];
        VirtualArtistCatalog.Artist b = pair[1];
        if (a.id == b.id) b = VirtualArtistCatalog.at(a.id);
        int turn = Math.floorMod(date.toEpochDay() + episodeIndex, THEMES.length);
        String topic = THEMES[turn];
        String title = RadioConversationScript.cleanTitle(songTitle);

        host(script, "아환의 마루 라디오, 오늘은 가상 AI 가수 초대석입니다. "
                + a.name + "님, " + b.name + "님, 두 분을 모셨습니다.");
        guest(script, a, "안녕하세요, " + a.name + "입니다. "
                + "저는 " + a.genre + "의 감정을 좋아합니다. " + a.motto);
        guest(script, b, "반갑습니다. " + b.name + "입니다. "
                + "제 음악 취향은 " + b.genre + "입니다. " + b.motto);
        host(script, "오늘의 대화 주제는 " + topic
                + "입니다. 바로 노래를 틀기보다 서로의 이야기를 길게 나눠보겠습니다.");

        for (int i = 0; i < QUESTIONS.length; i++) {
            host(script, QUESTIONS[i]);
            VirtualArtistCatalog.Artist guest = i % 2 == 0 ? a : b;
            VirtualArtistCatalog.Artist partner = i % 2 == 0 ? b : a;
            guest(script, guest, i % 3 == 0
                    ? "제가 좋아하는 " + guest.genre + "에서는 " + guest.viewpoint
                    : i % 3 == 1
                        ? guest.motto + " 그래서 " + topic + "의 느낌을 천천히 떠올려봅니다."
                        : "저라면 " + topic + "을 한 문장의 편지처럼 전하고 싶습니다. "
                                + guest.viewpoint);
            guest(script, partner, "제 생각은 조금 달라요. "
                    + partner.motto + " " + FOLLOW_UPS[(i + turn) % FOLLOW_UPS.length]);
            if (i == 3 && approvedStory != null && !approvedStory.trim().isEmpty()) {
                host(script, "이번에는 운영자가 낭독을 승인한 청취자 사연을 읽겠습니다. "
                        + RadioConversationScript.cleanTitle(nickname) + "님의 사연입니다.");
                for (String chunk : chunks(approvedStory, 90))
                    host(script, chunk);
                guest(script, a, "사연을 보내주신 마음에 감사드립니다. "
                        + "한 사람의 이야기에는 여러 감정이 함께 담기지요.");
                guest(script, b, "그 마음을 단정하지 않고 조심스럽게 듣겠습니다. "
                        + "오늘 음악이 작은 휴식이 되었으면 좋겠어요.");
            }
            if (i == 7 && approvedResearchNote != null
                    && !approvedResearchNote.trim().isEmpty()) {
                host(script, "이어서 방송 운영자가 직접 확인하고 저장한 참고 메모를 소개합니다. "
                        + "외부 자료의 사실 여부는 따로 확인해야 합니다.");
                for (String chunk : chunks(approvedResearchNote, 90))
                    host(script, chunk);
                guest(script, b, "새로운 자료를 만나면 시각이 넓어지는 것 같아요. "
                        + "다음 대화에서도 이 메모를 참고해볼 수 있겠네요.");
            }
        }

        host(script, listenerRequested
                ? "이제 운영자가 접수한 신청곡을 들려드릴 차례입니다. "
                        + "신청곡 제목은 " + title + "입니다."
                : "두 분과 긴 이야기를 나눴습니다. "
                        + "다음으로 준비한 원곡의 제목은 " + title + "입니다.");
        guest(script, a, "이 곡을 듣는 분마다 다른 장면을 떠올리시면 좋겠습니다.");
        guest(script, b, "음악이 끝나면 또 다른 초대석 이야기를 나누겠습니다.");
        host(script, "아환의 마루 라디오에서 등록된 원곡 음원으로 이어갑니다. "
                + "가상 가수 대화와 실제 노래 음원은 별개입니다.");
        return script.toArray(new RadioConversationScript.Line[0]);
    }

    /** Short chunks keep each TTS utterance below its watchdog. */
    public static List<String> chunks(String s, int max) {
        List<String> out = new ArrayList<>();
        if (s == null || max < 20) return out;
        String cleaned = s.replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        for (int start = 0; start < cleaned.length(); start += max)
            out.add(cleaned.substring(start, Math.min(cleaned.length(), start + max)));
        return out;
    }

    private static void host(List<RadioConversationScript.Line> list, String text) {
        list.add(new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN, text));
    }

    private static void guest(List<RadioConversationScript.Line> list,
                              VirtualArtistCatalog.Artist artist, String text) {
        list.add(new RadioConversationScript.Line(
                RadioConversationScript.Speaker.GUEST, text, artist));
    }
}
