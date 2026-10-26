ALTER TABLE board_post
    ADD INDEX idx_board_post_purpose_created_id
        (usage_purpose_id, created_at DESC, id DESC),
    ALGORITHM = INPLACE,
    LOCK = NONE;
