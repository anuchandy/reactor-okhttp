package reactor.okhttp.http.client;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;

/**
 * Reactor based OKHttp nextClient.
 */
public class HttpClient {
    final OkHttpClient okHttpClient;
    private final HttpClient parent;
    private final Interceptor interceptor;

    HttpClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.interceptor = null;
        this.parent = null;
    }

    private HttpClient(HttpClient parent, Interceptor interceptor) {
        this.parent = parent;
        this.interceptor = interceptor;
        this.okHttpClient = null;
    }

    public HttpClient registerInterceptor(Interceptor interceptor) {
        return new HttpClient(this, interceptor);
    }

    /**
     * Send the provided request asynchronously.
     *
     * @param request The HTTP request to intercept
     * @return A {@link Mono} that emits response asynchronously
     */
    public Mono<okhttp3.Response> send(okhttp3.Request request) {
        return this.sendIntern(request);
    }

    private Mono<okhttp3.Response> sendIntern(okhttp3.Request request) {
        if (this.parent != null) {
            if (this.interceptor != null) {
                return Mono.defer(() -> interceptor.intercept(request, new NextInterceptor(parent)));
            } else {
                return this.parent.sendIntern(request);
            }
        } else {
            return Mono.create(sink -> {
                Call call = okHttpClient.newCall(request);
                sink.onCancel(() -> call.cancel());
                call.enqueue(new OkHttpCallback(sink));
            });
        }
    }

    public final class NextInterceptor {
        private final HttpClient parent;

        NextInterceptor(HttpClient parent) {
            this.parent = parent;
        }

        public final Mono<Response> intercept(Request request) {
            return this.parent.sendIntern(request);
        }
    }

    public interface Interceptor {
        Mono<Response> intercept(Request request, NextInterceptor nextInterceptor);
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
