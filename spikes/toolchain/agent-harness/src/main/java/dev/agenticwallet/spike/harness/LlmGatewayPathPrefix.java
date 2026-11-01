package dev.agenticwallet.spike.harness;

import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * T-008 finding and workaround: Spring AI 2.0.2's OpenAI client keeps only scheme, host and port of
 * {@code spring.ai.openai.base-url}, so a gateway whose API lives under a path (OpenRouter: {@code
 * /api/v1}) is unreachable by configuration alone. This interceptor puts the configured prefix back
 * in front of every request path that lacks it. Remove it once Spring AI keeps the base URL's path.
 */
@Component
class LlmGatewayPathPrefix implements OpenAiHttpClientBuilderCustomizer {

    private final String prefix;

    LlmGatewayPathPrefix(@Value("${agentic.llm-gateway.path-prefix}") String prefix) {
        if (!prefix.isEmpty() && !prefix.startsWith("/")) {
            throw new IllegalArgumentException("agentic.llm-gateway.path-prefix must start with /");
        }
        this.prefix = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    @Override
    public void customize(SpringAiOpenAiHttpClient.Builder builder) {
        if (!prefix.isEmpty()) {
            builder.interceptor(this::withPrefix);
        }
    }

    private Response withPrefix(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url();
        if (url.encodedPath().startsWith(prefix + "/")) {
            return chain.proceed(request);
        }
        HttpUrl prefixed = url.newBuilder().encodedPath(prefix + url.encodedPath()).build();
        return chain.proceed(request.newBuilder().url(prefixed).build());
    }
}
