package reactor.okhttp.http.client.test;

import reactor.okhttp.http.client.HttpInterceptor;
import reactor.okhttp.http.client.HttpRequest;
import reactor.okhttp.http.client.HttpResponse;
import reactor.okhttp.http.client.Next;

import java.io.IOException;

public class SampleHttpInterceptor extends HttpInterceptor {
    @Override
    public HttpResponse send(Next next, HttpRequest request) throws IOException {
        // process request
        HttpResponse response = next.send(request);
        // process response
        return response;
    }
}
