package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

final class AssistantConversationStateKeys {
    private AssistantConversationStateKeys() {
    }

    static String stateKey(AssistantRequestContext context) {
        String userKey = "anonymous";
        if (context.user() != null) {
            if (context.user().userId() != null) {
                userKey = String.valueOf(context.user().userId());
            } else if (context.user().sessionId() != null) {
                userKey = "session:" + context.user().sessionId();
            }
        }
        return userKey + ":conversation:" + context.conversationId();
    }
}
