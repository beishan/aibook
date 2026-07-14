import SwiftUI

// MARK: - ReaderThemeSheet（阅读主题选择面板，与安卓 ReaderThemePage 对齐）

@MainActor
struct ReaderThemeSheet: View {
    @Binding var theme: ReaderTheme
    let onDismiss: () -> Void

    private let themes: [(ReaderTheme, String, Color, Color)] = [
        (.light, "默认白", Color.white, Color(hex: 0xFF333333)),
        (.paper, "纸质", Color(hex: 0xFFF5E6D3), Color(hex: 0xFF5B4636)),
        (.green, "护眼绿", Color(hex: 0xFFCCE8CF), Color(hex: 0xFF3E5E3F)),
        (.gray, "浅灰", Color(hex: 0xFFE6E6E6), Color(hex: 0xFF333333)),
        (.dark, "暗黑", Color(hex: 0xFF1A1A1A), Color(hex: 0xFFCCCCCC)),
    ]

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                Text("选择阅读主题")
                    .font(.headline)
                    .padding(.top, 16)

                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                ], spacing: 16) {
                    ForEach(themes, id: \.0) { item in
                        let (themeValue, label, bgColor, textColor) = item
                        let isSelected = theme == themeValue

                        Button {
                            theme = themeValue
                        } label: {
                            VStack(spacing: 8) {
                                // 主题预览
                                ZStack {
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(bgColor)
                                        .frame(height: 80)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(
                                                    isSelected ? DesignTokens.accent : Color.gray.opacity(0.3),
                                                    lineWidth: isSelected ? 3 : 1
                                                )
                                        )

                                    VStack(spacing: 4) {
                                        Text("汗牛充栋")
                                            .font(.system(size: 11, weight: .medium))
                                            .foregroundColor(textColor)
                                        Text("阅读示例文字")
                                            .font(.system(size: 9))
                                            .foregroundColor(textColor.opacity(0.7))
                                    }
                                }

                                HStack(spacing: 4) {
                                    Text(label)
                                        .font(.caption)
                                        .foregroundColor(.primary)
                                    if isSelected {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.caption)
                                            .foregroundColor(DesignTokens.accent)
                                    }
                                }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 24)

                Spacer()
            }
            .background(DesignTokens.appBackground)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { onDismiss() }
                }
            }
        }
        .presentationDetents([.fraction(0.45)])
    }
}
