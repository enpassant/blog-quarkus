package hu.rapids.discussion.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.rapids.common.Domain.*;
import hu.rapids.persistence.CommandActor;
import jakarta.ws.rs.*;
import org.apache.pekko.actor.Props;
import java.util.*;
import static hu.rapids.common.CommandValidation.*;

/** Keeps the same comment index tree as rapids, independently of query collections. */
public final class DiscussionActor extends CommandActor {
    public record CommentIndex(List<Integer> path,int childCount) {}
    public record State(String id,String blogId,String title,Map<String,CommentIndex> comments,int childCount) {}
    private final String id;
    private final ObjectMapper mapper=new ObjectMapper();
    private State state;
    public DiscussionActor(String id,boolean snapshotsEnabled) { super(snapshotsEnabled); this.id=id; }
    public static Props props(String id,boolean snapshotsEnabled) {
        return Props.create(DiscussionActor.class,() -> new DiscussionActor(id,snapshotsEnabled));
    }
    @Override public String persistenceId() { return "discussion-"+id; }
    @Override protected Event decide(Command command,User user) {
        String date=date(command.datetime());
        if(command.type().equals("StartDiscussion")) {
            if(state!=null) throw new ClientErrorException("Discussion already exists",409);
            identifier(command.blogId()); text(command.title(),"title");
            return new Event("DiscussionStarted",id,null,null,command.blogId(),user.userId(),user.userName(),command.title(),null,date,List.of()).withRequestId(command.requestId());
        }
        if(!command.type().equals("AddComment") && !command.type().equals("ReplyComment")) throw new BadRequestException("Unsupported discussion command");
        if(state==null) throw new NotFoundException("Discussion not found in Pekko journal");
        identifier(command.id()); text(command.content(),"content");
        if(state.comments().containsKey(command.id())) throw new ClientErrorException("Comment already exists",409);
        List<Integer> path;
        if(command.type().equals("AddComment")) path=List.of(state.childCount());
        else {
            CommentIndex parent=state.comments().get(command.parentId());
            if(parent==null) throw new NotFoundException("Parent comment not found");
            path=new ArrayList<>(); path.add(parent.childCount()); path.addAll(parent.path());
        }
        return new Event(command.type().equals("AddComment")?"CommentAdded":"CommentReplied",id,command.id(),command.parentId(),null,
                user.userId(),user.userName(),null,command.content(),date,path).withRequestId(command.requestId());
    }
    @Override protected void apply(Event event) {
        if(event.type().equals("DiscussionStarted")) {
            state=new State(id,event.blogId(),event.title(),new TreeMap<>(),0); return;
        }
        if(!event.type().equals("CommentAdded") && !event.type().equals("CommentReplied")) throw new IllegalArgumentException("Unexpected discussion journal event");
        if(state==null) throw new IllegalStateException("Comment precedes DiscussionStarted in journal");
        if(state.comments().containsKey(event.commentId())) return;
        Map<String,CommentIndex> comments=new TreeMap<>(state.comments());
        int count=state.childCount();
        if(event.type().equals("CommentAdded")) count++;
        else {
            CommentIndex parent=comments.get(event.parentId());
            if(parent==null) throw new IllegalStateException("Missing parent comment in Pekko journal");
            comments.put(event.parentId(),new CommentIndex(parent.path(),parent.childCount()+1));
        }
        comments.put(event.commentId(),new CommentIndex(event.path(),0));
        state=new State(id,state.blogId(),state.title(),comments,count);
    }
    @Override protected JsonNode snapshot() { return mapper.valueToTree(state); }
    @Override protected void restore(JsonNode snapshot) {
        var copy=snapshot.deepCopy();
        if(copy.isObject()) {
            var node=(com.fasterxml.jackson.databind.node.ObjectNode)copy;
            node.remove(List.of("_t","userId","userName"));
        }
        state=copy.isNull()?null:mapper.convertValue(copy,State.class);
    }
}
