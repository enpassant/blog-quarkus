package hu.rapids.web;
import hu.rapids.common.MessageBus;
import hu.rapids.common.ServiceRegistry;
import hu.rapids.common.CommandValidation;
import hu.rapids.common.Domain;
import hu.rapids.common.web.AuthService;
import hu.rapids.common.web.Pages;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;
import static hu.rapids.common.Domain.*;

@Path("/")
@Blocking
public class WebResource {
    @Inject MessageBus bus;
    @Inject ServiceRegistry registry;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Inject AuthService auth;
    @Inject Pages pages;
    @ConfigProperty(name="blog.title") String title;
    @GET public Response home() {
        StringBuilder links=new StringBuilder("<h1>"+Pages.escape(title)+"</h1><div id=\"services\">");
        registry.links().forEach(link -> links.append("<p><a href=\"").append(Pages.escape(link.url())).append("\">").append(Pages.escape(link.title())).append("</a></p>"));
        links.append("</div>");
        return Response.ok(pages.page(title,links.toString()),MediaType.TEXT_HTML).header("Link",registry.header()).build();
    }
    @GET @Path("services") @Produces(MediaType.APPLICATION_JSON)
    public Object services() { return registry.all(); }
    @POST @Path("login") public Response login(@HeaderParam("Authorization") String authorization,@CookieParam("X-Token") String cookie) {
        User user=auth.authenticate(authorization,cookie); String token=auth.issue(user);
        return Response.ok(Map.of("userId",user.userId(),"userName",user.userName()),MediaType.APPLICATION_JSON)
                .header("X-Token",token).build();
    }
    @GET @Path("login") public Response oidcLogin(@HeaderParam("Authorization") String authorization,@CookieParam("X-Token") String cookie) {
        return login(authorization,cookie);
    }
    @POST @Path("commands/{topic}/{id}") @Consumes({MediaType.APPLICATION_JSON,MediaType.TEXT_PLAIN}) @Produces(MediaType.APPLICATION_JSON)
    public Response command(@PathParam("topic") String topic,@PathParam("id") String id, Command command,
                            @HeaderParam("Authorization") String authorization,@CookieParam("X-Token") String cookie) {
        User user=auth.authenticate(authorization,cookie);
        CommandValidation.identifier(id);
        if(!Set.of("blog","discussion").contains(topic)) throw new BadRequestException("Unknown command topic");
        if(command==null || command.type()==null) throw new BadRequestException("Missing _t command type");
        String requestId=command.requestId()==null?UUID.randomUUID().toString():command.requestId();
        CommandValidation.identifier(requestId);
        String token=auth.issue(user);
        com.fasterxml.jackson.databind.node.ObjectNode payload=mapper.valueToTree(command);
        payload.put("_requestId",requestId);
        payload.set("loggedIn",mapper.valueToTree(Map.of("userId",user.userId(),"userName",user.userName(),"token",token)));
        bus.send(topic+"-command",id,payload);
        return Response.status(bus.kafkaEnabled()?202:200).entity(Map.of("value","CommandAccepted","requestId",requestId)).header("X-Token",token).build();
    }
}
