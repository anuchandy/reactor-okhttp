package reactor.okhttp.http.client;

import java.io.IOException;

public abstract class HttpInterceptor {
    public abstract HttpResponse send(Next next, HttpRequest request) throws IOException;
}
