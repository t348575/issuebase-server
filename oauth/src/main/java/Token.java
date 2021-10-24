import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        String fullCode = this.redis.get(code);

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
        // Tokens.removeWithPattern(username + ":*", redis);

        Date expiry = new Date();
        expiry.setTime(expiry.getTime() + 900000);
        Date idExpiry = new Date();
        idExpiry.setTime(idExpiry.getTime() + 86400000);

        String idToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(idExpiry).withClaim("id", username).sign(algo);
        String accessToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(expiry).withClaim("id", username).sign(algo);
        String refreshToken = JWT.create().withIssuer("issuebase").withIssuedAt(new Date()).withExpiresAt(expiry).withClaim("id", username).sign(algo);

        redis.setex(username + ":" + idToken, 86400, idToken);
        redis.setex(username + ":" + accessToken, 900, accessToken);
        redis.setex(username + ":" + refreshToken, 900, refreshToken);

        this.id_token = idToken;
        this.access_token = accessToken;
        this.refresh_token = refreshToken;
    }

    public static void removeWithPattern(String pattern, Jedis redis) {
        Set<String> matchingKeys = new HashSet<>();
        ScanParams params = new ScanParams();
        params.match(pattern);

        String nextCursor = "0";
        do {
            ScanResult<String> scanResult = redis.scan(nextCursor, params);
            List<String> keys = scanResult.getResult();
            nextCursor = scanResult.getCursor();

            matchingKeys.addAll(keys);

        } while(!nextCursor.equals("0"));

        if (matchingKeys.size() == 0) {
            return;
        }

        redis.del(matchingKeys.toArray(new String[matchingKeys.size()]));
    }

    public static String userFromToken(String token, Jedis redis) {
        Set<String> matchingKeys = new HashSet<>();
        ScanParams params = new ScanParams();
        params.match("*" + token);

        String nextCursor = "0";
        do {
            ScanResult<String> scanResult = redis.scan(nextCursor, params);
            List<String> keys = scanResult.getResult();
            nextCursor = scanResult.getCursor();

            matchingKeys.addAll(keys);

        } while(!nextCursor.equals("0"));

        String total = matchingKeys.toArray(new String[matchingKeys.size()])[0];
        return total.substring(0, total.indexOf(token) - 1);
    }
}

class NewUserRes {
    boolean new_user;

    NewUserRes(boolean new_user) {
        this.new_user = new_user;
    }
}

class NewUserTokens extends Tokens {
    boolean new_user = false;
}