package hu.rapids.blog.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.rapids.common.Domain.*;
import hu.rapids.persistence.CommandActor;
import jakarta.ws.rs.*;
import org.apache.pekko.actor.Props;
import java.util.*;
import static hu.rapids.common.CommandValidation.*;

/** Java equivalent of rapids' persistent BlogActor. */
public final class BlogActor extends CommandActor {
    private final String id;
    private final ObjectMapper mapper=new ObjectMapper();
    private Blog state;
    public BlogActor(String id,boolean snapshotsEnabled) { super(snapshotsEnabled); this.id=id; }
    public static Props props(String id,boolean snapshotsEnabled) {
        return Props.create(BlogActor.class,() -> new BlogActor(id,snapshotsEnabled));
    }
    @Override public String persistenceId() { return "blog-"+id; }
    @Override protected Event decide(Command command,User user) {
        String type;
        switch(command.type()) {
            case "CreateBlog" -> {
                if(state!=null) throw new ClientErrorException("Blog already exists",409);
                type="BlogCreated";
            }
            case "ModifyBlog" -> {
                if(state==null) throw new NotFoundException("Blog not found in Pekko journal");
                type="BlogModified";
            }
            default -> throw new BadRequestException("Unsupported blog command");
        }
        text(command.title(),"title"); text(command.content(),"content");
        return new Event(type,id,null,null,null,user.userId(),user.userName(),command.title(),command.content(),
                date(command.datetime()),List.of()).withRequestId(command.requestId());
    }
    @Override protected void validateForeign(Event event) {
        if(!"DiscussionStarted".equals(event.type()) || !id.equals(event.blogId()))
            throw new BadRequestException("DiscussionStarted belongs to another blog");
    }
    @Override protected void apply(Event event) {
        switch(event.type()) {
            case "BlogCreated", "BlogModified" -> {
                if(state==null) state=new Blog();
                state.id=id; state.userId=event.userId(); state.userName=event.userName();
                state.title=event.title(); state.content=event.content(); state.datetime=event.datetime();
            }
            case "DiscussionStarted" -> {
                if(state!=null && state.discussions.stream().noneMatch(d -> d.id().equals(event.id())))
                    state.discussions.addFirst(new DiscussionItem(event.id(),event.title(),event.userId(),event.userName()));
            }
            default -> throw new IllegalArgumentException("Unexpected blog journal event: "+event.type());
        }
    }
    @Override protected JsonNode snapshot() { return mapper.valueToTree(state); }
    @Override protected void restore(JsonNode snapshot) {
        var copy=snapshot.deepCopy();
        if(copy.isObject()) ((com.fasterxml.jackson.databind.node.ObjectNode)copy).remove("_t");
        state=copy.isNull()?null:mapper.convertValue(copy,Blog.class);
    }
}
