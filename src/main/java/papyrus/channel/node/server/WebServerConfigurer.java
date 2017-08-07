package papyrus.channel.node.server;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NegotiatingServerConnectionFactory;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.Ssl;
import org.springframework.boot.context.embedded.jetty.JettyEmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.jetty.JettyServerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.ResourceUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;

import com.fasterxml.jackson.databind.SerializationFeature;

import blitz.core.api.auth.CurrentUserResolver;

@Configuration
@EnableConfigurationProperties({BlitzServerProperties.class, ServerProperties.class})
public class WebServerConfigurer extends WebMvcConfigurerAdapter {
    private static final Logger log = LoggerFactory.getLogger(WebServerConfigurer.class);

    @Autowired
    private CurrentUserResolver currentUserResolver;
    
    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        Jackson2ObjectMapperBuilder b = new Jackson2ObjectMapperBuilder();
        b.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return b;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(currentUserResolver);
    }


    @Bean
    public EmbeddedServletContainerCustomizer servletContainerCustomizer(BlitzServerProperties blitz, ServerProperties serverProperties) {
        return new EmbeddedServletContainerCustomizer() {

            @Override
            public void customize(ConfigurableEmbeddedServletContainer container) {
                if (container instanceof JettyEmbeddedServletContainerFactory) {
                    configureJetty((JettyEmbeddedServletContainerFactory) container);
                }
            }

            private void configureJetty(JettyEmbeddedServletContainerFactory jettyFactory) {
                jettyFactory.addServerCustomizers(new JettyServerCustomizer() {

                    @Override
                    public void customize(Server server) {
                        HttpConfiguration config = new HttpConfiguration();
                        config.setSecureScheme("https");
                        config.setSecurePort(blitz.getPort());
                        config.setSendXPoweredBy(false);
                        config.setSendServerVersion(false);
                        config.addCustomizer(new ForwardedRequestCustomizer());
                        config.addCustomizer(new SecureRequestCustomizer());

                        HttpConnectionFactory http1 = new HttpConnectionFactory(config);
                        
                        //enable stuff for HTTP/2
                        HTTP2ServerConnectionFactory http2 = null;
                        ALPNServerConnectionFactory alpn = null;
                        try {
                            http2 = new HTTP2ServerConnectionFactory(config);
                            NegotiatingServerConnectionFactory.checkProtocolNegotiationAvailable();
                            alpn = new ALPNServerConnectionFactory();
                            alpn.setDefaultProtocol(http1.getProtocol());
                        } catch (IllegalStateException e) {
                            log.error("No ALPN available, HTTP2 will be disabled: " + e.toString());
                        }

                        // SSL Connection Factory
                        SslContextFactory sslContextFactory = createSslContextFactory(serverProperties);
                        SslConnectionFactory ssl = new SslConnectionFactory(sslContextFactory, alpn != null ? alpn.getProtocol() : http1.getProtocol());

                        List<Connector> connectors = new ArrayList<>(3);
                                                                                                            
                        ServerConnector sslConnector = new ServerConnector(server, removeNulls(ssl, alpn, http2, http1));
                        sslConnector.setPort(blitz.getPort());
                        connectors.add(sslConnector);
                        if (blitz.getHttp1SslPort() != null) {
                            SslConnectionFactory sslToHttp1 = new SslConnectionFactory(sslContextFactory, http1.getProtocol());
                            ServerConnector proxyConnector = new ServerConnector(server, sslToHttp1, http1);
                            proxyConnector.setPort(blitz.getHttp1SslPort());
                            connectors.add(proxyConnector);
                        }
                        if (blitz.getProxyPortSsl() != null) {
                            //add support for AWS/GCE load balancers proxy protocol
                            ProxyConnectionFactory proxy = new ProxyConnectionFactory(ssl.getProtocol());
                            //create proxy connector
                            ServerConnector proxyConnector = new ServerConnector(server, removeNulls(proxy, ssl, alpn, http2, http1));
                            proxyConnector.setPort(blitz.getProxyPortSsl());
                            connectors.add(proxyConnector);
                        }
                        if (blitz.getProxyPort() != null) {
                            //create proxy connector
                            //add support for AWS/GCE load balancers proxy protocol
                            ProxyConnectionFactory proxy = new ProxyConnectionFactory(HttpVersion.HTTP_1_1.asString());
                            ServerConnector proxyConnector = new ServerConnector(server, proxy, http1);
                            proxyConnector.setPort(blitz.getProxyPort());
                            connectors.add(proxyConnector);
                        }
                        if (blitz.getHttpPort() != null) {
                            //create plain http connector
                            ServerConnector connector = new ServerConnector(server, http1);
                            connector.setPort(blitz.getHttpPort());
                            connectors.add(connector);
                        }
                        server.setConnectors(connectors.toArray(new Connector[0]));
                    }
                });
            }
        };
    }
    
    private static ConnectionFactory[] removeNulls(ConnectionFactory... factories) {
        return Arrays.stream(factories).filter(Objects::nonNull).toArray(ConnectionFactory[]::new);
    }

    @Bean
    public SslContextFactory createSslContextFactory(ServerProperties properties) {
        Ssl sslProps = properties.getSsl();
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStoreType(sslProps.getKeyStoreType());
        sslContextFactory.setKeyStoreResource(getResource(sslProps.getKeyStore()));
        sslContextFactory.setKeyStorePassword(sslProps.getKeyStorePassword());
        sslContextFactory.setKeyManagerPassword(sslProps.getKeyPassword());
        sslContextFactory.setCertAlias(sslProps.getKeyAlias());
        sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
        sslContextFactory.setUseCipherSuitesOrder(true);
        return sslContextFactory;
    }

    public Resource getResource(String location) {
        try {
            return Resource.newResource(ResourceUtils.getURL(location));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
