import Foundation

// MARK: - ServerConfigStore（与安卓 ServerConfigStore 对齐）

final class ServerConfigStore {
    private let defaults = UserDefaults.standard

    var serverUrl: String? {
        get { defaults.string(forKey: "server.url") }
        set { defaults.set(newValue, forKey: "server.url") }
    }

    var token: String? {
        get { defaults.string(forKey: "server.token") }
        set { defaults.set(newValue, forKey: "server.token") }
    }

    var username: String? {
        get { defaults.string(forKey: "server.username") }
        set { defaults.set(newValue, forKey: "server.username") }
    }

    var email: String? {
        get { defaults.string(forKey: "server.email") }
        set { defaults.set(newValue, forKey: "server.email") }
    }

    var wifiOnlySync: Bool {
        get { defaults.bool(forKey: "server.wifiOnlySync") }
        set { defaults.set(newValue, forKey: "server.wifiOnlySync") }
    }

    var personalizedRecommendations: Bool {
        get { defaults.object(forKey: "server.personalizedRecommendations") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "server.personalizedRecommendations") }
    }

    var usageStatistics: Bool {
        get { defaults.bool(forKey: "server.usageStatistics") }
        set { defaults.set(newValue, forKey: "server.usageStatistics") }
    }
}
