package reactor.okhttp.http.client;

import okhttp3.Authenticator;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Builder to configure and build an implementation Reactor OkHttp Client.
 */
public class HttpClientBuilder {
    private final okhttp3.OkHttpClient okHttpClient;

    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(60);

    private List<Interceptor> networkInterceptors = new ArrayList<>();
    private List<Interceptor> httpInterceptors = new ArrayList<>();
    private Duration readTimeout;
    private Duration connectionTimeout;
    private ConnectionPool connectionPool;
    private Dispatcher dispatcher;
    private java.net.Proxy proxy;
    private Authenticator proxyAuthenticator;
    private List<Consumer<OkHttpClient.Builder>> configurationSetters = new ArrayList<>();

    /**
     * Creates HttpClientBuilder.
     */
    public HttpClientBuilder() {
        this.okHttpClient = null;
    }

    /**
     * Creates HttpClientBuilder from the builder of an existing OkHttpClient.
     *
     * @param okHttpClient the httpclient
     */
    public HttpClientBuilder(OkHttpClient okHttpClient) {
        this.okHttpClient = Objects.requireNonNull(okHttpClient, "okHttpClient cannot be null.");
    }

    /**
     * Add a network layer interceptor to Http request pipeline.
     *
     * @param networkInterceptor the network interceptor to add
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder addNetworkInterceptor(Interceptor networkInterceptor) {
        Objects.requireNonNull(networkInterceptor, "networkInterceptor cannot be null.");
        this.networkInterceptors.add(networkInterceptor);
        return this;
    }

    /**
     * Add network layer interceptors to Http request pipeline.
     *
     * This replaces all previously-set network interceptors.
     *
     * @param networkInterceptors the network interceptors to set
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setNetworkInterceptors(List<Interceptor> networkInterceptors) {
        this.networkInterceptors = Objects.requireNonNull(networkInterceptors, "networkInterceptors cannot be null.");
        return this;
    }

    /**
     * Add a http layer interceptor to Http request pipeline.
     *
     * @param httpInterceptor the http interceptor to add
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder addHttpInterceptor(Interceptor httpInterceptor) {
        Objects.requireNonNull(httpInterceptor, "httpInterceptor cannot be null.");
        this.httpInterceptors.add(httpInterceptor);
        return this;
    }

    /**
     * Add http layer httpInterceptors to Http request pipeline.
     *
     * This replaces all previously-set http Interceptors.
     *
     * @param httpInterceptors the http interceptors to set
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setHttpInterceptors(List<Interceptor> httpInterceptors) {
        this.httpInterceptors = Objects.requireNonNull(httpInterceptors, "httpInterceptors cannot be null.");
        return this;
    }

    /**
     * Sets the read timeout.
     *
     * The default read timeout is 120 seconds.
     *
     * @param readTimeout the read timeout
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setReadTimeout(Duration readTimeout) {
        // setReadTimeout can be null
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * Sets the connection timeout.
     *
     * The default connection timeout is 60 seconds.
     *
     * @param connectionTimeout the connection timeout
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setConnectionTimeout(Duration connectionTimeout) {
        // setConnectionTimeout can be null
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Sets the Http connection pool.
     *
     * @param connectionPool the OkHttp connection pool to use
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setConnectionPool(ConnectionPool connectionPool) {
        // Null ConnectionPool is not allowed
        this.connectionPool = Objects.requireNonNull(connectionPool, "connectionPool cannot be null.");
        return this;
    }

    /**
     * Sets the dispatcher that also composes the thread pool for executing HTTP requests.
     *
     * @param dispatcher the dispatcher to use
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setDispatcher(Dispatcher dispatcher) {
        // Null Dispatcher is not allowed
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null.");
        return this;
    }

    /**
     * Sets the proxy.
     *
     * @param proxy the proxy
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setProxy(java.net.Proxy proxy) {
        // Proxy can be null
        this.proxy = proxy;
        return this;
    }

    /**
     * Sets the proxy authenticator.
     *
     * @param proxyAuthenticator the proxy authenticator
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setProxyAuthenticator(Authenticator proxyAuthenticator) {
        // Null Authenticator is not allowed
        this.proxyAuthenticator = Objects.requireNonNull(proxyAuthenticator, "proxyAuthenticator cannot be null.");
        return this;
    }

    /**
     * Register a configuration setter.
     *
     * The configuration setters will be invoked with {@link okhttp3.OkHttpClient.Builder} when {@link this#build()}
     * gets called, the setter can set any arbitrary configuration on the builder.
     *
     * @param configurationSetter the configuration setter
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setConfiguration(Consumer<OkHttpClient.Builder> configurationSetter) {
        Objects.requireNonNull(configurationSetter, "configurationSetter cannot be null.");
        this.configurationSetters.add(configurationSetter);
        return this;
    }

    /**
     * Register a list of configuration setter.
     *
     * The configuration setters will be invoked with the okHttp client builder when {@link this#build()}
     * gets called, the setter can set any arbitrary configuration on the builder.
     *
     * This replaces all previously-set configuration setters.
     *
     * @param configurationSetters the configuration setters
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setConfiguration(List<Consumer<OkHttpClient.Builder>> configurationSetters) {
        Objects.requireNonNull(configurationSetters, "configurationSetters cannot be null.");
        this.configurationSetters = configurationSetters;
        return this;
    }

    /**
     * Build a HttpClient with current configurations.
     *
     * @return a {@link HttpClient}.
     */
    public HttpClient build() {
        OkHttpClient.Builder httpClientBuilder = this.okHttpClient == null
                ? new OkHttpClient.Builder()
                : this.okHttpClient.newBuilder();

        for (Interceptor interceptor : this.networkInterceptors) {
            httpClientBuilder = httpClientBuilder.addNetworkInterceptor(interceptor);
        }

        for (Interceptor interceptor : this.httpInterceptors) {
            httpClientBuilder = httpClientBuilder.addInterceptor(interceptor);
        }

        if (this.readTimeout != null) {
            httpClientBuilder = httpClientBuilder.readTimeout(this.readTimeout);
        } else {
            httpClientBuilder = httpClientBuilder.readTimeout(DEFAULT_READ_TIMEOUT);
        }
        if (this.connectionTimeout != null) {
            httpClientBuilder = httpClientBuilder.connectTimeout(this.connectionTimeout);
        } else {
            httpClientBuilder = httpClientBuilder.connectTimeout(DEFAULT_CONNECT_TIMEOUT);
        }
        if (this.connectionPool != null) {
            httpClientBuilder = httpClientBuilder.connectionPool(connectionPool);
        }
        if (this.dispatcher != null) {
            httpClientBuilder = httpClientBuilder.dispatcher(dispatcher);
        }
        httpClientBuilder = httpClientBuilder.proxy(this.proxy);
        if (this.proxyAuthenticator != null) {
            httpClientBuilder = httpClientBuilder.authenticator(this.proxyAuthenticator);
        }
        for (Consumer<OkHttpClient.Builder> configurationSetter : this.configurationSetters) {
            configurationSetter.accept(httpClientBuilder);
        }
        return new HttpClient(httpClientBuilder.build());
    }
}
