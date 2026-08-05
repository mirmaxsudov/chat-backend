package uz.mirmaxsudov.chatclonebackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.mirmaxsudov.chatclonebackend.common.util.APIUtil;
import uz.mirmaxsudov.chatclonebackend.service.base.user.UserService;

@RestController
@RequiredArgsConstructor
@RequestMapping(APIUtil.API_BASE_URL + "users")
public class UserController {
    private final UserService userService;
}