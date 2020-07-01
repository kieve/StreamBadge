package ca.kieve.streambadge;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

@Controller
public class MainController {
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

    private static final String OAUTH =
            "https://id.twitch.tv/oauth2/token"
                    + "?client_id=%s"
                    + "&client_secret=%s"
                    + "&grant_type=client_credentials";

    private static final String TWITCH_STREAM =
            "https://api.twitch.tv/helix/streams?user_login=%s";
    private static final String TWITCH_USER =
            "https://api.twitch.tv/helix/users?login=%s";
    private static final String TWITCH_GAME =
            "https://api.twitch.tv/helix/games?id=%s";

    private static final Color  TWITCH_PURPLE = new Color(100, 65, 164);
    private static final Color  TWITCH_RED = new Color(207, 54, 54);
    private static final Color  TWITCH_GREY = new Color(137, 131, 149);

    private static String m_authToken;
    private static long m_authExpiresAt = 0;

    private static String getAuthToken() {
        long now = System.currentTimeMillis();
        if (m_authToken == null || m_authExpiresAt - now <= 10_000) {
            refreshAuthToken();
        }
        return m_authToken;
    }

    private static synchronized void refreshAuthToken() {
        // Repeat the check, in case it was updated concurrently
        if (m_authToken != null && m_authExpiresAt - System.currentTimeMillis() > 10_000) {
            // Was updated
            return;
        }

        String json = null;
        try {
            Config config = StreamBadge.getConfig();
            URL url = new URL(String.format(OAUTH, config.getTwitchClientId(),
                    config.getTwitchClientSecret()));

            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestMethod("POST");
            json = readJson(httpsURLConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (json == null) {
            System.out.println("Auth Token is null...");
            return;
        }

        JSONObject jsonObject = new JSONObject(json);
        m_authToken = jsonObject.getString("access_token");
        m_authExpiresAt = System.currentTimeMillis() + jsonObject.getInt("expires_in") * 1000;
    }

    private ResponseEntity<?> error(String errorMessage) {
        byte[] body = errorMessage.getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(body);
        return ResponseEntity.badRequest()
                .contentLength(body.length)
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }

    @GetMapping({"/"})
    public ResponseEntity<?> index(@RequestParam String user) throws IOException {
        if (StringUtils.isEmpty(user)) {
            return error("not ok: no user specified");
        }

        TwitchMetaData metaData = getMetaData(user);
        if (metaData == null) {
            return error("not ok: can't find twitch user");
        }

        TwitchGameData gameData = getGameImage(metaData.gameId);
        Image profileImage = getProfileImage(metaData);

        BufferedImage image = new BufferedImage(500, 300,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2d = StreamBadge.getGraphicsEnvironment().createGraphics(image);

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

            g2d.drawImage(StreamBadge.getTwitchLive(), 64, 45, 9, 9, null);
            g2d.setColor(TWITCH_RED);
            String viewers = NumberFormat.getNumberInstance(Locale.US).format(metaData.viewers);
            g2d.drawString(viewers, 78, 54);
            int leftViewers = fm.stringWidth(viewers) + 78;

            g2d.drawImage(StreamBadge.getTwitchEye(), leftViewers + 5, 45, 11, 9,
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
            g2d.drawImage(StreamBadge.getTwitchGlitch(), leftMostText + 17, 7, 50, 50, null);
            leftMostText += 74;
        }

        g2d.dispose();

        BufferedImage result = image.getSubimage(0, 0, leftMostText, 64);

        ByteArrayOutputStream byteOS = new ByteArrayOutputStream();
        ImageIO.write(result, "png", byteOS);
        byte[] bytes = byteOS.toByteArray();

        Resource resource = new ByteArrayResource(bytes);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);

        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.IMAGE_PNG)
                .headers(headers)
                .body(resource);
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
        try {
            URL url = new URL(urlString);

            Config config = StreamBadge.getConfig();
            HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
            httpsURLConnection.setRequestProperty("Client-ID", config.getTwitchClientId());
            httpsURLConnection.setRequestProperty("Authorization", "Bearer " + getAuthToken());

            return readJson(httpsURLConnection);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String readJson(HttpsURLConnection httpsURLConnection) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
            StringBuilder buffer = new StringBuilder();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }

            return buffer.toString();
        } finally {
            if (reader != null) {
                closeQuietly(reader);
            }
        }
    }

    private TwitchGameData getGameImage(String gameId) {
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
        if (image == null) {
            System.out.println("Couldn't read game image.");
        }

        TwitchGameData result = new TwitchGameData();
        result.gameName = gameName;
        result.image = image;
        return result;
    }

    private Image getProfileImage(TwitchMetaData metaData) {

        if (metaData.profileImageUrl == null) {
            System.out.println("Profile image url is null.");
            return null;
        }

        Image image = readImage(metaData.profileImageUrl);
        if (image == null) {
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

    private static void closeQuietly(Closeable c) {
        try {
            c.close();
        } catch (Exception e) {
            // Do nothing.
        }
    }
}
