package com.maru.radio;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RadioEnsembleScriptSelfTest {
    private static void ok(boolean good, String name) {
        if (!good) throw new AssertionError("FAIL " + name);
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        ok(RadioEnsembleScript.CAST.length == 9, "exactly Ahwan + 8 other presenters");
        ok("아환".equals(RadioEnsembleScript.CAST[0].name), "lead is Ahwan");
        Set<String> names = new HashSet<>();
        for (RadioEnsembleScript.Role role : RadioEnsembleScript.CAST) {
            names.add(role.name);
            ok(role.pitch >= .8f && role.pitch <= 1.3f
                    && role.rate >= .82f && role.rate <= 1.12f,
                    "safe Android speech variation " + role.name);
        }
        ok(names.size() == 9, "each role uniquely identified");
        Set<Integer> heard = new HashSet<>();
        Set<String> intros = new HashSet<>();
        for (int song = 0; song < 24; song++) {
            List<RadioEnsembleScript.Line> scene =
                    RadioEnsembleScript.scene(song, "가을 바다", 4);
            ok(scene.size() == 4, "4-turn ordered scene number " + song);
            ok(scene.get(0).roleIndex == 0 && scene.get(3).roleIndex == 0,
                    "Ahwan leads and introduces the next song");
            for (RadioEnsembleScript.Line line : scene) {
                heard.add(line.roleIndex);
                ok(!line.text.isEmpty() && line.text.length() < 230,
                        "short readable line " + song + ":" + line.role().name);
            }
            intros.add(scene.get(0).text);
        }
        ok(heard.size() == 9, "all eight guest presenters rotate");
        ok(intros.size() >= 4, "varied openers rather than a fixed loop");
        ok(RadioEnsembleScript.scene(0, "제목", 2).size()==2,
                "two-turn quick conversation option");
        ok(RadioEnsembleScript.scene(0, "제목", 3).size()==3,
                "three-turn conversation option");
        ok(RadioEnsembleScript.scene(0, "제목", 4).get(3).text.contains("제목"),
                "song handoff includes the song title");
        ok(RadioEnsembleScript.scene(0, null, 4).get(3).text.contains("다음 음악"),
                "null track title fallback");
        ok(RadioEnsembleScript.scene(0, "악성<입력>\n이름", 4).get(3).text.indexOf('\n')<0,
                "title escaped for spoken introduction");
        ok(RadioEnsembleScript.clampLines(-5)==2 && RadioEnsembleScript.clampLines(22)==4,
                "limit dialogue length");
        ok(RadioEnsembleScript.clampEvery(0)==1 && RadioEnsembleScript.clampEvery(999)==4,
                "limit song cadence");
        ok(RadioEnsembleScript.shouldRun(0,2)
                && !RadioEnsembleScript.shouldRun(1,2)
                && RadioEnsembleScript.shouldRun(2,2), "talk every two songs if selected");
        ok(!RadioEnsembleScript.shouldRun(-1,1), "negative ordinal denied");
        System.out.println("NO LLM model, no 9-way cloned voices, no remote BIGO stream established.");
    }
}
