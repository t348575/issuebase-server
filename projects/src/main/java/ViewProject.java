import com.google.gson.Gson;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;

@WebServlet("/viewProject")
public class ViewProject extends HttpServlet {
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
        String project_name=req.getParameter("project_name");
        try {

            PreparedStatement statement = this.conn.prepareStatement("SELECT * from PROJECT where project_name = ?");
            statement.setString(1, project_name);
            ResultSet rs = statement.executeQuery();
            while (rs.next()){
                System.out.println(rs);
            }
            //Avail av = new Avail();
            //av.available = rs.next();
            //JsonWriter.writeJson(res, this.gson.toJson(av), 200);
        } catch (SQLException e) {
            //JsonWriter.writeJson(res, this.gson.toJson(new OAuthError("server_error", "An unknown database error occurred!")), 500);
        }
    }
}


