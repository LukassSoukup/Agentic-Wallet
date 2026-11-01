package dev.agenticwallet.spike.harness;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/** The smallest possible round trip through the LLM gateway (OpenAI-compatible, OpenRouter). */
@Component
class ChatHello {

    private final ChatClient chat;

    ChatHello(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    String hello() {
        String reply = chat.prompt().user("Reply with exactly one word: hello").call().content();
        if (reply == null || reply.isBlank()) {
            throw new EmptyModelReplyException();
        }
        return reply.trim();
    }

    static final class EmptyModelReplyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        EmptyModelReplyException() {
            super("the model returned no content");
        }
    }
}
