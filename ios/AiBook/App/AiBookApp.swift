import SwiftUI

// MARK: - AiBookApp（@main 入口，与安卓 MainActivity + AiBookApp 对齐）

@main
@MainActor
struct AiBookApp: App {
    @State private var locator = ServiceLocator.shared
    @State private var readerSettingsStore = ServiceLocator.shared.readerSettingsStore
    @State private var startupIssue = ServiceLocator.shared.startupIssue

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(\.appTheme, AppTheme(
                    appThemeMode: readerSettingsStore.appThemeMode,
                    accentColor: readerSettingsStore.accentColor
                ))
                .environment(locator)
                .preferredColorScheme(colorScheme)
                .alert(item: $startupIssue) { issue in
                    Alert(
                        title: Text("存储初始化失败"),
                        message: Text(issue.message),
                        dismissButton: .default(Text("知道了"))
                    )
                }
        }
    }

    private var colorScheme: ColorScheme? {
        switch readerSettingsStore.appThemeMode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}
