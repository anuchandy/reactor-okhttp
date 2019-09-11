package reactor.okhttp.http.client;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;

/**
 * Reactor based OKHttp client.
 */
public class HttpClient {
    OkHttpClient okHttpClient;

    HttpClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /**
     * Send the provided request asynchronously.
     *
     * @param request The HTTP request to send
     * @return A {@link Mono} that emits response asynchronously
     */
    public Mono<okhttp3.Response> send(okhttp3.Request request) {
        return Mono.create(sink -> sink.onRequest(ignored -> {
            Call call = okHttpClient.newCall(request);
            sink.onCancel(() -> call.cancel());
            call.enqueue(new OkHttpCallback(sink));
        }));
    }

    private static class OkHttpCallback implements okhttp3.Callback {
        private final MonoSink<okhttp3.Response> sink;

        OkHttpCallback(MonoSink<okhttp3.Response> sink) {
            this.sink = sink;
        }

        @Override
        public void onFailure(okhttp3.Call call, IOException e) {
            sink.error(e);
        }

        @Override
        public void onResponse(okhttp3.Call call, okhttp3.Response response) {
            sink.success(response);
        }
    }
}
