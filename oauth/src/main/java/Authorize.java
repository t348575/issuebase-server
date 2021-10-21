import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import redis.clients.jedis.Jedis;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.sql.*;

@WebServlet("/authorize")
public class Authorize extends HttpServlet {
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
        String redirectUri = req.getParameter("redirect_uri");
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        Argon2 argon2 = Argon2Factory.create();
        try {
            PreparedStatement statement = this.conn.prepareStatement("SELECT username, password FROM users WHERE username=? OR email=? OR PHONE=?");
            statement.setString(1, username);
            statement.setString(2, username);
            statement.setString(3, username);

            ResultSet rs = statement.executeQuery();

            if (!rs.next()) {
                JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "The user does not exist!")), 403);
                return;
            }

            if (!argon2.verify(rs.getString("password"), password)) {
                JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "Incorrect password!")), 403);
                return;
            }

            Date expiry = new Date();
            expiry.setTime(expiry.getTime() + 60000);
            String codeToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(expiry).withClaim("id", username).sign(this.algo);

            this.redis.setex(codeToken.substring(0, 16), 70, codeToken);

            if (redirectUri.contains("&")) {
                res.sendRedirect(redirectUri + "&code=" + codeToken);
                return;
            }

            res.sendRedirect(redirectUri + "?code=" + codeToken);
        } catch (SQLException e) {
            JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("server_error", "An unknown database error occurred!")), 500);
        }
    }
}
