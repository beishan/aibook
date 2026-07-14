import SwiftUI

// MARK: - ThemeSettingsScreen（与安卓 ReaderThemeSettingsScreen 对齐）

@MainActor
struct ThemeSettingsScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var themeMode: AppThemeMode = .system
    @State private var accentColor: AccentColor_ = .orange

    var body: some View {
        Form {
            Section("主题模式") {
                ForEach(AppThemeMode.allCases, id: \.self) { mode in
                    Button {
                        themeMode = mode
                        locator.readerSettingsStore.appThemeMode = mode
                    } label: {
                        HStack {
                            Image(systemName: themeModeIcon(mode))
                            Text(themeModeLabel(mode))
                            Spacer()
                            if themeMode == mode {
                                Image(systemName: "checkmark")
                                    .foregroundColor(DesignTokens.accent)
                            }
                        }
                    }
                    .foregroundColor(.primary)
                }
            }

            Section("强调色") {
                ForEach(AccentColor_.allCases, id: \.self) { color in
                    Button {
                        accentColor = color
                        locator.readerSettingsStore.accentColor = color
                    } label: {
                        HStack(spacing: 12) {
                            Circle()
                                .fill(Color(hex: color.colorValue))
                                .frame(width: 24, height: 24)
                            Text(accentColorLabel(color))
                            Spacer()
                            if accentColor == color {
                                Image(systemName: "checkmark")
                                    .foregroundColor(DesignTokens.accent)
                            }
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
        }
        .navigationTitle("主题设置")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            themeMode = locator.readerSettingsStore.appThemeMode
            accentColor = locator.readerSettingsStore.accentColor
        }
    }

    private func themeModeLabel(_ mode: AppThemeMode) -> String {
        switch mode {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    private func themeModeIcon(_ mode: AppThemeMode) -> String {
        switch mode {
        case .system: return "circle.lefthalf.filled"
        case .light: return "sun.max"
        case .dark: return "moon"
        }
    }

    private func accentColorLabel(_ color: AccentColor_) -> String {
        switch color {
        case .orange: return "橙色"
        case .green: return "绿色"
        case .blue: return "蓝色"
        case .purple: return "紫色"
        case .red: return "红色"
        }
    }
}
