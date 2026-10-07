-- ============================================================
-- SYNAPSE SOCIAL — MESSAGE LIFECYCLE ENHANCEMENTS MIGRATION
-- ============================================================

-- Ensure columns exist on public.messages
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS content_state TEXT DEFAULT 'active';
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS failure_reason TEXT;
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

-- Backfill sent_at from created_at if null
UPDATE public.messages SET sent_at = created_at WHERE sent_at IS NULL;

-- Backfill content_state from is_deleted/is_edited flags
UPDATE public.messages SET content_state = 'deleted' WHERE is_deleted = true AND content_state != 'deleted';
UPDATE public.messages SET content_state = 'edited' WHERE is_edited = true AND is_deleted = false AND content_state = 'active';

-- Function: mark_messages_as_read
CREATE OR REPLACE FUNCTION public.mark_messages_as_read(p_chat_id UUID, p_user_id TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
BEGIN
    -- Update last_read_at in chat_participants
    UPDATE public.chat_participants
    SET last_read_at = v_now
    WHERE chat_id = p_chat_id AND user_id = p_user_id;

    -- Mark unread messages sent by other participants as read
    UPDATE public.messages
    SET read_at = v_now,
        delivered_at = COALESCE(delivered_at, v_now),
        delivery_status = 'read',
        read_by = CASE
            WHEN read_by IS NULL THEN jsonb_build_array(p_user_id)
            WHEN NOT (read_by @> jsonb_build_array(p_user_id)) THEN read_by || jsonb_build_array(p_user_id)
            ELSE read_by
        END,
        updated_at = v_now
    WHERE chat_id = p_chat_id
      AND sender_id != p_user_id
      AND (delivery_status IS NULL OR delivery_status != 'read');
END;
$$;

-- Function: mark_messages_as_delivered
CREATE OR REPLACE FUNCTION public.mark_messages_as_delivered(p_chat_id UUID, p_user_id TEXT)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_now TIMESTAMPTZ := NOW();
BEGIN
    UPDATE public.messages
    SET delivered_at = COALESCE(delivered_at, v_now),
        delivery_status = 'delivered',
        updated_at = v_now
    WHERE chat_id = p_chat_id
      AND sender_id != p_user_id
      AND (delivery_status IS NULL OR delivery_status IN ('sending', 'sent'));
END;
$$;

GRANT EXECUTE ON FUNCTION public.mark_messages_as_read(UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.mark_messages_as_delivered(UUID, TEXT) TO authenticated;
