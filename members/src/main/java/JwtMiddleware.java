import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

@WebFilter("/*")
public class JwtMiddleware implements Filter {
    private Algorithm algo;
    private JWTVerifier verifier;
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        try {
            this.algo = Algorithm.RSA256(Pem.readPublicKey(new File(String.valueOf(Paths.get(System.getenv("jwtPublicKey"))))), Pem.readPrivateKey(new File(String.valueOf(Paths.get(System.getenv("jwtPrivateKey"))))));
            this.verifier = JWT.require(this.algo).withIssuer("issuebase").build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {
        try {
            var authHeader = ((HttpServletRequest) servletRequest).getHeader("Authorization");
            var tokenSplit = authHeader.split("Bearer ");

            if (tokenSplit.length != 2) {
                ((HttpServletResponse) servletResponse).sendError(401);
                return;
            }

            DecodedJWT jwt = verifier.verify(tokenSplit[1]);
            servletRequest.setAttribute("username", jwt.getClaim("username"));
        } catch (JWTVerificationException exception){
            ((HttpServletResponse) servletResponse).sendError(403);
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }
}