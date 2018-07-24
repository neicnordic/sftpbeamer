package no.neic.tryggve;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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

    private static Logger logger = LoggerFactory.getLogger(HttpVerticle.class);


    @Override
    public void start() {

        HttpServerOptions httpServerOptions = new HttpServerOptions();
        httpServerOptions.setUsePooledBuffers(true);
        HttpServer httpServer = vertx.createHttpServer(httpServerOptions);
        Router router = Router.router(vertx);

        router.route().handler(CookieHandler.create());

        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        router.route("/sftp/*").handler(BodyHandler.create());

        router.put(UrlPath.SFTP_UPLOAD).blockingHandler(HttpRequestHandler::chunkUploadHandler, false);

        router.get(UrlPath.SFTP_INFO).produces(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::fetchInfoHandler);

        router.post(UrlPath.SFTP_LOGIN).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::loginHandler, false);

        router.post(UrlPath.SFTP_CONNECT).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::connectHandler, false);

        router.post(UrlPath.SFTP_TRANSFER_PREPARE).consumes(MediaType.APPLICATION_JSON).produces(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::transferPrepareHandler);

        router.post(UrlPath.SFTP_TRANSFER_REGISTER).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::registerTransferHandler, false);

        router.post(UrlPath.SFTP_TRANSFER_JOB_SUBMIT).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::submitDataTransferHandler, false);

        router.get(UrlPath.SFTP_LIST).produces(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::listHandler, false);

        router.post(UrlPath.SFTP_FILE_CHECK).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::checkFileHandler);

        router.delete(UrlPath.SFTP_DELETE).consumes(MediaType.APPLICATION_JSON).blockingHandler(HttpRequestHandler::deleteHandler, false);

        router.delete(UrlPath.SFTP_DISCONNECT).produces(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::disconnectHandler);

        router.post(UrlPath.SFTP_CREATE).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::createFolderHandler);

        router.post(UrlPath.SFTP_RENAME).consumes(MediaType.APPLICATION_JSON).handler(HttpRequestHandler::renameHandler);

        router.get(UrlPath.SFTP_DOWNLOAD).blockingHandler(HttpRequestHandler::downloadHandler, false);

        router.get(UrlPath.SFTP_ZIP).blockingHandler(HttpRequestHandler::downloadZipHandler, false);

        router.get(UrlPath.SFTP_DOWNLOAD_CHECK).handler(HttpRequestHandler::downloadCheckHandler);

        router.route().handler(StaticHandler.create());
        router.route("/static/*").handler(StaticHandler.create());

        httpServer.requestHandler(router::accept).listen(
                Integer.parseInt(Config.valueOf(ConfigName.HTTP_VERTICLE_PORT)),
                Config.valueOf(ConfigName.HOST));

    }
}
