package com.jivesoftware.os.miru.reader.deployable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.List;
import java.util.Map;

/**
 *
 */
// soy.miru.chrome.chromeRegion
public class MiruChromeRegion<I, R extends MiruPageRegion<I>> implements MiruRegion<I> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final String template;
    private final MiruSoyRenderer renderer;
    private final MiruHeaderRegion headerRegion;
    private final List<MiruReaderUIPlugin> plugins;
    private final R region;

    public MiruChromeRegion(String template,
        MiruSoyRenderer renderer,
        MiruHeaderRegion headerRegion,
        List<MiruReaderUIPlugin> plugins,
        R region) {
        this.template = template;
        this.renderer = renderer;
        this.headerRegion = headerRegion;
        this.plugins = plugins;
        this.region = region;
    }

    @Override
    public String render(I input) {

        Map<String, Object> data = Maps.newHashMap();
        data.put("header", headerRegion.render(null));
        data.put("region", region.render(input));
        data.put("title", region.getTitle());
        data.put("plugins", Lists.transform(plugins,
            plugin -> ImmutableMap.of("glyphicon", plugin.glyphicon, "name", plugin.name, "path", plugin.path)));

        return renderer.render(template, data);
    }

}
