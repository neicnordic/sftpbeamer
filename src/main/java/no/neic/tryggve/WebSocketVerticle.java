package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import no.neic.tryggve.constants.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Once the data transfer between two hosts is started, the web socket connection is established, and is used to
 * send transfer progress to web client.
 *
 * The data transfer task is started in HttpVerticle, the communication between HttpVerticle and WebSocketVerticle is
 * through the event bus of vertx. When a data transfer task is started by a web client, a random number is generated and
 * represents a web socket connection. The WebsocketVerticle is using this key to distinguish the web socket connections from
 * different web clients.
 */
public final class WebSocketVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketVerticle.class);

    /**
     * It is used to keep every web socket connection. The key is generated when a web client is starting to request the connection.
     */
    private Map<String, ServerWebSocket> webSocketHolder;

    @Override
    public void start() throws Exception {
        webSocketHolder = new HashMap<>();
        vertx.eventBus().<String>consumer(VertxConstant.TRANSFER_EVENTBUS_NAME, message -> {

            JsonObject jsonObject = new JsonObject(message.body());
            String webSocketAddress = jsonObject.getString(JsonPropertyName.ADDRESS);
            if (webSocketHolder.containsKey(webSocketAddress)) { //check if webSocketHolder keeps a connection associated with a provided address
                webSocketHolder.get(webSocketAddress).writeFinalTextFrame(message.body());
                if (jsonObject.getString(JsonPropertyName.STATUS).equals(TransferStatus.FINISH) || jsonObject.getString(JsonPropertyName.STATUS).equals(TransferStatus.ERROR)) { // check if this web socket connection should be closed or not
                    webSocketHolder.remove(webSocketAddress).close();
                }
            }
        });
        vertx.createHttpServer().websocketHandler(serverWebSocket -> {
            if (serverWebSocket.path().equals(UrlPath.SFTP_WS)) {

                serverWebSocket.handler(buffer -> {

                    String address = buffer.toJsonObject().getString(JsonPropertyName.ADDRESS);
                    webSocketHolder.put(address, serverWebSocket);
                    serverWebSocket.writeFinalTextFrame(new JsonObject().put(JsonPropertyName.STATUS, TransferStatus.CONNECTED).encode());
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
