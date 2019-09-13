# reactor-okhttp

Reactor OkHttp offers HTTP client based on [OkHttp](https://github.com/square/okhttp) framework.

# Getting Started

`Reactor OkHttp` requires Java 8 or + to run.

TODO: Add maven dependency section once available in maven.

# Examples

### Simple usage

```java
HttpClient httpClient = new HttpClientBuilder().build();

HttpRequest request = new HttpRequest(HttpMethod.POST, new URL("https://httpbin.org/post"));
request.setBody("I'm a post body!");
request.setHeader("Content-Type", "application/octet-stream");

Mono<HttpResponse> responseMono = httpClient.send(request);

Mono<String> contentMono = responseMono.flatMap((Function<HttpResponse, Mono<String>>) httpResponse -> 
        httpResponse.getBodyAsString());

String content = contentMono.block();

System.out.println(content);

```

### Using interceptor

Reactor OkHttp supports adding interceptor to inspect and modify HTTP Request-Response.

`addInterceptor` method in `HttpClientBuilder` can be used to insert interceptor for the Http calls.

#### Intercept request

Following sample uses interceptor to add a http header to request.

```java
HttpClient httpClient = new HttpClientBuilder()
        .addInterceptor((request, next) -> {
            request.setHeader("hello", "world");
            return next.intercept(request);
        })
        .build();

HttpRequest request = new HttpRequest(HttpMethod.GET, new URL("https://httpbin.org/anything"));

Mono<HttpResponse> responseMono = httpClient.send(request);
HttpResponse response = responseMono.block();
```

#### Intercept response

Following sample uses interceptor to log response content.

```java
HttpClient httpClient = new HttpClientBuilder()
        .addInterceptor((request, next) -> next.intercept(request)
            .flatMap((Function<HttpResponse, Mono<HttpResponse>>) httpResponse -> {
                final HttpResponse bufferedResponse = httpResponse.buffer();
                return bufferedResponse.getBodyAsString().map(content -> {
                    System.out.println(content);
                    return bufferedResponse;
                });
            }))
        .build();

HttpRequest request = new HttpRequest(HttpMethod.GET, new URL("https://httpbin.org/anything"));

Mono<HttpResponse> responseMono = httpClient.send(request);
HttpResponse response = responseMono.block();

```

### Configuring HttpClient

The `addConfiguration` method in `HttpClientBuilder`can be used to register configuration to be applied on underlying `okhttp3.OkHttpClient.Builder`.


#### Setting dispatcher

Following sample uses `addConfiguration` to set thread pool in `okhttp3.OkHttpClient.Builder`.

```java
HttpClient httpClient = new HttpClientBuilder()
        .addConfiguration(builder -> {
            ExecutorService executorService = Executors.newCachedThreadPool(r -> new Thread(r));
            builder.dispatcher(new Dispatcher(executorService));
        })
        .build();

HttpRequest request = new HttpRequest(HttpMethod.GET, new URL("https://httpbin.org/anything"));

Mono<HttpResponse> responseMono = httpClient.send(request);
responseMono.block();
```