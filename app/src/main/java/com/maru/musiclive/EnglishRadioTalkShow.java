package com.maru.musiclive;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Original English-language fictional radio banter. Only one featured guest
 * per episode, not a carousel of artificial speakers discussing the same
 * topic. Lines are prewritten, not a live conversational model.
 */
public final class EnglishRadioTalkShow {
    private static final String[] TOPICS = {
        "the little things that make a bad day better",
        "songs that bring old friends back",
        "the world's most awkward first date",
        "the smell of coffee on a rainy morning",
        "the ridiculous stuff we believed as kids",
        "the brave things we almost didn't do",
        "home cooking and family recipes",
        "tiny victories on a very long Monday",
        "a journey that didn't go as planned",
        "that one friend who is always late",
        "the music we played on the way home",
        "finding time to laugh at ourselves",
        "an unexpected act of kindness",
        "a summer evening we still remember",
        "trying something new after sixty",
        "why a good apology matters"
    };
    private static final String[] GUEST_NAMES = {
        "Alex", "Sam", "Jordan", "Taylor", "Morgan", "Riley", "Casey", "Jamie",
        "Avery", "Charlie", "Robin", "Drew", "Frankie", "Quinn", "Sky", "Lee"
    };
    private static final String[] QUESTIONS = {
        "Okay, real question. What's the first thing that pops into your head?",
        "Have you ever had a moment where you just had to laugh?",
        "Who would you call if that happened today?",
        "Tell me the part you normally leave out of the story.",
        "What did you learn, apart from never doing that again?",
        "Would you handle it differently if it happened tomorrow?",
        "What's one little detail people might recognize?",
        "I can picture this already. Did anybody actually keep a straight face?",
        "If this were a movie, what would the soundtrack sound like?",
        "What would you say to somebody listening on their way home?"
    };
    private static final String[] ANSWERS = {
        "I think it starts with an ordinary day, and then something small changes everything.",
        "Oh, absolutely. I once tried to look cool and walked straight into a glass door. Ha ha!",
        "Probably my oldest friend. She'd listen kindly, then tease me for a week.",
        "The embarrassing part? I practiced my big speech in the mirror and forgot every word.",
        "That nobody has a perfect script. Sometimes you just need to show up.",
        "I'd worry less about looking clever and more about being kind. I'm still working on it.",
        "It's the smell of tea, the chair by the window, and somebody saying, 'You made it home.'",
        "Not a chance. My brother laughed so hard he had to sit down. I nearly joined him.",
        "I'd start with a quiet piano and let the chorus do the talking.",
        "I'd tell them they've made it this far today. That counts for something."
    };
    private static final String[] REACTIONS = {
        "Ha! Please tell me someone got a photo.",
        "Oh, I know that feeling. That's painfully familiar.",
        "You know what? That actually makes me feel better.",
        "That's the kind of moment that stays with you.",
        "I'm laughing, but I really do get what you mean.",
        "Okay, now I'm picturing that happening to me. No, thank you!",
        "That's a lovely way to put it, actually.",
        "Sometimes the simplest answer turns out to be the best one.",
        "You make it sound easy. I suspect there was some chaos involved.",
        "That's a good note to leave with everyone."
    };
    private static final String[] SIDE_JOKES = {
        "No pressure, of course. Only a few imaginary microphones in the room.",
        "I promise not to tell the embarrassing bits. Unless they're very funny.",
        "For the record, my coffee is doing most of the hosting tonight.",
        "Somewhere, a very patient piano teacher is shaking their head.",
        "This is why we have music. It gives us a few minutes to recover.",
        "There's wisdom in that. And possibly a small amount of trouble.",
        "Let's pretend we planned that transition. Very professional.",
        "I'm stealing that line. With full credit, of course."
    };

    private EnglishRadioTalkShow() {}

    public static RadioConversationScript.Line[] beforeSong(String title, int episode,
            LocalDate date, int assignedArtist, String nickname, String approvedStory,
            String approvedNote, boolean requested) {
        if (date == null) throw new IllegalArgumentException("date required");
        int topicIdx = Math.floorMod(date.toEpochDay() * 7L + episode, TOPICS.length);
        int guestIdx = Math.floorMod(date.toEpochDay() + episode, GUEST_NAMES.length);
        VirtualArtistCatalog.Artist artist = assignedArtist > 0
                && assignedArtist <= VirtualArtistCatalog.all().size()
                ? VirtualArtistCatalog.at(assignedArtist - 1)
                : VirtualArtistCatalog.at(guestIdx);
        String stageName = GUEST_NAMES[Math.floorMod(artist.id - 1, GUEST_NAMES.length)];
        List<RadioConversationScript.Line> lines = new ArrayList<>();
        host(lines, "Hey, welcome back to Maru Radio. I'm Ahwan. Pull up a chair and make yourself at home.");
        guest(lines, artist, "And I'm " + stageName + ". I brought no grand speech, just a few stories and some good music.");
        host(lines, "That is exactly what we need. Tonight we're talking about " + TOPICS[topicIdx] + ".");
        guest(lines, artist, "Now that's a subject I know a little too well. And I'm not naming names!");
        host(lines, "Ha ha! Okay, fair enough. No witnesses, no court case. Just an honest conversation.");
        // Keep one topic and one guest throughout the episode. Rotate each
        // episode's starting point so the same exchange is not repeated.
        int offset = Math.floorMod(date.toEpochDay() + episode * 3L, QUESTIONS.length);
        for (int i = 0; i < QUESTIONS.length; i++) {
            int q = (offset + i) % QUESTIONS.length;
            host(lines, QUESTIONS[q]);
            guest(lines, artist, ANSWERS[q]);
            host(lines, REACTIONS[(q + topicIdx) % REACTIONS.length]);
            if (i == 2 || i == 6) {
                guest(lines, artist, SIDE_JOKES[(q + episode) % SIDE_JOKES.length]);
            }
            if (i == 3 && approvedStory != null && !approvedStory.trim().isEmpty()) {
                host(lines, "We have a listener story the producer approved for the show. "
                        + "It's from " + safeName(nickname) + ".");
                for (String chunk : GuestTalkShow.chunks(approvedStory, 85)) host(lines, chunk);
                guest(lines, artist, "Thank you for trusting us with that. It's real life, not a neat little movie ending.");
                host(lines, "Exactly. If you're listening and relating to this, you're not the only one.");
            }
            if (i == 7 && approvedNote != null && !approvedNote.trim().isEmpty()) {
                host(lines, "Here's a short note our producer saved after reviewing an outside source. "
                        + "It isn't a verified news report.");
                for (String chunk : GuestTalkShow.chunks(approvedNote, 85)) host(lines, chunk);
                guest(lines, artist, "That's an interesting perspective. Let's keep our minds open.");
            }
        }
        host(lines, "Well, that turned into quite a conversation, didn't it?");
        guest(lines, artist, "It did. And surprisingly, we only embarrassed ourselves a little. Ha ha!");
        host(lines, requested
                ? "Time for a listener request. Here's " + safeTitle(title) + "."
                : "Let's hear the next track from our local music collection: " + safeTitle(title) + ".");
        guest(lines, artist, "Thanks for spending this time with us. Enjoy the song, and we'll catch up again soon.");
        return lines.toArray(new RadioConversationScript.Line[0]);
    }
    private static String safeName(String s) {
        String clean = RadioAudienceStore.clean(s, 24);
        return clean.isEmpty() ? "a listener" : clean;
    }
    private static String safeTitle(String s) {
        return RadioConversationScript.cleanTitle(s);
    }
    private static void host(List<RadioConversationScript.Line> list, String text) {
        list.add(new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN, text));
    }
    private static void guest(List<RadioConversationScript.Line> list,
            VirtualArtistCatalog.Artist artist, String text) {
        list.add(new RadioConversationScript.Line(RadioConversationScript.Speaker.GUEST, text, artist));
    }
}
