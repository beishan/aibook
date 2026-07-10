import SwiftUI

// MARK: - SettingsScreen（与安卓 SettingsScreen.kt 对齐 — 完整实现）

struct SettingsScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var viewModel: SettingsViewModel?

    var body: some View {
        Group {
            if let vm = viewModel {
                settingsContent(vm)
            } else {
                ProgressView()
                    .onAppear {
                        let vm = SettingsViewModel(locator: locator)
                        vm.load()
                        viewModel = vm
                    }
            }
        }
        .navigationTitle("设置")
        .navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder
    private func settingsContent(_ vm: SettingsViewModel) -> some View {
        List {
            // 用户信息
            if vm.isLoggedIn {
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: "person.circle.fill")
                            .font(.system(size: 40))
                            .foregroundColor(DesignTokens.accent)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(vm.username)
                                .font(.headline)
                            Text("已登录")
                                .font(.caption)
                                .foregroundColor(DesignTokens.success)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundColor(DesignTokens.softText)
                    }
                    .contentShape(Rectangle())
                }
            }

            // 阅读与外观
            Section("阅读与外观") {
                NavigationLink(value: Screen.themeSettings) {
                    Label {
                        HStack {
                            Text("主题设置")
                            Spacer()
                            Text(themeLabel(vm.appThemeMode))
                                .font(.caption)
                                .foregroundColor(DesignTokens.softText)
                        }
                    } icon: {
                        Image(systemName: "paintbrush")
                    }
                }
            }

            // 书库与扫描
            Section("书库与扫描") {
                NavigationLink(value: Screen.scanDirectories) {
                    Label("扫描目录", systemImage: "folder")
                }
                NavigationLink(value: Screen.storageCache) {
                    Label {
                        HStack {
                            Text("存储缓存")
                            Spacer()
                            Text(vm.storageUsed)
                                .font(.caption)
                                .foregroundColor(DesignTokens.softText)
                        }
                    } icon: {
                        Image(systemName: "internaldrive")
                    }
                }
            }

            // 同步与连接
            Section("同步与连接") {
                NavigationLink(value: Screen.syncConnectionSettings) {
                    Label {
                        HStack {
                            Text("同步连接")
                            Spacer()
                            if vm.isLoggedIn {
                                Text("已连接")
                                    .font(.caption)
                                    .foregroundColor(DesignTokens.success)
                            } else {
                                Text("未连接")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                        }
                    } icon: {
                        Image(systemName: "arrow.triangle.2.circlepath")
                    }
                }
            }

            // 隐私与安全
            Section("隐私与安全") {
                NavigationLink(value: Screen.privacyPermissions) {
                    Label("隐私权限", systemImage: "lock.shield")
                }
            }

            // 关于
            Section("关于") {
                NavigationLink(value: Screen.about) {
                    Label("关于汗牛充栋", systemImage: "info.circle")
                }
            }
        }
        .background(DesignTokens.appBackground)
        .navigationDestination(for: Screen.self) { screen in
            switch screen {
            case .themeSettings: ThemeSettingsScreen()
            case .scanDirectories: ScanDirectoryScreen()
            case .syncConnectionSettings: SyncConnectionSettingsScreen()
            case .storageCache: StorageCacheScreen()
            case .privacyPermissions: PrivacyPermissionsScreen()
            case .about: AboutScreen()
            default: EmptyView()
            }
        }
    }

    private func themeLabel(_ mode: AppThemeMode) -> String {
        switch mode {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }
}
