package reactor.okhttp.http.client;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.ByteString;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Reactor based OKHttp HTTP client.
 */
public class HttpClient {
    final OkHttpClient okHttpClient;
    private final HttpClient.Interceptor[] interceptors;

    private static final Mono<okio.ByteString> EMPTY_BYTE_STRING_MONO = Mono.just(okio.ByteString.EMPTY);
    private static final MediaType MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream");

    /**
     * Creates HttpClient.
     *
     * The provided {@code interceptors} will be invoked in the order they appear in the list.
     *
     * @param okHttpClient the {@link okhttp3.OkHttpClient} to use internally for http calls
     * @param interceptors the interceptors to inspect and modify HTTP Request and Response
     */
    HttpClient(OkHttpClient okHttpClient, List<HttpClient.Interceptor> interceptors) {
        Objects.requireNonNull(okHttpClient);
        Objects.requireNonNull(interceptors);
        this.okHttpClient = okHttpClient;
        this.interceptors = interceptors.toArray(new HttpClient.Interceptor[0]);
    }

    /**
     * Send the provided HTTP Request asynchronously.
     *
     * @param request the HTTP Request to send
     * @return a {@link Mono} that emits HTTP Response asynchronously
     */
    public Mono<HttpResponse> send(HttpRequest request) {
        return Mono.defer(() -> new NextInterceptor(this, interceptors, 0).intercept(request));
    }

    /**
     * Get a new builder which can be used to build a {@link HttpClient} that shares
     * the same connection pool, thread pools and configurations with this {@link HttpClient}.
     *
     * @return the {@link HttpClientBuilder}
     */
    public HttpClientBuilder newBuilder() {
        return new HttpClientBuilder(this.okHttpClient)
                .setInterceptors(Arrays.asList(this.interceptors));
    }

    /**
     * An internal method to create Mono, upon subscription it schedule the http call
     * to OkHttp thread pool for asynchronous execution of HTTP Request and once completes
     * it emits the HTTP Response.
     *
     * @param request the HTTP Request to send
     * @return the HTTP Response mono
     */
    private Mono<HttpResponse> sendIntern(HttpRequest request) {
        return Mono.create(sink -> sink.onRequest(ignored -> {
            toOkHttpRequest(request).subscribe(okHttpRequest -> {
                Call call = okHttpClient.newCall(okHttpRequest);
                call.enqueue(new OkHttpEnqueueCallback(sink, request));
                sink.onCancel(() -> call.cancel());
            }, sink::error);
        }));
    }

    /**
     * Converts the given {@link HttpRequest} to {@link okhttp3.Request}.
     *
     * @param request the HTTP Request
     * @return the Mono emitting OkHttp Request
     */
    private static Mono<okhttp3.Request> toOkHttpRequest(HttpRequest request) {
        return Mono.just(new okhttp3.Request.Builder())
                .map(rb -> {
                    rb.url(request.getUrl());
                    if (request.getHeaders() != null) {
                        return rb.headers(okhttp3.Headers.of(request.getHeaders().toMap()));
                    } else {
                        return rb.headers(okhttp3.Headers.of(new HashMap<>()));
                    }
                })
                .flatMap((Function<Request.Builder, Mono<Request.Builder>>) rb -> {
                    if (request.getHttpMethod() == HttpMethod.GET) {
                        return Mono.just(rb.get());
                    } else if (request.getHttpMethod() == HttpMethod.HEAD) {
                        return Mono.just(rb.head());
                    } else {
                        return toOkHttpRequestBody(request.getBody(), request.getHeaders())
                                .map(requestBody -> rb.method(request.getHttpMethod().toString(), requestBody));
                    }
                })
                .map(rb -> rb.build());
    }

    /**
     * Create a Mono of {@link okhttp3.ResponseBody} from the given {@link java.nio.ByteBuffer} Flux.
     *
     * @param bbFlux stream of ByteBuffer representing HTTP Request content
     * @param headers the headers associated with the HTTP Request
     * @return the Mono emitting {@link okhttp3.ResponseBody}
     */
    private static Mono<RequestBody> toOkHttpRequestBody(Flux<ByteBuffer> bbFlux, HttpHeaders headers) {
        Mono<okio.ByteString> bsMono = bbFlux == null
                ? EMPTY_BYTE_STRING_MONO
                : toByteString(bbFlux);

        return bsMono.map(bs -> {
            String contentType = headers.value("Content-Type");
            if (contentType == null) {
                return RequestBody.create(bs, MEDIA_TYPE_OCTET_STREAM);
            } else {
                return RequestBody.create(bs, MediaType.parse(contentType));
            }
        });
    }

    /**
     * Aggregate Flux of {@link java.nio.ByteBuffer} to single {@link okio.ByteString}.
     *
     * Pooled {@link okio.Buffer} type is used to buffer emitted ByteBuffer instances.
     * Content of each ByteBuffer will be written (i.e copied) to the internal okio.Buffer
     * slots. Once the stream terminates, the contents of all slots get copied to
     * one single byte array and okio.ByteString will be created referring this byte array.
     * Finally the initial okio.Buffer will be returned to the pool.
     *
     * @param bbFlux the Flux of ByteBuffer to aggregate
     * @return a mono emitting aggregated ByteString
     */
    private static Mono<ByteString> toByteString(Flux<ByteBuffer> bbFlux) {
        Objects.requireNonNull(bbFlux);
        return Mono.using(okio.Buffer::new,
                buffer -> bbFlux.reduce(buffer, (b, byteBuffer) -> {
                    try {
                        b.write(byteBuffer);
                        return b;
                    } catch (IOException ioe) {
                        throw Exceptions.propagate(ioe);
                    }
                })
                        .map(b -> ByteString.of(b.readByteArray())),
                okio.Buffer::clear)
                .switchIfEmpty(EMPTY_BYTE_STRING_MONO);
    }

    /**
     * An Interceptor to inspect and modify HTTP Request-Response.
     */
    public interface Interceptor {
        /**
         * Intercept HTTP Request and Response.
         *
         * @param request the HTTP Request to intercept
         * @param nextInterceptor the next interceptor that returns Mono emitting
         *                        HTTP Response from the next interceptor
         * @return a Mono emitting HTTP Response
         */
        Mono<HttpResponse> intercept(HttpRequest request, NextInterceptor nextInterceptor);
    }

    /**
     * Handler to invoke next interceptor.
     */
    public static final class NextInterceptor {
        private final HttpClient.Interceptor[] interceptors;
        private final int currentIndex;
        private final HttpClient httpClient;

        /**
         * Creates NextInterceptor.
         *
         * @param httpClient the HTTP client for sending Request to wire
         * @param interceptors the interceptors
         * @param index the index of interceptor this handler invokes upon
         *              calling {@link this#intercept(HttpRequest)}
         */
        NextInterceptor(HttpClient httpClient,
                        HttpClient.Interceptor[] interceptors,
                        int index) {
            this.httpClient = httpClient;
            this.interceptors = interceptors;
            this.currentIndex = index;
        }

        /**
         * Send the HTTP Request to next interceptor for processing.
         *
         * @param request the HTTP Request
         * @return a Mono emitting HTTP Response
         */
        public Mono<HttpResponse> intercept(HttpRequest request) {
            if (this.currentIndex >= this.interceptors.length) {
                return this.httpClient.sendIntern(request);
            } else {
                return this.interceptors[this.currentIndex].intercept(request,
                        new NextInterceptor(httpClient, this.interceptors, this.currentIndex + 1));
            }
        }
    }

    /**
     * An implementation of {@link okhttp3.Callback} to receive result of HTTP Request
     * execution and to redirect it to {@code MonoSink}.
     */
    private static class OkHttpEnqueueCallback implements okhttp3.Callback {
        private final MonoSink<HttpResponse> sink;
        private final HttpRequest request;

        OkHttpEnqueueCallback(MonoSink<HttpResponse> sink, HttpRequest request) {
            this.sink = sink;
            this.request = request;
        }

        @Override
        public void onFailure(okhttp3.Call call, IOException e) {
            sink.error(e);
        }

        @Override
        public void onResponse(okhttp3.Call call, okhttp3.Response response) {
            sink.success(new HttpResponse(response, request));
        }
    }

}
