package backend.auth.jwt.filter;

import backend.auth.jwt.dto.CustomUserDetails;
import backend.auth.jwt.util.JwtUtil;
import backend.user.entity.UserEntity;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

//session 은 stateless 상태로 관리되기 때문에 oncePerRequest 즉 해당 한번의 요청이 끝나면 소멸된다.
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //header 에서 access key 에 담긴 token 을 꺼낸다
        String accessToken = request.getHeader("access");

        //access Token 이 없다면 다음 필터로 넘긴다
        if (accessToken == null) {
            //해당 method 가 종료되기 전에 doFilter 를 호출해서 filterChain 에 엮인 해당 request 와 response 를
            //해당 filter 에서는 종료해주고 다음 filter 에 넘겨준다.
            filterChain.doFilter(request, response);

            //조건이 해당되면 method 종료 **필수**
            return;
        }

        //Token 만료 여부 확인, 만료시 다음 필터로 넘기지 않는다.
        try {
            jwtUtil.isExpired(accessToken);
        } catch (ExpiredJwtException e) {
            //Response Body
            PrintWriter writer = response.getWriter();
            writer.print("access Token Expired");

            //Response Status Code
            //여기서는 다음 필터로 넘겨주지 않고 바로 UNAUTHORIZED 를 내려준다
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        //token 이 access 인지 확인 (발급시 payload 에 명시)
        String category = jwtUtil.getCategory(accessToken);

        if (!category.equals("access")) {
            //Response Body
            PrintWriter writer = response.getWriter();
            writer.print("Invalid Access Token");

            //Response Status Code
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // token 검증 완료됨 이제 username, role 값을 획득
        String username = jwtUtil.getUsername(accessToken);
        String role = jwtUtil.getRole(accessToken);

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setRole(role);

        //UserDetails 에 userEntity 객체 정보 담기
        CustomUserDetails customUserDetails = new CustomUserDetails(user);
        
        //Spring Security 인증 Token 생성
        Authentication authToken = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());

        //Session 에 사용자 등록해주면 사용자 정보를 요청하는 경로의 요청을 정상적으로 진행 가능함
        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }
}
