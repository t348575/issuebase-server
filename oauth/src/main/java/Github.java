import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

@WebServlet("/github")
public class Github extends HttpServlet {
    private Connection conn;
    private Gson gson = new Gson();
    private Algorithm algo;
    private Jedis redis;
    private GithubClient client;

    public void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(System.getenv("dbUrl"), System.getenv("dbUser"), System.getenv("dbPassword"));

            this.algo = Algorithm.RSA256(Pem.readPublicKey(new File(String.valueOf(Paths.get(System.getenv("jwtPublicKey"))))), Pem.readPrivateKey(new File(String.valueOf(Paths.get(System.getenv("jwtPrivateKey"))))));

            this.redis = new Jedis(System.getenv("redisHost"), Integer.parseInt(System.getenv("redisPort")));
            this.redis.auth(System.getenv("redisPassword"));

            this.client = this.gson.fromJson(Files.readString(Paths.get(System.getenv("github"))), GithubClient.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String code = req.getParameter("code");
        String username = req.getParameter("username");
        String phone = req.getParameter("phone");

        try {
            String accessData;
            if (phone == null) {
                var client = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder(URI.create("https://github.com/login/oauth/access_token?code=" + code + "&client_id=" + this.client.client_id + "&client_secret=" + this.client.client_secret))
                        .header("accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build();

                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                accessData = response.body();

                if (response.statusCode() > 204) {
                    JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                    return;
                }
            }
            else {
                String token = this.redis.get(code);
                if (token == null || token.length() == 0) {
                    JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                    return;
                }

                accessData = "{\"access_token\": \"" + token + "\"}";
            }

            GithubAccessToken accessToken = this.gson.fromJson(accessData, GithubAccessToken.class);

            var userClient = HttpClient.newHttpClient();
            var userRequest = HttpRequest.newBuilder(URI.create("https://api.github.com/user"))
                    .header("accept", "application/json")
                    .header("Authorization", "token " + accessToken.access_token)
                    .build();

            var userResponse = userClient.send(userRequest, HttpResponse.BodyHandlers.ofString());

            if (userResponse.statusCode() > 204) {
                JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                return;
            }

            String userBody = userResponse.body();
            GithubData data = this.gson.fromJson(userBody, GithubData.class);
            JsonObject githubBody = this.gson.fromJson(userBody, JsonObject.class);
            githubBody.addProperty("access_token", accessToken.access_token);

            PreparedStatement statement = this.conn.prepareStatement("SELECT username FROM users WHERE email=?");
            statement.setString(1, data.email);

            ResultSet rs = statement.executeQuery();
            boolean state = rs.next();

            if (phone == null && !state) {
                this.redis.setex(code, 600, accessToken.access_token);
                JsonWriter.writeJson(res, this.gson.toJson(new GithubNewUser(true, code)), 200);
                return;
            }

            if (!state) {
                statement = this.conn.prepareStatement("INSERT INTO users VALUES(?, ?, ?, ?, ?, ?) ");
                statement.setString(1, username);
                statement.setString(2, data.email);
                statement.setString(3, phone);
                statement.setString(4, data.name);
                statement.setString(5, "");
                statement.setString(6, this.gson.toJson(githubBody));
            }
            else {
                statement = this.conn.prepareStatement("UPDATE users SET social=? WHERE email=?");
                statement.setString(1, this.gson.toJson(githubBody));
                statement.setString(2, data.email);
            }

            statement.executeUpdate();

            GithubTokens githubRes = new GithubTokens();
            githubRes.generateTokens(data.email, this.redis, this.algo);
            githubRes.imageUrl = data.avatar_url;
            githubRes.username = data.email;
            githubRes.github_access_token = accessToken.access_token;

            JsonWriter.writeJson(res, this.gson.toJson(githubRes), 200);
        } catch (InterruptedException | SQLException e) {
            JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("server_error", "An unknown database error occurred!")), 500);
        }
    }
}

class GithubAccessToken {
    String access_token, scope, token_type;
}

class GithubClient {
    String client_id, client_secret;
}

class GithubData {
    String email, name, avatar_url;
}

class GithubTokens extends NewUserTokens {
    String imageUrl, username, github_access_token;
}

class GithubNewUser extends NewUserRes {
    String code;
    GithubNewUser(boolean new_user, String code) {
        super(new_user);
        this.code = code;
    }
}