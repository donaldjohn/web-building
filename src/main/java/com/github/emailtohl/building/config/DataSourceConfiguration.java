package com.github.emailtohl.building.config;
import static com.github.emailtohl.building.config.RootContextConfiguration.PROFILE_DEVELPMENT;
import static com.github.emailtohl.building.config.RootContextConfiguration.PROFILE_PRODUCTION;
import static com.github.emailtohl.building.config.RootContextConfiguration.PROFILE_QA;

import java.io.File;

import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.lookup.JndiDataSourceLookup;
import org.springframework.util.StringUtils;
/**
 * 数据源配置
 * @author HeLei
 * @date 2017.02.04
 */
@Configuration
@PropertySource({ "classpath:database.properties", "classpath:config.properties" })
public class DataSourceConfiguration {
	private static final Logger logger = LogManager.getLogger();
	/**
	 * 将@PropertySource中引入的属性封装到Environment
	 */
	@Inject
	Environment env;

	/**
	 * 若要使用@Value直接将值注入Bean中，除了在@Value中使用SpEl表达式外，
	 * 还需要为spring配置PropertySourcesPlaceholderConfigurer对应xml中的配置是
	 * <context:property-placeholder />
	 */
	@Value("${jdbc.driverClassName}")
	String driverClassName;
	@Value("${jdbc.url}")
	String url;
	@Value("${jdbc.username}")
	String username;
	@Value("${jdbc.password}")
	String password;
	
	/**
	 * 静态配置方法，该方法将在最早执行，这样才能读取properties配置
	 * @return
	 */
	@Bean
	public static PropertySourcesPlaceholderConfigurer placeholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	/**
	 * 开发环境
	 * 没有连接池
	 * @return
	 */
	@Profile(PROFILE_DEVELPMENT)
	@Bean(name = "test_dataSource")
	public DataSource springTestDataSource() {
		logger.info(env.getProperty("jdbc.driverClassName"));
		logger.info(env.getProperty("jdbc.url"));
		logger.info(env.getProperty("jdbc.username"));
		logger.info(env.getProperty("jdbc.password"));
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(driverClassName);
		dataSource.setUrl(url);
		dataSource.setUsername(username);
		dataSource.setPassword(password);
		return dataSource;
	}
	
	/**
	 * 开发环境
	 * tomcat的连接池
	 * @return
	 */
	@Profile(PROFILE_DEVELPMENT)
	@Bean(name = "dataSource")
	public DataSource tomcatJdbc() {
		// 创建连接池属性对象
		PoolProperties poolProps = new PoolProperties();
		poolProps.setUrl(url);
		poolProps.setDriverClassName(driverClassName);
		poolProps.setUsername(username);
		poolProps.setPassword(password);
		// 创建连接池, 使用了 tomcat 提供的的实现，它实现了 javax.sql.DataSource 接口
		org.apache.tomcat.jdbc.pool.DataSource dataSource = new org.apache.tomcat.jdbc.pool.DataSource();
		// 为连接池设置属性
		dataSource.setPoolProperties(poolProps);
		return dataSource;
	}
	
	/**
	 * 生产或QA环境
	 * 获取容器提供的数据源
	 * @return
	 */
	@Profile({ PROFILE_PRODUCTION, PROFILE_QA })
	@Bean(name = "dataSource")
	public DataSource jndiDataSource() {
		JndiDataSourceLookup lookup = new JndiDataSourceLookup();
		return lookup.getDataSource("jdbc/building");
	}

	/**
	 * 项目目录
	 * 未在容器中，则返回项目的所在目录
	 * @return web-building\target\test-classes
	 */
	@Profile(PROFILE_DEVELPMENT)
	@Bean(name = "contextRoot")
	public File projectContextRoot() {
		File f = new File(getClass().getResource("/").getFile());
		logger.debug("测试环境中的上下文根目录是：{}", f.getAbsolutePath());
		return f;
	}
	
	/**
	 * 项目目录
	 * 在容器中可以返回容器的上下文根目录
	 * @param servletContext 被注入进来的容器上下文
	 * @return
	 */
	@Profile({ PROFILE_PRODUCTION, PROFILE_QA })
	@Bean(name = "contextRoot")
	public File webContextRoot(ServletContext servletContext) {
		File f = new File(servletContext.getRealPath(""));
		logger.debug("生产环境中的上下文根目录是：{}", f.getAbsolutePath());
		return f;
	}
	
	/**
	 * 项目中存放数据的目录，如果是开发环境，就存放在target目录下
	 * @return
	 */
	@Profile(PROFILE_DEVELPMENT)
	@Bean(name = "dataPath")
	public File projectDataPath() {
		// 这个projectContextRoot的路径是：web-building\target\test-classes
		File projectContextRoot = projectContextRoot();
		File dataPath = new File(projectContextRoot.getParentFile(), "web-building-data");
		if (!dataPath.exists())
			dataPath.mkdir();
		return dataPath;
	}
	
	/**
	 * 项目中存放数据的目录，如果是在生产环境下，如果配置了dataPath，则以配置为准，否则就存放在web部署的目录下
	 * @param servletContext 被注入进来的容器上下文
	 * @return
	 */
	@Profile({ PROFILE_PRODUCTION, PROFILE_QA })
	@Bean(name = "dataPath")
	public File webDataPath(ServletContext servletContext) {
		String dataPathStr = env.getProperty("dataPath");
		File dataPath;
		if (StringUtils.hasText(dataPathStr)) {
			dataPath = new File(dataPathStr);
		} else {
			File webContextRoot = webContextRoot(servletContext);
			dataPath = new File(webContextRoot.getParentFile(), "web-building-data");
			if (!dataPath.exists())
				dataPath.mkdir();
		}
		return dataPath;
	}
	
	/**
	 * web项目中可访问的资源目录
	 * @param servletContext 被注入进来的容器上下文
	 * @return
	 */
	@Profile({ PROFILE_PRODUCTION, PROFILE_QA })
	@Bean(name = "resourcePath")
	public File resourcePath(ServletContext servletContext) {
		File webDataPath = webDataPath(servletContext);
		File resourcePath = new File(webDataPath, "resource");
		if (!resourcePath.exists())
			resourcePath.mkdir();
		return resourcePath;
	}
}