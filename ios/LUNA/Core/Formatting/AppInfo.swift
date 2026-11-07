import Foundation

enum AppInfo {
    /// "0.1.0 (1)", or just the version when there is no build number.
    static func versionString(short: String?, build: String?) -> String {
        let version = short?.trimmingCharacters(in: .whitespaces).nilIfEmpty ?? "unknown"
        guard let build = build?.trimmingCharacters(in: .whitespaces).nilIfEmpty else { return version }
        return "\(version) (\(build))"
    }

    static var currentVersion: String {
        let info = Bundle.main.infoDictionary
        return versionString(
            short: info?["CFBundleShortVersionString"] as? String,
            build: info?["CFBundleVersion"] as? String
        )
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
