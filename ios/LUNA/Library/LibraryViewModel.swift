import Foundation

@MainActor
final class LibraryViewModel: ObservableObject {
    @Published private(set) var tracks: [Track]
    @Published private(set) var isImporting = false
    @Published var alertMessage: String?

    private let provider: FilesLibraryProvider

    init(provider: FilesLibraryProvider) {
        self.provider = provider
        self.tracks = provider.tracks
    }

    func url(for track: Track) -> URL? {
        provider.url(for: track)
    }

    /// Takes the result of the Files picker.
    func importFiles(_ result: Result<[URL], Error>) async {
        switch result {
        case .failure(let error):
            if (error as NSError).code != NSUserCancelledError {
                alertMessage = error.localizedDescription
            }
        case .success(let urls):
            guard !urls.isEmpty else { return }
            isImporting = true
            let outcome = await provider.importFiles(from: urls)
            tracks = provider.tracks
            isImporting = false
            if !outcome.failures.isEmpty {
                alertMessage = outcome.failures.joined(separator: "\n")
            }
        }
    }

    func remove(_ track: Track) {
        provider.remove(track)
        tracks = provider.tracks
    }
}
