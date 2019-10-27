package ca.kieve.streambadge;

import fi.iki.elonen.NanoHTTPD;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class App extends NanoHTTPD {
    private static final String CLIENT_ID_HEADER = "Client-ID";

    private static final String TWITCH_STREAM =
            "https://api.twitch.tv/helix/streams?user_login=%s";
    private static final String TWITCH_USER =
            "https://api.twitch.tv/helix/users?login=%s";
    private static final String TWITCH_GAME =
            "https://api.twitch.tv/helix/games?id=%s";

    private static final String CONFIG = "/config.properties";
    private static final String CONFIG_TWITCH_CLIENT_ID = "twitchClientId";

    private static final String HELVETICA_NEUE = "/HelveticaNeueBd.ttf";

    private static final String TWITCH_GLITCH = "/Glitch_White_RGB.png";
    private static final Color  TWITCH_PURPLE = new Color(100, 65, 164);
    private static final String TWITCH_LIVE = "/liveman.png";
    private static final Color  TWITCH_RED = new Color(207, 54, 54);
    private static final String TWITCH_EYE = "/viewseye.png";
    private static final Color  TWITCH_GREY = new Color(137, 131, 149);

    private static class TwitchMetaData {
        String displayName;
        boolean streaming;
        String gameId;
        String profileImageUrl;
        int viewers;
        int views;
    }

    private static class TwitchGameData {
        String gameName;
        Image image;
    }

    private final String              m_twitchClientId;
    private final GraphicsEnvironment m_graphicsEnvironment;

    private Map<String, Image> m_gameImageCache;
    private Map<String, Image> m_profileImageCache;

    private Image m_twitchGlitch;
    private Image m_twitchLive;
    private Image m_twitchEye;

    private App() throws IOException {
        super(8080);

        Properties props = new Properties();
        props.load(getClass().getResourceAsStream(CONFIG));
        m_twitchClientId = props.getProperty(CONFIG_TWITCH_CLIENT_ID);

        m_graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        try {
            Font font = Font.createFont(Font.TRUETYPE_FONT,
                    getClass().getResourceAsStream(HELVETICA_NEUE));
            m_graphicsEnvironment.registerFont(font);
        } catch (Exception e) {
            System.out.println("Couldn't load the font :(");
            e.printStackTrace();
        }

        m_gameImageCache = new HashMap<>();
        m_profileImageCache = new HashMap<>();

        m_twitchGlitch = ImageIO.read(getClass().getResourceAsStream(TWITCH_GLITCH));
        m_twitchLive = ImageIO.read(getClass().getResourceAsStream(TWITCH_LIVE));
        m_twitchEye = ImageIO.read(getClass().getResourceAsStream(TWITCH_EYE));

        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browsers to http://localhost:8080/ \n");
    }

    public static void main(String[] args) {
        try {
            new App();
        } catch (IOException e) {
            System.err.println("Couldn't start server");
            e.printStackTrace();
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        try {
            String uri = session.getUri();
            if (uri.startsWith("/oauth")) {
                // Do nothing... I don't accept oauth requests.
                return status("ok: oauth attempt");
            }

            Map<String, String> params = session.getParms();
            String twitchUser = params.get("user");

            if (twitchUser == null) {
                return status("not ok: no user specified");
            }

            TwitchMetaData metaData = getMetaData(twitchUser);
            if (metaData == null) {
                return status("not ok: can't find twitch user");
            }

            TwitchGameData gameData = getGameImage(metaData.gameId);
            Image profileImage = getProfileImage(metaData);

            BufferedImage image = new BufferedImage(500, 300,
                    BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = m_graphicsEnvironment.createGraphics(image);

            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            g2d.setColor(new Color(234, 234, 234));
            g2d.fillRect(0, 0, 500, 64);

            Font font20B = new Font("HelveticaNeue", Font.BOLD, 20);
            Font font12 = new Font("HelveticaNeue", Font.PLAIN, 12);
            g2d.setFont(font20B);

            if (profileImage != null) {
                g2d.drawImage(profileImage, 10, 10, 44, 44, null);
            }

            g2d.setColor(TWITCH_PURPLE);
            g2d.drawString(metaData.displayName, 64, 25);
            FontMetrics fm = g2d.getFontMetrics();
            int leftMostText = fm.stringWidth(metaData.displayName) + 64;

            if (metaData.streaming) {
                g2d.setFont(font12);
                fm = g2d.getFontMetrics();

                g2d.setColor(Color.BLACK);
                g2d.drawString("Playing", 64, 39);
                g2d.setColor(TWITCH_PURPLE);
                int leftPlaying = fm.stringWidth("Playing ") + 64;

                String gameName = gameData == null ? "Unknown" : gameData.gameName;
                g2d.drawString(gameName, leftPlaying, 39);

                int leftNowPlaying = fm.stringWidth(gameName) + leftPlaying;
                leftMostText = Math.max(leftMostText, leftNowPlaying);

                g2d.drawImage(m_twitchLive, 64, 45, 9, 9, null);
                g2d.setColor(TWITCH_RED);
                String viewers = NumberFormat.getNumberInstance(Locale.US).format(metaData.viewers);
                g2d.drawString(viewers, 78, 54);
                int leftViewers = fm.stringWidth(viewers) + 78;

                g2d.drawImage(m_twitchEye, leftViewers + 5, 45, 11, 9,
                        null);
                g2d.setColor(TWITCH_GREY);
                String views = NumberFormat.getNumberInstance(Locale.US).format(metaData.views);
                g2d.drawString(views, leftViewers + 20, 54);
                int leftViews = fm.stringWidth(views) + leftViewers + 20;
                leftMostText = Math.max(leftMostText, leftViews);
            } else {
                g2d.setFont(font20B);
                g2d.setColor(Color.BLACK);
                g2d.drawString("Offline", 64, 54);
                int leftOffline = fm.stringWidth("Offline") + 64;
                leftMostText = Math.max(leftMostText, leftOffline);
            }

            if (gameData != null && gameData.image != null) {
                g2d.drawImage(gameData.image, 10 + leftMostText, 0, 46, 64, null);
                leftMostText += 56;
            } else {
                g2d.setColor(TWITCH_PURPLE);
                g2d.fillRect(10 + leftMostText, 0, 64, 64);
                g2d.drawImage(m_twitchGlitch, leftMostText + 17, 7, 50, 50, null);
                leftMostText += 74;
            }

            g2d.dispose();

            BufferedImage result = image.getSubimage(0, 0, leftMostText, 64);

            try {
                ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
                ImageIO.write(result, "png", byteOS);
                byte[] bytes = byteOS.toByteArray();
                ByteArrayInputStream byteIS = new ByteArrayInputStream(bytes);

                Response response = newFixedLengthResponse(Response.Status.OK,
                        "image/png", byteIS, bytes.length);
                response.addHeader("Cache-Control",
                        "no-cache, no-store, must-revalidate");
                response.addHeader("Pragma", "no-cache");
                response.addHeader("Expires", "0");
                return response;
            } catch (Exception e) {
                e.printStackTrace();
                return status("not ok: couldn't write image");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return status("not ok: exception");
        }
    }

    private static JSONObject getJSONObject(JSONObject parent, String key) {
        try {
            return parent.getJSONObject(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONArray getJsonArray(JSONObject parent, String key) {
        try {
            return parent.getJSONArray(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject getJSONObject(JSONArray parent, int index) {
        try {
            return parent.getJSONObject(index);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getString(JSONObject parent, String key) {
        try {
            return parent.getString(key);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getInt(JSONObject parent, String key) {
        try {
            return parent.getInt(key);
        } catch (Exception e) {
            return null;
        }
    }

    private TwitchMetaData getMetaData(String userName) {
        JSONObject json = getJson(String.format(TWITCH_STREAM, userName));
        if (json == null) {
            return null;
        }

        JSONArray streams = getJsonArray(json, "data");
        JSONObject stream = null;
        JSONObject user = null;

        if (streams != null && streams.length() != 0) {
            stream = getJSONObject(streams, 0);
        }

        json = getJson(String.format(TWITCH_USER, userName));
        if (json != null) {
            JSONArray users = getJsonArray(json, "data");
            if (users != null && users.length() != 0) {
                user = getJSONObject(users, 0);
            }
        }

        if (stream == null && user == null) {
            return null;
        }

        TwitchMetaData result = new TwitchMetaData();
        if (stream != null) {
            result.streaming = true;
            result.gameId = getString(stream, "game_id");
            Integer viewers = getInt(stream, "viewer_count");
            result.viewers = viewers == null ? 0 : viewers;
        }
        if (user != null) {
            result.displayName = getString(user, "display_name");
            result.profileImageUrl = getString(user, "profile_image_url");
            Integer views = getInt(user, "view_count");
            result.views = views == null ? 0 : views;
        }

        return result;
    }

    private JSONObject getJson(String urlString) {
        String json = readJson(urlString);
        return json == null ? null : new JSONObject(json);
    }

    private String readJson(String urlString) {
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty(CLIENT_ID_HEADER, m_twitchClientId);
            reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
            StringBuilder buffer = new StringBuilder();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }

            return buffer.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (reader != null) {
                closeQuietly(reader);
            }
        }
    }

    private TwitchGameData getGameImage(String gameId) {
        if (m_gameImageCache.containsKey(gameId)) {
            // TODO: Fix this
//            return m_gameImageCache.get(gameName);
        }

        if (gameId == null) {
            System.out.println("Game name is null.");
            return null;
        }

        JSONObject json = getJson(String.format(TWITCH_GAME, gameId));
        if (json == null) {
            System.out.println("Game response is null");
            return null;
        }

        JSONArray gameList = getJsonArray(json, "data");
        if (gameList == null) {
            System.out.println("Game list is null.");
            return null;
        }

        JSONObject game = getJSONObject(gameList, 0);
        if (game == null) {
            System.out.println("Game is null.");
            return null;
        }

        String gameName = getString(game, "name");
        String boxArtUrl = getString(game, "box_art_url");
        if (boxArtUrl == null) {
            System.out.println("Template is null.");
            return null;
        }
        boxArtUrl = boxArtUrl.replace("{width}", "68");
        boxArtUrl = boxArtUrl.replace("{height}", "95");

        Image image = readImage(boxArtUrl);
        if (image != null) {
//            m_gameImageCache.put(gameName, image);
        } else {
            System.out.println("Couldn't read game image.");
        }

        TwitchGameData result = new TwitchGameData();
        result.gameName = gameName;
        result.image = image;
        return result;
    }

    private Image getProfileImage(TwitchMetaData metaData) {
        if (m_profileImageCache.containsKey(metaData.displayName)) {
            // TODO: Fix this
//            return m_profileImageCache.get(metaData.displayName);
        }

        if (metaData.profileImageUrl == null) {
            System.out.println("Profile image url is null.");
            return null;
        }

        Image image = readImage(metaData.profileImageUrl);
        if (image != null) {
            m_profileImageCache.put(metaData.displayName, image);
        } else {
            System.out.println("Couldn't read profile image.");
        }
        return image;
    }

    private static Image readImage(String urlString) {
        try {
            URL url = new URL(urlString);
            return ImageIO.read(url);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Response status(String message) {
        return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, message);
    }

    private static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (Exception e) {
            // Do nothing.
        }
    }
}
