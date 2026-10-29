import SwiftUI
import AVFoundation
import MediaPlayer
import Observation

struct AudioTrackDTO: Codable, Identifiable, Sendable {
    let id: Int64
    let title: String
    let durationMs: Int64
    let mimeType: String
    let fileSize: Int64?
    let fileHash: String?
}
struct ListeningProgressDTO: Codable, Sendable {
    let revision: Int64
    let trackId: Int64?
    let positionMs: Int64
    let status: String
}
struct AudioSegmentDTO: Codable, Sendable {
    let trackId: Int64
    let startMs: Int64
    let endMs: Int64
}
struct AudioChapterDTO: Codable, Identifiable, Sendable {
    let id: Int64
    let title: String
    let segments: [AudioSegmentDTO]
}
struct AudioEditionDTO: Codable, Identifiable, Sendable {
    let id: Int64
    let bookId: Int64
    let title: String
    let name: String
    let narrator: String
    let durationMs: Int64
    let contentRevision: Int64?
    let tracks: [AudioTrackDTO]?
    let chapters: [AudioChapterDTO]?
    var progress: ListeningProgressDTO?
}
struct AudioPageDTO: Decodable, Sendable { let content: [AudioEditionDTO]; let totalElements: Int }
struct AudioSessionDTO: Decodable, Sendable {
    let sessionId: String
    let key: String
    let contentRevision: Int64
    let progress: ListeningProgressDTO
}
struct AudioSettingsDTO: Codable, Sendable {
    var speed: Double = 1
    var backSeconds: Int = 15
    var forwardSeconds: Int = 30
    var sleepMinutes: Int = 30
}
private struct AudioSessionRequest: Encodable, Sendable { let takeover: Bool }
private struct AudioHeartbeat: Encodable, Sendable {
    let sequence: Int64
    let revision: Int64
    let contentRevision: Int64
    let trackId: Int64
    let positionMs: Int64
    let elapsedMs: Int64
    let finished: Bool
}
private struct AudioBookmarkRequest: Encodable, Sendable { let trackId: Int64; let positionMs: Int64; let title: String }

/// Application-owned AVPlayer; view dismissal must not stop background audio.
@MainActor
@Observable
final class AudiobookPlayer {
    static let shared = AudiobookPlayer()
    private(set) var edition: AudioEditionDTO?
    private(set) var track: AudioTrackDTO?
    private(set) var positionMs: Int64 = 0
    private(set) var playing = false
    private(set) var settings = AudioSettingsDTO()
    var error: String?
    var conflictId: Int64?
    private(set) var sleepRemaining: Int = 0
    private let player = AVPlayer()
    private var session: AudioSessionDTO?
    private var revision: Int64 = 0
    private var sequence: Int64 = 0
    private var elapsed: Int64 = 0
    private var tick: Task<Void, Never>?
    private var saveTask: Task<Void, Never>?
    private var sleepDeadline: Date?
    private var observer: NSObjectProtocol?
    private var interruptions: NSObjectProtocol?
    private var routeChanges: NSObjectProtocol?
    private var identity = ""
    private var opening = false
    private var offlineProgress: AudioOfflineProgress?
    private var lastTick = Date()
    private let client = ServiceLocator.shared.apiClient
    private let config = ServiceLocator.shared.serverConfigStore

    private init() {
        observer = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] notification in
            Task { @MainActor [weak self] in
                guard let self, notification.object as? AVPlayerItem === self.player.currentItem else { return }
                await self.advance()
            }
        }
        interruptions = NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in self?.pause() }
        }
        routeChanges = NotificationCenter.default.addObserver(forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main) { [weak self] notification in
            let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt
            if reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue {
                Task { @MainActor [weak self] in self?.pause() }
            }
        }
        let remote = MPRemoteCommandCenter.shared()
        remote.playCommand.addTarget { [weak self] _ in Task { @MainActor [weak self] in self?.play() }; return .success }
        remote.pauseCommand.addTarget { [weak self] _ in Task { @MainActor [weak self] in self?.pause() }; return .success }
        remote.skipBackwardCommand.preferredIntervals = [15]
        remote.skipForwardCommand.preferredIntervals = [30]
        remote.skipBackwardCommand.addTarget { [weak self] _ in Task { @MainActor [weak self] in guard let self else { return }; self.seek(self.positionMs - Int64(self.settings.backSeconds) * 1000) }; return .success }
        remote.skipForwardCommand.addTarget { [weak self] _ in Task { @MainActor [weak self] in guard let self else { return }; self.seek(self.positionMs + Int64(self.settings.forwardSeconds) * 1000) }; return .success }
        remote.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let ms = Int64(event.positionTime * 1000)
            Task { @MainActor [weak self] in self?.seek(ms) }
            return .success
        }
    }

    func open(_ id: Int64, takeover: Bool = false) async {
        guard !opening else { return }
        opening = true
        defer { opening = false }
        do {
            pause()
            await saveTask?.value
            if let session { try? await client.delete("api/audiobooks/sessions/\(session.sessionId)") }
            session = nil; offlineProgress = nil
            let selectedIdentity = "\(config.serverUrl ?? "")|\(config.token ?? "")"
            let detail: AudioEditionDTO = try await client.get("api/audiobooks/\(id)")
            let preference: AudioSettingsDTO = try await client.get("api/audiobooks/settings")
            let response: AudioSessionDTO = try await client.post("api/audiobooks/\(id)/sessions", body: AudioSessionRequest(takeover: takeover))
            guard selectedIdentity == "\(config.serverUrl ?? "")|\(config.token ?? "")" else { return }
            guard let tracks = detail.tracks, let first = tracks.first else { throw ApiError.notFound }
            identity = selectedIdentity
            edition = detail; settings = preference; session = response
            revision = response.progress.revision; sequence = 0; elapsed = 0; sleepDeadline = nil
            conflictId = nil; error = nil
            let selected = tracks.first(where: { $0.id == response.progress.trackId }) ?? first
            try select(selected, position: response.progress.positionMs)
            startTicker()
        } catch {
            self.error = error.localizedDescription
            if case ApiError.httpError(let code, _) = error, code == 409 { conflictId = id }
        }
    }

    private func startTicker() {
        lastTick = Date()
        tick?.cancel()
        tick = Task { [weak self] in
            var counter = 0
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard let self else { return }
                if self.identity != "\(self.config.serverUrl ?? "")|\(self.config.token ?? "")" {
                    self.pause(); self.player.replaceCurrentItem(with: nil); self.session = nil; self.offlineProgress = nil; self.edition = nil
                    self.error = "账户或服务器已切换，收听已停止"
                    return
                }
                let now = Date()
                let actuallyPlaying = self.player.timeControlStatus == .playing
                if actuallyPlaying {
                    let delta = Int64(min(30, max(0, now.timeIntervalSince(self.lastTick))) * 1000)
                    self.elapsed += delta
                    if var pending = self.offlineProgress {
                        let end = Int64(now.timeIntervalSince1970 * 1000)
                        if let last = pending.intervals.last, last.endMs >= end - delta - 1000 {
                            pending.intervals[pending.intervals.count - 1].endMs = end
                        } else { pending.intervals.append(AudioListeningInterval(startMs: end - delta, endMs: end)) }
                        if pending.intervals.count > 1000 { self.pause(); self.error = "离线记录较多，请联网同步后继续" }
                        else { self.offlineProgress = pending }
                    }
                }
                self.lastTick = now
                let seconds = self.player.currentTime().seconds
                if seconds.isFinite { self.positionMs = Int64(max(0, seconds) * 1000) }
                self.playing = actuallyPlaying
                if let deadline = self.sleepDeadline {
                    self.sleepRemaining = max(0, Int(deadline.timeIntervalSinceNow.rounded(.up)))
                    if now >= deadline { self.pause(); self.sleepDeadline = nil }
                }
                if let failure = self.player.currentItem?.error { self.error = failure.localizedDescription; self.pause() }
                self.updateNowPlaying()
                counter += 1
                if counter % 10 == 0 && actuallyPlaying { self.save() }
            }
        }
    }

    private func select(_ selected: AudioTrackDTO, position: Int64) throws {
        let url: URL
        if offlineProgress != nil {
            guard let edition else { throw ApiError.notFound }
            url = try AudioOfflineCache.shared.file(edition.id, selected.id)
            guard FileManager.default.fileExists(atPath: url.path) else { throw AudioCacheError.message("离线音轨缺失，请重新下载") }
        } else {
            guard let session, let base = config.serverUrl else { throw ApiError.unauthorized }
            guard let media = URL(string: "\(base.trimmingCharacters(in: CharacterSet(charactersIn: "/")))/api/audio-media/\(session.sessionId)/\(selected.id)?key=\(session.key)") else { throw ApiError.invalidUrl }
            url = media
        }
        try AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try AVAudioSession.sharedInstance().setActive(true)
        track = selected; positionMs = position
        player.replaceCurrentItem(with: AVPlayerItem(url: url))
        player.seek(to: CMTime(value: position, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero)
        play()
    }
    func jump(_ segment: AudioSegmentDTO) async {
        guard let selected = edition?.tracks?.first(where: { $0.id == segment.trackId }) else { return }
        save(); await saveTask?.value
        do { try select(selected, position: segment.startMs); save() } catch { self.error = error.localizedDescription }
    }
    private func advance() async {
        guard let tracks = edition?.tracks, let track, let index = tracks.firstIndex(where: { $0.id == track.id }) else { return }
        if index + 1 < tracks.count { await jump(AudioSegmentDTO(trackId: tracks[index + 1].id, startMs: 0, endMs: tracks[index + 1].durationMs)) }
        else { playing = false; save(finished: true) }
    }
    func openOffline(_ cached: AudioEditionDTO) async {
        guard !opening else { return }
        opening = true
        defer { opening = false }
        do {
            pause(); await saveTask?.value
            if let session { try? await client.delete("api/audiobooks/sessions/\(session.sessionId)") }
            session = nil
            guard let first = cached.tracks?.first else { throw ApiError.notFound }
            identity = "\(config.serverUrl ?? "")|\(config.token ?? "")"
            edition = cached
            offlineProgress = try AudioOfflineCache.shared.progress(cached.id) ?? AudioOfflineProgress(
                eventId: UUID().uuidString,baseRevision: cached.progress?.revision ?? 0,
                contentRevision: cached.contentRevision ?? 1,trackId: cached.progress?.trackId ?? first.id,
                positionMs: cached.progress?.positionMs ?? 0)
            offlineProgress?.eventId = UUID().uuidString
            let selected = cached.tracks?.first(where: { $0.id == offlineProgress?.trackId }) ?? first
            error = nil; conflictId = nil; sleepDeadline = nil
            try select(selected,position: offlineProgress?.positionMs ?? 0)
            startTicker()
        } catch { self.error = error.localizedDescription }
    }
    func stopOfflineAndSave(_ id: Int64) async {
        guard edition?.id == id, offlineProgress != nil else { return }
        pause(); await saveTask?.value
        offlineProgress = nil; player.replaceCurrentItem(with: nil); edition = nil; track = nil
    }

    func play() { guard session != nil || offlineProgress != nil else { return }; player.playImmediately(atRate: Float(settings.speed)); playing = true }
    func pause() { player.pause(); playing = false; save() }
    func toggle() { playing ? pause() : play() }
    func seek(_ value: Int64) {
        guard session != nil || offlineProgress != nil else { return }
        positionMs = min(max(0, value), track?.durationMs ?? 0)
        player.seek(to: CMTime(value: positionMs, timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero)
        save()
    }
    func speed(_ value: Double) async {
        var updated = settings; updated.speed = value
        do {
            let _: EmptyResponse = try await client.put("api/audiobooks/settings", body: updated)
            settings = updated
            if playing { player.rate = Float(value) }
        } catch { self.error = "倍速未保存：\(error.localizedDescription)" }
    }
    func sleep(_ minutes: Int?) {
        sleepDeadline = minutes.map { Date().addingTimeInterval(Double($0 * 60)) }
        sleepRemaining = (minutes ?? 0) * 60
    }
    func bookmark() async {
        guard let edition, let track else { return }
        do {
            let _: EmptyResponse = try await client.post("api/audiobooks/\(edition.id)/bookmarks", body: AudioBookmarkRequest(trackId: track.id, positionMs: positionMs, title: "\(track.title) \(positionMs / 1000)秒"))
        } catch { self.error = "书签保存失败：\(error.localizedDescription)" }
    }
    private func save(finished: Bool = false) {
        if var pending = offlineProgress, let edition, let track,
                identity == "\(config.serverUrl ?? "")|\(config.token ?? "")" {
            pending.trackId = track.id; pending.positionMs = min(positionMs,track.durationMs); pending.finished = finished
            offlineProgress = pending
            do { try AudioOfflineCache.shared.save(edition.id,pending) }
            catch { player.pause(); playing = false; self.error = "离线进度未能保存，已暂停" }
            return
        }
        guard let current = session, let track, identity == "\(config.serverUrl ?? "")|\(config.token ?? "")" else { return }
        let prior = saveTask
        let position = min(positionMs, track.durationMs), total = elapsed
        saveTask = Task {
            await prior?.value
            guard self.session?.sessionId == current.sessionId else { return }
            do {
                let event = AudioHeartbeat(sequence: sequence, revision: revision, contentRevision: current.contentRevision, trackId: track.id, positionMs: position, elapsedMs: total, finished: finished)
                sequence += 1
                let result: ListeningProgressDTO = try await client.put("api/audiobooks/sessions/\(current.sessionId)", body: event)
                if session?.sessionId == current.sessionId { revision = result.revision }
            } catch {
                guard session?.sessionId == current.sessionId else { return }
                session = nil; player.pause(); playing = false
                self.error = "进度保存失败，已暂停；请重新打开收听。"
            }
        }
    }
    private func updateNowPlaying() {
        guard let edition, let track else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: "\(edition.title) · \(track.title)",
            MPMediaItemPropertyArtist: edition.narrator,
            MPMediaItemPropertyPlaybackDuration: Double(track.durationMs) / 1000,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: Double(positionMs) / 1000,
            MPNowPlayingInfoPropertyPlaybackRate: playing ? settings.speed : 0
        ]
    }
}

@MainActor
struct AudiobooksScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var entries: [AudioEditionDTO] = []
    @State private var player = AudiobookPlayer.shared
    @State private var page = 0
    @State private var total = 0
    @State private var error: String?
    @State private var tab = 0
    @State private var seekPosition = 0.0
    @State private var dragging = false
    @State private var cached: [AudioEditionDTO] = []
    @State private var downloadTask: Task<Void,Never>?
    @State private var downloadMessage: String?
    @State private var conflict: AudioSyncConflict?
    @State private var deleteId: Int64?

    var body: some View {
        VStack(spacing: 16) {
            AudioSlidingSegmented(options: ["书库", "播放", "章节", "离线"], selectedIndex: $tab)
            if let downloadMessage { Text(downloadMessage).font(.caption) }
            if downloadTask != nil { Button("取消下载") { downloadTask?.cancel(); downloadTask = nil } }
            if let error { Text(error).foregroundStyle(.red) }
            if let error = player.error { Text(error).foregroundStyle(.red).font(.caption) }
            if let id = player.conflictId {
                Button("接管并使用服务器进度") { Task { await player.open(id, takeover: true) } }
            }
            if tab == 0 {
                List(entries) { edition in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(edition.title).font(.headline)
                        Text("\(edition.name) · \(edition.narrator)").font(.subheadline)
                        HStack {
                            Button("开始 / 继续收听") { tab = 1; Task { await player.open(edition.id) } }
                            Spacer()
                            Button("离线下载") {
                                downloadTask = Task {
                                    do {
                                        try await AudioOfflineCache.shared.download(edition.id) { downloadMessage = $0 }
                                        cached = try AudioOfflineCache.shared.list()
                                        downloadMessage = "下载完成"
                                    } catch { self.error = error.localizedDescription }
                                    downloadTask = nil
                                }
                            }.disabled(downloadTask != nil)
                        }.buttonStyle(.borderless)
                    }
                }
                HStack {
                    Button("上一页") { page -= 1 }.disabled(page == 0)
                    Spacer(); Text("\(page + 1)"); Spacer()
                    Button("下一页") { page += 1 }.disabled((page + 1) * 24 >= total)
                }
            } else if tab == 1, let edition = player.edition {
                Spacer()
                Image(systemName: "headphones").font(.system(size: 70)).foregroundStyle(.tint)
                Text(edition.title).font(.title.bold())
                Text("演播：\(edition.narrator)")
                Text(player.track?.title ?? "")
                Slider(value: Binding(get: { dragging ? seekPosition : Double(player.positionMs) }, set: { seekPosition = $0 }),
                    in: 0...Double(max(1, player.track?.durationMs ?? 1)), onEditingChanged: { editing in
                        dragging = editing
                        if !editing { player.seek(Int64(seekPosition)) }
                    })
                Text("\(audioTime(player.positionMs)) / \(audioTime(player.track?.durationMs ?? 0))").monospacedDigit()
                HStack(spacing: 36) {
                    Button { player.seek(player.positionMs - Int64(player.settings.backSeconds) * 1000) } label: { Image(systemName: "gobackward.15") }
                    Button { player.toggle() } label: { Image(systemName: player.playing ? "pause.circle.fill" : "play.circle.fill").font(.system(size: 60)) }
                    Button { player.seek(player.positionMs + Int64(player.settings.forwardSeconds) * 1000) } label: { Image(systemName: "goforward.30") }
                }.font(.title)
                HStack {
                    Menu("\(player.settings.speed, specifier: "%.2g")×") {
                        ForEach([0.75, 1, 1.25, 1.5, 2], id: \.self) { speed in Button("\(speed, specifier: "%.2g")×") { Task { await player.speed(speed) } } }
                    }
                    Spacer()
                    Menu(player.sleepRemaining > 0 ? "\(player.sleepRemaining / 60 + 1)分钟后停止" : "睡眠定时") {
                        ForEach([15,30,60], id: \.self) { minutes in Button("\(minutes) 分钟") { player.sleep(minutes) } }
                        Button("关闭定时") { player.sleep(nil) }
                    }
                    Spacer(); Button("书签") { Task { await player.bookmark() } }
                }
                Button("重新打开收听") { Task { await player.open(edition.id) } }
                Spacer()
            } else if tab == 3 {
                List(cached) { edition in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(edition.title).font(.headline)
                        Button("离线收听") { tab = 1; Task { await player.openOffline(edition) } }
                        Button("同步离线进度") { Task { await synchronize(edition) } }
                        Button("清理离线文件", role: .destructive) {
                            Task {
                                await player.stopOfflineAndSave(edition.id)
                                do {
                                    if try AudioOfflineCache.shared.progress(edition.id) != nil { deleteId = edition.id }
                                    else { try AudioOfflineCache.shared.remove(edition.id); cached = try AudioOfflineCache.shared.list() }
                                } catch { self.error = error.localizedDescription }
                            }
                        }
                    }.buttonStyle(.borderless)
                }
                Text("离线缓存限额5GB，完整下载后可离线收听。未同步进度会阻止清理。")
                    .font(.caption).foregroundStyle(.secondary)
            } else if tab == 2 {
                List(player.edition?.chapters ?? []) { chapter in
                    Button(chapter.title) { if let first = chapter.segments.first { Task { await player.jump(first) } } }
                }
            } else { ContentUnavailableView("选择一本有声书", systemImage: "headphones") }
        }
        .padding()
        .navigationTitle("有声书")
        .confirmationDialog("发现另一设备的新进度", isPresented: Binding(get: { conflict != nil }, set: { if !$0 { conflict = nil } }), titleVisibility: .visible) {
            if let pending = conflict {
                Button("使用此设备进度") { Task { await synchronize(pending.edition, resolve: true) } }
                Button("使用服务器进度") {
                    do {
                        try AudioOfflineCache.shared.clearProgress(pending.edition.id)
                        var updated = pending.edition; updated.progress = pending.server
                        try AudioOfflineCache.shared.manifest(updated)
                        cached = try AudioOfflineCache.shared.list(); conflict = nil
                    } catch { self.error = error.localizedDescription }
                }
                Button("取消", role: .cancel) { conflict = nil }
            }
        } message: {
            if let pending = conflict {
                Text("此设备：音轨 \(pending.local.trackId) \(audioTime(pending.local.positionMs))\n服务器：音轨 \(pending.server.trackId ?? 0) \(audioTime(pending.server.positionMs))\n选择前保留两份位置。")
            }
        }
        .alert("丢弃离线进度并清理？", isPresented: Binding(get: { deleteId != nil }, set: { if !$0 { deleteId = nil } })) {
            Button("确认丢弃并清理", role: .destructive) {
                if let id = deleteId {
                    do {
                        try AudioOfflineCache.shared.clearProgress(id)
                        try AudioOfflineCache.shared.remove(id)
                        cached = try AudioOfflineCache.shared.list()
                    } catch { self.error = error.localizedDescription }
                }
                deleteId = nil
            }
            Button("取消",role: .cancel) { deleteId = nil }
        } message: { Text("未同步的本机位置将被丢弃；服务器位置和原始音频不受影响。") }
        .onDisappear { downloadTask?.cancel(); downloadTask = nil }
        .task(id: page) {
            cached = (try? AudioOfflineCache.shared.list()) ?? []
            do {
                let result: AudioPageDTO = try await locator.apiClient.get("api/audiobooks?page=\(page)&size=24")
                entries = result.content; total = result.totalElements
            } catch { self.error = error.localizedDescription }
        }
    }
    private func synchronize(_ edition: AudioEditionDTO, resolve: Bool = false) async {
        await player.stopOfflineAndSave(edition.id)
        do {
            guard var local = try AudioOfflineCache.shared.progress(edition.id) else { return }
            let remote: AudioEditionDTO = try await locator.apiClient.get("api/audiobooks/\(edition.id)")
            guard remote.contentRevision == local.contentRevision, let progress = remote.progress else {
                error = "音轨已变化，离线位置保留；请重新下载核对"
                return
            }
            if resolve { local.baseRevision = progress.revision; local.resolveConflict = true }
            do {
                let updated: ListeningProgressDTO = try await locator.apiClient.post("api/audiobooks/\(edition.id)/offline-progress", body: local)
                try AudioOfflineCache.shared.clearProgress(edition.id)
                var manifest = edition; manifest.progress = updated
                try AudioOfflineCache.shared.manifest(manifest)
                cached = try AudioOfflineCache.shared.list(); conflict = nil; error = nil
            } catch ApiError.httpError(let code, _) where code == 409 {
                conflict = AudioSyncConflict(edition: edition, local: local, server: progress)
            }
        } catch { self.error = error.localizedDescription }
    }
    private func audioTime(_ ms: Int64) -> String { String(format: "%lld:%02lld", max(0,ms) / 60000, max(0,ms) / 1000 % 60) }
}

private struct AudioSlidingSegmented: View {
    let options: [String]
    @Binding var selectedIndex: Int
    @Namespace private var selection
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(Array(options.enumerated()), id: \.offset) { index, title in
                    Button { withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.22)) { selectedIndex = index } } label: {
                        Text(title).fontWeight(selectedIndex == index ? .semibold : .regular)
                            .frame(minWidth: 80).padding(.vertical, 10).padding(.horizontal, 12)
                            .background {
                                if selectedIndex == index {
                                    RoundedRectangle(cornerRadius: 11).fill(.background)
                                        .matchedGeometryEffect(id: "audio-selection", in: selection)
                                }
                            }
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(selectedIndex == index ? Color.accentColor : .secondary)
                    .accessibilityAddTraits(selectedIndex == index ? .isSelected : [])
                }
            }.padding(3)
        }
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("有声书页面")
    }
}

private struct AudioSyncConflict {
    let edition: AudioEditionDTO
    let local: AudioOfflineProgress
    let server: ListeningProgressDTO
}
