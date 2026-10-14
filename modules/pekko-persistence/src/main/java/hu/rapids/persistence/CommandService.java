package hu.rapids.persistence;

import org.apache.pekko.actor.*;
import org.apache.pekko.pattern.Patterns;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/** Creates and watches one persistent child actor for each aggregate ID. */
public final class CommandService extends AbstractActor {
    public record Routed(String id,Object message) {}
    private final Function<String,Props> factory;
    private final Map<String,ActorRef> children=new HashMap<>();
    public CommandService(Function<String,Props> factory) { this.factory=factory; }
    public static Props props(Function<String,Props> factory) {
        return Props.create(CommandService.class,() -> new CommandService(factory));
    }
    @Override public Receive createReceive() {
        return receiveBuilder().match(Routed.class,r -> {
            ActorRef actor=children.computeIfAbsent(r.id(),id -> getContext().watch(getContext().actorOf(factory.apply(id))));
            actor.forward(r.message(),getContext());
        }).match(Terminated.class,t -> children.values().remove(t.actor())).build();
    }
    public static Object ask(ActorRef service,String id,Object message) {
        try { return Patterns.ask(service,new Routed(id,message),Duration.ofSeconds(45)).toCompletableFuture().join(); }
        catch(CompletionException e) {
            if(e.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Persistent command failed",e.getCause());
        }
    }
}
