import com.google.gson.Gson;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;

@WebServlet("/viewProjects")
public class ViewProjects extends HttpServlet {
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

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String username=req.getParameter("username");
        try {
            PreparedStatement statement = this.conn.prepareStatement("select name from projects inner join project_members on projects.project_id=project_members.project_id where username=?;");
            statement.setString(1, username);
            ResultSet rs = statement.executeQuery();
            String op = "";
            while (rs.next()){
                op = op + rs.getString("name")+" \n ";
            }
            JsonWriter.writeJson(res,op,200);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }
}




