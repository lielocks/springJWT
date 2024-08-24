package backend.user.controller;

import backend.auth.jwt.model.RefreshEntity;
import backend.auth.jwt.repository.RefreshRepository;
import backend.auth.jwt.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;

@Controller
@RequiredArgsConstructor
@ResponseBody
public class ReissueController {

    private final JwtUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(HttpServletRequest request, HttpServletResponse response) {

        //날라온 request 에서 Refresh Token 을 가져와서 확인해본다
        String refresh = null;

        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("refresh")) {
                refresh = cookie.getValue();
            }
        }

        if (refresh == null) {
            //Response Status Code 내려주기
            return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
        }

        //Expired 만료시간 check
        try {
            jwtUtil.isExpired(refresh);
        } catch (ExpiredJwtException e) {
            //Response Status Code 내려주기
            return new ResponseEntity<>("refresh token null", HttpStatus.BAD_REQUEST);
        }

        //Token 이 Refresh Token 인지 확인
        String category = jwtUtil.getCategory(refresh);
        if (!category.equals("refresh")) {
            //Response Status Code 내려주기
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }

        //DB에 이미 저장되어 있는 Refresh Token 인지 확인
        Boolean isExist = refreshRepository.existsByRefresh(refresh);
        if (!isExist) {
            //Response Body 내려주기
            return new ResponseEntity<>("invalid refresh token", HttpStatus.BAD_REQUEST);
        }

        String username = jwtUtil.getUsername(refresh);
        String role = jwtUtil.getRole(refresh);

        //새로운 Jwt 생성한다 새로운 accessToken, refreshToken 발급 -> Refresh Rotate
        String newAccess = jwtUtil.createJwt("access", username, role, 600000L);
        String newRefresh = jwtUtil.createJwt("refresh", username, role, 86400000L);

        //Refresh Token 저장 DB에 기존의 Refresh Token 삭제하고 새로운 Refresh Token 저장해준다
        refreshRepository.deleteByRefresh(refresh);
        saveNewRefreshEntity(username, newRefresh, 86400000L);

        //Response Header 와 cookie 에 각각 넣어준다
        response.setHeader("access", newAccess);
        response.addCookie(createCookie("refresh", newRefresh));

        return new ResponseEntity<>(HttpStatus.OK);
    }

    private Cookie createCookie(String key, String value) {
        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(24*60*60);
        //cookie.setSecure(true); https 설정이 필요할때
        //cookie.setPath("/");
        cookie.setHttpOnly(true); //앞단 client 단에서 javascript 로 해당 cookie 에 접근하지 못하도록 필수적으로 막기

        return cookie;
    }

    private void saveNewRefreshEntity(String username, String refresh, Long expiredMs) {
        Date date = new Date(System.currentTimeMillis() + expiredMs);

        RefreshEntity refreshEntity = new RefreshEntity();
        refreshEntity.setUsername(username);
        refreshEntity.setRefresh(refresh);
        refreshEntity.setExpiration(date.toString());

        refreshRepository.save(refreshEntity);
    }
}
