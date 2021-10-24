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

@WebServlet("/autoLogin")
public class AutoLogin extends HttpServlet {
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
        String method = req.getParameter("method");

        String username = Tokens.userFromToken(req.getParameter("id_token"), this.redis);
        switch (method) {
            case "google": {
                try {
                    String token = req.getParameter("access_token");
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

                    JsonObject googleResBody = this.gson.fromJson(response.body(), JsonObject.class);
                    googleResBody.addProperty("access_token", token);

                    PreparedStatement statement = this.conn.prepareStatement("UPDATE users SET social=? WHERE email=?");
                    statement.setString(1, this.gson.toJson(googleResBody));
                    statement.setString(2, username);

                    statement.executeUpdate();

                    Tokens dataRes = new Tokens();
                    dataRes.generateTokens(username, this.redis, this.algo);

                    JsonWriter.writeJson(res, this.gson.toJson(dataRes), 200);
                } catch (InterruptedException | SQLException e) {
                    JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                    return;
                }
                break;
            }
            case "github": {
                try {
                    String token = req.getParameter("access_token");
                    var userClient = HttpClient.newHttpClient();
                    var userRequest = HttpRequest.newBuilder(URI.create("https://api.github.com/user"))
                            .header("accept", "application/json")
                            .header("Authorization", "token " + token)
                            .build();
                    var userResponse = userClient.send(userRequest, HttpResponse.BodyHandlers.ofString());

                    if (userResponse.statusCode() > 204) {
                        JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                        return;
                    }

                    PreparedStatement statement = this.conn.prepareStatement("SELECT social, username FROM users WHERE email=?");
                    statement.setString(1, username);

                    ResultSet rs = statement.executeQuery();
                    rs.next();
                    JsonObject finalRes = this.gson.fromJson(rs.getString("social"), JsonObject.class);
                    finalRes.addProperty("github_access_token", finalRes.getAsJsonPrimitive("access_token").getAsString());

                    Tokens dataRes = new Tokens();
                    dataRes.generateTokens(username, this.redis, this.algo);

                    finalRes.addProperty("username", rs.getString("username"));
                    finalRes.addProperty("id_token", dataRes.id_token);
                    finalRes.addProperty("access_token", dataRes.access_token);
                    finalRes.addProperty("refresh_token", dataRes.refresh_token);

                    JsonWriter.writeJson(res, this.gson.toJson(finalRes), 200);
                } catch (InterruptedException | SQLException e) {
                    JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "OAuth social credentials do not work")), 403);
                    return;
                }
                break;
            }
        }
    }
}
