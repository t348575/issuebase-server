import com.google.gson.Gson;
import java.io.*;
import org.json.JSONObject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.Date;

@WebServlet("/createProject")
public class CreateProject extends HttpServlet {

    public static String inputStreamToString(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        StringWriter sw = new StringWriter();
        char c[] = new char[1024];
        while (true) {
            int n = br.read(c, 0, c.length);
            if (n < 0)
                break;
            sw.write(c, 0, n);
        }
        isr.close();
        return sw.toString();
    }

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
        String project_name=req.getParameter("project_name");
        String username = (String) req.getAttribute("username");
        String role=req.getParameter("role");
        Date date = new Date();
        try {
            var projNoStmt = this.conn.prepareStatement("SELECT COUNT(name) as cnt FROM projects");
            ResultSet rs = projNoStmt.executeQuery();
            System.out.println(rs);
            if (!rs.next()){
                System.out.println("here");
            }
            int projNo = rs.getInt("cnt") + 1;
            PreparedStatement statement = this.conn.prepareStatement("INSERT into projects values(?,?,?)");
            String body = inputStreamToString(req.getInputStream());
            JSONObject obj = new JSONObject(body);
            statement.setInt(1, projNo);
            statement.setString(2, obj.get("project_name").toString());
            statement.setTimestamp(3, new Timestamp(date.getTime()));
            statement.executeUpdate();
            PreparedStatement proj_mem_statement = this.conn.prepareStatement("INSERT into project_members values(?,?,?)");
            statement.setInt(1, projNo);
            statement.setString(2, username);
            statement.setString(3, role);
           res.setStatus(200);
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
}
