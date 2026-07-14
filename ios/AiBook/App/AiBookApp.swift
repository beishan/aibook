import SwiftUI

// MARK: - AiBookApp（@main 入口，与安卓 MainActivity + AiBookApp 对齐）

@main
@MainActor
struct AiBookApp: App {
    @State private var locator = ServiceLocator.shared
    @State private var readerSettingsStore = ServiceLocator.shared.readerSettingsStore

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(\.appTheme, AppTheme(
                    appThemeMode: readerSettingsStore.appThemeMode,
                    accentColor: readerSettingsStore.accentColor
                ))
                .environment(locator)
                .preferredColorScheme(colorScheme)
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
