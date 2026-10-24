package csulzc.My_Personal_Blogger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开启定时任务（文章计数批量落库依赖 {@code @Scheduled}）。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
