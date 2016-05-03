package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import java.io.IOException;


public class App extends AbstractVerticle {

    public static void main(String[] args) throws IOException{
        System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.Log4jLogDelegateFactory");
        Config.init();
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
        vertx.deployVerticle(UploadVerticle.class.getName());

    }

    @Override
    public void start() throws Exception {
        Config.init();
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
        vertx.deployVerticle(UploadVerticle.class.getName());
    }

}
