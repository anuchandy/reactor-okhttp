package reactor.okhttp.http.client;

import okhttp3.Call;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class HttpClientTests {
    @Rule public final MockWebServer server = new MockWebServer();

    @Test
    public void canGet() {
        final String body = "hello";
        final String hdrName = "hdr1";
        final String hdrVal = "val1";

        server.enqueue(new MockResponse()
                .setBody(body)
                .addHeader(hdrName, hdrVal));

        HttpClient client = new HttpClientBuilder()
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.POST, endpointFor(server));
        request.setHeader(hdrName, hdrVal);
        request.setBody("hello");

        StepVerifier.create(client.send(request))
                .thenRequest(1)
                .assertNext(r -> {
                    Assert.assertNotNull(r);
                    Assert.assertNotNull(r.getHeaders());
                    Assert.assertEquals(hdrVal, r.getHeaderValue(hdrName));
                    Assert.assertNull(r.getHeaderValue("not-exists"));
                    Assert.assertEquals(body, r.getBodyAsString().block());
                })
                .verifyComplete();
    }

    @Test
    public void ensureBackPressureSupport() {
        server.enqueue(new MockResponse());

        HttpClient client = new HttpClientBuilder()
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.GET, endpointFor(server));

        StepVerifierOptions stepVerifierOptions = StepVerifierOptions.create();
        stepVerifierOptions.initialRequest(0);

        StepVerifier.create(client.send(request), stepVerifierOptions)
                .expectNextCount(0) // intercept() does not intercept without request
                .thenRequest(1)
                .expectNextCount(1)
                .thenRequest(1)// intercept() returns mono hence only the first request matter
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    public void ensureOnErrorInvoked() {
        server.enqueue(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));

        HttpClient client = new HttpClientBuilder()
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.GET, endpointFor(server));

        StepVerifier.create(client.send(request))
                .thenRequest(1)
                .verifyError(ConnectException.class);
    }

    @Test
    public void ensureDisposeCancelsCall() {
        server.enqueue(new MockResponse());

        HttpClient client = new HttpClientBuilder()
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.GET, endpointFor(server));

        Disposable disposable = client.send(request).subscribe();
        List<Call> calls = client.okHttpClient.dispatcher().runningCalls();
        Assert.assertEquals(1, calls.size());
        disposable.dispose();
        Assert.assertTrue(calls.get(0).isCanceled());
    }

    @Test
    public void ensureCallCancelDoesNotDispose() {
        server.enqueue(new MockResponse());

        HttpClient client = new HttpClientBuilder()
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.GET, endpointFor(server));

        Disposable disposable = client.send(request).subscribe();
        List<Call> calls = client.okHttpClient.dispatcher().runningCalls();
        Assert.assertEquals(1, calls.size());
        calls.get(0).cancel();
        Assert.assertTrue(calls.get(0).isCanceled());
        Assert.assertFalse(disposable.isDisposed());
    }

    @Test
    public void canIntercept() {
        server.enqueue(new MockResponse());
        List<String> interceptCallOrder = new ArrayList<>();
        //
        HttpClient client = new HttpClientBuilder()
                .addInterceptor((request, nextInterceptor) -> {
                    interceptCallOrder.add("1_processing_request");
                    return nextInterceptor.intercept(request)
                            .map(response -> {
                                interceptCallOrder.add("1_processing_response");
                                return response;
                            });
                })
                .addInterceptor((request, nextInterceptor) -> {
                    interceptCallOrder.add("2_processing_request");
                    return nextInterceptor.intercept(request)
                            .map(response -> {
                                interceptCallOrder.add("2_processing_response");
                                return response;
                            });
                })
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.GET, endpointFor(server));

        StepVerifier.create(client.send(request))
                .thenRequest(1)
                .assertNext(r -> Assert.assertNotNull(r))
                .verifyComplete();

        Assert.assertEquals(4, interceptCallOrder.size());
        Assert.assertEquals("1_processing_request", interceptCallOrder.get(0));
        Assert.assertEquals("2_processing_request", interceptCallOrder.get(1));
        Assert.assertEquals("2_processing_response", interceptCallOrder.get(2));
        Assert.assertEquals("1_processing_response", interceptCallOrder.get(3));
    }

    @Test
    public void ensureBodyDisposedOnRead() {
        server.enqueue(new MockResponse()
                .setBody("hello"));

        HttpClient client = new HttpClientBuilder()
                .build();

        HttpRequest request = new HttpRequest(HttpMethod.POST, endpointFor(server));
        request.setBody("hello");

        Mono<HttpResponse> responseMono = client.send(request);
        HttpResponse response = responseMono.block();
        String body = response.getBodyAsString().block();
        Assert.assertEquals("hello", body);
        //
        try {
            response.getBodyAsString().block();
            Assert.fail("Expected RuntimeException would be thrown.");
        } catch (RuntimeException ise) {
            Assert.assertEquals("closed", ise.getMessage());
        }
    }

    @Test
    public void ensureBodyCanReadAsByteBuffer() {
        server.enqueue(new MockResponse()
                .setBody("hello"));

        HttpClient client = new HttpClientBuilder()
                .build();
        HttpRequest request = new HttpRequest(HttpMethod.POST, endpointFor(server));
        request.setBody("hello");

        String[] data = new String[1];
        Mono<HttpResponse> responseMono = client.send(request);
        Flux<ByteBuffer> bodyFlux = responseMono.block().getBody();
        //
        bodyFlux.doOnNext(byteBuffer -> {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes, 0, bytes.length);
            data[0] = new String(bytes);
        }).blockLast();
        Assert.assertNotNull(data[0]);
        Assert.assertEquals("hello", data[0]);
        //
        // Trying to read body again should throw
        try {
            bodyFlux.blockLast();
            Assert.fail("Expected RuntimeException would be thrown.");
        } catch (RuntimeException re) {
            Assert.assertTrue(re.getCause() instanceof IOException);
        }
    }

    @Test
    public void canBufferResponse() {
        server.enqueue(new MockResponse()
                .setBody("hello"));

        HttpClient client = new HttpClientBuilder()
                .build();
        HttpRequest request = new HttpRequest(HttpMethod.POST, endpointFor(server));
        request.setBody("hello");

        String[] data = new String[1];
        Mono<HttpResponse> responseMono = client.send(request);
        HttpResponse response = responseMono.block().buffer();
        Flux<ByteBuffer> bodyFlux = response.getBody();
        // Read once
        bodyFlux.doOnNext(byteBuffer -> {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes, 0, bytes.length);
            data[0] = new String(bytes);
        }).blockLast();
        Assert.assertNotNull(data[0]);
        Assert.assertEquals("hello", data[0]);
        // Read again
        String result = response.getBodyAsString().block();
        Assert.assertNotNull(result);
        Assert.assertEquals(result,  "hello");
    }

    private static URL endpointFor(MockWebServer server) {
        try {
            return new URL(String.format("http://%s:%s", server.getHostName(), server.getPort()));
        } catch (MalformedURLException me) {
            throw  Exceptions.propagate(me);
        }
    }
}
