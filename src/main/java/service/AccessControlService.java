package service;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lädt und prüft Benutzerberechtigungen aus der Ressource
 * /config/access-config.json. Bietet eine einfache API, um festzustellen,
 * ob ein Benutzer eine bestimmte Berechtigung besitzt.
 */
public class AccessControlService {
    private final Map<String, UserConfig> userPermissions;

    public AccessControlService() {
        this.userPermissions = loadUserConfig();
    }

    private Map<String, UserConfig> loadUserConfig() {
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/config/access-config.json")))) {
            Gson gson = new Gson();
            UserPermissions permissions = gson.fromJson(reader, UserPermissions.class);
            return permissions.getUsers();
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
         * Prüft, ob der angegebene Benutzer die gegebene Berechtigung besitzt.
         *
         * @param username Windows-/Anwendungsbenutzername
         * @param permission Berechtigungs-Schlüssel (z. B. "op-status" oder "all")
         * @return true, wenn vorhanden; sonst false
         */
        public boolean hasPermission(String username, String permission) {
        if (userPermissions.containsKey(username)) {
            UserConfig user = userPermissions.get(username);
            return user.getPermissions().contains(permission);
        }
        return false;
    }

    // Classes pour la désérialisation du JSON
    static class UserPermissions {
        Map<String, UserConfig> users;
        public Map<String, UserConfig> getUsers() { return users; }
    }

    static class UserConfig {
        String role;
        List<String> permissions;
        public String getRole() { return role; }
        public List<String> getPermissions() { return permissions; }
    }
}