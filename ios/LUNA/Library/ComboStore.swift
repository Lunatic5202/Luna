import Foundation

/// A user-made playlist. It stores track ids only, so it never holds a stale copy of a track's
/// metadata and a removed track simply drops out when the combo is shown.
struct Combo: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var trackIDs: [String]
}

/// The user's combos, saved as JSON after every change.
@MainActor
final class ComboStore: ObservableObject {
    @Published private(set) var combos: [Combo]
    private let fileURL: URL?

    /// `fileURL` nil keeps everything in memory (tests, previews).
    init(fileURL: URL?) {
        self.fileURL = fileURL
        self.combos = fileURL.map(Self.load) ?? []
    }

    static func makeDefault() -> ComboStore {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        guard let directory = base?.appendingPathComponent("LUNA", isDirectory: true) else {
            return ComboStore(fileURL: nil)
        }
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return ComboStore(fileURL: directory.appendingPathComponent("combos.json"))
    }

    // MARK: - Editing

    /// Returns nil when the name is blank.
    @discardableResult
    func create(name: String, trackIDs: [String] = []) -> Combo? {
        let clean = Self.clean(name)
        guard !clean.isEmpty else { return nil }
        var seen = Set<String>()
        let combo = Combo(
            id: UUID().uuidString,
            name: clean,
            trackIDs: trackIDs.filter { seen.insert($0).inserted }
        )
        combos.append(combo)
        save()
        return combo
    }

    func rename(_ id: String, to name: String) {
        let clean = Self.clean(name)
        guard !clean.isEmpty, let index = combos.firstIndex(where: { $0.id == id }) else { return }
        combos[index].name = clean
        save()
    }

    func delete(_ id: String) {
        combos.removeAll { $0.id == id }
        save()
    }

    func add(_ trackID: String, to comboID: String) {
        guard let index = combos.firstIndex(where: { $0.id == comboID }),
              !combos[index].trackIDs.contains(trackID) else { return }
        combos[index].trackIDs.append(trackID)
        save()
    }

    func remove(_ trackID: String, from comboID: String) {
        guard let index = combos.firstIndex(where: { $0.id == comboID }),
              combos[index].trackIDs.contains(trackID) else { return }
        combos[index].trackIDs.removeAll { $0 == trackID }
        save()
    }

    /// For a track deleted from the library: takes it out of every combo.
    func forget(_ trackID: String) {
        var changed = false
        for index in combos.indices where combos[index].trackIDs.contains(trackID) {
            combos[index].trackIDs.removeAll { $0 == trackID }
            changed = true
        }
        if changed { save() }
    }

    /// The combo's tracks in the user's order, skipping any that are no longer in the library.
    func tracks(in combo: Combo, from library: [Track]) -> [Track] {
        let byID = Dictionary(library.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return combo.trackIDs.compactMap { byID[$0] }
    }

    // MARK: - Persistence

    private func save() {
        guard let fileURL, let data = try? JSONEncoder().encode(combos) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    /// A missing or unreadable file means no combos rather than a crash.
    private nonisolated static func load(_ url: URL) -> [Combo] {
        guard let data = try? Data(contentsOf: url),
              let combos = try? JSONDecoder().decode([Combo].self, from: data) else { return [] }
        return combos
    }

    private static func clean(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
