import SwiftUI

// MARK: - SyncConnectionSettingsScreen（与安卓 SyncConnectionSettingsScreen.kt 对齐 — 完整实现）

@MainActor
struct SyncConnectionSettingsScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var serverUrl: String = ""
    @State private var username: String = ""
    @State private var password: String = ""
    @State private var isLoggedIn: Bool = false
    @State private var wifiOnlySync: Bool = false
    @State private var isTesting: Bool = false
    @State private var isLoggingIn: Bool = false
    @State private var testResult: String?
    @State private var testSuccess: Bool = false
    @State private var loginError: String?

    var body: some View {
        Form {
            // 服务器地址
            Section("服务器") {
                HStack {
                    TextField("服务器地址（如 http://192.168.1.100:8080）", text: $serverUrl)
                        .keyboardType(.URL)
                        .autocapitalization(.none)
                        .autocorrectionDisabled()
                        .onSubmit { saveServerUrl() }

                    if !serverUrl.isEmpty {
                        Button {
                            Task { await testConnection() }
                        } label: {
                            if isTesting {
                                ProgressView().scaleEffect(0.7)
                            } else {
                                Image(systemName: "network")
                                    .foregroundColor(DesignTokens.accent)
                            }
                        }
                        .disabled(isTesting)
                    }
                }

                if let result = testResult {
                    HStack(spacing: 6) {
                        Image(systemName: testSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundColor(testSuccess ? DesignTokens.success : .red)
                        Text(result)
                            .font(.caption)
                    }
                }
            }

            // 登录 / 已登录
            if !isLoggedIn {
                Section("登录") {
                    TextField("用户名", text: $username)
                        .autocapitalization(.none)
                        .autocorrectionDisabled()
                    SecureField("密码", text: $password)

                    Button {
                        Task { await login() }
                    } label: {
                        HStack {
                            if isLoggingIn {
                                ProgressView().scaleEffect(0.8)
                            }
                            Text(isLoggingIn ? "登录中..." : "登录")
                                .frame(maxWidth: .infinity)
                        }
                        .foregroundColor(.white)
                    }
                    .listRowBackground(DesignTokens.accent)
                    .disabled(isLoggingIn || username.isEmpty || password.isEmpty || serverUrl.isEmpty)

                    if let error = loginError {
                        HStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(error)
                                .font(.caption)
                                .foregroundColor(.red)
                        }
                    }
                }

                Section("注册") {
                    NavigationLink("注册新账号") {
                        RegisterScreen()
                    }
                }
            } else {
                Section("账户") {
                    HStack {
                        Image(systemName: "person.circle.fill")
                            .font(.title2)
                            .foregroundColor(DesignTokens.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(locator.serverConfigStore.username ?? "")
                                .font(.headline)
                            Text(locator.serverConfigStore.email ?? locator.serverConfigStore.serverUrl ?? "")
                                .font(.caption)
                                .foregroundColor(DesignTokens.softText)
                        }
                        Spacer()
                        Text("已登录")
                            .font(.caption)
                            .foregroundColor(DesignTokens.success)
                    }

                    Button(role: .destructive) {
                        logout()
                    } label: {
                        Text("退出登录")
                    }
                }
            }

            // 同步设置
            Section("同步设置") {
                Toggle("仅 WiFi 同步", isOn: $wifiOnlySync)
                    .onChange(of: wifiOnlySync) { _, newValue in
                        locator.serverConfigStore.wifiOnlySync = newValue
                    }

                HStack {
                    Text("同步状态")
                    Spacer()
                    if isLoggedIn {
                        Text("已连接")
                            .foregroundColor(DesignTokens.success)
                    } else {
                        Text("未连接")
                            .foregroundColor(.gray)
                    }
                }
            }
        }
        .navigationTitle("同步连接")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { loadState() }
    }

    // MARK: - 加载状态

    private func loadState() {
        serverUrl = locator.serverConfigStore.serverUrl ?? ""
        isLoggedIn = locator.serverRepository.isAuthenticated
        username = locator.serverConfigStore.username ?? ""
        wifiOnlySync = locator.serverConfigStore.wifiOnlySync
    }

    // MARK: - 保存服务器地址

    private func saveServerUrl() {
        let normalized = serverUrl.hasPrefix("http") ? serverUrl : "http://\(serverUrl)"
        locator.serverConfigStore.serverUrl = normalized
        serverUrl = normalized
    }

    // MARK: - 测试连接

    private func testConnection() async {
        let normalized = serverUrl.hasPrefix("http") ? serverUrl : "http://\(serverUrl)"
        isTesting = true
        testResult = nil

        do {
            let success = try await locator.serverRepository.testConnection(url: normalized)
            if success {
                testResult = "连接成功"
                testSuccess = true
                locator.serverConfigStore.serverUrl = normalized
                serverUrl = normalized
            } else {
                testResult = "服务器无响应"
                testSuccess = false
            }
        } catch {
            testResult = "连接失败: \(error.localizedDescription)"
            testSuccess = false
        }

        isTesting = false
    }

    // MARK: - 登录

    private func login() async {
        saveServerUrl()
        isLoggingIn = true
        loginError = nil

        do {
            let success = try await locator.serverRepository.login(username: username, password: password)
            if success {
                isLoggedIn = true
                password = "" // 清空密码
            }
        } catch {
            loginError = error.localizedDescription
        }

        isLoggingIn = false
    }

    // MARK: - 退出

    private func logout() {
        locator.serverRepository.logout()
        isLoggedIn = false
        username = ""
    }
}

// MARK: - 注册页面

@MainActor
struct RegisterScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @Environment(\.dismiss) private var dismiss

    @State private var username = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var email = ""
    @State private var isRegistering = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            Section("账号信息") {
                TextField("用户名", text: $username)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
                SecureField("密码", text: $password)
                SecureField("确认密码", text: $confirmPassword)
                TextField("邮箱（可选）", text: $email)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
            }

            Section {
                Button {
                    Task { await register() }
                } label: {
                    HStack {
                        if isRegistering {
                            ProgressView().scaleEffect(0.8)
                        }
                        Text(isRegistering ? "注册中..." : "注册")
                            .frame(maxWidth: .infinity)
                    }
                    .foregroundColor(.white)
                }
                .listRowBackground(DesignTokens.accent)
                .disabled(isRegistering || !isFormValid)
            }

            if let error = errorMessage {
                Section {
                    HStack(spacing: 6) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                }
            }
        }
        .navigationTitle("注册")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var isFormValid: Bool {
        !username.isEmpty &&
        password.count >= 6 &&
        password == confirmPassword
    }

    private func register() async {
        guard password == confirmPassword else {
            errorMessage = "两次密码不一致"
            return
        }

        isRegistering = true
        errorMessage = nil

        do {
            let success = try await locator.serverRepository.register(
                username: username,
                password: password,
                email: email.isEmpty ? nil : email
            )
            if success {
                dismiss()
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isRegistering = false
    }
}
