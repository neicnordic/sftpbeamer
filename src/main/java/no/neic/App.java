package no.neic;

import io.vertx.core.Vertx;
import no.neic.tryggve.HttpVerticle;
import no.neic.tryggve.WebSocketVerticle;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {

        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(HttpVerticle.class.getName());
        vertx.deployVerticle(WebSocketVerticle.class.getName());
    }
}
