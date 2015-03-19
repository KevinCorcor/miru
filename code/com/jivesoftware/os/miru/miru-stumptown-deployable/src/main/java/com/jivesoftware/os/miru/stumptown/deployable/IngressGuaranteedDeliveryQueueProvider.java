package com.jivesoftware.os.miru.stumptown.deployable;

import com.jivesoftware.os.filer.queue.guaranteed.delivery.DeliveryCallback;
import com.jivesoftware.os.filer.queue.guaranteed.delivery.FileQueueBackGuaranteedDeliveryFactory;
import com.jivesoftware.os.filer.queue.guaranteed.delivery.FileQueueBackGuaranteedDeliveryServiceConfig;
import com.jivesoftware.os.filer.queue.guaranteed.delivery.GuaranteedDeliveryService;
import com.jivesoftware.os.filer.queue.guaranteed.delivery.GuaranteedDeliveryServiceStatus;
import com.jivesoftware.os.filer.queue.processor.PhasedQueueProcessorConfig;
import com.jivesoftware.os.jive.utils.health.api.HealthCheckUtil;
import com.jivesoftware.os.jive.utils.health.api.HealthChecker;
import com.jivesoftware.os.jive.utils.health.api.HealthFactory;
import com.jivesoftware.os.jive.utils.health.api.MinMaxHealthChecker;
import com.jivesoftware.os.jive.utils.health.api.ScheduledHealthCheck;
import com.jivesoftware.os.jive.utils.health.api.ScheduledMinMaxHealthCheckConfig;
import com.jivesoftware.os.jive.utils.health.checkers.DiskFreeHealthChecker;
import com.jivesoftware.os.mlogger.core.Counter;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.mlogger.core.ValueType;
import java.io.File;
import java.io.IOException;
import org.merlin.config.defaults.LongDefault;
import org.merlin.config.defaults.StringDefault;

/**
 *
 */
public class IngressGuaranteedDeliveryQueueProvider {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    static interface IngressDiskCheck extends ScheduledMinMaxHealthCheckConfig {

        @StringDefault("ingress>disk")
        @Override
        public String getName();

        @LongDefault(80)
        @Override
        public Long getMax();

    }

    static interface IngressPendingCheck extends ScheduledMinMaxHealthCheckConfig {

        @StringDefault ("ingress>pending")
        @Override
        public String getName();

        @LongDefault (100000)
        @Override
        public Long getMax();

    }

    private final GuaranteedDeliveryService[] guaranteedDeliveryServices;

    public IngressGuaranteedDeliveryQueueProvider(final String pathToQueues,
        int numberOfPartitions,
        int numberOfSendThreadsPerPartition,
        DeliveryCallback deliveryCallback) throws IOException {

        HealthFactory.scheduleHealthChecker(IngressDiskCheck.class,
            new HealthFactory.HealthCheckerConstructor<Counter, IngressDiskCheck>() {

                @Override
                public HealthChecker<Counter> construct(IngressDiskCheck config) {
                    return new DiskFreeHealthChecker(config, new File[] { new File(pathToQueues) });
                }
            });

        this.guaranteedDeliveryServices = new GuaranteedDeliveryService[numberOfPartitions];
        for (int i = 0; i < guaranteedDeliveryServices.length; i++) {
            guaranteedDeliveryServices[i] = FileQueueBackGuaranteedDeliveryFactory.createService(
                FileQueueBackGuaranteedDeliveryServiceConfig
                    .newBuilder(pathToQueues, "stumptownQueue-" + i,
                        PhasedQueueProcessorConfig
                            .newBuilder("stumptownQueue-" + i)
                            .setIdealMinimumBatchSize(1000)
                            .setIdealMinimumBatchSizeMaxWaitMillis(1000)
                            .setMaxBatchSize(10000)
                            .build())
                    .setNumberOfConsumers(numberOfSendThreadsPerPartition)
                    .build(),
                deliveryCallback);
        }

        HealthFactory.scheduleHealthChecker(IngressPendingCheck.class,
            new HealthFactory.HealthCheckerConstructor<Counter, IngressPendingCheck>() {

                @Override
                public HealthChecker<Counter> construct(IngressPendingCheck config) {
                    return new PendingHealthChecker(guaranteedDeliveryServices, config);
                }
            });
    }

    private static class PendingHealthChecker extends MinMaxHealthChecker implements ScheduledHealthCheck {

        private final GuaranteedDeliveryService[] guaranteedDeliveryServices;
        private final ScheduledMinMaxHealthCheckConfig config;

        PendingHealthChecker(GuaranteedDeliveryService[] guaranteedDeliveryServices, ScheduledMinMaxHealthCheckConfig config) {
            super(config);
            this.guaranteedDeliveryServices = guaranteedDeliveryServices;
            this.config = config;
        }

        @Override
        public long getCheckIntervalInMillis() {
            return config.getCheckIntervalInMillis();
        }

        @Override
        public void run() {
            try {
                StringBuilder sb = new StringBuilder();
                long maxUndelivered = 0;
                for (int i = 0; i < guaranteedDeliveryServices.length; i++) {
                    GuaranteedDeliveryService guaranteedDeliveryService = guaranteedDeliveryServices[i];
                    GuaranteedDeliveryServiceStatus status = guaranteedDeliveryService.getStatus();

                    double percentageUsed = HealthCheckUtil.zeroToOne(0, config.getMax(), status.undelivered());
                    sb.append("queue:").append(i).append(" at ").append(100 * percentageUsed).append("% used. ");
                    if (maxUndelivered < status.undelivered()) {
                        maxUndelivered = status.undelivered();
                    }
                }
                Counter counter = new Counter(ValueType.RATE);
                counter.set(maxUndelivered);
                check(counter, sb.toString(), "unknown");
            } catch (Exception x) {
                // TODO what?
            }
        }

    }

    public GuaranteedDeliveryService getGuaranteedDeliveryServices(String key) {
        return guaranteedDeliveryServices[Math.abs(key.hashCode()) % guaranteedDeliveryServices.length];
    }

}
