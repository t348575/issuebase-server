import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Date;

@WebServlet("/google")
public class Google extends HttpServlet {
    private Connection conn;
    private Gson gson = new Gson();
    private Algorithm algo;
    private Jedis redis;

    public void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(System.getenv("dbUrl"), System.getenv("dbUser"), System.getenv("dbPassword"));

            this.algo = Algorithm.RSA256(Pem.readPublicKey(new File(String.valueOf(Paths.get(System.getenv("jwtPublicKey"))))), Pem.readPrivateKey(new File(String.valueOf(Paths.get(System.getenv("jwtPrivateKey"))))));

            this.redis = new Jedis(System.getenv("redisHost"), Integer.parseInt(System.getenv("redisPort")));
            this.redis.auth(System.getenv("redisPassword"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String email = req.getParameter("email");
        String name = req.getParameter("name");
        String username = req.getParameter("username");
        String phone = req.getParameter("phone");
        String token = req.getParameter("token");

        try {
            PreparedStatement statement = this.conn.prepareStatement("SELECT username FROM users WHERE email=?");
            statement.setString(1, email);

            ResultSet rs = statement.executeQuery();
            if (token == null && !rs.next()) {
                JsonWriter.writeJson(res, this.gson.toJson(new NewUserRes(true)), 200);
                return;
            }

            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder(URI.create("https://www.googleapis.com/oauth2/v3/userinfo"))
                    .header("accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() > 204) {
                JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                return;
            }
            String body = response.body();
            GoogleUserInfo info = this.gson.fromJson(body, GoogleUserInfo.class);

            statement = this.conn.prepareStatement("INSERT INTO users VALUES(?, ?, ?, ?, ?, ?) ");
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.setString(4, name);
            statement.setString(5, "");
            statement.setString(6, body);

            statement.executeUpdate();

            Tokens tok = new Tokens();
            tok.generateTokens(username, this.redis, this.algo);

            GoogleTokens googleRes = new GoogleTokens();
            googleRes.id_token = tok.id_token;
            googleRes.access_token = tok.access_token;
            googleRes.refresh_token = tok.refresh_token;

            JsonWriter.writeJson(res, this.gson.toJson(googleRes), 200);
        } catch (SQLException | InterruptedException e) {
            JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("server_error", "An unknown database error occurred!")), 500);
        }
    }
}

class NewUserRes {
    boolean new_user;

    NewUserRes(boolean new_user) {
        this.new_user = new_user;
    }
}

class GoogleUserInfo {
    String sub, name, given_name, family_name, picture, email, email_verified, locale;
}

class GoogleTokens {
    boolean new_user = false;
    String id_token;
    String access_token;
    String refresh_token;
}