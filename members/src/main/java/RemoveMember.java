import com.google.gson.Gson;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;

@WebServlet("/removeMember")
    public class RemoveMember extends HttpServlet {
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
        String curr_username = (String) req.getAttribute("username");
        try {
            PreparedStatement statement1= this.conn.prepareStatement("SELECT * FROM PROJECT_MEMBERS where username=? and role=0");
            statement1.setString(1, curr_username);
            var rs = statement1.executeQuery();
            if (rs.next()) {
                String remove_username = req.getParameter("remove_username");
                PreparedStatement statement = this.conn.prepareStatement("DELETE from project_members where username=?");
                statement.setString(1, remove_username);
                statement.executeUpdate();
                ResultSet rs1 = statement.executeQuery("SELECT * from project_members");
            }
            else{
                JsonWriter.writeJson(res,this.gson.toJson(new JsonError("Not administrator")),400);
            }
        }
        catch(SQLException e){
            JsonWriter.writeJson(res,this.gson.toJson(new JsonError("Not administrator")),400);
        }
    }
}

