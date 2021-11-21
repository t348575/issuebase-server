import com.google.gson.Gson;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;

@WebServlet("/create")
public class CreateIssue extends HttpServlet {
    private Connection conn;
    private final Gson gson = new Gson();

    public void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            this.conn = DriverManager.getConnection(System.getenv("dbUrl"), System.getenv("dbUser"), System.getenv("dbPassword"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        InputStream body = req.getInputStream();
        CreateIssueFields fields = this.gson.fromJson(new String(body.readAllBytes(), StandardCharsets.UTF_8), CreateIssueFields.class);
        try {
            if (!this.usersExist(fields.users)) {
                JsonWriter.writeJson(res, this.gson.toJson(new JsonError("invalid_users", "some of the provided users do not exist!")), 400);
                return;
            }

            var issueNoStmt = this.conn.prepareStatement("SELECT COUNT(issue_no) + 1 as cnt FROM issues WHERE project_id=?");
            issueNoStmt.setLong(1, fields.project_id);
            ResultSet rs = issueNoStmt.executeQuery();
            if (!rs.next()) {
                JsonWriter.writeJson(res, this.gson.toJson(new JsonError("unknown_error", "an unknown error occurred")), 500);
                return;
            }

            int issueNo = rs.getInt("cnt");

            var statement = this.conn.prepareStatement("INSERT INTO issues(project_id, deadline, issue_no, status," +
                    "created_at, updated_at, priority, heading, body) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, fields.project_id);
            statement.setTimestamp(2, Timestamp.from(Instant.ofEpochSecond(fields.deadline)));
            statement.setInt(3, issueNo);
            statement.setInt(4, 1);
            statement.setTimestamp(5, Timestamp.from(Instant.now()));
            statement.setTimestamp(6, Timestamp.from(Instant.now()));
            statement.setInt(7, fields.priority);
            statement.setString(8, fields.heading);
            statement.setString(9, fields.body);

            statement.executeUpdate();

            rs = statement.getGeneratedKeys();
            if (!rs.next()) {
                JsonWriter.writeJson(res, this.gson.toJson(new JsonError("unknown_error")), 500);
                return;
            }

            long newId = rs.getLong(1);
            var issueUsers = this.conn.prepareStatement("INSERT INTO issue_users VALUES (?, ?)");
            for (String temp: fields.users) {
                issueUsers.setLong(1, newId);
                issueUsers.setString(2, temp);
                issueUsers.addBatch();
            }
            issueUsers.executeBatch();

            var tags = this.conn.prepareStatement("INSERT IGNORE INTO tags VALUES (?)");
            for (String temp: fields.tags) {
                tags.setString(1, temp);
                tags.addBatch();
            }
            tags.executeBatch();

            var issueTags = this.conn.prepareStatement("INSERT INTO issue_tags VALUES (?, ?)");
            for (String temp: fields.tags) {
                issueTags.setLong(1, newId);
                issueTags.setString(2, temp);
                issueTags.addBatch();
            }
            issueTags.executeBatch();

            JsonWriter.writeJson(res, this.gson.toJson(new JsonError("none")), 200);
        } catch (SQLException e) {
            JsonWriter.writeJson(res, this.gson.toJson(e), 400);
        }
    }

    private boolean usersExist(String[] users) {
        try {
            String query = "SELECT COUNT(name) as cnt FROM users WHERE username IN (";
            StringBuilder temp = new StringBuilder();

            temp.append(",?".repeat(users.length));

            temp = new StringBuilder(temp.toString().replaceFirst(",", ""));
            temp.append(")");
            query = query + temp;

            PreparedStatement statement = conn.prepareStatement(query);

            for (var i = 1; i <= users.length; i++){
                statement.setString(i, users[i - 1]);
            }

            var rs = statement.executeQuery();

            if (!rs.next()) {
                return false;
            }

            return rs.getInt("cnt") == users.length;
        } catch (SQLException e) {
            return false;
        }
    }
}

class CreateIssueFields {
    long project_id, deadline;
    String[] users, tags;
    int priority;
    String heading, body;
}

class JsonError {
    String error, error_description;
    public JsonError(String error, String error_description) {
        this.error = error;
        this.error_description = error_description;
    }
    public JsonError(String error) {
        this.error = error;
    }
}