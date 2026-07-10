import Foundation

// MARK: - ReaderSettingsStore（与安卓 ReaderSettingsStore 对齐，使用 UserDefaults）

final class ReaderSettingsStore {
    private let defaults = UserDefaults.standard

    // MARK: - 阅读器设置

    var fontScale: Float {
        get { defaults.float(forKey: "reader.fontScale").nilIfZero ?? 1.0 }
        set { defaults.set(newValue, forKey: "reader.fontScale") }
    }

    var fontType: ReaderFontType {
        get { ReaderFontType(rawValue: defaults.string(forKey: "reader.fontType") ?? "") ?? .system }
        set { defaults.set(newValue.rawValue, forKey: "reader.fontType") }
    }

    var customFontName: String? {
        get { defaults.string(forKey: "reader.customFontName") }
        set { defaults.set(newValue, forKey: "reader.customFontName") }
    }

    var customFontPath: String? {
        get { defaults.string(forKey: "reader.customFontPath") }
        set { defaults.set(newValue, forKey: "reader.customFontPath") }
    }

    var lineHeight: Float {
        get { defaults.float(forKey: "reader.lineHeight").nilIfZero ?? 1.45 }
        set { defaults.set(newValue, forKey: "reader.lineHeight") }
    }

    var theme: ReaderTheme {
        get { ReaderTheme(rawValue: defaults.string(forKey: "reader.theme") ?? "") ?? .paper }
        set { defaults.set(newValue.rawValue, forKey: "reader.theme") }
    }

    var paragraphSpacing: ParagraphSpacing {
        get { ParagraphSpacing(rawValue: defaults.string(forKey: "reader.paragraphSpacing") ?? "") ?? .small }
        set { defaults.set(newValue.rawValue, forKey: "reader.paragraphSpacing") }
    }

    var textAlignment: TextAlignment_ {
        get { TextAlignment_(rawValue: defaults.string(forKey: "reader.textAlignment") ?? "") ?? .left }
        set { defaults.set(newValue.rawValue, forKey: "reader.textAlignment") }
    }

    var pageTurnMode: PageTurnMode {
        get { PageTurnMode(rawValue: defaults.string(forKey: "reader.pageTurnMode") ?? "") ?? .simulation }
        set { defaults.set(newValue.rawValue, forKey: "reader.pageTurnMode") }
    }

    var autoBrightness: Bool {
        get { defaults.object(forKey: "reader.autoBrightness") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "reader.autoBrightness") }
    }

    var screenAlwaysOn: Bool {
        get { defaults.bool(forKey: "reader.screenAlwaysOn") }
        set { defaults.set(newValue, forKey: "reader.screenAlwaysOn") }
    }

    // MARK: - 应用主题

    var appThemeMode: AppThemeMode {
        get { AppThemeMode(rawValue: defaults.string(forKey: "app.themeMode") ?? "") ?? .system }
        set { defaults.set(newValue.rawValue, forKey: "app.themeMode") }
    }

    var accentColor: AccentColor_ {
        get { AccentColor_(rawValue: defaults.string(forKey: "app.accentColor") ?? "") ?? .orange }
        set { defaults.set(newValue.rawValue, forKey: "app.accentColor") }
    }

    // MARK: - 快照（用于阅读器设置预览+回滚）

    func snapshot() -> ReaderSettings {
        ReaderSettings(
            fontScale: fontScale,
            fontType: fontType,
            customFontName: customFontName,
            customFontPath: customFontPath,
            lineHeight: lineHeight,
            theme: theme,
            paragraphSpacing: paragraphSpacing,
            textAlignment: textAlignment,
            pageTurnMode: pageTurnMode,
            autoBrightness: autoBrightness,
            screenAlwaysOn: screenAlwaysOn
        )
    }

    func apply(_ settings: ReaderSettings) {
        fontScale = settings.fontScale
        fontType = settings.fontType
        customFontName = settings.customFontName
        customFontPath = settings.customFontPath
        lineHeight = settings.lineHeight
        theme = settings.theme
        paragraphSpacing = settings.paragraphSpacing
        textAlignment = settings.textAlignment
        pageTurnMode = settings.pageTurnMode
        autoBrightness = settings.autoBrightness
        screenAlwaysOn = settings.screenAlwaysOn
    }
}

private extension Float {
    var nilIfZero: Float? { self == 0 ? nil : self }
}
