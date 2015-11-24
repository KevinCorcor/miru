package com.jivesoftware.os.miru.stream.plugins.fulltext;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MinMaxPriorityQueue;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.miru.api.activity.MiruActivity;
import com.jivesoftware.os.miru.api.field.MiruFieldType;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.bitmap.MiruIntIterator;
import com.jivesoftware.os.miru.plugin.context.MiruRequestContext;
import com.jivesoftware.os.miru.plugin.index.MiruActivityInternExtern;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInternalActivity;
import com.jivesoftware.os.miru.plugin.solution.FieldAndTermId;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.stream.plugins.fulltext.FullTextAnswer.ActivityScore;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.commons.lang.mutable.MutableInt;

/**
 *
 */
public class FullText {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final MiruProvider miruProvider;

    public FullText(MiruProvider miruProvider) {
        this.miruProvider = miruProvider;
    }

    public MiruFilter parseQuery(String defaultField, String query) throws Exception {
        return miruProvider.getQueryParser(defaultField).parse(query);
    }

    public <BM extends IBM, IBM> FullTextAnswer getActivityScores(MiruBitmaps<BM, IBM> bitmaps,
        MiruRequestContext<IBM, ?> requestContext,
        MiruRequest<FullTextQuery> request,
        Optional<FullTextReport> lastReport,
        BM answer,
        Map<FieldAndTermId, MutableInt> termCollector) throws Exception {

        StackBuffer stackBuffer = new StackBuffer();

        log.debug("Get full text for answer={} request={}", answer, request);
        //System.out.println("Number of matches: " + bitmaps.cardinality(answer));

        List<ActivityScore> activityScores;
        if (request.query.strategy == FullTextQuery.Strategy.TF_IDF) {
            activityScores = collectTfIdf(bitmaps, requestContext, request, lastReport, answer, termCollector, stackBuffer);
        } else if (request.query.strategy == FullTextQuery.Strategy.TIME) {
            activityScores = collectTime(bitmaps, requestContext, request, lastReport, answer, stackBuffer);
        } else {
            activityScores = Collections.emptyList();
        }

        boolean resultsExhausted = request.query.strategy == FullTextQuery.Strategy.TIME
            && request.query.timeRange.smallestTimestamp > requestContext.getTimeIndex().getLargestTimestamp();

        FullTextAnswer result = new FullTextAnswer(activityScores, resultsExhausted);
        log.debug("result={}", result);
        return result;
    }

    private <BM extends IBM, IBM> List<ActivityScore> collectTfIdf(MiruBitmaps<BM, IBM> bitmaps,
        MiruRequestContext<IBM, ?> requestContext,
        MiruRequest<FullTextQuery> request,
        Optional<FullTextReport> lastReport,
        BM answer,
        Map<FieldAndTermId, MutableInt> termCollector,
        StackBuffer stackBuffer) throws Exception {

        MiruActivityInternExtern internExtern = miruProvider.getActivityInternExtern(request.tenantId);
        MiruFieldIndex<IBM> primaryFieldIndex = requestContext.getFieldIndexProvider().getFieldIndex(MiruFieldType.primary);

        int desiredNumberOfResults = request.query.desiredNumberOfResults;
        int alreadyScoredCount = lastReport.isPresent() ? lastReport.get().scoredActivities : 0;

        List<ActivityScore> activityScores = Lists.newArrayListWithCapacity(request.query.desiredNumberOfResults);

        Map<FieldAndTermId, Float> termMultipliers = Maps.newHashMapWithExpectedSize(termCollector.size());
        for (Map.Entry<FieldAndTermId, MutableInt> entry : termCollector.entrySet()) {
            FieldAndTermId fieldAndTermId = entry.getKey();
            long idf = primaryFieldIndex.getGlobalCardinality(fieldAndTermId.fieldId, fieldAndTermId.termId, stackBuffer);
            if (idf > 0) {
                float multiplier = entry.getValue().floatValue() / (float) idf;
                termMultipliers.put(fieldAndTermId, multiplier);
            }
        }

        MinMaxPriorityQueue<RawBitScore> scored = MinMaxPriorityQueue
            .expectedSize(desiredNumberOfResults)
            .maximumSize(desiredNumberOfResults)
            .create();
        MiruIntIterator iter = bitmaps.intIterator(answer);
        float minScore = lastReport.isPresent() ? lastReport.get().lowestScore : -Float.MAX_VALUE;
        MutableInt acceptableBelowMin = new MutableInt(desiredNumberOfResults - alreadyScoredCount);

        int batchSize = 1000; //TODO configure?
        int[] ids = new int[batchSize];
        int i = 0;
        while (iter.hasNext()) {
            int lastSetBit = iter.next();
            ids[i] = lastSetBit;
            i++;

            if (i == batchSize) {
                batchTfIdf(requestContext, request, internExtern, primaryFieldIndex, termMultipliers, scored, minScore, acceptableBelowMin, ids, stackBuffer);
                i = 0;
            }
        }

        if (i > 0) {
            int[] remainder = new int[i];
            System.arraycopy(ids, 0, remainder, 0, i);
            batchTfIdf(requestContext, request, internExtern, primaryFieldIndex, termMultipliers, scored, minScore, acceptableBelowMin, remainder,
                stackBuffer);
        }

        Iterables.addAll(activityScores, Iterables.transform(scored, (RawBitScore input) -> {
            try {
                return new ActivityScore(input.activity.get(), input.score);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
        Collections.sort(activityScores);

        return activityScores;
    }

    private <BM extends IBM, IBM> void batchTfIdf(MiruRequestContext<IBM, ?> requestContext,
        MiruRequest<FullTextQuery> request,
        MiruActivityInternExtern internExtern,
        MiruFieldIndex<BM> primaryFieldIndex,
        Map<FieldAndTermId, Float> termMultipliers,
        MinMaxPriorityQueue<RawBitScore> scored,
        float minScore,
        MutableInt acceptableBelowMin,
        int[] ids,
        StackBuffer stackBuffer) throws Exception {

        float[] scores = new float[ids.length];
        for (Map.Entry<FieldAndTermId, Float> entry : termMultipliers.entrySet()) {
            FieldAndTermId fieldAndTermId = entry.getKey();
            float multiplier = entry.getValue();

            long[] tf = primaryFieldIndex.getCardinalities(fieldAndTermId.fieldId, fieldAndTermId.termId, ids, stackBuffer);
            for (int i = 0; i < tf.length; i++) {
                if (tf[i] > 0) {
                    scores[i] += multiplier * (float) tf[i];
                }
            }
        }

        for (int i = 0; i < ids.length; i++) {
            int _i = i;
            if (scores[i] > minScore) {
                RawBitScore bitScore = new RawBitScore(new Promise<>(() -> {
                    MiruInternalActivity internalActivity = requestContext.getActivityIndex().get(request.tenantId, ids[_i], stackBuffer);
                    return internExtern.extern(internalActivity, requestContext.getSchema());
                }), ids[i], scores[i]);
                scored.add(bitScore);
            } else if (acceptableBelowMin.intValue() > 0) {
                RawBitScore bitScore = new RawBitScore(new Promise<>(() -> {
                    MiruInternalActivity internalActivity = requestContext.getActivityIndex().get(request.tenantId, ids[_i], stackBuffer);
                    return internExtern.extern(internalActivity, requestContext.getSchema());
                }), ids[i], scores[i]);
                scored.add(bitScore);
                acceptableBelowMin.decrement();
            }
        }
    }

    private <BM extends IBM, IBM> List<ActivityScore> collectTime(MiruBitmaps<BM, IBM> bitmaps,
        MiruRequestContext<IBM, ?> requestContext,
        MiruRequest<FullTextQuery> request,
        Optional<FullTextReport> lastReport,
        BM answer,
        StackBuffer stackBuffer) throws Exception {

        MiruActivityInternExtern internExtern = miruProvider.getActivityInternExtern(request.tenantId);

        int desiredNumberOfResults = request.query.desiredNumberOfResults;
        int collectedResults = lastReport.isPresent() ? lastReport.get().scoredActivities : 0;

        List<ActivityScore> activityScores = Lists.newArrayListWithCapacity(request.query.desiredNumberOfResults);
        MiruIntIterator iter = bitmaps.descendingIntIterator(answer);
        while (iter.hasNext()) {
            int lastSetBit = iter.next();
            MiruInternalActivity internalActivity = requestContext.getActivityIndex().get(request.tenantId, lastSetBit, stackBuffer);
            float score = 0f; //TODO ?
            ActivityScore activityScore = new ActivityScore(internExtern.extern(internalActivity, requestContext.getSchema()), score);
            activityScores.add(activityScore);
            collectedResults++;
            if (collectedResults >= desiredNumberOfResults) {
                break;
            }
        }

        return activityScores;
    }

    private static class RawBitScore implements Comparable<RawBitScore> {

        private final Promise<MiruActivity> activity;
        private final int id;
        private final float score;

        public RawBitScore(Promise<MiruActivity> activity, int id, float score) {
            this.activity = activity;
            this.id = id;
            this.score = score;
        }

        @Override
        public int compareTo(RawBitScore o) {
            // higher scores first
            int c = -Float.compare(score, o.score);
            if (c != 0) {
                return c;
            }
            // higher id first
            return -Integer.compare(id, o.id);
        }
    }

    private static class Promise<V> {

        private final Callable<V> callable;
        private boolean empty = true;
        private V value;

        public Promise(Callable<V> callable) {
            this.callable = callable;
        }

        public V get() throws Exception {
            if (empty) {
                value = callable.call();
                empty = false;
            }
            return value;
        }
    }
}
