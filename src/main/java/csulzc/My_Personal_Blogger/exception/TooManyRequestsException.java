package csulzc.My_Personal_Blogger.exception;

/**
 * 触发访问频率限制时抛出，由 GlobalExceptionHandler 映射为 429。
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
