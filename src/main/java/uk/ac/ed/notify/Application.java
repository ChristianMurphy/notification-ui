package uk.ac.ed.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.security.oauth2.sso.EnableOAuth2Sso;
import org.springframework.cloud.security.oauth2.sso.OAuth2SsoConfigurerAdapter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by rgood on 18/09/2015.
 */
@SpringBootApplication
@EnableZuulProxy
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Component
    @EnableOAuth2Sso
    public static class LoginConfigurer extends OAuth2SsoConfigurerAdapter {

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http.logout()
                    .and().httpBasic().disable()
                    .antMatcher("/**").authorizeRequests()
                    .antMatchers("/office365NewEmailCallback/**","/office365NewSubscriptionCallback/**","/office365RenewSubscriptionCallback/**",    "/notification/**","/user","/favicon.ico","/js/**","/css/**","/webjars/**","/**/*.html").permitAll()
                    .anyRequest().authenticated().and().csrf().disable();
                   /* .csrfTokenRepository(csrfTokenRepository()).and()
                    .addFilterAfter(csrfHeaderFilter(), CsrfFilter.class);*/
        }


        private Filter csrfHeaderFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                                                HttpServletResponse response, FilterChain filterChain)
                        throws ServletException, IOException {
                    CsrfToken csrf = (CsrfToken) request
                            .getAttribute(CsrfToken.class.getName());
                    if (csrf != null) {
                        Cookie cookie = new Cookie("XSRF-TOKEN",
                                csrf.getToken());
                        cookie.setPath("/");
                        response.addCookie(cookie);
                    }
                    filterChain.doFilter(request, response);
                }
            };
        }

        private CsrfTokenRepository csrfTokenRepository() {
            HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
            repository.setHeaderName("X-XSRF-TOKEN");
            return repository;
        }
    }

}
