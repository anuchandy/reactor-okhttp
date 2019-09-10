package reactor.okhttp.http.client;

import okhttp3.Interceptor;

import java.io.IOException;

public final class Next {
    private Interceptor.Chain chain;

    Next(Interceptor.Chain chain) {
        this.chain = chain;
    }

    public HttpResponse send(HttpRequest request) throws IOException {
        return toHttpResponse(chain.proceed(toOkHttpRequest(request)));
    }

    private static okhttp3.Request toOkHttpRequest(HttpRequest request) {
        // create okhttp request
        return new okhttp3.Request.Builder()
                .build();
    }

    private static HttpResponse toHttpResponse(okhttp3.Response okHttpResponse) {
        // create HttpResponse from okHttpResponse
        return new HttpResponse();
    }
}
