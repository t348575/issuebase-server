import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
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
import java.util.Date;

@WebServlet("/token")
public class Token extends HttpServlet  {
    private Connection conn;
    private Gson gson = new Gson();
    private Algorithm algo;
    private Jedis redis;
    private JWTVerifier verifier;

    public void init() {
        try {
            this.algo = Algorithm.RSA256(Pem.readPublicKey(new File(String.valueOf(Paths.get(System.getenv("jwtPublicKey"))))), Pem.readPrivateKey(new File(String.valueOf(Paths.get(System.getenv("jwtPrivateKey"))))));
            this.verifier = JWT.require(this.algo).withIssuer("issuebase").build();

            this.redis = new Jedis(System.getenv("redisHost"), Integer.parseInt(System.getenv("redisPort")));
            this.redis.auth(System.getenv("redisPassword"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String code = req.getParameter("code");

        String fullCode = this.redis.get(code.substring(0, 16));

        try {
            DecodedJWT jwt = verifier.verify(fullCode);
            String userId = jwt.getClaim("id").asString();

            Tokens tok = new Tokens();
            tok.generateTokens(userId, this.redis, this.algo);

            JsonWriter.writeJson(res, this.gson.toJson(tok), 200);
        }
        catch(JWTVerificationException e) {
            JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("invalid_request", "invalid or malformed code")), 400);
        }
    }
}

class Tokens {
    String id_token;
    String access_token;
    String refresh_token;

    public void generateTokens(String username, Jedis redis, Algorithm algo) {
        Date expiry = new Date();
        expiry.setTime(expiry.getTime() + 900000);
        Date idExpiry = new Date();
        idExpiry.setTime(idExpiry.getTime() + 86400000);

        String idToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(idExpiry).withClaim("id", username).sign(algo);
        String accessToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(expiry).withClaim("id", username).sign(algo);
        String refreshToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(expiry).withClaim("id", username).sign(algo);

        redis.setex(idToken.substring(0, 16), 86400, idToken);
        redis.setex(accessToken.substring(0, 16), 900, accessToken);
        redis.setex(refreshToken.substring(0, 16), 900, refreshToken);

        this.id_token = idToken;
        this.access_token = accessToken;
        this.refresh_token = refreshToken;
    }
}