package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

import java.io.IOException;


public class App extends AbstractVerticle {

    public static void main(String[] args) throws IOException{
        Config.init();
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());

    }

    /**
     * This is starting point of this web app. It mainly contains three different verticles.
     * Both WebSocketVerticle and UploadVerticle have only one purpose. WebSocketVerticle is
     * used to push the file transfer progress to web browser. UploadVerticle is used to handle
     * with uploading files. HttpVerticle will do the remaining tasks, for example, connecting
     * with remote host, listing the content, deleting data and so on.
     */
    @Override
    public void start() throws Exception {
        Config.init();
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
    }

}
