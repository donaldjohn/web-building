package com.github.emailtohl.building.site.service.security;
import static org.junit.Assert.*;
import static com.github.emailtohl.building.site.entities.Authority.*;
import java.util.Arrays;
import java.util.HashSet;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.github.emailtohl.building.site.entities.Authority;
import com.github.emailtohl.building.site.entities.User;
import com.github.emailtohl.building.site.service.AuthenticationService;
import com.github.emailtohl.building.site.service.UserService;

public class AuthenticationServiceTest {
	UserService userService;
	AuthenticationService authenticationService;
	AuthenticationManager authenticationManager;
	
	@SuppressWarnings("resource")
	@Before
	public void setUp() {
		// SecurityTestConfig 中的配置，没有扫描包下的Bean，而是直接使用注册在里面的Bean
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SecurityTestConfig.class);
		userService = context.getBean(UserService.class);
		authenticationService = context.getBean(AuthenticationService.class);
		authenticationManager = context.getBean(AuthenticationManager.class);
	}
	
	@Test
	public void testAuthenticate() {
		Authentication auth = authenticationService.authenticate("foo@test.com", "123456");
		assertTrue(auth.isAuthenticated());
	}
	
	@Test(expected = AuthenticationCredentialsNotFoundException.class)
	public void testGrantedAuthority1() {
		SecurityContextHolder.clearContext();
		authenticationService.grantedAuthority(1000L, new HashSet<Authority>(Arrays.asList(ADMIN, MANAGER)));
	}
	
	@Test(expected = AccessDeniedException.class)
	public void testGrantedAuthority2() {
		User u = new User();
		setBar();
		userService.mergeUser(1000L, u);
	}
	
	@Test
	public void testGrantedAuthority3() {
		User u = new User();
		setFoo();
		userService.mergeUser(1000L, u);
	}
	
	@Test
	public void testGrantedAuthority4() {
		User u = new User();
		setEmailtohl();
		userService.mergeUser(1000L, u);
	}
	
	private void setEmailtohl() {
		SecurityContextHolder.clearContext();
		String name = "emailtohl@163.com";
		String password = "123456";
		Authentication token = new UsernamePasswordAuthenticationToken(name, password);
		Authentication authentication = authenticationManager.authenticate(token);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
	
	private void setFoo() {
		SecurityContextHolder.clearContext();
		String name = "foo@test.com";
		String password = "123456";
		Authentication token = new UsernamePasswordAuthenticationToken(name, password);
		Authentication authentication = authenticationManager.authenticate(token);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
	private void setBar() {
		SecurityContextHolder.clearContext();
		String name = "bar@test.com";
		String password = "123456";
		Authentication token = new UsernamePasswordAuthenticationToken(name, password);
		Authentication authentication = authenticationManager.authenticate(token);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}