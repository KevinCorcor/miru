package com.jivesoftware.os.miru.stream.plugins.filter;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruQueryServiceException;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.query.filter.MiruAuthzExpression;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan
 */
public class AggregateCountsCustomQuestion implements Question<AggregateCountsQuery, AggregateCountsAnswer, AggregateCountsReport> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final AggregateCounts aggregateCounts;
    private final MiruRequest<AggregateCountsQuery> request;
    private final MiruRemotePartition<AggregateCountsQuery, AggregateCountsAnswer, AggregateCountsReport> remotePartition;
    private final MiruBitmapsDebug bitmapsDebug = new MiruBitmapsDebug();
    private final MiruAggregateUtil aggregateUtil = new MiruAggregateUtil();

    public AggregateCountsCustomQuestion(AggregateCounts aggregateCounts,
        MiruRequest<AggregateCountsQuery> request,
        MiruRemotePartition<AggregateCountsQuery, AggregateCountsAnswer, AggregateCountsReport> remotePartition) {
        this.aggregateCounts = aggregateCounts;
        this.request = request;
        this.remotePartition = remotePartition;
    }

    @Override
    public <BM extends IBM, IBM> MiruPartitionResponse<AggregateCountsAnswer> askLocal(MiruRequestHandle<BM, IBM, ?> handle,
        Optional<AggregateCountsReport> report)
        throws Exception {

        StackBuffer stackBuffer = new StackBuffer();

        MiruSolutionLog solutionLog = new MiruSolutionLog(request.logLevel);
        MiruRequestContext<IBM, ?> context = handle.getRequestContext();
        MiruBitmaps<BM, IBM> bitmaps = handle.getBitmaps();

        MiruTimeRange timeRange = request.query.answerTimeRange;
        if (!context.getTimeIndex().intersects(timeRange)) {
            solutionLog.log(MiruSolutionLogLevel.WARN, "No time index intersection. Partition {}: {} doesn't intersect with {}",
                handle.getCoord().partitionId, context.getTimeIndex(), timeRange);
            return new MiruPartitionResponse<>(aggregateCounts.getAggregateCounts(solutionLog,
                bitmaps, context, request, report, bitmaps.create(), Optional.absent()), solutionLog.asList());
        }

        List<IBM> ands = new ArrayList<>();

        BM filtered = aggregateUtil.filter(bitmaps, context.getSchema(), context.getTermComposer(), context.getFieldIndexProvider(), request.query.streamFilter,
            solutionLog, null, context.getActivityIndex().lastId(stackBuffer), -1, stackBuffer);
        ands.add(filtered);

        ands.add(bitmaps.buildIndexMask(context.getActivityIndex().lastId(stackBuffer), context.getRemovalIndex().getIndex(stackBuffer)));

        if (!MiruAuthzExpression.NOT_PROVIDED.equals(request.authzExpression)) {
            ands.add(context.getAuthzIndex().getCompositeAuthz(request.authzExpression, stackBuffer));
        }

        if (!MiruTimeRange.ALL_TIME.equals(request.query.answerTimeRange)) {
            ands.add(bitmaps.buildTimeRangeMask(context.getTimeIndex(), timeRange.smallestTimestamp, timeRange.largestTimestamp, stackBuffer));
        }

        BM answer = bitmaps.create();
        bitmapsDebug.debug(solutionLog, bitmaps, "ands", ands);
        bitmaps.and(answer, ands);

        BM counter = null;
        if (!MiruTimeRange.ALL_TIME.equals(request.query.countTimeRange)) {
            counter = bitmaps.create();
            bitmaps.and(counter, Arrays.asList(answer, bitmaps.buildTimeRangeMask(
                context.getTimeIndex(), request.query.countTimeRange.smallestTimestamp, request.query.countTimeRange.largestTimestamp, stackBuffer)));
        }

        return new MiruPartitionResponse<>(aggregateCounts.getAggregateCounts(solutionLog, bitmaps, context, request, report, answer,
            Optional.fromNullable(counter)), solutionLog.asList());
    }

    @Override
    public MiruPartitionResponse<AggregateCountsAnswer> askRemote(MiruHost host,
        MiruPartitionId partitionId,
        Optional<AggregateCountsReport> report) throws MiruQueryServiceException {
        return remotePartition.askRemote(host, partitionId, request, report);
    }

    @Override
    public Optional<AggregateCountsReport> createReport(Optional<AggregateCountsAnswer> answer) {
        Optional<AggregateCountsReport> report = Optional.absent();
        if (answer.isPresent()) {

            AggregateCountsAnswer currentAnswer = answer.get();
            Map<String, AggregateCountsReportConstraint> constraintReport = Maps.newHashMapWithExpectedSize(currentAnswer.constraints.size());
            for (Map.Entry<String, AggregateCountsAnswerConstraint> entry : currentAnswer.constraints.entrySet()) {
                AggregateCountsAnswerConstraint value = entry.getValue();
                constraintReport.put(entry.getKey(),
                    new AggregateCountsReportConstraint(value.aggregateTerms, value.skippedDistincts, value.collectedDistincts));
            }

            report = Optional.of(new AggregateCountsReport(constraintReport));
        }
        return report;
    }
}
