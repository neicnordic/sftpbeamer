package no.neic.tryggve;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.BodyHandlerImpl;
import no.neic.tryggve.constants.UrlPath;

public final class HttpBodyHandler extends BodyHandlerImpl{

    @Override
    public void handle(RoutingContext context) {
        if (!context.request().path().equals(UrlPath.SFTP_UPLOAD)) {
            super.handle(context);
        } else {
            HttpRequestFacade.uploadHandler(context);
        }
    }
}
