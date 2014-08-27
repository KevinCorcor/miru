package com.jivesoftware.os.miru.service.stream.factory;

import com.google.common.base.Optional;
import com.jivesoftware.os.jive.utils.http.client.rest.RequestHelper;
import com.jivesoftware.os.jive.utils.logger.MetricLogger;
import com.jivesoftware.os.jive.utils.logger.MetricLoggerFactory;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.query.RecoQuery;
import com.jivesoftware.os.miru.api.query.result.RecoResult;
import com.jivesoftware.os.miru.reader.MiruHttpClientReader;
import com.jivesoftware.os.miru.service.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.service.partition.MiruQueryHandle;
import com.jivesoftware.os.miru.service.query.RecoReport;
import com.jivesoftware.os.miru.service.query.base.ExecuteMiruFilter;
import com.jivesoftware.os.miru.service.stream.MiruQueryStream;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class RecoExecuteQuery<BM> implements ExecuteQuery<RecoResult, RecoReport> {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final MiruBitmaps<BM> bitmaps;
    private final MiruFilterUtils<BM> utils;
    private final RecoQuery query;

    public RecoExecuteQuery(MiruBitmaps<BM> bitmaps,
            MiruFilterUtils<BM> utils,
            RecoQuery query) {
        this.bitmaps = bitmaps;
        this.utils = utils;
        this.query = query;
    }

    @Override
    public RecoResult executeLocal(MiruQueryHandle handle, Optional<RecoReport> report) throws Exception {
        MiruQueryStream<BM> stream = handle.getQueryStream();

        // Start building up list of bitmap operations to run
        List<BM> ands = new ArrayList<>();

        // 1) Execute the combined filter above on the given stream, add the bitmap
        ExecuteMiruFilter<BM> executeMiruFilter = new ExecuteMiruFilter<>(bitmaps, stream.schema, stream.fieldIndex, stream.executorService,
                query.constraintsFilter, Optional.<BM>absent(), -1);
        ands.add(executeMiruFilter.call());

        // 2) Add in the authz check if we have it
        if (query.authzExpression.isPresent()) {
            ands.add(stream.authzIndex.getCompositeAuthz(query.authzExpression.get()));
        }

        // 3) Mask out anything that hasn't made it into the activityIndex yet, orToSourceSize that has been removed from the index
        ands.add(bitmaps.buildIndexMask(stream.activityIndex.lastId(), Optional.of(stream.removalIndex.getIndex())));

        // AND it all together and return the results
        BM answer = utils.bufferedAnd(ands);
        RecoResult reco = utils.collaborativeFiltering(stream, query, report, answer);

        return reco;
    }

    @Override
    public RecoResult executeRemote(RequestHelper requestHelper, MiruPartitionId partitionId, Optional<RecoResult> lastResult) throws Exception {
        return new MiruHttpClientReader(requestHelper).collaborativeFilteringRecommendations(partitionId, query, lastResult);
    }

    @Override
    public Optional<RecoReport> createReport(Optional<RecoResult> result) {
        Optional<RecoReport> report = Optional.absent();
        if (result.isPresent()) {
            report = Optional.of(new RecoReport());
        }
        return report;
    }
}
