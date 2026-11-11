package com.cie.competition.module.user.service;

import com.cie.competition.module.user.dto.LoginDTO;
import com.cie.competition.module.user.vo.LoginVO;
import com.cie.competition.module.user.vo.UserInfoVO;

public interface AuthService {

    LoginVO login(LoginDTO dto);

    UserInfoVO getCurrentUser();
}
