package csulzc.My_Personal_Blogger;

/**
 * 压力参数统一入口。
 *
 * <p>所有性能/并发测试通过本类读取 JVM 系统属性 {@code -Dstress.*} 来调整压力规模，
 * 不传任何参数时回退到代码内的默认值——保证改造前后默认行为完全一致，零配置即可跑通。</p>
 *
 * <p>支持的属性（{@code scripts/concurrent-test/run.ps1} 会把命令行开关翻译成这些属性透传给 Maven）：</p>
 * <ul>
 *   <li>{@code stress.scale} — 全局倍率，默认 1.0；对所有"未被绝对值覆盖"的压力参数生效</li>
 *   <li>{@code stress.threads} — 全局并发线程数绝对值；设置后覆盖<b>所有</b>并发场景，且不再乘 scale</li>
 *   <li>{@code stress.threads.<场景名>} — 单场景线程数绝对值（优先级：全局 > 场景 > 默认×scale）</li>
 *   <li>{@code stress.ops.<key>} — 实体 mass-ops 循环次数绝对值</li>
 *   <li>{@code stress.batch.<key>} — 批量插入条数绝对值</li>
 * </ul>
 *
 * <p>设计原则：任何属性缺失或解析失败都静默回退默认值，绝不因参数问题让测试启动失败。</p>
 */
public final class StressParams {

    private StressParams() {
    }

    /** 全局倍率在类加载时读取一次（测试 JVM 启动后系统属性即固定） */
    private static final double SCALE = parseDouble(System.getProperty("stress.scale"), 1.0);

    public static double scale() {
        return SCALE;
    }

    /**
     * 并发线程数：优先级 stress.threads（全局）> stress.threads.{@code scenario}（场景）> 默认值×scale。
     * 绝对值覆盖（全局/场景）不再乘 scale，避免 -Threads 与 -Scale 叠加产生歧义。
     */
    public static int threads(String scenario, int defaultValue) {
        Integer global = parseInt(System.getProperty("stress.threads"));
        if (global != null) {
            return Math.max(1, global);
        }
        Integer specific = parseInt(System.getProperty("stress.threads." + scenario));
        if (specific != null) {
            return Math.max(1, specific);
        }
        return applyScale(defaultValue);
    }

    /** 通用整数压力值：stress.{@code key} 绝对值覆盖 > 默认值×scale */
    public static int intValue(String key, int defaultValue) {
        Integer specific = parseInt(System.getProperty("stress." + key));
        if (specific != null) {
            return Math.max(1, specific);
        }
        return applyScale(defaultValue);
    }

    /** 通用长整数压力值（如千万级循环次数）：stress.{@code key} 绝对值覆盖 > 默认值×scale */
    public static long longValue(String key, long defaultValue) {
        Long specific = parseLong(System.getProperty("stress." + key));
        if (specific != null) {
            return Math.max(1L, specific);
        }
        return applyScale(defaultValue);
    }

    private static int applyScale(int v) {
        long r = Math.round(v * SCALE);
        return (int) Math.max(1L, Math.min(r, Integer.MAX_VALUE));
    }

    private static long applyScale(long v) {
        return Math.max(1L, Math.round(v * SCALE));
    }

    private static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double parseDouble(String s, double def) {
        if (s == null) {
            return def;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
