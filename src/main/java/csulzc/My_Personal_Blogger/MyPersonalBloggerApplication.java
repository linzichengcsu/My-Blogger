package csulzc.My_Personal_Blogger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 排除 UserDetailsServiceAutoConfiguration：应用为无状态 JWT 认证，
// 不需要 Spring Boot 默认生成的内存用户与随机密码
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@RestController
public class MyPersonalBloggerApplication {

	public static void main(String[] args)
	{
		SpringApplication.run(MyPersonalBloggerApplication.class, args);
	}
	@GetMapping("/")
	public String hello()
	{
		return "Hello, Spring Boot!";
	}
}
