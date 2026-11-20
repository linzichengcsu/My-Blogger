package csulzc.My_Personal_Blogger.api.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountDTO {

    /** 未读数量 */
    private long unread;

    /** 总数 */
    private long total;
}
