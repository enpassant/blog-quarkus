package hu.rapids.blog.query;
import hu.rapids.common.BlogReadModel;
import hu.rapids.common.Domain;
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
public class BlogQueryResource {
    @Inject BlogReadModel service;
    @Inject Pages pages;
    @ConfigProperty(name="blog.title") String title;
    @GET @Path("blog") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response blogs(@Context HttpHeaders headers) {
        List<Blog> blogs=service.all();
        List<Map<String,Object>> summaries=blogs.stream().map(b -> {
            Map<String,Object> m=new LinkedHashMap<>(); m.put("_id",b.id); m.put("title",b.title); m.put("userId",b.userId);
            m.put("userName",b.userName); m.put("datetime",b.datetime); m.put("discussions",b.discussions); return m;
        }).toList();
        return WebResponses.representation(headers,Map.of("title",title,"blogs",summaries),pages.blogs(title,blogs));
    }
    @GET @Path("blog/new") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response newBlog(@Context HttpHeaders headers) {
        String id=UUID.randomUUID().toString(); return WebResponses.representation(headers,Map.of("uuid",id),pages.blogForm(id,null));
    }
    @GET @Path("blog/{id}") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response blog(@PathParam("id") String id,@Context HttpHeaders headers) {
        Blog b=service.get(id); return WebResponses.representation(headers,b,pages.blog(b));
    }
    @GET @Path("blog/{id}/edit") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response editBlog(@PathParam("id") String id,@Context HttpHeaders headers) {
        Blog b=service.get(id); return WebResponses.representation(headers,b,pages.blogForm(id,b));
    }
}
