package reactor.okhttp.http.client;

import okhttp3.Headers;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import okio.BufferedSource;

/**
 *The incoming HTTP Response.
 */
public final class HttpResponse implements Closeable {
    private final HttpRequest request;
    private final int statusCode;
    private final HttpHeaders headers;
    private final Mono<ResponseBody> responseBodyMono;
    // using 4K as default buffer size: https://stackoverflow.com/a/237495/1473510
    private static final int BYTE_BUFFER_CHUNK_SIZE = 4096;

    /**
     * Creates a HttpResponse from {@link okhttp3.Response}.
     *
     * @param innerResponse the OkHttp Response
     * @param request the HTTP Request corresponds to the Response
     */
    HttpResponse(Response innerResponse, HttpRequest request) {
        this.request = request;
        this.statusCode = innerResponse.code();
        this.headers = fromOkHttpHeaders(innerResponse.headers());
        if (innerResponse.body() == null) {
            // innerResponse.body() getter will not return null for server returned responses.
            // It can be null:
            // [a]. if response is built manually with null body (e.g for mocking)
            // [b]. for the cases described here
            // [ref](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response/body/).
            //
            this.responseBodyMono = Mono.empty();
        } else {
            this.responseBodyMono = Mono.using(() -> innerResponse.body(),
                    rb -> Mono.just(rb),
                    // Resource cleanup
                    // square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/#the-response-body-must-be-closed
                    ResponseBody::close);
        }
    }

    /**
     * Creates a HttpResponse which will buffer the response's body when/if it is read.
     *
     * @param responseBodyMono the response body to buffer
     * @param statusCode the status code
     * @param headers the headers
     * @param request the HTTP Request corresponds to the Response
     */
    HttpResponse(Mono<ResponseBody> responseBodyMono, int statusCode, HttpHeaders headers, HttpRequest request) {
        this.request = request;
        this.statusCode = statusCode;
        this.headers = headers;
        this.responseBodyMono = responseBodyMono.map(responseBody -> {
            BufferedSource bufferedSource = responseBody.source();
            try {
                bufferedSource.request(Long.MAX_VALUE);
            } catch (IOException ioe) {
                throw Exceptions.propagate(ioe);
            }
            Buffer bufferedContent = bufferedSource.getBuffer();
            return ResponseBody.create(bufferedContent.readByteArray(), responseBody.contentType());
        })
        .cache();
    }

    /**
     * @return the status code
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * Get value of a header with given name.
     *
     * @param name the name of the header to lookup
     * @return the header value if exists, null otherwise
     */
    public String getHeaderValue(String name) {
        return this.headers.value(name);
    }

    /**
     * @return the esponse headers
     */
    public HttpHeaders getHeaders() {
        return this.headers;
    }

    /**
     * @return a Flux emitting response body chunks as {@link java.nio.ByteBuffer}.
     *
     */
    public Flux<ByteBuffer> getBody() {
        return this.responseBodyMono
                .flatMapMany(irb -> toFluxByteBuffer(irb.byteStream()));
    }

    /**
     * @return a Mono emitting aggregated response body as byte array.
     */
    public Mono<byte[]> getBodyAsByteArray() {
        return this.responseBodyMono
                .flatMap(rb -> {
                    try {
                        byte[] content = rb.bytes();
                        return content.length == 0 ? Mono.empty() : Mono.just(content);
                    } catch (IOException ioe) {
                        throw Exceptions.propagate(ioe);
                    }
                });
    }

    /**
     * @return a Mono emitting aggregated response body as string.
     */
    public Mono<String> getBodyAsString() {
        return this.responseBodyMono
                .flatMap(rb -> {
                    try {
                        String content = rb.string();
                        return content.length() == 0 ? Mono.empty() : Mono.just(content);
                    } catch (IOException ioe) {
                        throw Exceptions.propagate(ioe);
                    }
                });
    }

    /**
     * Get Mono emitting aggregated response body as string.
     *
     * @param charset the charset to use
     * @return Mono emitting aggregated response body as string
     */
    public Mono<String> getBodyAsString(Charset charset) {
        return getBodyAsByteArray()
                .map(bytes -> new String(bytes, charset));
    }

    /**
     * Get a new Response object wrapping this response with it's content
     * buffered into memory.
     *
     * @return the new Response object
     */
    public HttpResponse buffer() {
        return new HttpResponse(this.responseBodyMono, this.statusCode, this.headers, this.request);
    }

    @Override
    public void close() {
        this.responseBodyMono.subscribe().dispose();
    }

    /**
     * Creates {@link HttpHeaders} from {@link okhttp3.Headers}.
     *
     * @param headers OkHttp headers
     * @return the HttpHeaders
     */
    private static HttpHeaders fromOkHttpHeaders(Headers headers) {
        HttpHeaders httpHeaders = new HttpHeaders();
        for (String headerName : headers.names()) {
            httpHeaders.put(headerName, headers.get(headerName));
        }
        return httpHeaders;
    }

    /**
     * Creates a Flux of ByteBuffer, with each ByteBuffer wrapping bytes read from the given
     * InputStream.
     *
     * @param inputStream InputStream to back the Flux
     * @return Flux of ByteBuffer backed by the InputStream
     */
    private static Flux<ByteBuffer> toFluxByteBuffer(InputStream inputStream) {
        Pair pair = new Pair();
        return Flux.just(true)
                .repeat()
                .map(ignore -> {
                    byte[] buffer = new byte[BYTE_BUFFER_CHUNK_SIZE];
                    try {
                        int numBytes = inputStream.read(buffer);
                        if (numBytes > 0) {
                            return pair.buffer(ByteBuffer.wrap(buffer, 0, numBytes)).readBytes(numBytes);
                        } else {
                            return pair.buffer(null).readBytes(numBytes);
                        }
                    } catch (IOException ioe) {
                        throw Exceptions.propagate(ioe);
                    }
                })
                .takeUntil(p -> p.readBytes() == -1)
                .filter(p -> p.readBytes() > 0)
                .map(Pair::buffer);
    }

    private static class Pair {
        private ByteBuffer byteBuffer;
        private int readBytes;

        ByteBuffer buffer() {
            return this.byteBuffer;
        }

        int readBytes() {
            return this.readBytes;
        }

        Pair buffer(ByteBuffer byteBuffer) {
            this.byteBuffer = byteBuffer;
            return this;
        }

        Pair readBytes(int cnt) {
            this.readBytes = cnt;
            return this;
        }
    }
}