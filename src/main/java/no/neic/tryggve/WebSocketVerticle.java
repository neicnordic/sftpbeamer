package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import no.neic.tryggve.constants.ConfigName;

public final class WebSocketVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketVerticle.class);

    @Override
    public void start() throws Exception {
        vertx.createHttpServer().websocketHandler(serverWebSocket -> {
            if (serverWebSocket.path().equals("/ws")) {

                serverWebSocket.handler(buffer -> {

                    MessageConsumer<String> consumer = vertx.eventBus().consumer(buffer.toJsonObject().getString("address"));
                    consumer.handler(message -> {
                        String str = message.body();
                        serverWebSocket.writeFinalTextFrame(str);
                        JsonObject jsonObject = new JsonObject(str);
                        if (jsonObject.getString("status").equals("done")) {
                            serverWebSocket.close();
                            consumer.unregister();
                        }
                    });
                });


            } else {
                serverWebSocket.reject();
            }
        }).listen(Integer.parseInt(Config.valueOf(ConfigName.WEBSOCKET_VERTICLE_PORT)),
                Config.valueOf(ConfigName.HOST));
    }

    @Override
    public void stop() throws Exception {
        super.stop();
    }
}
