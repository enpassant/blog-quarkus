package hu.rapids.monitor;
import hu.rapids.common.Domain;
import hu.rapids.common.Statistics;
import hu.rapids.common.web.Pages;
import hu.rapids.common.web.WebResponses;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;
import static hu.rapids.common.Domain.*;

@Path("/")
@Blocking
public class MonitorResource {
    @Inject Statistics service;
    @Inject Pages pages;
    @GET @Path("monitor") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response monitor(@Context HttpHeaders headers) {
        return WebResponses.representation(headers,service.stats(),pages.page("Monitor","<h1>Monitor</h1><pre id=\"stats\"></pre>"));
    }
}
