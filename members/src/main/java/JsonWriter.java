import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class JsonWriter {
    public static void writeJson(HttpServletResponse res, String data, int status) throws IOException {
        res.setStatus(status);
        res.setCharacterEncoding("UTF-8");
        PrintWriter out = res.getWriter();
        res.setContentType("application/json");
        out.print(data);
        out.flush();
    }
}
