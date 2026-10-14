package hu.rapids.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import hu.rapids.common.Domain.*;
import org.apache.pekko.actor.*;
import org.apache.pekko.persistence.*;
import jakarta.ws.rs.ClientErrorException;
import java.time.Duration;
import java.util.*;

public abstract class CommandActor extends AbstractPersistentActor {
    public record Execute(Command command,User user) {}
    private final Map<String,Event> completed=new HashMap<>();
    private final boolean snapshotsEnabled;
    protected CommandActor(boolean snapshotsEnabled) { this.snapshotsEnabled=snapshotsEnabled; }
    @Override public Recovery recovery() {
        return snapshotsEnabled?Recovery.create():Recovery.create(SnapshotSelectionCriteria.none());
    }
    @Override public void preStart() throws Exception {
        super.preStart(); getContext().setReceiveTimeout(Duration.ofMinutes(10));
    }
    @Override public Receive createReceiveRecover() {
        return receiveBuilder().match(Event.class,this::record).match(SnapshotOffer.class,offer -> {
            if(!(offer.snapshot() instanceof ActorSnapshot snapshot)) throw new IllegalStateException("Unsupported actor snapshot");
            restore(snapshot.state()); completed.clear(); snapshot.completedRequests().forEach(this::remember);
        }).build();
    }
    @Override public Receive createReceive() {
        return receiveBuilder().match(Execute.class,request -> {
            try {
                Event previous=completed.get(request.command().requestId());
                if(previous!=null) { checkRetry(previous,request); getSender().tell(List.of(previous),getSelf()); }
                else persistEvent(decide(request.command(),request.user()));
            } catch(RuntimeException e) { getSender().tell(new Status.Failure(e),getSelf()); }
        }).match(Event.class,event -> {
            try {
                Event previous=completed.get(event.requestId());
                if(previous!=null) getSender().tell(List.of(previous),getSelf());
                else { validateForeign(event); persistEvent(event); }
            } catch(RuntimeException e) { getSender().tell(new Status.Failure(e),getSelf()); }
        }).matchEquals("snap",ignored -> saveSnapshot(new ActorSnapshot(snapshot(),List.copyOf(completed.values()))))
          .match(SaveSnapshotSuccess.class,ignored -> {})
          .match(SaveSnapshotFailure.class,failure -> getContext().getSystem().log().error(failure.cause(),"Snapshot failed for {}",persistenceId()))
          .match(ReceiveTimeout.class,ignored -> getContext().stop(getSelf())).build();
    }
    private void persistEvent(Event event) {
        ActorRef replyTo=getSender();
        // Stash subsequent commands until persistence and state update finish.
        persist(event,saved -> { record(saved); replyTo.tell(List.of(saved),getSelf()); });
    }
    private void record(Event event) { apply(event); remember(event); }
    private void remember(Event event) {
        if(event.requestId()!=null && !event.requestId().isBlank()) completed.put(event.requestId(),event);
    }
    private void checkRetry(Event event,Execute request) {
        Command command=request.command();
        String expected=switch(command.type()) {
            case "CreateBlog" -> "BlogCreated"; case "ModifyBlog" -> "BlogModified";
            case "StartDiscussion" -> "DiscussionStarted"; case "AddComment" -> "CommentAdded";
            case "ReplyComment" -> "CommentReplied"; default -> "";
        };
        if(!expected.equals(event.type()) || !Objects.equals(event.userId(),request.user().userId())
                || !Objects.equals(event.content(),command.content()) || !Objects.equals(event.title(),command.title())
                || !Objects.equals(event.parentId(),command.parentId()) || !Objects.equals(event.blogId(),command.blogId())
                || ((expected.equals("CommentAdded") || expected.equals("CommentReplied")) && !Objects.equals(event.commentId(),command.id())))
            throw new ClientErrorException("Request ID belongs to another command",409);
    }
    protected void validateForeign(Event event) { throw new IllegalArgumentException("Unsupported actor event"); }
    protected abstract Event decide(Command command,User user);
    protected abstract void apply(Event event);
    protected abstract JsonNode snapshot();
    protected abstract void restore(JsonNode snapshot);
}
