package uz.mirmaxsudov.chatclonebackend.security.service;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import uz.mirmaxsudov.chatclonebackend.repository.user.UserRepository;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        return userRepository.findByPhoneNumberAndDeletedFalse(phoneNumber)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid phone number or password"));
    }
}
