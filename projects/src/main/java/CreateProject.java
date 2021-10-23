import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
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

    public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
       
        String project_name="blem";//req.getParameter("project_name");
        Integer project_id=2;//req.getParameter("project_id");
        Date date = new Date();
        try {

            PreparedStatement statement = this.conn.prepareStatement("INSERT into project values(?,?,?)");
            statement.setInt(1, project_id);
            statement.setString(2, project_name);
            statement.setTimestamp(3, new Timestamp(date.getTime()));


            int rs = statement.executeUpdate();
            System.out.println(rs);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
