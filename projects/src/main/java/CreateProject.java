import com.google.gson.Gson;
import java.io.*;
import org.json.JSONObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.Date;

@WebServlet("/createProject")
public class CreateProject extends HttpServlet {
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

    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            InputStream body = req.getInputStream();
            CreateProjectFields fields = this.gson.fromJson(new String(body.readAllBytes(), StandardCharsets.UTF_8), CreateProjectFields.class);
            PreparedStatement statement = this.conn.prepareStatement("INSERT INTO projects(name, created_at) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, fields.project_name);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            
            statement.executeUpdate();

            var rs = statement.getGeneratedKeys();
            if (!rs.next()) {
                JsonWriter.writeJson(res, this.gson.toJson(new JsonError("unknown_error")), 500);
                return;
            }

            long newId = rs.getLong(1);

            var projectUsers = this.conn.prepareStatement("INSERT INTO project_members(project_id, username, role) VALUES (?, ?, ?)");
            for (String temp: fields.users) {
                projectUsers.setLong(1, newId);
                projectUsers.setString(2, temp);
                projectUsers.setInt(3, 1);
                projectUsers.addBatch();
            }
            projectUsers.executeBatch();

            JsonWriter.writeJson(res, this.gson.toJson(new JsonError("none")), 200);
        } catch (SQLException e) {
            JsonWriter.writeJson(res, this.gson.toJson(e), 400);
        }
    }
}

class CreateProjectFields {
    String project_name;
    String[] users;
}
