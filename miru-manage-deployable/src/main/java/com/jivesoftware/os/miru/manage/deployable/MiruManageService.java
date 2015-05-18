package com.jivesoftware.os.miru.manage.deployable;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.manage.deployable.region.MiruChromeRegion;
import com.jivesoftware.os.miru.manage.deployable.region.MiruFrameRegion;
import com.jivesoftware.os.miru.manage.deployable.region.MiruHeaderRegion;
import com.jivesoftware.os.miru.manage.deployable.region.MiruManagePlugin;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import java.util.List;

/**
 *
 */
public class MiruManageService {

    private final MiruSoyRenderer renderer;
    private final MiruHeaderRegion headerRegion;
    private final MiruPageRegion<Void> adminRegion;
    private final MiruPageRegion<Optional<MiruHost>> hostsRegion;
    private final MiruPageRegion<Void> balancerRegion;
    private final MiruPageRegion<Optional<String>> schemaRegion;
    private final MiruPageRegion<Optional<MiruTenantId>> tenantsRegion;

    private final List<MiruManagePlugin> plugins = Lists.newCopyOnWriteArrayList();

    public MiruManageService(
        MiruSoyRenderer renderer,
        MiruHeaderRegion headerRegion,
        MiruPageRegion<Void> adminRegion,
        MiruPageRegion<Optional<MiruHost>> hostsRegion,
        MiruPageRegion<Void> balancerRegion,
        MiruPageRegion<Optional<String>> schemaRegion,
        MiruPageRegion<Optional<MiruTenantId>> tenantsRegion) {
        this.renderer = renderer;
        this.headerRegion = headerRegion;
        this.adminRegion = adminRegion;
        this.hostsRegion = hostsRegion;
        this.balancerRegion = balancerRegion;
        this.schemaRegion = schemaRegion;
        this.tenantsRegion = tenantsRegion;
    }

    public void registerPlugin(MiruManagePlugin plugin) {
        plugins.add(plugin);
    }

    private <I, R extends MiruPageRegion<I>> MiruChromeRegion<I, R> chrome(R region) {
        return new MiruChromeRegion<>("soy.miru.chrome.chromeRegion", renderer, headerRegion, plugins, region);
    }

    private <I, R extends MiruPageRegion<I>> MiruFrameRegion<I, R> frame(R region) {
        return new MiruFrameRegion<>("soy.miru.frame.chromeRegion", renderer, region);
    }

    public String render() {
        return chrome(adminRegion).render(null);
    }

    public String renderHosts() {
        return chrome(hostsRegion).render(Optional.<MiruHost>absent());
    }

    public String renderHostsWithFocus(MiruHost host) {
        return chrome(hostsRegion).render(Optional.of(host));
    }

    public String renderBalancer() {
        return chrome(balancerRegion).render(null);
    }

    public String renderSchema() {
        return chrome(schemaRegion).render(Optional.<String>absent());
    }

    public String renderSchemaWithLookup(String lookupJSON) {
        return chrome(schemaRegion).render(Optional.of(lookupJSON));
    }

    public String renderTenants() {
        return chrome(tenantsRegion).render(Optional.<MiruTenantId>absent());
    }

    public String renderTenantsWithFocus(MiruTenantId tenantId) {
        return chrome(tenantsRegion).render(Optional.of(tenantId));
    }

    public <I> String renderPlugin(MiruPageRegion<I> pluginRegion, I input) {
        return chrome(pluginRegion).render(input);
    }

    public <I> String renderFramePlugin(MiruPageRegion<I> pluginRegion, I input) {
        return frame(pluginRegion).render(input);
    }

}
