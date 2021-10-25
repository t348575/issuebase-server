import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import redis.clients.jedis.Jedis;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.stream.Collectors;

@WebServlet("/createUser")
public class CreateUser extends HttpServlet {
    private Connection conn;
    private Gson gson = new Gson();

    public void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(System.getenv("dbUrl"), System.getenv("dbUser"), System.getenv("dbPassword"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        CreateUserReq reqData = this.gson.fromJson(req.getReader().lines().map(line -> line + "\n").collect(Collectors.joining()), CreateUserReq.class);

        Argon2 argon2 = Argon2Factory.create();
        String hash = argon2.hash(10, 65536, 1, reqData.password);
        try {
            var statement = this.conn.prepareStatement("INSERT INTO users(username, email, phone, name, password) VALUES(?, ?, ?, ?, ?)");
            statement.setString(1, reqData.username);
            statement.setString(2, reqData.email);
            statement.setString(3, reqData.phone);
            statement.setString(4, reqData.name);
            statement.setString(5, hash);

            statement.executeUpdate();

            res.setStatus(200);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("server_error", "Email already exists!")), 401);
                return;
            }
            JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("server_error", "An unknown database error occurred!")), 500);
        }
    }
}

class CreateUserReq {
    String email, password, username, phone, name;
}