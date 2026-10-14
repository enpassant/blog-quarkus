package hu.rapids.discussion.command;

import hu.rapids.common.*;
import hu.rapids.persistence.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.pekko.actor.ActorRef;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.List;
import static hu.rapids.common.Domain.*;

/** Kafka adapter; DiscussionActor owns the persistent comment index. */
@ApplicationScoped
public class DiscussionCommandHandler implements CommandHandler,MessageHandler {
    @Inject PekkoRuntime runtime;
    @Inject com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Inject MessageBus bus;
    @Inject TokenService tokens;
    @ConfigProperty(name="blog.pekko.snapshots-enabled",defaultValue="false") boolean snapshotsEnabled;
    private ActorRef service;
    @jakarta.annotation.PostConstruct void open() {
        boolean snapshots=snapshotsEnabled;
        service=runtime.system().actorOf(CommandService.props(id -> DiscussionActor.props(id,snapshots)),"discussion-command");
    }
    public String topic() { return "discussion"; }
    @SuppressWarnings("unchecked")
    public List<Event> execute(String id,Command command,User user) {
        CommandValidation.identifier(id);
        return (List<Event>)CommandService.ask(service,id,new CommandActor.Execute(command,user));
    }
    public String group() { return "discussion-command"; }
    public List<String> topics() { return List.of("discussion-command"); }
    public void handle(MessageBus.Message message) throws Exception {
        var json=mapper.readTree(message.value());
        Command command=mapper.treeToValue(json,Command.class);
        if(command.type()==null) throw new BadRequestException("Missing _t command type");
        User user;
        if(command.type().equals("StartDiscussion") && !json.path("loggedIn").hasNonNull("token")) {
            // Original Rapids sends this internal command with user fields, without a login token.
            user=new User(json.path("userId").asText(),json.path("userName").asText());
        } else user=tokens.verifyQueued(json.path("loggedIn").path("token").asText(),message.timestampMillis());
        for(Event event:execute(message.key(),command,user)) {
            bus.send("discussion-event",event.id(),event);
            if(event.type().equals("DiscussionStarted")) bus.send("blog-command",event.blogId(),event);
        }
    }
}
