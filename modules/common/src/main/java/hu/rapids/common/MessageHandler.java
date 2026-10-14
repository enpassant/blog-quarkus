package hu.rapids.common;
import java.util.List;
public interface MessageHandler {
    String group();
    List<String> topics();
    void handle(MessageBus.Message message) throws Exception;
}
