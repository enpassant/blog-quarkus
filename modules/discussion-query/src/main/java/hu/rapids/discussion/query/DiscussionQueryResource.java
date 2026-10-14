package hu.rapids.discussion.query;
import hu.rapids.common.CommandValidation;
import hu.rapids.common.DiscussionReadModel;
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
public class DiscussionQueryResource {
    @Inject DiscussionReadModel service;
    @Inject Pages pages;
    @GET @Path("discussion/{id}") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response discussion(@PathParam("id") String id,@Context HttpHeaders headers) {
        Discussion d=service.get(id); return WebResponses.representation(headers,d,pages.discussion(d));
    }
    @GET @Path("discussion/{id}/new") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response newComment(@PathParam("id") String id,@Context HttpHeaders headers) { return commentForm(id,null,headers); }
    @GET @Path("discussion/{id}/comment/{commentId}/new") @Produces({MediaType.TEXT_HTML,MediaType.APPLICATION_JSON})
    public Response reply(@PathParam("id") String id,@PathParam("commentId") String commentId,@Context HttpHeaders headers) { return commentForm(id,commentId,headers); }
    private Response commentForm(String id,String parent,HttpHeaders headers) {
        Discussion d=service.get(id);
        if(parent!=null && CommandValidation.find(d.comments,parent)==null) throw new NotFoundException("Comment not found");
        String uuid=UUID.randomUUID().toString(); Map<String,String> json=new LinkedHashMap<>(); json.put("_id",id); json.put("uuid",uuid);
        if(parent!=null) json.put("commentId",parent);
        return WebResponses.representation(headers,json,pages.commentForm(id,parent,uuid));
    }
}
