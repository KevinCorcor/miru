package com.jivesoftware.os.miru.sea.anomaly.deployable.region;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
// soy.sea.anomaly.chrome.chromeRegion
public class SeaAnomalyChromeRegion<I, R extends MiruPageRegion<I>> implements MiruRegion<I> {

    private final String template;
    private final MiruSoyRenderer renderer;
    private final MiruHeaderRegion headerRegion;
    private final List<SeaAnomalyPlugin> plugins;
    private final R region;

    public SeaAnomalyChromeRegion(String template, MiruSoyRenderer renderer, MiruHeaderRegion headerRegion, List<SeaAnomalyPlugin> plugins, R region) {
        this.template = template;
        this.renderer = renderer;
        this.headerRegion = headerRegion;
        this.plugins = plugins;
        this.region = region;
    }

    @Override
    public String render(I input) {
        List<Map<String, String>> p = Lists.transform(plugins,
            plugin -> ImmutableMap.of("name", plugin.name, "path", plugin.path, "glyphicon", plugin.glyphicon));
        Map<String, Object> headerData = new HashMap<>();
        headerData.put("plugins", p);

        Map<String, Object> data = Maps.newHashMap();
        data.put("header", headerRegion.render(headerData));
        data.put("region", region.render(input));
        data.put("title", region.getTitle());
        data.put("plugins", p);
        return renderer.render(template, data);
    }

}
