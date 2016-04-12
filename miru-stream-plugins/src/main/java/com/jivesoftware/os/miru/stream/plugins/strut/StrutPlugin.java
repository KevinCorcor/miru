package com.jivesoftware.os.miru.stream.plugins.strut;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jivesoftware.os.miru.plugin.Miru;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.plugin.MiruEndpointInjectable;
import com.jivesoftware.os.miru.plugin.plugin.MiruPlugin;
import com.jivesoftware.os.miru.plugin.solution.FstRemotePartitionReader;
import com.jivesoftware.os.miru.plugin.solution.MiruRemotePartition;
import com.jivesoftware.os.routing.bird.health.HealthCheck;
import com.jivesoftware.os.routing.bird.health.HealthCheckResponse;
import com.jivesoftware.os.routing.bird.health.api.HealthCheckConfig;
import com.jivesoftware.os.routing.bird.http.client.HttpResponseMapper;
import com.jivesoftware.os.routing.bird.http.client.TenantAwareHttpClient;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.merlin.config.defaults.DoubleDefault;
import org.merlin.config.defaults.LongDefault;
import org.merlin.config.defaults.StringDefault;

/**
 *
 */
public class StrutPlugin implements MiruPlugin<StrutEndpoints, StrutInjectable> {

    public interface PendingUpdatesHealthCheckConfig extends HealthCheckConfig {

        @Override
        @StringDefault("strut>updates>pending")
        String getName();

        @Override
        @StringDefault("Number of pending updates for strut model scores.")
        String getDescription();

        @LongDefault(10_000L)
        long getUnhealthyAfterNPendingUpdates();

        @DoubleDefault(0.2)
        double getUnhealthyPercent();
    }

    @Override
    public Class<StrutEndpoints> getEndpointsClass() {
        return StrutEndpoints.class;
    }

    @Override
    public Collection<MiruEndpointInjectable<StrutInjectable>> getInjectables(MiruProvider<? extends Miru> miruProvider) {

        StrutConfig config = miruProvider.getConfig(StrutConfig.class);
        TenantAwareHttpClient<String> catwalkHttpClient = miruProvider.getCatwalkHttpClient();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.registerModule(new GuavaModule());

        HttpResponseMapper responseMapper = new HttpResponseMapper(mapper);

        Cache<String, StrutModel> modelCache = CacheBuilder
            .newBuilder()
            .expireAfterWrite(config.getModelCacheExpirationInMillis(), TimeUnit.MILLISECONDS)
            .maximumSize(config.getModelCacheMaxSize())
            .build();

        StrutModelCache cache = new StrutModelCache(catwalkHttpClient, mapper, responseMapper, modelCache);

        ExecutorService asyncExecutorService = Executors.newFixedThreadPool(config.getAsyncThreadPoolSize(),
            new ThreadFactoryBuilder().setNameFormat("strut-async-%d").build());

        Strut strut = new Strut(cache);

        AtomicLong pendingUpdates = new AtomicLong();
        HealthCheck pendingUpdatesHealthCheck = new PendingUpdatesHealthChecker(miruProvider.getConfig(PendingUpdatesHealthCheckConfig.class), pendingUpdates);
        miruProvider.addHealthCheck(pendingUpdatesHealthCheck);

        return Collections.singletonList(new MiruEndpointInjectable<>(
            StrutInjectable.class,
            new StrutInjectable(miruProvider, asyncExecutorService, strut, pendingUpdates, config.getMaxUpdatesBeforeFlush())
        ));
    }

    @Override
    public Collection<MiruRemotePartition<?, ?, ?>> getRemotePartitions(MiruProvider<? extends Miru> miruProvider) {
        FstRemotePartitionReader remotePartitionReader = new FstRemotePartitionReader(miruProvider.getReaderHttpClient(),
            miruProvider.getReaderStrategyCache(),
            false);
        return Arrays.asList(new StrutRemotePartition(remotePartitionReader));
    }

    private static class PendingUpdatesHealthChecker implements HealthCheck {

        private final PendingUpdatesHealthCheckConfig config;
        private final AtomicLong pendingUpdates;

        public PendingUpdatesHealthChecker(PendingUpdatesHealthCheckConfig config, AtomicLong pendingUpdates) {
            this.config = config;
            this.pendingUpdates = pendingUpdates;
        }

        @Override
        public HealthCheckResponse checkHealth() throws Exception {
            return new HealthCheckResponse() {
                @Override
                public String getName() {
                    return config.getName();
                }

                @Override
                public String getDescription() {
                    return config.getDescription();
                }

                @Override
                public double getHealth() {
                    return pendingUpdates.get() > config.getUnhealthyAfterNPendingUpdates() ? config.getUnhealthyPercent() : 1.0;
                }

                @Override
                public String getStatus() {
                    return "There are " + pendingUpdates.get() + " pending updates.";
                }

                @Override
                public String getResolution() {
                    return "Look at the logs and see if you can resolve the issue.";
                }

                @Override
                public long getTimestamp() {
                    return System.currentTimeMillis();
                }
            };
        }
    }
}
