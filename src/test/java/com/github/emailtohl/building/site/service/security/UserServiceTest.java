package com.github.emailtohl.building.site.service.security;

import static com.github.emailtohl.building.site.entities.Authority.*;
import static org.junit.Assert.fail;

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
import com.github.emailtohl.building.site.service.UserService;
/**
 * 测试spring security的配置
 * @author Helei
 */
public class UserServiceTest {
	UserService userService;
	AuthenticationManager authenticationManager;
	
	
	@SuppressWarnings("resource")
	@Before
	public void setUp() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SecurityTestConfig.class);
		userService = context.getBean(UserService.class);
		authenticationManager = context.getBean(AuthenticationManager.class);
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
	
	@Test
	public void testAddUser() {
		userService.addUser(new User());
	}

	@Test
	public void testEnableUser() {
		userService.enableUser(1000L);
	}

	@Test
	public void testDisableUser() {
		SecurityContextHolder.clearContext();
		try {
			userService.deleteUser(1000L);
		} catch (AuthenticationCredentialsNotFoundException e) {
			System.out.println("deleteUser 调用被拒绝，符合预期");
		}
		setBar();
		try {
			userService.deleteUser(1000L);
		} catch (AccessDeniedException e) {
			System.out.println("deleteUser 调用被拒绝，符合预期");
		}
		setEmailtohl();
		userService.deleteUser(1000L);
	}

//	@Test
	public void testGrantedAuthority() {
		SecurityContextHolder.clearContext();
		try {
			userService.grantedAuthority(1000L, new HashSet<Authority>(Arrays.asList(ADMIN, MANAGER)));
		} catch (AuthenticationCredentialsNotFoundException e) {
			System.out.println("grantedAuthority 调用被拒绝，符合预期");
		}
		setEmailtohl();
		userService.grantedAuthority(1000L, new HashSet<Authority>(Arrays.asList(ADMIN, MANAGER)));
	}

//	@Test
	public void testMergeUser() {
		SecurityContextHolder.clearContext();
		fail("Not yet implemented");
	}
	
	@Test
	public void testChangePassword() {
		setEmailtohl();
		System.out.println(SecurityContextHolder.getContext().getAuthentication());
		userService.changePassword("emailtohl@163.com", "987654321");
		try {
			userService.changePassword("foo@test.com", "987654321");
		} catch (AccessDeniedException e) {
			System.out.println("changePassword 调用被拒绝，符合预期");
		}
	}

	@Test
	public void testDeleteUser() {
		SecurityContextHolder.clearContext();
		try {
			userService.deleteUser(1000L);
		} catch (AuthenticationCredentialsNotFoundException e) {
			System.out.println("deleteUser 调用被拒绝，符合预期");
		}
		setFoo();
		try {
			userService.deleteUser(1000L);
		} catch (AccessDeniedException e) {
			System.out.println("deleteUser 调用被拒绝，符合预期");
		}
		setEmailtohl();
		userService.deleteUser(1000L);
	}

	@Test
	public void testGetUser() {
		SecurityContextHolder.clearContext();
		try {
			userService.getUser(1000L);
		} catch (AuthenticationCredentialsNotFoundException e) {
			System.out.println("getUser 调用被拒绝，符合预期");
		}
		setBar();
		try {
			userService.getUser(1000L);
		} catch (AccessDeniedException e) {
			System.out.println("getUser 调用被拒绝，符合预期");
		}
		userService.getUser(2000L);
		setFoo();
		userService.getUser(1000L);
		setEmailtohl();
		userService.getUser(1000L);
	}

	@Test
	public void testGetUserPager() {
		SecurityContextHolder.clearContext();
		try {
			userService.getUserPager(null, null);
		} catch (AuthenticationCredentialsNotFoundException e) {
			System.out.println("getUser 调用被拒绝，符合预期");
		}
		setBar();
		userService.getUserPager(null, null);
	}

	@Test
	public void testAuthenticate() {
		SecurityContextHolder.clearContext();
		userService.authenticate(null, null);
	}

}