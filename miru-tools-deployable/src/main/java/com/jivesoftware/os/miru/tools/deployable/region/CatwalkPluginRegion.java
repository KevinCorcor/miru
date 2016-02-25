package com.jivesoftware.os.miru.tools.deployable.region;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.miru.api.query.filter.FilterStringUtil;
import com.jivesoftware.os.miru.api.query.filter.MiruFilter;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.http.client.HttpResponseMapper;
import com.jivesoftware.os.routing.bird.http.client.TenantAwareHttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 *
 */
// soy.miru.page.analyticsPluginRegion
public class CatwalkPluginRegion implements MiruPageRegion<Optional<CatwalkPluginRegion.CatwalkPluginRegionInput>> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final String template;
    private final MiruSoyRenderer renderer;
    private final TenantAwareHttpClient<String> readerClient;
    private final ObjectMapper requestMapper;
    private final HttpResponseMapper responseMapper;
    private final FilterStringUtil filterStringUtil = new FilterStringUtil();

    public CatwalkPluginRegion(String template,
        MiruSoyRenderer renderer,
        TenantAwareHttpClient<String> readerClient,
        ObjectMapper requestMapper,
        HttpResponseMapper responseMapper) {
        this.template = template;
        this.renderer = renderer;
        this.readerClient = readerClient;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    public static class CatwalkPluginRegionInput {

        final String tenant;
        final long fromTimeAgo;
        final String fromTimeUnit;
        final long toTimeAgo;
        final String toTimeUnit;
        final int buckets;
        final String field1;
        final String terms1;
        final String field2;
        final String terms2;
        final String filters;
        final String logLevel;

        public CatwalkPluginRegionInput(String tenant,
            long fromTimeAgo,
            String fromTimeUnit,
            long toTimeAgo,
            String toTimeUnit,
            int buckets,
            String field1,
            String terms1,
            String field2,
            String terms2,
            String filters,
            String logLevel) {
            this.tenant = tenant;
            this.fromTimeAgo = fromTimeAgo;
            this.fromTimeUnit = fromTimeUnit;
            this.toTimeUnit = toTimeUnit;
            this.toTimeAgo = toTimeAgo;
            this.buckets = buckets;
            this.field1 = field1;
            this.terms1 = terms1;
            this.field2 = field2;
            this.terms2 = terms2;
            this.filters = filters;
            this.logLevel = logLevel;
        }
    }

    @Override
    public String render(Optional<CatwalkPluginRegionInput> optionalInput) {
        Map<String, Object> data = Maps.newHashMap();
        try {
            if (optionalInput.isPresent()) {
                CatwalkPluginRegionInput input = optionalInput.get();

                TimeUnit fromTimeUnit = TimeUnit.valueOf(input.fromTimeUnit);
                long fromTimeAgo = input.fromTimeAgo;
                long fromMillisAgo = fromTimeUnit.toMillis(fromTimeAgo);

                TimeUnit toTimeUnit = TimeUnit.valueOf(input.toTimeUnit);
                long toTimeAgo = input.toTimeAgo;
                long toMillisAgo = toTimeUnit.toMillis(toTimeAgo);

                data.put("logLevel", input.logLevel);
                data.put("tenant", input.tenant);
                data.put("fromTimeAgo", String.valueOf(fromTimeAgo));
                data.put("fromTimeUnit", String.valueOf(fromTimeUnit));
                data.put("toTimeAgo", String.valueOf(toTimeAgo));
                data.put("toTimeUnit", String.valueOf(toTimeUnit));
                data.put("buckets", String.valueOf(input.buckets));
                data.put("field1", input.field1);
                data.put("terms1", input.terms1);
                data.put("field2", input.field2);
                data.put("terms2", input.terms2);
                data.put("filters", input.filters);

                List<String> terms1 = Lists.newArrayList();
                for (String term : input.terms1.split(",")) {
                    String trimmed = term.trim();
                    if (!trimmed.isEmpty()) {
                        terms1.add(trimmed);
                    }
                }

                List<String> terms2 = Lists.newArrayList();
                for (String term : input.terms2.split(",")) {
                    String trimmed = term.trim();
                    if (!trimmed.isEmpty()) {
                        terms2.add(trimmed);
                    }
                }

                SnowflakeIdPacker snowflakeIdPacker = new SnowflakeIdPacker();
                long jiveCurrentTime = new JiveEpochTimestampProvider().getTimestamp();
                final long packCurrentTime = snowflakeIdPacker.pack(jiveCurrentTime, 0, 0);
                final long fromTime, toTime;
                if (fromMillisAgo > toMillisAgo) {
                    fromTime = packCurrentTime - snowflakeIdPacker.pack(fromMillisAgo, 0, 0);
                    toTime = packCurrentTime - snowflakeIdPacker.pack(toMillisAgo, 0, 0);
                } else {
                    fromTime = packCurrentTime - snowflakeIdPacker.pack(toMillisAgo, 0, 0);
                    toTime = packCurrentTime - snowflakeIdPacker.pack(fromMillisAgo, 0, 0);
                }

                MiruFilter constraintsFilter = filterStringUtil.parse(input.filters);

                /*
                MiruResponse<AnalyticsAnswer> response = null;
                if (!input.tenant.trim().isEmpty()) {
                    MiruTenantId tenantId = new MiruTenantId(input.tenant.trim().getBytes(Charsets.UTF_8));
                    ImmutableMap.Builder<String, MiruFilter> analyticsFiltersBuilder = ImmutableMap.builder();
                    for (String term1 : terms1) {
                        if (input.field2.isEmpty() || terms2.isEmpty()) {
                            analyticsFiltersBuilder.put(
                                term1,
                                new MiruFilter(MiruFilterOperation.and,
                                    false,
                                    Collections.singletonList(
                                        MiruFieldFilter.ofTerms(MiruFieldType.primary, input.field1, term1)),
                                    null));
                        } else {
                            for (String term2 : terms2) {
                                analyticsFiltersBuilder.put(
                                    term1 + ", " + term2,
                                    new MiruFilter(MiruFilterOperation.and,
                                        false,
                                        Arrays.asList(
                                            MiruFieldFilter.ofTerms(MiruFieldType.primary, input.field1, term1),
                                            MiruFieldFilter.ofTerms(MiruFieldType.primary, input.field2, term2)),
                                        null));
                            }
                        }
                    }
                    ImmutableMap<String, MiruFilter> analyticsFilters = analyticsFiltersBuilder.build();

                    String endpoint = AnalyticsConstants.ANALYTICS_PREFIX + AnalyticsConstants.CUSTOM_QUERY_ENDPOINT;
                    String request = requestMapper.writeValueAsString(new MiruRequest<>("toolsAnalytics",
                        tenantId,
                        MiruActorId.NOT_PROVIDED,
                        MiruAuthzExpression.NOT_PROVIDED,
                        new AnalyticsQuery(
                            Collections.singletonList(new AnalyticsQueryScoreSet("tools",
                                new MiruTimeRange(fromTime, toTime),
                                input.buckets)),
                            constraintsFilter,
                            analyticsFilters),
                        MiruSolutionLogLevel.valueOf(input.logLevel)));
                    MiruResponse<AnalyticsAnswer> analyticsResponse = readerClient.call("",
                        new RoundRobinStrategy(),
                        "analyticsPluginRegion",
                        httpClient -> {
                            HttpResponse httpResponse = httpClient.postJson(endpoint, request, null);
                            @SuppressWarnings("unchecked")
                            MiruResponse<AnalyticsAnswer> extractResponse = responseMapper.extractResultFromResponse(httpResponse,
                                MiruResponse.class,
                                new Class<?>[] { AnalyticsAnswer.class },
                                null);
                            return new ClientResponse<>(extractResponse, true);
                        });
                    if (analyticsResponse != null && analyticsResponse.answer != null) {
                        response = analyticsResponse;
                    } else {
                        log.warn("Empty analytics response from {}", tenantId);
                    }
                }

                if (response != null && response.answer != null) {
                    List<Waveform> answerWaveforms = response.answer.waveforms != null ? response.answer.waveforms.get("tools") : Collections.emptyList();
                    ImmutableMap<String, Waveform> uniqueIndex = Maps.uniqueIndex(answerWaveforms, w -> w.getId().last());
                    Map<String, long[]> waveforms = Maps.transformValues(uniqueIndex, w -> {
                        long[] waveform = new long[input.buckets];
                        w.mergeWaveform(waveform);
                        return waveform;
                    });
                    data.put("elapse", String.valueOf(response.totalElapsed));
                    //data.put("waveform", waveform == null ? "" : waveform.toString());

                    data.put("waveform", "data:image/png;base64," + new PNGWaveforms().hitsToBase64PNGWaveform(1024, 400, 32, waveforms,
                        Optional.<MinMaxDouble>absent()));
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    data.put("summary", Joiner.on("\n").join(response.log) + "\n\n" + mapper.writeValueAsString(response.solutions));
                }*/
                List<Map<String, Object>> features = new ArrayList<>();
                Map<String, Object> feature = new HashMap<>();
                feature.put("values", Arrays.asList("2 1002", "3 1231", "5"));
                feature.put("numerator", 4);
                feature.put("denominator", 10);
                features.add(feature);

                Map<String, Object> featureClasses = new HashMap<>();
                featureClasses.put("A,B,C", features);

                HashMap<String, Object> model = new HashMap<>();
                model.put("featureClasses", featureClasses);

                data.put("model", model);

            }
        } catch (Exception e) {
            LOG.error("Unable to retrieve data", e);
        }

        return renderer.render(template, data);
    }

    @Override
    public String getTitle() {
        return "Catwalk";
    }

}
