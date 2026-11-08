package csulzc.My_Personal_Blogger.api.dto.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 时间工具类：数据库以 {@link Instant}（UTC 瞬时）作为时间的唯一真相源，
 * 所有"当前时间获取 / 时区边界计算"等时间处理逻辑集中于此，
 * 不进入任何 Service 层代码，保证口径统一、便于维护。
 *
 * <p>序列化层面由 Jackson 默认输出带 {@code Z} 的 ISO-8601 字符串，
 * 前端按浏览器本地时区解析展示，从而彻底消除时区歧义。</p>
 */
public final class DateTimeUtils {

    /** 系统业务时区：统计"今日 / 本月"等自然日口径时使用 */
    public static final ZoneId SYSTEM_ZONE = ZoneId.of("Asia/Shanghai");

    private DateTimeUtils() {
    }

    /**
     * 当前 UTC 瞬时（时间唯一真相源）。
     * 取代散落各处的 {@code Instant.now()}，统一时间获取入口。
     */
    public static Instant now() {
        return Instant.now();
    }

    /** 系统时区（{@link #SYSTEM_ZONE}）下今天零点的瞬时 */
    public static Instant startOfToday() {
        return startOfToday(SYSTEM_ZONE);
    }

    /** 指定时区下今天零点的瞬时 */
    public static Instant startOfToday(ZoneId zone) {
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    /** 系统时区（{@link #SYSTEM_ZONE}）下本月一号零点的瞬时 */
    public static Instant startOfMonth() {
        return startOfMonth(SYSTEM_ZONE);
    }

    /** 指定时区下本月一号零点的瞬时 */
    public static Instant startOfMonth(ZoneId zone) {
        return LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
    }
}
