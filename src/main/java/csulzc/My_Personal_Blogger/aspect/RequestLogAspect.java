package csulzc.My_Personal_Blogger.aspect;

import tools.jackson.databind.ObjectMapper;
import csulzc.My_Personal_Blogger.api.dto.common.PageResponseDTO;
import csulzc.My_Personal_Blogger.api.response.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequestLogAspect {

    private static final int MAX_PARAM_LENGTH = 500;

    private static final Pattern SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(?i)(\"(?:password|oldPassword|newPassword|refreshToken|token|base64Data)\"\\s*:\\s*\")(.*?)(\")");

    private final ObjectMapper objectMapper;

    @Around("execution(public * csulzc.My_Personal_Blogger.controller..*.*(..))")
    public Object logControllerRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String method = "?";
        String uri = "?";

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            method = request.getMethod();
            uri = request.getRequestURI();
        }

        log.info("==> 请求开始: {} {}, 入参: {}", method, uri, buildParams(joinPoint));
        try {
            Object result = joinPoint.proceed();
            long costMs = System.currentTimeMillis() - startTime;
            log.info("<== 请求成功: {} {}, 耗时: {}ms, 响应: {}", method, uri, costMs, buildResponse(result));
            return result;
        } catch (Throwable e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.warn("==> 请求异常: {} {}, 耗时: {}ms, 异常: {}: {}（详细堆栈见全局异常处理器日志）",
                    method, uri, costMs, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    private String buildParams(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "无";
        }
        List<String> paramList = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                    || arg instanceof BindingResult) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                paramList.add(file.getOriginalFilename() + " (" + file.getSize() + " bytes)");
                continue;
            }
            if (arg instanceof Resource resource) {
                paramList.add("Resource{" + resource.getFilename() + "}");
                continue;
            }
            if (isSimpleType(arg)) {
                paramList.add(String.valueOf(arg));
                continue;
            }
            paramList.add(toJson(arg));
        }
        return paramList.isEmpty() ? "无" : String.join(", ", paramList);
    }

    private String buildResponse(Object result) {
        if (result instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            if (body instanceof Result<?> r) {
                return String.format("Result{code=%d, message=\"%s\", data=%s}",
                        r.getCode(), r.getMessage(), summarizeData(r.getData()));
            }
            return "ResponseEntity{status=" + responseEntity.getStatusCode().value()
                    + ", body=" + toJson(body) + "}";
        }
        return toJson(result);
    }

    private String summarizeData(Object data) {
        if (data == null) {
            return "null";
        }
        if (data instanceof PageResponseDTO<?> page) {
            return String.format("PageResponseDTO{page=%d, size=%d, totalElements=%d, contentSize=%d}",
                    page.getPage(), page.getSize(), page.getTotalElements(),
                    page.getContent() == null ? 0 : page.getContent().size());
        }
        if (data instanceof Collection<?> collection) {
            return data.getClass().getSimpleName() + "{size=" + collection.size() + "}";
        }
        if (data instanceof Map<?, ?> map) {
            return "Map{size=" + map.size() + "}";
        }
        return toJson(data);
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            json = SENSITIVE_FIELD_PATTERN.matcher(json).replaceAll("$1******$3");
            if (json.length() > MAX_PARAM_LENGTH) {
                json = json.substring(0, MAX_PARAM_LENGTH) + "...(已截断)";
            }
            return json;
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private boolean isSimpleType(Object obj) {
        return obj instanceof CharSequence || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || obj instanceof Enum<?>;
    }
}