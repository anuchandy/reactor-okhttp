package reactor.okhttp.http.client;

import okhttp3.Interceptor;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class HttpInterceptorWrapper implements Interceptor {
    /**
     * DevNote: each wrapper creates 4 additional objects when intercept is called:
     *   1. HttpRequest object from okhttp3.Request
     *   2. okhttp3.Request object from HttpRequest
     *   3. HttpResponse object from okhttp3.Response
     *   4. okhttp3.Response object from HttpResponse
     */
    //
    //
    /**
     * The HttpInterceptor this type wraps.
     */
    private final HttpInterceptor inner;

    public HttpInterceptorWrapper(HttpInterceptor httpInterceptor) {
        this.inner = httpInterceptor;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        HttpRequest httpRequest = toHttpRequest(chain.request());
        HttpResponse httpResponse = this.inner.send(new Next(chain), httpRequest);
        return toOkHttpResponse(httpResponse);
    }


    private static HttpRequest toHttpRequest(okhttp3.Request request) {
        // build HttpRequest from okHttp Request.
        return new HttpRequest();
    }

    private static  okhttp3.Response toOkHttpResponse(HttpResponse httpResponse) {
        // build okhttp3.Response from HttpResponse
        return new okhttp3.Response.Builder()
                .build();
    }
}
