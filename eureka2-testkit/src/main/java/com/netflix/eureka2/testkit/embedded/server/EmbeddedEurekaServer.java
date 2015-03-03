package com.netflix.eureka2.testkit.embedded.server;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.health.EurekaHealthStatusModule;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.spi.ExtensionLoader;
import com.netflix.eureka2.server.utils.guice.PostInjectorModule;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.guice.LifecycleInjectorBuilder;
import com.netflix.governator.guice.transformer.OverrideAllDuplicateBindings;
import com.netflix.governator.lifecycle.LifecycleManager;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.metrics3.MetricsRegistry;
import netflix.adminresources.AdminResourcesContainer;
import netflix.adminresources.resources.Eureka2ClientProviderImpl;

/**
 * @author Tomasz Bak
 */
public abstract class EmbeddedEurekaServer<C extends EurekaCommonConfig, R> {
    private final boolean withExt;
    private final boolean withAdminUI;
    private final ServerType serverType;
    protected final C config;

    protected Injector injector;
    protected Injector webAdminInjector;
    protected LifecycleManager lifecycleManager;

    protected EmbeddedEurekaServer(ServerType serverType, C config, boolean withExt, boolean withAdminUI) {
        this.serverType = serverType;
        this.config = config;
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
    }

    public abstract void start();

    public void shutdown() {
        lifecycleManager.close();
    }

    public Injector getInjector() {
        return injector;
    }

    public SourcedEurekaRegistry<InstanceInfo> getEurekaServerRegistry() {
        return injector.getInstance(SourcedEurekaRegistry.class);
    }

    public int getWebAdminPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return webAdminInjector == null ? -1 : webAdminInjector.getInstance(AdminResourcesContainer.class).getServerPort();
    }

    public int getHttpServerPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(EurekaHttpServer.class).serverPort();
    }

    protected abstract ServerResolver getInterestServerResolver();

    public abstract R serverReport();

    protected void setup(Module[] modules) {
        LifecycleInjectorBuilder builder = LifecycleInjector.builder()
                .withModuleTransformer(new OverrideAllDuplicateBindings());
        builder.withAdditionalModules(PostInjectorModule.forLifecycleInjectorBuilder(builder));
        builder.withAdditionalModules(modules);

        // Extensions
        builder.withAdditionalModules(new ExtensionLoader(!withExt).asModuleArray(serverType));

        EmbeddedKaryonAdminModule adminUIModule = null;
        if (withAdminUI) {
            adminUIModule = createAdminUIModule(builder);
            if (adminUIModule != null) {
                adminUIModule.bindKaryonAdminEnvironment(builder);
            }
        }

        bindMetricsRegistry(builder);

        EurekaHealthStatusModule healthStatusModule = new EurekaHealthStatusModule();
        builder.withAdditionalModules(healthStatusModule);

        injector = builder.build().createInjector();

        lifecycleManager = injector.getInstance(LifecycleManager.class);
        try {
            lifecycleManager.start();

            // Admin console
            if (adminUIModule != null) {
                webAdminInjector = injector.createChildInjector(adminUIModule);
                // This is hack to force warming up adminUI singletons, that read Archaius parameters,
                // which itself is singleton, and changes values for each subsequently created new server.
                adminUIModule.connectToAdminUI();
            }
        } catch (Exception e) {
            throw new RuntimeException("Container setup failure", e);
        }
    }

    protected EmbeddedKaryonAdminModule createAdminUIModule(LifecycleInjectorBuilder builder) {
        return new EmbeddedKaryonAdminModule() {

            @Override
            protected Properties getProperties() {
                Properties props = new Properties();
                loadInstanceProperties(props);
                return props;
            }

            @Override
            protected int getEurekaWebAdminPort() {
                return config.getWebAdminPort();
            }

            @Override
            protected int getEurekaHttpServerPort() {
                return getHttpServerPort();
            }

            @Override
            protected ServerResolver getInterestResolver() {
                return getInterestServerResolver();
            }
        };
    }

    protected void bindMetricsRegistry(LifecycleInjectorBuilder bootstrapBinder) {
        bootstrapBinder.withAdditionalModules(new AbstractModule() {
            @Override
            protected void configure() {
                MetricRegistry internalRegistry = new MetricRegistry();
                final JmxReporter reporter = JmxReporter.forRegistry(internalRegistry).build();
                reporter.start();

                ExtendedRegistry registry = new ExtendedRegistry(new MetricsRegistry(Clock.SYSTEM, internalRegistry));
                bind(ExtendedRegistry.class).toInstance(registry);
            }
        });
    }

    protected void loadInstanceProperties(Properties props) {
        props.setProperty(AdminResourcesContainer.CONTAINER_LISTEN_PORT, Integer.toString(config.getWebAdminPort()));
        props.setProperty("netflix.platform.admin.pages.packages", "netflix");

        // TODO Until admin WEB configuration is more flexible we take port of first write server
        String writeServer = config.getServerList()[0];
        Matcher matcher = Pattern.compile("[^:]+:\\d+:(\\d+):\\d+").matcher(writeServer);
        if (matcher.matches()) {
            String interestPort = matcher.group(1);
            props.setProperty(Eureka2ClientProviderImpl.CONFIG_DISCOVERY_PORT, interestPort);
        }
    }

}
