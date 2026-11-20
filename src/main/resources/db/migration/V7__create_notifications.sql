-- ============================================
-- V7: 站内通知表
-- ============================================

CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,

    receiver_id  BIGINT       NOT NULL,
    sender_id    BIGINT,                 -- 系统通知时为 NULL
    type         VARCHAR(32)  NOT NULL,  -- COMMENT / REPLY / LIKE_ARTICLE / LIKE_COMMENT / SYSTEM
    title        VARCHAR(255),
    content      VARCHAR(1000),
    article_id   BIGINT,
    comment_id   BIGINT,
    is_read      TINYINT(1)   NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    INDEX idx_notifications_receiver_read (receiver_id, is_read),
    INDEX idx_notifications_receiver_created (receiver_id, created_at),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_sender   FOREIGN KEY (sender_id)   REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_article  FOREIGN KEY (article_id)  REFERENCES articles (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_comment  FOREIGN KEY (comment_id)  REFERENCES comments (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci;
