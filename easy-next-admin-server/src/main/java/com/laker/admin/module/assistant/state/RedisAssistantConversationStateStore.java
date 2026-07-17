package com.laker.admin.module.assistant.state;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.support.EasySecurityToken;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Redis 会话快照仓库。
 *
 * <p>每个会话只使用一个 Hash Key。Lua 在一次原子操作中校验旧版本、写入完整 JSON 快照并续期，
 * 不会出现 Pending Action 已更新而 Chat Memory 只写了一半的状态。</p>
 */
@Primary
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisAssistantConversationStateStore implements AssistantConversationStateStore {
    private static final Logger log = LoggerFactory.getLogger(RedisAssistantConversationStateStore.class);
    private static final Duration CONVERSATION_TTL = Duration.ofHours(2);
    private static final String KEY_PREFIX = "easy:assistant:conversation:";
    private static final String VERSION_FIELD = "version";
    private static final String PAYLOAD_FIELD = "payload";
    private static final String SAVE_SCRIPT = """
            local currentVersion = redis.call('HGET', KEYS[1], 'version')
            if not currentVersion then
                currentVersion = '0'
            end
            if currentVersion ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1], 'version', ARGV[2], 'payload', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """;

    private final RedisScript<Long> saveScript = new DefaultRedisScript<>(SAVE_SCRIPT, Long.class);
    private final StringRedisTemplate redisTemplate;
    private final EasyJsonCodec jsonCodec;

    public RedisAssistantConversationStateStore(StringRedisTemplate redisTemplate, EasyJsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public AssistantConversationState load(AssistantRequestContext context) {
        String key = key(context);
        List<Object> values = redisTemplate.opsForHash().multiGet(
                key, List.of(VERSION_FIELD, PAYLOAD_FIELD));
        String storedVersion = text(values, 0);
        String payload = text(values, 1);
        if (!StringUtils.hasText(storedVersion) && !StringUtils.hasText(payload)) {
            return AssistantConversationState.empty();
        }
        if (!StringUtils.hasText(storedVersion) || !StringUtils.hasText(payload)) {
            throw corruptedState(key, null);
        }
        try {
            long version = Long.parseLong(storedVersion);
            AssistantConversationState state = jsonCodec.fromJson(payload, AssistantConversationState.class);
            if (state.version() != version) {
                throw new IllegalStateException("hash version and payload version differ");
            }
            return state.activeAt(context.requestInstant());
        } catch (RuntimeException ex) {
            throw corruptedState(key, ex);
        }
    }

    @Override
    public void save(AssistantRequestContext context,
                     long expectedVersion,
                     AssistantConversationState newState) {
        AssistantConversationStateVersionGuard.validateNextVersion(expectedVersion, newState);
        Long saved = redisTemplate.execute(
                saveScript,
                List.of(key(context)),
                String.valueOf(expectedVersion),
                String.valueOf(newState.version()),
                jsonCodec.toJson(newState),
                String.valueOf(CONVERSATION_TTL.toMillis())
        );
        if (saved == null || saved != 1L) {
            throw AssistantConversationStateVersionGuard.concurrentStateChange();
        }
    }

    private static String text(List<Object> values, int index) {
        if (values == null || values.size() <= index || values.get(index) == null) {
            return "";
        }
        return String.valueOf(values.get(index));
    }

    private static BusinessException corruptedState(String key, RuntimeException cause) {
        String keyHash = EasySecurityToken.sha256(key).substring(0, 16);
        log.error("Assistant conversation state is corrupted, keyHash={}", keyHash, cause);
        return new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "会话状态读取失败，请稍后重试",
                cause
        );
    }

    private static String key(AssistantRequestContext context) {
        return KEY_PREFIX + AssistantConversationStateKeys.stateKey(context);
    }
}
