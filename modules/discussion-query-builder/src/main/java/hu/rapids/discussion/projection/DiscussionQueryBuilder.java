package hu.rapids.discussion.projection;
import hu.rapids.common.CommandValidation;
import hu.rapids.common.DiscussionReadModel;
import hu.rapids.common.Domain;
import hu.rapids.common.Markdown;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import static hu.rapids.common.Domain.*;
@ApplicationScoped
public class DiscussionQueryBuilder implements hu.rapids.common.MessageHandler {
    @Inject DiscussionReadModel store;
    @Inject Markdown markdown;
    @Inject hu.rapids.common.MessageBus bus;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    public String group() { return "discussion-query"; }
    public java.util.List<String> topics() { return java.util.List.of("discussion-event"); }
    public void handle(hu.rapids.common.MessageBus.Message message) throws Exception {
        Event event=mapper.readValue(message.value(),Event.class); apply(event);
        bus.send("client-commands",event.userId(),hu.rapids.common.Notifications.event(event));
    }
    public synchronized void apply(Event e) {
        switch(e.type()) {
            case "DiscussionStarted" -> {
                try { store.get(e.id()); return; } catch(NotFoundException ignored) { }
                Discussion d=new Discussion(); d.id=e.id(); d.blogId=e.blogId(); d.title=e.title(); d.userId=e.userId();
                d.userName=e.userName(); d.datetime=CommandValidation.date(e.datetime()); store.save(d);
            }
            case "CommentAdded", "CommentReplied" -> {
                Discussion d=store.get(e.id()); if(CommandValidation.find(d.comments,e.commentId())!=null) return;
                Comment c=new Comment(); c.commentId=e.commentId(); c.userId=e.userId(); c.userName=e.userName();
                c.content=e.content(); c.htmlContent=markdown.render(e.content()); c.datetime=CommandValidation.date(e.datetime());
                if(e.type().equals("CommentAdded")) d.comments.add(c);
                else {
                    Comment parent=CommandValidation.find(d.comments,e.parentId());
                    if(parent==null) throw new NotFoundException("Parent comment not found"); parent.comments.add(c);
                }
                store.save(d);
            }
            default -> { }
        }
    }
}
