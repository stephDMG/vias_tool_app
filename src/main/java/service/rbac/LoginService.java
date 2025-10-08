package service.rbac;

public class LoginService {
    public String getCurrentWindowsUsername() {
        return System.getProperty("user.name");
    }
}
