package com.laker.admin.module.system.dto.profile;

import com.laker.admin.module.system.dto.OnlineSessionView;
import com.laker.admin.module.system.dto.auth.AuthUserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileSecurityOverview {
    private AuthUserProfile user;
    private List<OnlineSessionView> activeSessions;
    private List<ProfileLoginHistoryView> loginHistory;
}
