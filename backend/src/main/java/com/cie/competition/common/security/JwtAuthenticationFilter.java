package com.cie.competition.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器，解析请求头中的 Token 并写入安全上下文与用户上下文。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final com.cie.competition.module.user.mapper.UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = jwtUtil.resolveToken(request);
        if (token != null && jwtUtil.validateToken(token)) {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            var user = userMapper.selectById(userId);
            if (user != null && Integer.valueOf(1).equals(user.getStatus())) {
                String username = user.getUsername();
                String role = user.getRole();

                LoginUser loginUser = new LoginUser(userId, username, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                UserContext.set(loginUser);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
