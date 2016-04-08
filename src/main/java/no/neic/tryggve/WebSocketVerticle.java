package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public final class WebSocketVerticle extends AbstractVerticle {
    @Override
    public void start() throws Exception {
        vertx.createHttpServer().websocketHandler(serverWebSocket -> {
            if (serverWebSocket.path().equals("/ws")) {

                serverWebSocket.handler(buffer -> {
                    JsonObject jsonObject = buffer.toJsonObject();
                    String address = jsonObject.getString("address");
                    EventBus bus = vertx.eventBus();
                    MessageConsumer<String> consumer = bus.consumer(address);
                    consumer.handler(message -> {
                        String str = message.body();
                        serverWebSocket.writeFinalTextFrame(str);
                    });
                });
            } else {
                serverWebSocket.reject();
            }
        }).listen(8081);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
