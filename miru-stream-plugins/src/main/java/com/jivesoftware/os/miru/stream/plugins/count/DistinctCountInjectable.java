package com.jivesoftware.os.miru.stream.plugins.count;

import com.google.common.base.Optional;
import com.jivesoftware.os.miru.api.MiruQueryServiceException;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.plugin.Miru;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestAndReport;
import com.jivesoftware.os.miru.plugin.solution.MiruResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import com.jivesoftware.os.miru.plugin.solution.MiruSolvableFactory;

/**
 *
 */
public class DistinctCountInjectable {

    private final MiruProvider<? extends Miru> provider;
    private final DistinctCount distinctCount;

    public DistinctCountInjectable(MiruProvider<? extends Miru> provider, DistinctCount distinctCount) {
        this.provider = provider;
        this.distinctCount = distinctCount;
    }

    public MiruResponse<DistinctCountAnswer> countCustomStream(MiruRequest<DistinctCountQuery> request) throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askAndMerge(tenantId,
                new MiruSolvableFactory<>(request.name, provider.getStats(), "countCustomStream", new DistinctCountCustomQuestion(distinctCount,
                    request,
                    provider.getRemotePartition(DistinctCountCustomRemotePartition.class))),
                new DistinctCountAnswerEvaluator(request.query),
                new DistinctCounterAnswerMerger(),
                DistinctCountAnswer.EMPTY_RESULTS,
                request.logLevel);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to count custom stream", e);
        }
    }

    public MiruResponse<DistinctCountAnswer> countInboxStreamAll(MiruRequest<DistinctCountQuery> request)
        throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askAndMerge(tenantId,
                new MiruSolvableFactory<>(request.name, provider.getStats(), "countInboxStreamAll", new DistinctCountInboxQuestion(distinctCount,
                    provider.getBackfillerizer(tenantId),
                    request,
                    provider.getRemotePartition(DistinctCountInboxAllRemotePartition.class),
                    false)),
                new DistinctCountAnswerEvaluator(request.query),
                new DistinctCounterAnswerMerger(),
                DistinctCountAnswer.EMPTY_RESULTS,
                request.logLevel);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to count inbox all stream", e);
        }
    }

    public MiruResponse<DistinctCountAnswer> countInboxStreamUnread(MiruRequest<DistinctCountQuery> request)
        throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askAndMerge(tenantId,
                new MiruSolvableFactory<>(request.name, provider.getStats(), "countInboxStreamUnread", new DistinctCountInboxQuestion(distinctCount,
                    provider.getBackfillerizer(tenantId),
                    request,
                    provider.getRemotePartition(DistinctCountInboxUnreadRemotePartition.class),
                    true)),
                new DistinctCountAnswerEvaluator(request.query),
                new DistinctCounterAnswerMerger(),
                DistinctCountAnswer.EMPTY_RESULTS,
                request.logLevel);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to count inbox unread stream", e);
        }
    }

    public MiruPartitionResponse<DistinctCountAnswer> countCustomStream(MiruPartitionId partitionId,
        MiruRequestAndReport<DistinctCountQuery, DistinctCountReport> requestAndReport)
        throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = requestAndReport.request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askImmediate(tenantId,
                partitionId,
                new MiruSolvableFactory<>(requestAndReport.request.name, provider.getStats(), "countCustomStream",
                    new DistinctCountCustomQuestion(distinctCount,
                        requestAndReport.request,
                        provider.getRemotePartition(DistinctCountCustomRemotePartition.class))),
                Optional.fromNullable(requestAndReport.report),
                DistinctCountAnswer.EMPTY_RESULTS,
                MiruSolutionLogLevel.NONE);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to count custom stream for partition: " + partitionId.getId(), e);
        }
    }

    public MiruPartitionResponse<DistinctCountAnswer> countInboxStreamAll(MiruPartitionId partitionId,
        MiruRequestAndReport<DistinctCountQuery, DistinctCountReport> requestAndReport)
        throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = requestAndReport.request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askImmediate(tenantId,
                partitionId,
                new MiruSolvableFactory<>(requestAndReport.request.name, provider.getStats(), "countInboxStreamAll", new DistinctCountInboxQuestion(
                    distinctCount,
                    provider.getBackfillerizer(tenantId),
                    requestAndReport.request,
                    provider.getRemotePartition(DistinctCountInboxAllRemotePartition.class),
                    false)),
                Optional.fromNullable(requestAndReport.report),
                DistinctCountAnswer.EMPTY_RESULTS,
                MiruSolutionLogLevel.NONE);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to count inbox all stream for partition: " + partitionId.getId(), e);
        }
    }

    public MiruPartitionResponse<DistinctCountAnswer> countInboxStreamUnread(MiruPartitionId partitionId,
        MiruRequestAndReport<DistinctCountQuery, DistinctCountReport> requestAndReport)
        throws MiruQueryServiceException, InterruptedException {
        try {
            MiruTenantId tenantId = requestAndReport.request.tenantId;
            Miru miru = provider.getMiru(tenantId);
            return miru.askImmediate(tenantId,
                partitionId,
                new MiruSolvableFactory<>(requestAndReport.request.name, provider.getStats(), "countInboxStreamUnread", new DistinctCountInboxQuestion(
                    distinctCount,
                    provider.getBackfillerizer(tenantId),
                    requestAndReport.request,
                    provider.getRemotePartition(DistinctCountInboxUnreadRemotePartition.class),
                    true)),
                Optional.fromNullable(requestAndReport.report),
                DistinctCountAnswer.EMPTY_RESULTS,
                MiruSolutionLogLevel.NONE);
        } catch (MiruPartitionUnavailableException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            //TODO throw http error codes
            throw new MiruQueryServiceException("Failed to count inbox unread stream for partition: " + partitionId.getId(), e);
        }
    }

}
