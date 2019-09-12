package reactor.okhttp.http.client;

import okhttp3.OkHttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builder to configure and build {@link HttpClient}.
 */
public class HttpClientBuilder {
    private final okhttp3.OkHttpClient okHttpClient;
    private List<Consumer<OkHttpClient.Builder>> configurationSetters = new ArrayList<>();
    private List<HttpClient.Interceptor> interceptors = new ArrayList<>();

    /**
     * Creates HttpClientBuilder.
     */
    public HttpClientBuilder() {
        this.okHttpClient = null;
    }

    /**
     * Creates HttpClientBuilder from the builder of an existing {@link okhttp3.OkHttpClient}.
     *
     * @param okHttpClient the OkHttp client
     */
    public HttpClientBuilder(OkHttpClient okHttpClient) {
        this.okHttpClient = Objects.requireNonNull(okHttpClient, "okHttpClient cannot be null.");
    }

    /**
     * Register a configuration setter.
     *
     * The configuration setter will be invoked with {@link okhttp3.OkHttpClient.Builder}
     * when {@link this#build()} is called, the setter can set arbitrary configuration
     * on the builder.
     *
     * @param configurationSetter the configuration setter
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder addConfiguration(Consumer<OkHttpClient.Builder> configurationSetter) {
        Objects.requireNonNull(configurationSetter, "configurationSetter cannot be null.");
        this.configurationSetters.add(configurationSetter);
        return this;
    }

    /**
     * Register a list of configuration setter.
     *
     * The configuration setters will be invoked with the {@link okhttp3.OkHttpClient.Builder}
     * when {@link this#build()} is called, the setters can set arbitrary configurations
     * on the builder.
     *
     * This replaces all previously-set configuration setters.
     *
     * @param configurationSetters the configuration setters
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setConfigurations(List<Consumer<OkHttpClient.Builder>> configurationSetters) {
        Objects.requireNonNull(configurationSetters, "configurationSetters cannot be null.");
        this.configurationSetters = configurationSetters;
        return this;
    }

    /**
     * Sets the read timeout.
     *
     * @param readTimeout the read timeout
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setReadTimeout(Duration readTimeout) {
        this.addConfiguration(builder -> builder.readTimeout(readTimeout));
        return this;
    }

    /**
     * Sets the connection timeout.
     *
     * @param connectionTimeout the connection timeout
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setConnectionTimeout(Duration connectionTimeout) {
        this.addConfiguration(builder -> builder.readTimeout(connectionTimeout));
        return this;
    }

    /**
     * Sets the proxy.
     *
     * @param proxy the proxy
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setProxy(java.net.Proxy proxy) {
        this.addConfiguration(builder -> builder.proxy(proxy));
        return this;
    }

    /**
     * Add an interceptor that observe the full span of each HTTP call.
     *
     * @param interceptor the interceptor
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder addInterceptor(HttpClient.Interceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor cannot be null.");
        this.interceptors.add(interceptor);
        return this;
    }

    /**
     * Add interceptors those observes the full span of each HTTP call.
     *
     * This replaces all previously-set interceptors.
     *
     * @param interceptors the interceptors
     * @return the updated HttpClientBuilder object
     */
    public HttpClientBuilder setInterceptors(List<HttpClient.Interceptor> interceptors) {
        Objects.requireNonNull(configurationSetters, "interceptors cannot be null.");
        this.interceptors = new ArrayList<>(interceptors);
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

        for (Consumer<OkHttpClient.Builder> configurationSetter : this.configurationSetters) {
            configurationSetter.accept(httpClientBuilder);
        }
        return new HttpClient(httpClientBuilder.build(), this.interceptors);
    }
}
