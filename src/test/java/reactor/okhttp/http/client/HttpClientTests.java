package reactor.okhttp.http.client;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;

import java.io.IOException;
import java.net.ConnectException;
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

        Request request = new Request.Builder()
                .url(endpointFor(server))
                .get()
                .build();

        StepVerifier.create(client.send(request))
                .thenRequest(1)
                .assertNext(r -> {
                    Assert.assertNotNull(r);
                    Assert.assertNotNull(r.headers());
                    Assert.assertEquals(hdrVal, r.headers().get(hdrName));
                    Assert.assertNull(r.headers().get("not-exists"));
                    try {
                        Assert.assertEquals(body, r.body().string());
                    } catch (IOException ioe) {
                        Exceptions.propagate(ioe);
                    }
                })
                .verifyComplete();
    }

    @Test
    public void ensureBackPressureSupport() {
        server.enqueue(new MockResponse());

        HttpClient client = new HttpClientBuilder()
                .build();

        Request request = new Request.Builder()
                .url(endpointFor(server))
                .get()
                .build();

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

        Request request = new Request.Builder()
                .url(endpointFor(server))
                .get()
                .build();

        StepVerifier.create(client.send(request))
                .thenRequest(1)
                .verifyError(ConnectException.class);
    }

    @Test
    public void ensureDisposeCancelsCall() {
        server.enqueue(new MockResponse());

        HttpClient client = new HttpClientBuilder()
                .build();

        Request request = new Request.Builder()
                .url(endpointFor(server))
                .get()
                .build();

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

        Request request = new Request.Builder()
                .url(endpointFor(server))
                .get()
                .build();

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

        Request request = new Request.Builder()
                .url(endpointFor(server))
                .get()
                .build();

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

    private static String endpointFor(MockWebServer server) {
        return String.format("http://%s:%s", server.getHostName(), server.getPort());
    }
}
