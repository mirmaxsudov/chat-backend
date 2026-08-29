package uz.mirmaxsudov.chatclonebackend.model.enums.auth;

public enum RoleName {
    USER,
    ADMIN;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}
