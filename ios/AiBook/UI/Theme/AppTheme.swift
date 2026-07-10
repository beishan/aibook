import SwiftUI

// MARK: - AppTheme（与安卓 Theme.kt 对齐）

struct AppTheme {
    let appThemeMode: AppThemeMode
    let accentColor: AccentColor_

    var isDark: Bool {
        switch appThemeMode {
        case .system:
            // 在 SwiftUI 环境外默认浅色
            return UITraitCollection.current.userInterfaceStyle == .dark
        case .light:
            return false
        case .dark:
            return true
        }
    }

    var primaryColor: Color {
        Color(hex: accentColor.colorValue)
    }

    var backgroundColor: Color {
        isDark ? DesignTokens.darkBackground : DesignTokens.appBackground
    }

    var surfaceColor: Color {
        isDark ? DesignTokens.darkSurface : DesignTokens.cardBackground
    }
}

// MARK: - SwiftUI Environment Key

private struct AppThemeKey: EnvironmentKey {
    static let defaultValue = AppTheme(appThemeMode: .system, accentColor: .orange)
}

extension EnvironmentValues {
    var appTheme: AppTheme {
        get { self[AppThemeKey.self] }
        set { self[AppThemeKey.self] = newValue }
    }
}
