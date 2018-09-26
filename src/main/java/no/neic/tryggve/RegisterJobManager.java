package no.neic.tryggve;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import no.neic.tryggve.constants.JsonKey;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RegisterJobManager {
    private static Logger logger = LoggerFactory.getLogger(RegisterJobManager.class);

    private static RegisterJobManager instance = new RegisterJobManager();

    public static RegisterJobManager getManager() {
        return instance;
    }

    private Map<String, Session> map;

    private RegisterJobManager() {
        map = new ConcurrentHashMap<>();
    }

    public boolean register(JsonObject object) throws JSchException {
        String email = object.getString(JsonKey.EMAIL);
        Session newSession = Utils.createSftpSession(
                object.getString(JsonKey.USERNAME),
                object.getString(JsonKey.PASSWORD),
                object.getString(JsonKey.HOSTNAME),
                object.getInteger(JsonKey.PORT),
                StringUtils.isEmpty(object.getString(JsonKey.OTC)) ? Optional.empty() : Optional.of(object.getString(JsonKey.OTC)));

        Session session = map.putIfAbsent(email, newSession);
        if (session == null) {
            logger.debug("A new transfer job {} is registered", email);
            return true;
        } else {
            newSession.disconnect();
            return false;
        }
    }

    public Session fetch(String email) {
        logger.debug("A session for email {} is fetched", email);
        return map.remove(email);
    }

    public void remove(String email) {
        logger.debug("A session for email {} is removed", email);
        Session session = map.remove(email);
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
