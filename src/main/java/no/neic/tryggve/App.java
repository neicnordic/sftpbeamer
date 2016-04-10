package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;


public class App extends AbstractVerticle {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
//        DeploymentOptions options = new DeploymentOptions().setInstances(4);
//        DeploymentOptions options = new DeploymentOptions().setWorker(true);
        vertx.deployVerticle(UploadVerticle.class.getName());
    }

    @Override
    public void start() throws Exception {
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
    }

}
