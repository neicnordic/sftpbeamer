package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import no.neic.tryggve.constants.ConfigName;
import no.neic.tryggve.constants.JsonPropertyName;

import java.util.HashMap;
import java.util.Map;

public final class WebSocketVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketVerticle.class);

    private Map<String, ServerWebSocket> webSocketHolder;

    @Override
    public void start() throws Exception {
        webSocketHolder = new HashMap<>();
        vertx.eventBus().<String>consumer("transfer", message -> {

            JsonObject jsonObject = new JsonObject(message.body());
            String webSocketAddress = jsonObject.getString(JsonPropertyName.ADDRESS);
            if (webSocketHolder.containsKey(webSocketAddress)) {
                webSocketHolder.get(webSocketAddress).writeFinalTextFrame(message.body());
                if (jsonObject.getString(JsonPropertyName.STATUS).equals("done")) {
                    webSocketHolder.remove(webSocketAddress).close();
                }
            }
        });
        vertx.createHttpServer().websocketHandler(serverWebSocket -> {
            if (serverWebSocket.path().equals("/ws")) {

                serverWebSocket.handler(buffer -> {

                    String address = buffer.toJsonObject().getString(JsonPropertyName.ADDRESS);
                    webSocketHolder.put(address, serverWebSocket);
                    serverWebSocket.writeFinalTextFrame(new JsonObject().put(JsonPropertyName.STATUS, "connected").encode());
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
