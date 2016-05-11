package com.jivesoftware.os.miru.amza;

import org.merlin.config.Config;
import org.merlin.config.defaults.BooleanDefault;
import org.merlin.config.defaults.IntDefault;
import org.merlin.config.defaults.LongDefault;
import org.merlin.config.defaults.StringDefault;

/**
 *
 */
public interface MiruAmzaServiceConfig extends Config {

    @StringDefault("./var/amza/default/data/")
    String getWorkingDirectories();

    void setWorkingDirectories(String dir);

    @LongDefault(60_000)
    long getCheckIfCompactionIsNeededIntervalInMillis();

    @IntDefault(60_000)
    int getDeltaStripeCompactionIntervalInMillis();

    @IntDefault(100)
    int getCorruptionParanoiaFactor();

    @IntDefault(1_000_000)
    int getMaxUpdatesBeforeDeltaStripeCompaction();

    void setMaxUpdatesBeforeDeltaStripeCompaction(int maxUpdatesBeforeDeltaStripeCompaction);

    @IntDefault(24)
    int getNumberOfTakerThreads();

    @IntDefault(1)
    int getTakeFromFactor();

    @BooleanDefault(false)
    boolean getHardFsync();

    @LongDefault(30_000L)
    long getDiscoveryIntervalMillis();

    @IntDefault(3)
    int getActivityRingSize();

    @LongDefault(10_000L)
    long getActivityRoutingTimeoutMillis();

    @IntDefault(3)
    int getReadTrackingRingSize();

    @LongDefault(10_000L)
    long getReadTrackingRoutingTimeoutMillis();

    @LongDefault(60_000L)
    long getTakeSlowThresholdInMillis();
    
    @LongDefault(60_000)
    long getInterruptBlockingReadsIfLingersForNMillis();
}
