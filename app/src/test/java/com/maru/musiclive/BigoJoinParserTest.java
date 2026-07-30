package com.maru.musiclive;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BigoJoinParserTest {
    private final BigoJoinParser parser = new BigoJoinParser();

    @Test public void koreanJoin() {
        List<JoinEvent> events = parser.parseAll("봄날 님이 입장했습니다");
        assertEquals(1, events.size());
        assertEquals("봄날", events.get(0).nickname);
        assertEquals("ko", events.get(0).languageHint);
    }

    @Test public void koreanOcrSpaces() {
        List<JoinEvent> events = parser.parseAll("테스트청취자 님이 입 장 했 습 니 다");
        assertEquals(1, events.size());
        assertEquals("테스트청취자", events.get(0).nickname);
    }

    @Test public void koreanLiveRoom() {
        List<JoinEvent> events = parser.parseAll("하늘별 님이 라이브 방송에 입장했습니다");
        assertEquals(1, events.size());
        assertEquals("하늘별", events.get(0).nickname);
    }

    @Test public void chineseJoin() {
        List<JoinEvent> events = parser.parseAll("小雨进入了你的直播间");
        assertEquals(1, events.size());
        assertEquals("小雨", events.get(0).nickname);
        assertEquals("zh", events.get(0).languageHint);
    }

    @Test public void englishJoin() {
        List<JoinEvent> events = parser.parseAll("Blue Moon joined the live");
        assertEquals(1, events.size());
        assertEquals("Blue Moon", events.get(0).nickname);
        assertEquals("en", events.get(0).languageHint);
    }

    @Test public void koreanNoSpace() {
        List<JoinEvent> events =
                parser.parseAll("홍길동님이 방에 들어왔습니다");
        assertEquals(1, events.size());
        assertEquals("홍길동", events.get(0).nickname);
    }

    @Test public void koreanUiPrefix() {
        List<JoinEvent> events =
                parser.parseAll("알림: 🔥별빛 님 입장");
        assertEquals(1, events.size());
        assertEquals("별빛", events.get(0).nickname);
    }

    @Test public void englishSplitAcrossLines() {
        List<JoinEvent> events =
                parser.parseAll("Blue Moon\njoined the live");
        assertEquals(1, events.size());
        assertEquals("Blue Moon", events.get(0).nickname);
    }

    @Test public void chineseWithSpaces() {
        List<JoinEvent> events =
                parser.parseAll("小 雨 进入直播间");
        assertEquals(1, events.size());
        assertEquals("小 雨", events.get(0).nickname);
    }

    @Test public void chineseFullWidthAndEventSpaces() {
        List<JoinEvent> events =
                parser.parseAll("小　雨　进 入 直 播 间");
        assertEquals(1, events.size());
        assertEquals("小 雨", events.get(0).nickname);
        assertEquals("zh", events.get(0).languageHint);
    }

    @Test public void ignoresOrdinaryEnglishJoinedComment() {
        assertTrue(parser.parseAll("Alice: I joined yesterday").isEmpty());
    }

    @Test public void ignoresQuestionComment() {
        assertTrue(parser.parseAll("Alice: 게임 좋아하세요?").isEmpty());
    }

    @Test public void commentAboveJoinDoesNotLeakIntoNickname() {
        List<JoinEvent> events = parser.parseAll(
                "Alice: 게임 좋아하세요?\n봄날 님이 입장했습니다");
        assertEquals(1, events.size());
        assertEquals("봄날", events.get(0).nickname);
    }

    @Test public void noFalsePositive() {
        assertTrue(parser.parseAll("오늘도 좋은 음악과 함께하세요").isEmpty());
    }
}