import com.google.gson.Gson;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

@WebServlet("/filter")
public class GetIssues extends HttpServlet {

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

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        var project_id = Long.parseLong(req.getParameter("project_id"));
        var page = Integer.parseInt(req.getParameter("page"));
        var numPerPage = Integer.parseInt(req.getParameter("page_size"));
        var tags = req.getParameter("tags").split(",");
        var deadline = Long.parseLong(req.getParameter("deadline"));
        var priority = Integer.parseInt(req.getParameter("priority"));
        var status = Integer.parseInt(req.getParameter("status"));
        var sortBy = req.getParameter("sort");
        var dir = req.getParameter("sort_by");
        String query = "SELECT c.*, GROUP_CONCAT(username) as users FROM (SELECT id, project_id, deadline, issue_no, status, created_at, updated_at, priority, heading, GROUP_CONCAT(b.tag) AS tags FROM issues a RIGHT JOIN issue_tags b ON a.id = b.issue_id ";

        tags = Arrays.stream(tags).filter(value -> value != null && value.length() > 0).toArray(String[]::new);

        query += " GROUP BY id HAVING project_id = ? AND ";

        if (tags.length > 0) {
            StringBuilder temp = new StringBuilder();

            temp.append("tags LIKE ? AND ".repeat(tags.length));

            temp = new StringBuilder(temp.toString().replaceFirst(",", ""));
            query = query + temp;
        }

        if (priority != -1) {
            query += "priority = ? AND ";
        }

        if (status != -1) {
            query += "status = ? AND ";
        }

        if (deadline != -1) {
            query += "deadline = ? AND ";
        }

        query = query.substring(0, query.length() - 4);

        query += "ORDER BY " + getColumn(sortBy) + " " + getDir(dir);
        var countQuery = query + ") ";

        query += " LIMIT ? OFFSET ?) ";

        var finalStr = "c left join issue_users d on c.id = d.issue_id group by id";
        query += finalStr;
        countQuery += finalStr;

        try {
            var statement = this.conn.prepareStatement(query);
            var countStatement = this.conn.prepareStatement("SELECT COUNT(issue_no) as cnt FROM (" + countQuery + ") e");
            int i = 1, j = 0;

            statement.setLong(i, project_id);
            countStatement.setLong(i, project_id);

            if (tags.length > 0) {
                for (i = 2;j < tags.length; i++, j++) {
                    statement.setString(i, "%" + tags[j] + "%");
                    countStatement.setString(i, "%" + tags[j] + "%");
                }
                i--;
            }

            if (priority != -1) {
                statement.setInt(++i, priority);
                countStatement.setInt(i, priority);
            }

            if (status != -1) {
                statement.setInt(++i, status);
                countStatement.setInt(i, status);
            }

            if (deadline != -1) {
                statement.setTimestamp(++i, Timestamp.from(Instant.ofEpochSecond(deadline)));
                countStatement.setTimestamp(i, Timestamp.from(Instant.ofEpochSecond(deadline)));
            }

            statement.setInt(++i, numPerPage);
            statement.setInt(++i, numPerPage * page);

            var countRs = countStatement.executeQuery();
            var rs = statement.executeQuery();
            if (!countRs.next()) {
                JsonWriter.writeJson(res, this.gson.toJson(new IssueQuery(0, new Issue[]{})), 200);
                return;
            }

            int total = countRs.getInt("cnt");
            var issues = new Issue[total];

            i = 0;
            while (rs.next()) {
                var issue = new Issue();
                issue.setId(rs.getLong("id"));
                issue.setProject_id(rs.getLong("project_id"));
                issue.setDeadline(rs.getTimestamp("deadline").toInstant().getEpochSecond());
                issue.setIssue_no(rs.getInt("issue_no"));
                issue.setStatus(rs.getInt("status"));
                issue.setCreated_at(rs.getTimestamp("created_at").toInstant().getEpochSecond());
                issue.setUpdated_at(rs.getTimestamp("updated_at").toInstant().getEpochSecond());
                issue.setPriority(rs.getInt("priority"));
                issue.setHeading(rs.getString("heading"));
                issue.setTags(rs.getString("tags").split(","));
                issue.setUsers(rs.getString("users").split(","));
                issues[i++] = issue;
            }

            JsonWriter.writeJson(res, this.gson.toJson(new IssueQuery(total, issues)), 200);
        } catch (SQLException e) {
            JsonWriter.writeJson(res, this.gson.toJson(e), 400);
        }
    }

    private String getColumn(String sortBy) {
        if (sortBy == null) {
            return "updated_at";
        }

        String[] columns = new String[]{"project_id", "deadline", "issue_no", "created_at", "updated_at", "heading"};
        for (String temp: columns) {
            if (temp.equals(sortBy)) {
                return sortBy;
            }
        }
        return "updated_at";
    }

    private String getDir(String dir) {
        if (dir == null) {
            return "ASC";
        }


        if (dir.toLowerCase().equals("asc")) {
            return "ASC";
        }
        return "DESC";
    }
}

class IssueQuery {
    public int total;
    public Issue[] issues;
    public IssueQuery(int total, Issue[] issues) {
        this.total = total;
        this.issues = issues;
    }
}

class Issue {
    public long id, project_id, deadline, created_at, updated_at;
    public int issue_no, status, priority;
    public String heading;
    public String[] tags, users;

    public void setId(long id) {
        this.id = id;
    }

    public void setProject_id(long project_id) {
        this.project_id = project_id;
    }

    public void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public void setIssue_no(int issue_no) {
        this.issue_no = issue_no;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setCreated_at(long created_at) {
        this.created_at = created_at;
    }

    public void setUpdated_at(long updated_at) {
        this.updated_at = updated_at;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public void setUsers(String[] users) {
        this.users = users;
    }
}