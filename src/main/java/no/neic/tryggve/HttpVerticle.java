package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import no.neic.tryggve.constants.ConfigName;

public final class HttpVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(HttpVerticle.class);

    @Override
    public void start() throws Exception {

        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        router.route("/sftp/*").handler(BodyHandler.create());

        router.get("/sftp/info").produces("application/json").handler(HttpRequestFacade::fetchInfoHandler);

        router.post("/sftp/login").consumes("*/json").produces("application/json").blockingHandler(HttpRequestFacade::loginHandler, false);

        router.post("/sftp/transfer").consumes("*/json").produces("application/json").handler(HttpRequestFacade::transferHandler);

        router.get("/sftp/list").produces("application/json").blockingHandler(HttpRequestFacade::listHandler, false);

        router.get("/sftp/upload").produces("text/plain").handler(HttpRequestFacade::uploadHandler);

        router.delete("/sftp/delete").consumes("*/json").blockingHandler(HttpRequestFacade::deleteHandler, false);

        router.delete("/sftp/disconnect").produces("application/json").handler(HttpRequestFacade::disconnectHandler);

        router.route().handler(StaticHandler.create());
        router.route("/static/*").handler(StaticHandler.create());

        httpServer.requestHandler(router::accept).listen(
                Integer.parseInt(Config.valueOf(ConfigName.HTTP_VERTICLE_PORT)),
                Config.valueOf(ConfigName.HOST));

    }
}
