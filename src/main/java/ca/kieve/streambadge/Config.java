package ca.kieve.streambadge;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final String CONFIG_DIR = "/config";
    private static final String DEV_CONFIG_ROOT = System.getProperty("user.dir");
    private static final String CONFIG_FILE = "/config.properties";

    private final String m_configDir;
    private final String m_twitchClientId;
    private final String m_twitchClientSecret;

    public Config(boolean isDocker) {
        if (isDocker) {
            m_configDir = CONFIG_DIR;
        } else {
            m_configDir = DEV_CONFIG_ROOT + CONFIG_DIR;
        }

        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(m_configDir + CONFIG_FILE));
        } catch (IOException e) {
            System.err.println("Failed to load properties...");
            e.printStackTrace();
            System.exit(-1);
        }

        m_twitchClientId = (String) properties.get("twitchClientId");
        m_twitchClientSecret = (String) properties.get("twitchClientSecret");
    }

    public String getDir() {
        return m_configDir;
    }

    public String getTwitchClientId() {
        return m_twitchClientId;
    }

    public String getTwitchClientSecret() {
        return m_twitchClientSecret;
    }
}
