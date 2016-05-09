package no.neic.tryggve;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);

    private static Config instance;
    private String configPath = "./sftp.beamer.properties";

    private Properties properties;


    private Config() throws IOException {
        properties = new Properties();
        File file = new File(configPath);
        if (file.exists()) {
            try (final InputStream inputStream = new FileInputStream(file)) {
                properties.load(inputStream);
            } catch (IOException e) {
                logger.error(e.getMessage());
                throw e;
            }
        } else {
            try (final InputStream inputStream = Config.class.getClassLoader().getResourceAsStream("sftp.beamer.properties")) {
                properties.load(inputStream);
            } catch (IOException e) {
                logger.error(e.getMessage());
                throw e;
            }
        }
    }

    public static void init() throws IOException {
        if (instance == null) {
            instance = new Config();
        }
    }

    public static String valueOf(String key) {
        return instance.properties.getProperty(key);
    }
}
