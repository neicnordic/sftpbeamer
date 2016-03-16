package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;


public class App extends AbstractVerticle {

    @Override
    public void start() throws Exception {
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
    }

}
