import SwiftUI

// MARK: - AboutScreen（与安卓 AboutScreen.kt 对齐）

struct AboutScreen: View {
    var body: some View {
        List {
            Section {
                VStack(spacing: 12) {
                    Image(systemName: "book.fill")
                        .font(.system(size: 60))
                        .foregroundColor(DesignTokens.accent)

                    Text("汗牛充栋")
                        .font(.title2.bold())

                    Text("v0.1.0")
                        .font(.subheadline)
                        .foregroundColor(DesignTokens.softText)

                    Text("私有化图书管理与阅读平台")
                        .font(.caption)
                        .foregroundColor(DesignTokens.softText)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }

            Section("关于") {
                HStack {
                    Text("版本")
                    Spacer()
                    Text("0.1.0 (1)")
                        .foregroundColor(DesignTokens.softText)
                }
                HStack {
                    Text("平台")
                    Spacer()
                    Text("iOS")
                        .foregroundColor(DesignTokens.softText)
                }
            }

            Section("开源许可") {
                // TODO: 添加开源库列表
                Text("Readium Swift Toolkit")
                Text("Kingfisher")
                Text("SwiftSoup")
            }

            Section(footer: Text("© 2025 汗牛充栋 · 数据完全自持")) {
                EmptyView()
            }
        }
        .navigationTitle("关于")
        .navigationBarTitleDisplayMode(.inline)
    }
}
