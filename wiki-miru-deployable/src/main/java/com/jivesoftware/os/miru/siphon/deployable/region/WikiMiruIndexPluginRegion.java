package com.jivesoftware.os.miru.siphon.deployable.region;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.miru.siphon.deployable.WikiMiruIndexService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 *
 */
// soy.stumptown.page.stumptownStatusPluginRegion
public class WikiMiruIndexPluginRegion implements MiruPageRegion<WikiMiruIndexPluginRegion.WikiMiruIndexPluginRegionInput> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final String template;
    private final MiruSoyRenderer renderer;
    private final WikiMiruIndexService indexService;
    private final Map<String, WikiMiruIndexService.Indexer> indexers = Maps.newConcurrentMap();

    public WikiMiruIndexPluginRegion(String template,
        MiruSoyRenderer renderer,
        WikiMiruIndexService indexService) {
        this.template = template;
        this.renderer = renderer;
        this.indexService = indexService;
    }

    public static class WikiMiruIndexPluginRegionInput {

        final public String indexerId;
        final public String tenantId;
        final public String wikiDumpFile;
        final public int batchSize;
        final public boolean miruEnabled;
        final public String esClusterName;
        final public String action;

        public WikiMiruIndexPluginRegionInput(String indexerId,
            String tenantId,
            String wikiDumpFile,
            int batchSize,
            boolean miruEnabled,
            String esClusterName,
            String action) {

            this.indexerId = indexerId;
            this.tenantId = tenantId;
            this.wikiDumpFile = wikiDumpFile;
            this.batchSize = batchSize;
            this.miruEnabled = miruEnabled;
            this.esClusterName = esClusterName;
            this.action = action;
        }

    }

    @Override
    public String render(WikiMiruIndexPluginRegionInput input) {
        Map<String, Object> data = Maps.newHashMap();
        try {

            if (input.action.equals("grams")) {
                WikiMiruIndexService.Indexer i = indexService.index(input);
                indexers.put(i.indexerId, i);
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        i.startTuples();
                        return null;
                    } catch (Throwable x) {
                        i.message = "failed: " + x.getMessage();
                        LOG.error("Wiki oops", x);
                        return null;
                    }
                });
            }

            if (input.action.equals("start")) {

                WikiMiruIndexService.Indexer i = indexService.index(input);
                indexers.put(i.indexerId, i);
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        i.start();
                        return null;
                    } catch (Throwable x) {
                        i.message = "failed: " + x.getMessage();
                        LOG.error("Wiki oops", x);
                        return null;
                    }
                });
            }

            if (input.action.equals("stop")) {
                WikiMiruIndexService.Indexer i = indexers.get(input.indexerId);
                if (i != null) {
                    i.running.set(false);
                }
            }

            if (input.action.equals("remove")) {
                WikiMiruIndexService.Indexer i = indexers.remove(input.indexerId);
                if (i != null) {
                    i.running.set(false);
                }
            }

            List<Map<String, String>> rows = new ArrayList<>();
            for (WikiMiruIndexService.Indexer i : indexers.values()) {
                Map<String, String> m = new HashMap<>();
                m.put("message", i.message);
                m.put("indexerId", i.indexerId);
                m.put("running", i.running.toString());
                m.put("indexed", i.indexed.toString());
                m.put("tenantId", Joiner.on(",").join(i.tenantIds));
                m.put("pathToWikiDumpFile", i.pathToWikiDumpFile);
                m.put("elapse", String.valueOf(System.currentTimeMillis() - i.startTimestampMillis));

                rows.add(m);
            }
            data.put("indexers", rows);

        } catch (Exception e) {
            LOG.error("Unable to retrieve data", e);
        }

        return renderer.render(template, data);
    }

    @Override
    public String getTitle() {
        return "Wiki Indexer";
    }

}
