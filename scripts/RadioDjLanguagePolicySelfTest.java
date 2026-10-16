package com.maru.radio;

import java.util.List;

/** Pure JVM policy regressions; does not start a BIGO livestream. */
public final class RadioDjLanguagePolicySelfTest {
    private static void check(boolean yes, String label) {
        if (!yes) throw new AssertionError("FAIL: " + label);
        System.out.println("PASS: " + label);
    }

    private static void checkLanguages(List<RadioDjLanguagePolicy.Line> lines,
            String name, int... expected) {
        check(lines.size() == expected.length, name + ": segment count");
        for (int i=0;i<expected.length;i++) {
            check(lines.get(i).language == expected[i],
                    name + ": segment " + i + " locale");
            check(!lines.get(i).text.isEmpty(), name + ": no empty phrases");
        }
    }

    public static void main(String[] args) {
        checkLanguages(RadioDjLanguagePolicy.opening(), "start English only", 2);
        checkLanguages(RadioDjLanguagePolicy.closing(), "end English only", 2);
        check(RadioDjLanguagePolicy.opening().get(0).text.contains("Ahwan"),
                "announcer named Ahwan at opening");
        checkLanguages(RadioDjLanguagePolicy.songIntro("가을 남자",1),
                "Korean song Korean plus English",0,2);
        checkLanguages(RadioDjLanguagePolicy.songIntro("秋天",2),
                "Chinese song Chinese plus English",1,2);
        checkLanguages(RadioDjLanguagePolicy.songIntro("Moonlight",3),
                "Western song English only",2);
        checkLanguages(RadioDjLanguagePolicy.songIntro("바람의 소리",4),
                "instrumental English only",2);
        checkLanguages(RadioDjLanguagePolicy.songIntro("Unclassified",0),
                "unknown fallback English only",2);
        checkLanguages(RadioDjLanguagePolicy.gift("장미 고맙습니다",-1),
                "Korean gift",0);
        checkLanguages(RadioDjLanguagePolicy.gift("谢谢礼物",-1),
                "Chinese gift",1);
        checkLanguages(RadioDjLanguagePolicy.gift("プレゼント",-1),
                "Japanese gift",3);
        checkLanguages(RadioDjLanguagePolicy.gift("Hello",-1),
                "English gift",2);
        checkLanguages(RadioDjLanguagePolicy.gift(null,-1),
                "unknown sender fallback English",2);
        checkLanguages(RadioDjLanguagePolicy.gift("Hello",4),
                "manual sender language override",4);
        checkLanguages(RadioDjLanguagePolicy.gift("선물",-1),
                "gift language from supplied text",0);
        check(RadioDjLanguagePolicy.isGiftMessage("선물 고마워"),
                "gift trigger recognized");
        check(RadioDjLanguagePolicy.isGiftMessage("礼物"),
                "Chinese gift trigger recognized");
        check(!RadioDjLanguagePolicy.isGiftMessage("노래 신청"),
                "normal song request does not count as gift");
        for (int category=0;category<=5;category++) {
            check(RadioDjLanguagePolicy.songIntro("song",category).size() <= 2,
                    "never repeat 5-country intros for genre " + category);
        }
        System.out.println("MARU Ahwan event-language policy tests PASS (not live broadcaster test).");
    }
}
