package com.jivesoftware.os.miru.stream.plugins.count;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruQueryServiceException;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.query.filter.MiruAuthzExpression;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.plugin.backfill.MiruJustInTimeBackfillerizer;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmapsDebug;
import com.jivesoftware.os.miru.plugin.context.MiruRequestContext;
import com.jivesoftware.os.miru.plugin.solution.MiruAggregateUtil;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruRemotePartition;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestHandle;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLog;
import com.jivesoftware.os.miru.plugin.solution.MiruSolutionLogLevel;
import com.jivesoftware.os.miru.plugin.solution.MiruTimeRange;
import com.jivesoftware.os.miru.plugin.solution.Question;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jonathan
 */
public class DistinctCountInboxQuestion implements Question<DistinctCountQuery, DistinctCountAnswer, DistinctCountReport> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final DistinctCount distinctCount;
    private final MiruJustInTimeBackfillerizer backfillerizer;
    private final MiruRequest<DistinctCountQuery> request;
    private final MiruRemotePartition<DistinctCountQuery, DistinctCountAnswer, DistinctCountReport> remotePartition;
    private final boolean unreadOnly;
    private final MiruBitmapsDebug bitmapsDebug = new MiruBitmapsDebug();
    private final MiruAggregateUtil aggregateUtil = new MiruAggregateUtil();

    public DistinctCountInboxQuestion(DistinctCount distinctCount,
        MiruJustInTimeBackfillerizer backfillerizer,
        MiruRequest<DistinctCountQuery> request,
        MiruRemotePartition<DistinctCountQuery, DistinctCountAnswer, DistinctCountReport> remotePartition,
        boolean unreadOnly) {

        Preconditions.checkArgument(!MiruStreamId.NULL.equals(request.query.streamId), "Inbox queries require a streamId");
        this.distinctCount = distinctCount;
        this.backfillerizer = backfillerizer;
        this.request = request;
        this.remotePartition = remotePartition;
        this.unreadOnly = unreadOnly;
    }

    @Override
    public <BM extends IBM, IBM> MiruPartitionResponse<DistinctCountAnswer> askLocal(MiruRequestHandle<BM, IBM, ?> handle,
        Optional<DistinctCountReport> report) throws Exception {

        StackBuffer stackBuffer = new StackBuffer();
        MiruSolutionLog solutionLog = new MiruSolutionLog(request.logLevel);
        MiruRequestContext<IBM, ?> context = handle.getRequestContext();
        MiruBitmaps<BM, IBM> bitmaps = handle.getBitmaps();

        if (handle.canBackfill()) {
            backfillerizer.backfill(bitmaps, context, request.query.streamFilter, solutionLog, request.tenantId,
                handle.getCoord().partitionId, request.query.streamId);
        }

        List<IBM> ands = new ArrayList<>();
        if (!MiruTimeRange.ALL_TIME.equals(request.query.timeRange)) {
            MiruTimeRange timeRange = request.query.timeRange;

            // Short-circuit if the time range doesn't live here
            if (!context.getTimeIndex().intersects(timeRange)) {
                solutionLog.log(MiruSolutionLogLevel.WARN, "No time index intersection. Partition {}: {} doesn't intersect with {}",
                    handle.getCoord().partitionId, context.getTimeIndex(), timeRange);
                return new MiruPartitionResponse<>(distinctCount.numberOfDistincts(
                    bitmaps, context, request, report, bitmaps.create()), solutionLog.asList());
            }
            ands.add(bitmaps.buildTimeRangeMask(context.getTimeIndex(), timeRange.smallestTimestamp, timeRange.largestTimestamp, stackBuffer));
        }

        Optional<IBM> inbox = context.getInboxIndex().getInbox(request.query.streamId).getIndex(stackBuffer);
        if (inbox.isPresent()) {
            ands.add(inbox.get());
        } else {
            // Short-circuit if the user doesn't have an inbox here
            LOG.debug("No user inbox");
            return new MiruPartitionResponse<>(distinctCount.numberOfDistincts(bitmaps, context, request, report, bitmaps.create()), solutionLog.asList());
        }

        if (!MiruFilter.NO_FILTER.equals(request.query.constraintsFilter)) {
            BM filtered = aggregateUtil.filter(bitmaps, context.getSchema(), context.getTermComposer(), context.getFieldIndexProvider(),
                request.query.constraintsFilter, solutionLog, null, context.getActivityIndex().lastId(stackBuffer), -1, stackBuffer);
            ands.add(filtered);
        }
        if (!MiruAuthzExpression.NOT_PROVIDED.equals(request.authzExpression)) {
            ands.add(context.getAuthzIndex().getCompositeAuthz(request.authzExpression, stackBuffer));
        }
        if (unreadOnly) {
            Optional<IBM> unreadIndex = context.getUnreadTrackingIndex().getUnread(request.query.streamId).getIndex(stackBuffer);
            if (unreadIndex.isPresent()) {
                ands.add(unreadIndex.get());
            }
        }
        ands.add(bitmaps.buildIndexMask(context.getActivityIndex().lastId(stackBuffer), context.getRemovalIndex().getIndex(stackBuffer)));

        bitmapsDebug.debug(solutionLog, bitmaps, "ands", ands);
        BM answer = bitmaps.and(ands);

        return new MiruPartitionResponse<>(distinctCount.numberOfDistincts(bitmaps, context, request, report, answer), solutionLog.asList());
    }

    @Override
    public MiruPartitionResponse<DistinctCountAnswer> askRemote(MiruHost host,
        MiruPartitionId partitionId,
        Optional<DistinctCountReport> report) throws MiruQueryServiceException {
        return remotePartition.askRemote(host, partitionId, request, report);
    }

    @Override
    public Optional<DistinctCountReport> createReport(Optional<DistinctCountAnswer> answer) {
        Optional<DistinctCountReport> report = Optional.absent();
        if (answer.isPresent()) {
            report = Optional.of(new DistinctCountReport(
                answer.get().aggregateTerms,
                answer.get().collectedDistincts));
        }
        return report;
    }
}
