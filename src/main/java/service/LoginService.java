package service;

public class LoginService {
    public String getCurrentWindowsUsername() {
        return System.getProperty("user.name");
    }
}
