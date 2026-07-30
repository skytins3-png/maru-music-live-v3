import com.maru.musiclive.BroadcastClosingText;
import com.maru.musiclive.ChatMessage;
import com.maru.musiclive.ChatMessageParser;
import com.maru.musiclive.ConversationEngine;
import com.maru.musiclive.ConversationIntent;
import com.maru.musiclive.GreetingLanguage;
import com.maru.musiclive.SeenCache;

import java.util.List;

public final class UiAiClosingStressSelfTest {
    public static void main(String[] args) {
        ChatMessageParser parser = new ChatMessageParser();
        SeenCache seen = new SeenCache(2000);
        String[] languages = {
                GreetingLanguage.KOREAN,
                GreetingLanguage.ENGLISH,
                GreetingLanguage.CHINESE,
                GreetingLanguage.JAPANESE,
                GreetingLanguage.RUSSIAN
        };
        int checks = 0;
        long now = 1_700_000_000_000L;
        for (int i = 0; i < 1000; i++) {
            String nickname = "viewer" + i;
            List<ChatMessage> chats = parser.parseAll(nickname + ": hello");
            require(chats.size() == 1, "chat parser");
            ChatMessage chat = chats.get(0);
            ConversationEngine.Reply reply = ConversationEngine.reply(
                    chat.nickname, chat.message, GreetingLanguage.ENGLISH);
            require(reply.intent == ConversationIntent.HELLO, "hello intent");
            require(reply.shouldSpeak() && !reply.text.isEmpty(), "safe reply");
            require(seen.markIfNew(chat.fingerprint(), now + i, 600_000L), "first seen");
            require(!seen.markIfNew(chat.fingerprint(), now + i + 1, 600_000L), "dedupe");
            require(ConversationEngine.reply(
                    "guest", "게임 좋아하세요?", GreetingLanguage.KOREAN).intent
                    == ConversationIntent.UNKNOWN, "unknown question not auto answered");
            require(!BroadcastClosingText.build(languages[i % languages.length]).trim().isEmpty(),
                    "closing text");
            checks++;
        }
        require(checks == 1000, "expected 1000 checks");
        System.out.println("UI-AI-CLOSING-STRESS: 1000/1000 PASS");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
