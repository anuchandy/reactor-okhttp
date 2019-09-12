package reactor.okhttp.http.client;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reactor based OKHttp HttpClient.
 */
public class HttpClient {
    final OkHttpClient okHttpClient;
    private final HttpClient.Interceptor[] interceptors;

    /**
     * Creates HttpClient.
     *
     * @param okHttpClient the {@link okhttp3.OkHttpClient} to use for http calls.
     * @param interceptors the interceptors to inspect and modify request response.
     */
    HttpClient(OkHttpClient okHttpClient, List<HttpClient.Interceptor> interceptors) {
        Objects.requireNonNull(okHttpClient);
        Objects.requireNonNull(interceptors);
        this.okHttpClient = okHttpClient;
        this.interceptors = interceptors.toArray(new HttpClient.Interceptor[0]);
    }

    /**
     * Send the provided request asynchronously.
     *
     * @param request The HTTP request to send
     * @return A {@link Mono} that emits response asynchronously
     */
    public Mono<okhttp3.Response> send(okhttp3.Request request) {
        return Mono.defer(() -> new NextInterceptor(this, interceptors, 0).intercept(request));
    }

    /**
     * @return a new builder which can be used to build a {@link HttpClient} that shares
     * the same connection pool, thread pools, and configurations with this {@link HttpClient}.
     */
    public HttpClientBuilder newBuilder() {
        return new HttpClientBuilder(this.okHttpClient)
                .setInterceptors(Arrays.asList(this.interceptors));
    }

    /**
     * An internal method to send a request asynchronously.
     *
     * @param request The HTTP request to send
     * @return the HTTP response
     */
    private Mono<okhttp3.Response> sendIntern(okhttp3.Request request) {
        return Mono.create(sink -> sink.onRequest(ignored -> {
            Call call = okHttpClient.newCall(request);
            sink.onCancel(() -> call.cancel());
            call.enqueue(new OkHttpCallback(sink));
        }));
    }

    /**
     * An Interceptor to inspect and modify request response.
     */
    public interface Interceptor {
        /**
         * Intercept request response.
         *
         * @param request the request to intercept
         * @param nextInterceptor the next interceptor to be invoked
         * @return the response
         */
        Mono<Response> intercept(Request request, NextInterceptor nextInterceptor);
    }

    public static final class NextInterceptor {
        private final HttpClient.Interceptor[] interceptors;
        private final int currentIndex;
        private final HttpClient httpClient;

        NextInterceptor(HttpClient httpClient,
                        HttpClient.Interceptor[] interceptors,
                        int index) {
            this.httpClient = httpClient;
            this.interceptors = interceptors;
            this.currentIndex = index;
        }

        public Mono<Response> intercept(Request request) {
            if (this.currentIndex >= this.interceptors.length) {
                return this.httpClient.sendIntern(request);
            } else {
                return this.interceptors[this.currentIndex].intercept(request,
                        new NextInterceptor(httpClient, this.interceptors, this.currentIndex + 1));
            }
        }
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
