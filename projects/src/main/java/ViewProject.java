import com.google.gson.Gson;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
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
        //String username=req.getParameter("username");
        try {

            PreparedStatement statement = this.conn.prepareStatement("SELECT * from project");
            //statement.setString(1, username);
            ResultSet rs = statement.executeQuery();
            String op = "";
            while (rs.next()){
                op = op + rs.getString("project_name")+" \n ";
                System.out.println(rs.getString("project_name"));
            }
            System.out.println(op);
            res.setContentType("text/html");
            PrintWriter out = res.getWriter();
            out.println("<HTML>\n" +
                    "<HEAD><TITLE>Hello</TITLE></HEAD>\n" +
                    "<BODY BGCOLOR=\"#FDF5E6\">\n" +
                    "<H1>"+op+"</H1>\n" +
                    "</BODY></HTML>");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

    }
    }




