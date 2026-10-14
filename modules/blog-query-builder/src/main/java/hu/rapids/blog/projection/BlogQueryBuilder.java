package hu.rapids.blog.projection;
import hu.rapids.common.BlogReadModel;
import hu.rapids.common.CommandValidation;
import hu.rapids.common.Domain;
import hu.rapids.common.Markdown;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import static hu.rapids.common.Domain.*;
@ApplicationScoped
public class BlogQueryBuilder implements hu.rapids.common.MessageHandler {
    @Inject BlogReadModel store;
    @Inject Markdown markdown;
    @Inject hu.rapids.common.MessageBus bus;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    public String group() { return "blog-query"; }
    public java.util.List<String> topics() { return java.util.List.of("blog-event"); }
    public void handle(hu.rapids.common.MessageBus.Message message) throws Exception {
        Event event=mapper.readValue(message.value(),Event.class); apply(event);
        bus.send("client-commands",event.userId(),hu.rapids.common.Notifications.event(event));
    }
    public synchronized void apply(Event e) {
        switch(e.type()) {
            case "BlogCreated" -> {
                try { store.get(e.id()); return; } catch(NotFoundException ignored) { }
                Blog b=new Blog(); b.id=e.id(); b.userId=e.userId(); b.userName=e.userName(); b.title=e.title();
                b.content=e.content(); b.htmlContent=markdown.render(e.content()); b.datetime=CommandValidation.date(e.datetime()); store.save(b);
            }
            case "BlogModified" -> {
                Blog b=store.get(e.id()); b.title=e.title(); b.content=e.content(); b.htmlContent=markdown.render(e.content()); store.save(b);
            }
            case "DiscussionStarted" -> {
                Blog b=store.get(e.blogId());
                if(b.discussions.stream().noneMatch(d -> d.id().equals(e.id()))) { b.discussions.add(new DiscussionItem(e.id(),e.title(),e.userId(),e.userName())); store.save(b); }
            }
            default -> { }
        }
    }
}
