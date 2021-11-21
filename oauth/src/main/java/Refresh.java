import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;

@WebServlet("/refresh")
public class Refresh extends HttpServlet  {
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
        String idToken = req.getParameter("id_token");
        String accessToken = req.getParameter("access_token");
        String refreshToken = req.getParameter("refresh_token");

        if (Tokens.verify(refreshToken, this.redis, this.algo) && Tokens.verify(idToken, this.redis, this.algo)) {
            try {
                Tokens.getKeyWithToken(accessToken, this.redis);

                Tokens dataRes = new Tokens(GetUsername.Get(this.conn, Tokens.getEmail(idToken, this.algo)));
                dataRes.generateTokens(Tokens.userFromToken(idToken, this.redis), this.redis, this.algo);

                JsonWriter.writeJson(res, this.gson.toJson(dataRes), 200);
            }
            catch (Exception e) {
                JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "Invalid access or id token")), 400);
            }
        }
        else {
            JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("access_denied", "Invalid id or refresh token")), 400);
        }
    }
}
