package com.jivesoftware.os.miru.writer.deployable;

import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 *
 */
@Singleton
@Path("/miru/writer")
public class MiruWriterEndpoints {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruWriterUIService writerUIService;

    public MiruWriterEndpoints(@Context MiruWriterUIService writerUIService) {
        this.writerUIService = writerUIService;
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_HTML)
    public Response get() {
        String rendered = writerUIService.render();
        return Response.ok(rendered).build();
    }

}
