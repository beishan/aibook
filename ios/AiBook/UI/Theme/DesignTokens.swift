import SwiftUI

// MARK: - DesignTokens（与安卓 DesignTokens.kt 完全对齐）

enum DesignTokens {
    // MARK: - 颜色

    static let appBackground = Color(hex: 0xFFFFFCF8)
    static let cardBackground = Color(hex: 0xFFFFFEFC)
    static let warmCard = Color(hex: 0xFFFFF8F0)
    static let accent = Color(hex: 0xFFD47A1F)
    static let accentDark = Color(hex: 0xFFB96312)
    static let softText = Color(hex: 0xFF6F6A64)
    static let hairline = Color(hex: 0xFFECE5DE)
    static let success = Color(hex: 0xFF3A8A4C)
    static let opdsGreen = Color(hex: 0xFF6F8F52)

    // MARK: - 间距 / 圆角

    static let pagePadding: CGFloat = 24
    static let cardRadius: CGFloat = 18
    static let softShadow: CGFloat = 8

    // MARK: - 暗黑模式颜色

    static let darkBackground = Color(hex: 0xFF11140F)
    static let darkSurface = Color(hex: 0xFF11140F)
}

// MARK: - Color hex 初始化

extension Color {
    init(hex: UInt64, opacity: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, opacity: opacity)
    }
}
