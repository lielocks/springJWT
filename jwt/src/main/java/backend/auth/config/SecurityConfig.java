package backend.auth.config;

import backend.auth.jwt.filter.CustomLogoutFilter;
import backend.auth.jwt.filter.JwtFilter;
import backend.auth.jwt.filter.LoginFilter;
import backend.auth.jwt.repository.RefreshRepository;
import backend.auth.jwt.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Collections;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtUtil jwtUtil;
    private final RefreshRepository refreshRepository;

    public SecurityConfig(AuthenticationConfiguration authenticationConfiguration, JwtUtil jwtUtil, RefreshRepository refreshRepository) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.jwtUtil = jwtUtil;
        this.refreshRepository = refreshRepository;
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors((cors) -> cors.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration configuration = new CorsConfiguration();

                        //허용할 front단 서버
                        configuration.setAllowedOrigins(Collections.singletonList("http://localhost:3000"));
                        configuration.setAllowedMethods(Collections.singletonList("*"));
                        configuration.setAllowCredentials(true);
                        configuration.setAllowedHeaders(Collections.singletonList("*"));
                        configuration.setMaxAge(3600L);
                        //jwt token 을 Authorization header 를 담아서 주기 때문에 Authorization 허용해줘야 함
                        configuration.setExposedHeaders(Collections.singletonList("Authorization"));

                        return configuration;
                    }
                }));

        //csrf disable -> 세션 방식에서는 세션이 고정되기 때문에 csrf 공격을 필수적으로 방어
        //jwt 는 session 을 stateless 방식으로 관리하기 때문에 csrf 공격 방어하지 않아도 됨
        http
                .csrf((auth) -> auth.disable());

        //jwt 방식으로 로그인을 진행할 것이기 때문에 Form 로그인 방식과 http basic 인증 방식을 disable 해줌
        //formLogin 방식을 disable 해줬기 때문에 기본적으로 활성화되어 있는 UsernamePasswordAuthenticationFilter 가 동작이 안됨
        //따라서 custom 한 UsernamePasswordAuthentication Filter 작성해주어야 한다
        http
                .formLogin((auth) -> auth.disable());
        http
                .httpBasic((auth) -> auth.disable());

        //경로별 인가 작업
        //admin 경로는 ADMIN Role 을 가진 사용자만 접근 가능하게
        //그 외의 anyRequest 에 대해서는 로그인한 사용자만 접근 가능하게 authenticated
        http
                .authorizeHttpRequests((auth) -> auth
                        .requestMatchers("/login", "/", "/join").permitAll()
                        .requestMatchers("/admin").hasRole("ADMIN")
                        .requestMatchers("/reissue").permitAll()
                        .anyRequest().authenticated());

        http
                .addFilterBefore(new JwtFilter(jwtUtil), LoginFilter.class);

        //UsernamePasswordAuthenticationFilter 를 대체해서 등록해야 해서 그 자리에 딱 등록해줘야 하기때문에 FilterAt 을 사용
        http
                .addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtil, refreshRepository), UsernamePasswordAuthenticationFilter.class);

        http
                .addFilterBefore(new CustomLogoutFilter(jwtUtil, refreshRepository), LogoutFilter.class);

        //**중요**
        //jwt 에서는 session 을 항상 stateless 하게 관리해야 함
        http
                .sessionManagement((session) -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
