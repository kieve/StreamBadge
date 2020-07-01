package ca.kieve.streambadge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;

@SpringBootApplication
public class StreamBadge {
    private static final String HELVETICA_NEUE = "/HelveticaNeueBd.ttf";

    private static final String TWITCH_GLITCH = "/Glitch_White_RGB.png";
    private static final String TWITCH_LIVE = "/liveman.png";
    private static final String TWITCH_EYE = "/viewseye.png";

    private static Config m_config;

    private static GraphicsEnvironment m_graphicsEnvironment;
    private static Image m_twitchGlitch;
    private static Image m_twitchLive;
    private static Image m_twitchEye;

    public static void main(String[] args) {
        boolean isDocker = args.length != 0 && args[0].equals("--docker");
        if (isDocker) {
            System.out.println("Running from Docker.");
        }
        m_config = new Config(isDocker);

        m_graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT, getResource(HELVETICA_NEUE));
            m_graphicsEnvironment.registerFont(font);
        } catch (Exception e) {
            System.out.println("Couldn't load the font :(");
            e.printStackTrace();
            return;
        }

        try {
            m_twitchGlitch = ImageIO.read(getResource(TWITCH_GLITCH));
            m_twitchLive = ImageIO.read(getResource(TWITCH_LIVE));
            m_twitchEye = ImageIO.read(getResource(TWITCH_EYE));
        } catch (Exception e) {
            System.out.println("Couldn't load the images :(");
            e.printStackTrace();
            return;
        }

        SpringApplication.run(StreamBadge.class, args);
    }

    private static InputStream getResource(String path) throws IOException {
        return new ClassPathResource(path).getInputStream();
    }

    public static Config getConfig() {
        return m_config;
    }

    public static GraphicsEnvironment getGraphicsEnvironment() {
        return m_graphicsEnvironment;
    }

    public static Image getTwitchGlitch() {
        return m_twitchGlitch;
    }

    public static Image getTwitchLive() {
        return m_twitchLive;
    }

    public static Image getTwitchEye() {
        return m_twitchEye;
    }
}
