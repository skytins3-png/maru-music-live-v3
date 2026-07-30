package com.maru.musiclive;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class BigoEventParserTest {
    private final BigoEventParser parser = new BigoEventParser();

    @Test public void parsesFourKoreanEvents() {
        assertEquals(EventType.JOIN, parser.parseLine("민지 라이브에 입장하였습니다").type);
        assertEquals(EventType.LIKE, parser.parseLine("민지님이 좋아요를 눌렀습니다").type);
        assertEquals(EventType.GIFT, parser.parseLine("민지님이 선물을 보냈습니다 Rose x10").type);
        assertEquals(EventType.FOLLOW, parser.parseLine("민지님이 팔로우했습니다").type);
    }

    @Test public void parsesEuropeanEvents() {
        assertEquals("es", parser.parseLine("Ana envió un regalo").languageHint);
        assertEquals("fr", parser.parseLine("Marie a rejoint le live").languageHint);
        assertEquals("de", parser.parseLine("Hans folgt dir jetzt").languageHint);
        assertEquals("ru", parser.parseLine("Иван отправил подарок").languageHint);
    }

    @Test public void rejectsPunctuationOnlyName() {
        assertNull(parser.parseLine("... 라이브에 입장하였습니다"));
    }
}
