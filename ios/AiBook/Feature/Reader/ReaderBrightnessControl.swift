import SwiftUI
import UIKit

// MARK: - ReaderBrightnessControl（屏幕亮度控制，与安卓 autoBrightness / screenAlwaysOn 对齐）

final class ReaderBrightnessControl {
    private let originalBrightness: CGFloat
    private var isAutoBrightnessEnabled = false

    init() {
        self.originalBrightness = UIScreen.main.brightness
    }

    /// 设置屏幕亮度 (0.0 ~ 1.0)
    func setBrightness(_ value: CGFloat) {
        UIScreen.main.brightness = max(0, min(1, value))
    }

    /// 获取当前亮度
    func getBrightness() -> CGFloat {
        UIScreen.main.brightness
    }

    /// 开启自动亮度（阅读模式下降低亮度保护眼睛）
    func enableAutoBrightness() {
        isAutoBrightnessEnabled = true
        // 阅读时亮度略低于环境
        let current = UIScreen.main.brightness
        let readingBrightness = min(current, 0.7)
        UIScreen.main.brightness = max(0.15, readingBrightness)
    }

    /// 关闭自动亮度
    func disableAutoBrightness() {
        isAutoBrightnessEnabled = false
    }

    /// 设置屏幕常亮
    func setScreenAlwaysOn(_ enabled: Bool) {
        UIApplication.shared.isIdleTimerDisabled = enabled
    }

    /// 恢复原始亮度
    func restore() {
        UIScreen.main.brightness = originalBrightness
        UIApplication.shared.isIdleTimerDisabled = false
    }
}

// MARK: - BrightnessSlider（亮度调节滑块视图）

struct BrightnessSlider: View {
    @Binding var brightness: CGFloat
    let onAutoToggle: (Bool) -> Void
    @State private var isAuto: Bool = false

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "sun.min")
                    .font(.caption)
                    .foregroundColor(DesignTokens.softText)
                Slider(value: $brightness, in: 0.1...1.0, step: 0.05)
                    .tint(DesignTokens.accent)
                Image(systemName: "sun.max.fill")
                    .font(.caption)
                    .foregroundColor(DesignTokens.softText)
            }

            Toggle("护眼模式", isOn: $isAuto)
                .font(.caption)
                .onChange(of: isAuto) { _, newValue in
                    onAutoToggle(newValue)
                }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}
