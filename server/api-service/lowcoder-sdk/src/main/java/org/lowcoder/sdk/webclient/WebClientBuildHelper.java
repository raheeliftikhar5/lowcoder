package org.lowcoder.sdk.webclient;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.lowcoder.sdk.plugin.common.ssl.DisableVerifySslConfig;
import org.lowcoder.sdk.plugin.common.ssl.SslConfig;
import org.lowcoder.sdk.plugin.common.ssl.SslHelper;
import org.lowcoder.sdk.plugin.common.ssl.VerifySelfSignedCertSslConfig;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.SslProvider;
import reactor.netty.transport.ProxyProvider.Proxy;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Set;

@Slf4j
public class WebClientBuildHelper {

    private static final String proxyHost;
    private static final String proxyPortStr;

    private SslConfig sslConfig;
    private Set<String> disallowedHosts;
    private boolean systemProxy;
    private Long timeoutMs;

    static {
        proxyHost = System.getProperty("http.proxyHost");
        proxyPortStr = System.getProperty("http.proxyPort");
    }

    private WebClientBuildHelper() {
    }

    public static WebClientBuildHelper builder() {
        return new WebClientBuildHelper();
    }

    public WebClientBuildHelper sslConfig(SslConfig sslConfig) {
        this.sslConfig = sslConfig;
        return this;
    }

    public WebClientBuildHelper disallowedHosts(Set<String> disallowedHosts) {
        this.disallowedHosts = disallowedHosts;
        return this;
    }

    public WebClientBuildHelper systemProxy() {
        this.systemProxy = true;
        return this;
    }

    public WebClientBuildHelper timeoutMs(long milliseconds) {
        this.timeoutMs = milliseconds;
        return this;
    }    
    
    public WebClient build() {
        return toWebClientBuilder().build();
    }

    public Builder toWebClientBuilder() {
        HttpClient httpClient = HttpClient.create();
        if (timeoutMs != null)
        {
        	httpClient.responseTimeout(Duration.ofMillis(timeoutMs));
        }
        
        if (sslConfig != null) {
            if (sslConfig instanceof DisableVerifySslConfig) {
                httpClient = httpClient.secure(sslProviderWithoutCertVerify());
            }
            if (sslConfig instanceof VerifySelfSignedCertSslConfig verifySelfSignedCertSslConfig) {
                httpClient = httpClient.secure(sslProviderWithSelfSignedCert(verifySelfSignedCertSslConfig));
            }
        }
        if (systemProxy && StringUtils.isNoneBlank(proxyHost, proxyPortStr)) {
            httpClient = httpClient.proxy(typeSpec -> typeSpec.type(Proxy.HTTP)
                    .host(proxyHost)
                    .port(Integer.parseInt(proxyPortStr)));
        }
        if (CollectionUtils.isNotEmpty(disallowedHosts)) {
            httpClient = httpClient.resolver(new SafeHostResolverGroup(disallowedHosts));
        }
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }

    private static SslProvider sslProviderWithSelfSignedCert(VerifySelfSignedCertSslConfig verifySelfSignedCertSslConfig) {
        try {
            X509Certificate x509Certificate = SslHelper.parseCertificate(verifySelfSignedCertSslConfig.getSelfSignedCert());
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(x509Certificate)
                    .build();
            return SslProvider.builder()
                    .sslContext(sslContext)
                    .build();
        } catch (CertificateException | SSLException e) {
            log.error("parse certificate error", e);
            return SslProvider.defaultClientProvider();
        }
    }

    private static SslProvider sslProviderWithoutCertVerify() {
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            return SslProvider.builder()
                    .sslContext(sslContext)
                    .build();
        } catch (SSLException e) {
            return SslProvider.defaultClientProvider();
        }
    }
}
