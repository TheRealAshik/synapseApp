-- Migration: notification_navigation_targets_and_exclude_dm_in_app
-- Date: 2026-10-05

-- 1. Exclude direct-message notifications from in-app notifications feed
CREATE OR REPLACE FUNCTION private.handle_message_notification()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    -- DM notifications are explicitly excluded from in-app notifications feed.
    -- Chat push delivery pathways remain active separately.
    RETURN NEW;
END;
$$;

-- 2. Update notification target resolution helper / trigger handlers
-- Ensure post, comment, reply, reaction, follow, and mention notifications carry structured targets

CREATE OR REPLACE FUNCTION private.build_notification_data(
    p_target_type TEXT,
    p_post_id TEXT DEFAULT NULL,
    p_comment_id TEXT DEFAULT NULL,
    p_chat_id TEXT DEFAULT NULL,
    p_follower_id TEXT DEFAULT NULL,
    p_target_id TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_data JSONB := '{}'::jsonb;
BEGIN
    IF p_target_type IS NOT NULL THEN
        v_data := v_data || jsonb_build_object('targetType', p_target_type);
    END IF;
    IF p_post_id IS NOT NULL THEN
        v_data := v_data || jsonb_build_object('postId', p_post_id);
    END IF;
    IF p_comment_id IS NOT NULL THEN
        v_data := v_data || jsonb_build_object('commentId', p_comment_id);
    END IF;
    IF p_chat_id IS NOT NULL THEN
        v_data := v_data || jsonb_build_object('chat_id', p_chat_id);
    END IF;
    IF p_follower_id IS NOT NULL THEN
        v_data := v_data || jsonb_build_object('followerId', p_follower_id);
    END IF;
    IF p_target_id IS NOT NULL THEN
        v_data := v_data || jsonb_build_object('target_id', p_target_id);
    END IF;
    RETURN v_data;
END;
$$;
