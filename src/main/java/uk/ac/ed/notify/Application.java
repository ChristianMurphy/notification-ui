package uk.ac.ed.notify;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.cloud.security.oauth2.resource.EnableOAuth2Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;
import uk.ac.ed.notify.config.RemoteUserAuthenticationFilter;
import uk.ac.ed.notify.repository.UiUserRepository;
import uk.ac.ed.notify.service.UiUserDetailsService;

/**
 * Created by rgood on 18/09/2015.
 */
@SpringBootApplication
@EntityScan("uk.ac.ed.notify")
@ComponentScan({"uk.ac.ed.notify"})
@EnableOAuth2Resource
public class Application extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

@Configuration
@EnableWebSecurity
    protected static class LoginConfig extends WebSecurityConfigurerAdapter {

        @Autowired
        UiUserRepository uiUserRepository;

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.addFilterBefore(remoteUserAuthenticationFilter(), RequestHeaderAuthenticationFilter.class)
                    .authenticationProvider(preauthAuthProvider())
                    .csrf().disable()
                    .authorizeRequests().anyRequest().authenticated()
                    .antMatchers("/office365NewEmailCallback/**").permitAll()
                    .antMatchers("/scheduled-tasks").hasRole("SYSSUPPORT")
                    .antMatchers("/publishers", "/subscribers", "/topic-subscriptions/**").hasRole("SYSSUPPORT")
                    .antMatchers("/topic/**").hasRole("USRSUPPORT")
                    .antMatchers("/").hasRole("EMERGENCY");      
            
            
        }

        @Autowired
        public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
            auth.authenticationProvider(preauthAuthProvider());
        }

        @Bean
        public UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken> userDetailsServiceWrapper() {
            UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken> wrapper =
                    new UserDetailsByNameServiceWrapper<PreAuthenticatedAuthenticationToken>();
            wrapper.setUserDetailsService(new UiUserDetailsService(uiUserRepository));
            return wrapper;
        }

        @Bean
        public PreAuthenticatedAuthenticationProvider preauthAuthProvider() {
            PreAuthenticatedAuthenticationProvider preauthAuthProvider =
                    new PreAuthenticatedAuthenticationProvider();
            preauthAuthProvider.setPreAuthenticatedUserDetailsService(userDetailsServiceWrapper());
            return preauthAuthProvider;
        }

        @Bean
        public RemoteUserAuthenticationFilter remoteUserAuthenticationFilter() throws Exception {
            RemoteUserAuthenticationFilter filter = new RemoteUserAuthenticationFilter();
            filter.setAuthenticationManager(authenticationManager());
            return filter;
        }


        @Autowired
        private AuthenticationManager authenticationManager;

        @Override
        protected void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.parentAuthenticationManager(authenticationManager);
        }
    }


    
    @Value("${java.naming.ldap.derefAliases}")
    String derefAliases;
    @Bean
    @ConfigurationProperties(prefix="ldap.contextSource")
    public LdapContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        Map<String,Object> baseEnvironmentProperties = new HashMap<>();
        baseEnvironmentProperties.put("java.naming.ldap.derefAliases",derefAliases);
        contextSource.setBaseEnvironmentProperties(baseEnvironmentProperties);
        return contextSource;
    }

    @Bean
    public LdapTemplate ldapTemplate(ContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }    
}
