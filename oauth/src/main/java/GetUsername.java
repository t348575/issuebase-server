import java.sql.Connection;
import java.sql.PreparedStatement;

public class GetUsername {
    public static String Get(Connection conn, String email) throws Exception {
        PreparedStatement statement = conn.prepareStatement("SELECT username FROM users WHERE email=?");
        statement.setString(1, email);
        var rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getString("username");
        }
        throw new Exception("could not find user");
    }
}
