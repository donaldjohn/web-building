package com.github.emailtohl.building.config;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import javax.inject.Inject;
import javax.inject.Named;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.sql.DataSource;

import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.validator.HibernateValidator;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.util.ErrorHandler;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
/**
 * spring容器的配置类，它依赖数据源配置类和安全配置类
 * @author HeLei
 */
@Configuration
// 启动Aspect动态代理
@EnableAspectJAutoProxy
// 启动时间计划任务，spring在扫描类时，发现有@Scheduled注解的方法，即可定时执行该方法
@EnableScheduling
// 扫描包下的注解，将Bean纳入spring容器管理
@ComponentScan(basePackages = "com.github.emailtohl.building", excludeFilters = @ComponentScan.Filter({
		Controller.class, Configuration.class }))
// 代理功能时，如事务，安全等，proxyTargetClass = false 表示使用Java的动态代理
@EnableAsync(mode = AdviceMode.PROXY, proxyTargetClass = false, order = Ordered.HIGHEST_PRECEDENCE)
@Import({ DataSourceConfiguration.class, JPAConfiguration.class, SecurityConfiguration.class })
public class RootContextConfiguration
		implements AsyncConfigurer, SchedulingConfigurer, TransactionManagementConfigurer {
	public static final String PROFILE_DEVELPMENT = "develpment";
	public static final String PROFILE_QA = "qa";
	public static final String PROFILE_PRODUCTION = "production";
	
	private static final Logger log = LogManager.getLogger();
	private static final Logger schedulingLogger = LogManager.getLogger(log.getName() + ".[scheduling]");

	@Inject
	Environment env;
	
	@Inject
	@Named("dataSource")
	DataSource dataSource;
	
	@Inject
	@Named("jpaTransactionManager")
	PlatformTransactionManager jpaTransactionManager;

	@Bean
	public JdbcTemplate jdbcTemplate() {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public DataSourceTransactionManager transactionManagerForTest() {
		return new DataSourceTransactionManager(dataSource);
	}
	
	/**
	 * Spring总是使用ID为annotationDrivenTransactionManager的事务管理器，若没特别指定Bean名的话，最好实现接口
	 * TransactionManagementConfigurer，如此在有多个事务管理器的情况下指定默认事务管理器。
	 * 注意：如果没有实现接口TransactionManagementConfigurer，可在注解 @Transactional的value指定，否则会抛出异常。
	 */
	@Override
	public PlatformTransactionManager annotationDrivenTransactionManager() {
		return this.jpaTransactionManager;
	}
	
	/**
	 * 配置定时任务，此时还只是spring容器中管理的Bean，可在实现SchedulingConfigurer接口中的
	 * void configureTasks(ScheduledTaskRegistrar registrar)
	 * 方法中，使用本Bean
	 */
	@Bean
	public ThreadPoolTaskScheduler taskScheduler() {
		log.info("Setting up thread pool task scheduler with 20 threads.");
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(20);
		scheduler.setThreadNamePrefix("task-");
		scheduler.setAwaitTerminationSeconds(60);
		scheduler.setWaitForTasksToCompleteOnShutdown(true);
		scheduler.setErrorHandler(new ErrorHandler() {
			@Override
			public void handleError(Throwable t) {
				log.error("Unknown error occurred while executing task.", t);
			}
		});
		scheduler.setRejectedExecutionHandler(new RejectedExecutionHandler() {
			@Override
			public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
				schedulingLogger.error("Execution of task {} was rejected for unknown reasons.", r);
			}
		});
		return scheduler;
	}
	
	/**
	 * 应用程序中通常使用的是JDK提供的线程，如：
	 * ExecutorService = Executors.newCachedThreadPool();
	 * 不过这会启动额外的资源，为了让整个应用程序启动的线程在可控范围内，可以统一使用这一个执行器
	 */
	@Override
	public Executor getAsyncExecutor() {
		Executor executor = this.taskScheduler();
		log.info("Configuring asynchronous method executor {}.", executor);
		return executor;
	}

	/**
	 * 配置异步执行器的异常处理
	 */
	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new AsyncUncaughtExceptionHandler() {
			@Override
			public void handleUncaughtException(Throwable ex, Method method, Object... params) {
				schedulingLogger.error("调用异步任务出错了, message : " + method, ex);
			}};
	}

	/**
	 * SchedulingConfigurer接口需要实现的配置
	 */
	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		TaskScheduler scheduler = this.taskScheduler();
		log.info("Configuring scheduled method executor {}.", scheduler);
		registrar.setTaskScheduler(scheduler);
	}
	
	/**
	 * 对于Bean Validation，spring主要处理两种类型的beans：
	 * 1. 一种是普通存放数据的对象，如POJOs、JavaBeans-like，如实体（entities）和表单（form）
	 * 这主要校验他们的属性是否合法
	 * 
	 * 2. 另一种是被spring管理的bean，如被注解上@Controllers、@Services的
	 * 这主要校验传入他们的参数，以及返回的结果是否合法
	 * 
	 * 不论哪种，都需要校验器来执行，要获取校验器，就需要先创建Bean验证器工厂，并把该工厂注入到spring容器中。
	 * LocalValidatorFactoryBean产生的校验器可同时支持javax.validation.Validator和org.springframework.validation.Validator两个接口
	 * 而后者是前者的门面，它提供统一的报错机制，并且后者用于Spring MVC的验证中
	 * @return
	 */
	@Bean
	public LocalValidatorFactoryBean localValidatorFactoryBean() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		/*
		 * LocalValidatorFactoryBean会自动在classpath下搜索Bean Validation的实现
		 * 但若在JAVA EE容器里面有多个提供者就不可预测，故还是手动设置提供类
		 */
		validator.setProviderClass(HibernateValidator.class);
		return validator;
	}
	
	/**
	 * MethodValidationPostProcessor支持方法参数和返回值的验证，也就是前面说的第二种校验
	 * 
	 *	@Service
	 *	@Validated
	 *	public class SomeService {
	 *	    public void someMethod(@Valid SomeForm someForm) {
	 *	    	...
	 *	    }
	 *	}
	 * 
	 * MethodValidationPostProcessor会寻找标注了@org.springframework.validation.annotation.Validated
	 * 和@javax.validation.executable.ValidateOnExecution的类，并为其创建代理
	 */
	@Bean
	public MethodValidationPostProcessor methodValidationPostProcessor() {
		MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
		processor.setValidator(this.localValidatorFactoryBean());
		return processor;
	}
	
	@Bean
	public Gson gson() {
		return new Gson();
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
	
	@Bean
	public CloseableHttpClient acceptsUntrustedCertsHttpClient()
			throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, IOException {
		HttpClientBuilder builder = HttpClientBuilder.create();

		String proxyHost = env.getProperty("proxyHost");
		String proxyPort = env.getProperty("proxyPort");
		if (proxyHost != null && proxyHost.length() > 0 && proxyPort != null && proxyPort.length() > 0) {
			builder.setProxy(new HttpHost(proxyHost, Integer.valueOf(proxyPort)));
		}

		KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
		// setup a Trust Strategy that allows all certificates.
		SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(trustStore, new TrustStrategy() {
			public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
				log.warn("以下请求被信任且接受： \n" + "X509Certificate : " + Arrays.deepToString(arg0) + "  " + arg1);
				return true;
			}
		}).build();
		builder.setSSLContext(sslContext);
		// don't check Hostnames, either.
		// -- use SSLConnectionSocketFactory.getDefaultHostnameVerifier(), if
		// you don't want to weaken
		HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
		// here's the special part:
		// -- need to create an SSL Socket Factory, to use our weakened "trust
		// strategy";
		// -- and create a Registry, to register it.
		SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
				.register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslSocketFactory)
				.build();
		// now, we create connection-manager using our Registry.
		// -- allows multi-threaded use
		PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		connMgr.setMaxTotal(200);
		connMgr.setDefaultMaxPerRoute(100);
		builder.setConnectionManager(connMgr);
		// finally, build the HttpClient;
		// -- done!
		CloseableHttpClient client = builder.build();
		return client;
	}

	/**
	 * RestTemplate默认使用的StringHttpMessageConverter采用ISO-8859-1编码，所以对中文支持不友好，这里需要替换为UTF-8
	 * 注意： List<HttpMessageConverter<?>>中的各个元素顺序不能变，例如StringHttpMessageConverter在第二个位置，只能原地替换
	 */
	private void reInitMessageConverter(RestTemplate restTemplate) {
		List<HttpMessageConverter<?>> converterList = restTemplate.getMessageConverters();
		HttpMessageConverter<?> converterTarget = null;
		for (HttpMessageConverter<?> item : converterList) {
			if (item.getClass() == StringHttpMessageConverter.class) {
				converterTarget = item;
				break;
			}
		}
		if (converterTarget != null) {
			Collections.replaceAll(converterList, converterTarget, new StringHttpMessageConverter(StandardCharsets.UTF_8));
		}
	}

	@Bean
	public RestTemplate acceptsUntrustedCertsRestTemplate()
			throws KeyManagementException, KeyStoreException, NoSuchAlgorithmException, IOException {
		CloseableHttpClient httpClient = acceptsUntrustedCertsHttpClient();
		HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(
				httpClient);
		RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory);
		reInitMessageConverter(restTemplate);
		return restTemplate;
	}
	
	@Bean
	public JavaMailSender mailSender() {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost(env.getProperty("mailserver.host"));
		mailSender.setPort(Integer.valueOf(env.getProperty("mailserver.port")));
		mailSender.setUsername(env.getProperty("mailserver.username"));
		mailSender.setPassword(env.getProperty("mailserver.password"));
		return mailSender;
	}
}
