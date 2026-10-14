package hu.rapids.blog.command;

import hu.rapids.common.*;
import hu.rapids.persistence.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.pekko.actor.ActorRef;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;
import static hu.rapids.common.Domain.*;

/** Kafka adapter; domain decisions and persistence belong to BlogActor. */
@ApplicationScoped
public class BlogCommandHandler implements CommandHandler,MessageHandler {
    @Inject PekkoRuntime runtime;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Inject MessageBus bus;
    @Inject TokenService tokens;
    @ConfigProperty(name="blog.pekko.snapshots-enabled",defaultValue="false") boolean snapshotsEnabled;
    private ActorRef service;
    @jakarta.annotation.PostConstruct void open() {
        boolean snapshots=snapshotsEnabled;
        service=runtime.system().actorOf(CommandService.props(id -> BlogActor.props(id,snapshots)),"blog-command");
    }
    public String topic() { return "blog"; }
    @SuppressWarnings("unchecked")
    public List<Event> execute(String id,Command command,User user) {
        CommandValidation.identifier(id);
        return (List<Event>)CommandService.ask(service,id,new CommandActor.Execute(command,user));
    }
    public String group() { return "blog-command"; }
    public List<String> topics() { return List.of("blog-command"); }
    public void handle(MessageBus.Message message) throws Exception {
        var json=mapper.readTree(message.value());
        if(json.path("_t").asText().equals("DiscussionStarted")) {
            Event event=mapper.treeToValue(json,Event.class);
            CommandValidation.identifier(event.blogId());
            CommandService.ask(service,event.blogId(),event.withRequestId("discussion:"+event.id()));
            bus.send("blog-event",event.blogId(),event); return;
        }
        Command command=mapper.treeToValue(json,Command.class);
        if(command.type()==null) throw new BadRequestException("Missing _t command type");
        User user=tokens.verifyQueued(json.path("loggedIn").path("token").asText(),message.timestampMillis());
        for(Event event:execute(message.key(),command,user)) {
            bus.send("blog-event",event.id(),event);
            if(event.type().equals("BlogCreated")) {
                String discussionId="disc-"+event.id();
                Command start=new Command("StartDiscussion",discussionId,null,event.id(),event.title(),null,event.datetime(),command.requestId());
                com.fasterxml.jackson.databind.node.ObjectNode payload=mapper.valueToTree(start);
                payload.set("loggedIn",mapper.valueToTree(Map.of("userId",user.userId(),"userName",user.userName(),"token",tokens.issue(user))));
                bus.send("discussion-command",discussionId,payload);
            }
        }
    }
}
