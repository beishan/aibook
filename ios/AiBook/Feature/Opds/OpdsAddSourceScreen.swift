import SwiftUI

// MARK: - OpdsAddSourceScreen（与安卓 OpdsAddSourceScreen.kt 对齐 — 完整实现）

struct OpdsAddSourceScreen: View {
    let connectionId: String?
    @Environment(ServiceLocator.self) private var locator
    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    @State private var url = ""
    @State private var username = ""
    @State private var password = ""
    @State private var isTesting = false
    @State private var testResult: String?
    @State private var testSuccess = false

    var isEditing: Bool { connectionId != nil }

    var body: some View {
        Form {
            Section("书源信息") {
                TextField("名称（如：我的书库）", text: $name)
                TextField("OPDS 地址", text: $url)
                    .keyboardType(.URL)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
            }

            Section("认证（可选）") {
                TextField("用户名", text: $username)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
                SecureField("密码", text: $password)
            }

            // 测试连接
            Section {
                Button {
                    Task { await testConnection() }
                } label: {
                    HStack {
                        if isTesting {
                            ProgressView().scaleEffect(0.8)
                        } else {
                            Image(systemName: "network")
                        }
                        Text(isTesting ? "测试中..." : "测试连接")
                            .frame(maxWidth: .infinity)
                    }
                }
                .disabled(isTesting || url.isEmpty)

                if let result = testResult {
                    HStack {
                        Image(systemName: testSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundColor(testSuccess ? DesignTokens.success : .red)
                        Text(result)
                            .font(.caption)
                    }
                }
            }

            Section {
                Button {
                    save()
                } label: {
                    Text(isEditing ? "保存修改" : "添加书源")
                        .frame(maxWidth: .infinity)
                        .foregroundColor(.white)
                }
                .listRowBackground(DesignTokens.accent)
                .disabled(name.isEmpty || url.isEmpty)
            }
        }
        .navigationTitle(isEditing ? "编辑书源" : "添加书源")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadExisting() }
    }

    // MARK: - 加载已有连接

    @MainActor private func loadExisting() {
        guard let id = connectionId,
              let conn = locator.opdsConnectionRepository.fetch(byId: id) else { return }
        name = conn.name
        url = conn.baseUrl
        username = conn.username ?? ""
        password = conn.password ?? ""
    }

    // MARK: - 测试连接

    private func testConnection() async {
        let normalizedUrl = url.hasPrefix("http") ? url : "http://\(url)"
        guard let feedUrl = URL(string: normalizedUrl) else {
            testResult = "无效的 URL"
            testSuccess = false
            return
        }

        isTesting = true
        testResult = nil

        do {
            let feed = try await locator.opdsCatalogService.loadFeed(
                url: feedUrl,
                username: username.isEmpty ? nil : username,
                password: password.isEmpty ? nil : password
            )
            let entryCount = feed.entries.count
            testResult = "连接成功！发现 \(entryCount) 个条目"
            testSuccess = true
        } catch {
            testResult = "连接失败: \(error.localizedDescription)"
            testSuccess = false
        }

        isTesting = false
    }

    // MARK: - 保存

    @MainActor private func save() {
        let normalizedUrl = url.hasPrefix("http") ? url : "http://\(url)"

        if let id = connectionId {
            // 编辑模式
            var conn = locator.opdsConnectionRepository.fetch(byId: id) ?? OpdsConnection(
                id: id, name: name, baseUrl: normalizedUrl,
                username: nil, password: nil, enabled: true,
                lastSyncedAt: nil, bookCount: 0, syncState: .idle, lastErrorMessage: nil
            )
            conn.name = name
            conn.baseUrl = normalizedUrl
            conn.username = username.isEmpty ? nil : username
            conn.password = password.isEmpty ? nil : password
            locator.opdsConnectionRepository.save(conn)
        } else {
            // 新增模式
            let conn = OpdsConnection(
                id: UUID().uuidString,
                name: name,
                baseUrl: normalizedUrl,
                username: username.isEmpty ? nil : username,
                password: password.isEmpty ? nil : password,
                enabled: true,
                lastSyncedAt: nil,
                bookCount: 0,
                syncState: .idle,
                lastErrorMessage: nil
            )
            locator.opdsConnectionRepository.save(conn)
        }

        dismiss()
    }
}
