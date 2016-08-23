package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import no.neic.tryggve.constants.ConfigName;
import no.neic.tryggve.constants.UrlPath;

import javax.ws.rs.core.MediaType;

public final class HttpVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {

        HttpServer httpServer = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        router.route("/sftp/*").handler(BodyHandler.create());

        router.get(UrlPath.SFTP_INFO).produces(MediaType.APPLICATION_JSON).handler(HttpRequestFacade::fetchInfoHandler);

        router.post(UrlPath.SFTP_LOGIN).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestFacade::loginHandler, false);

        router.post(UrlPath.SFTP_TRANSFER_PREPARE).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).handler(HttpRequestFacade::transferPrepareHandler);

        router.post(UrlPath.SFTP_TRANSFER_START).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).handler(HttpRequestFacade::transferStartHandler);

        router.get(UrlPath.SFTP_LIST).produces(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestFacade::listHandler, false);

        router.get(UrlPath.SFTP_UPLOAD_REFERENCE).produces(MediaType.TEXT_PLAIN).handler(HttpRequestFacade::getReferenceHandler);

        router.delete(UrlPath.SFTP_UPLOAD_REFERENCE).consumes(MediaType.TEXT_PLAIN).handler(HttpRequestFacade::deleteReferenceHandler);

        router.delete(UrlPath.SFTP_DELETE).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestFacade::deleteHandler, false);

        router.delete(UrlPath.SFTP_DISCONNECT).produces(MediaType.APPLICATION_JSON).handler(HttpRequestFacade::disconnectHandler);

        router.post(UrlPath.SFTP_CREATE).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestFacade::createFolderHandler);

        router.post(UrlPath.SFTP_RENAME).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestFacade::renameHandler);

        router.route().handler(StaticHandler.create());
        router.route("/static/*").handler(StaticHandler.create());

        httpServer.requestHandler(router::accept).listen(
                Integer.parseInt(Config.valueOf(ConfigName.HTTP_VERTICLE_PORT)),
                Config.valueOf(ConfigName.HOST));

    }
}
