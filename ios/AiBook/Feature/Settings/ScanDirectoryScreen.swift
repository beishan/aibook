import SwiftUI

// MARK: - ScanDirectoryScreen（与安卓 ScanDirectoryScreen.kt 对齐）

struct ScanDirectoryScreen: View {
    @Environment(ServiceLocator.self) private var locator

    var body: some View {
        List {
            Section {
                Button {
                    // TODO: 添加扫描目录
                } label: {
                    Label("添加目录", systemImage: "folder.badge.plus")
                }
            }

            Section("扫描目录") {
                // TODO: 展示扫描目录列表
                Text("暂无扫描目录")
                    .foregroundColor(DesignTokens.softText)
            }

            Section {
                Button {
                    // TODO: 扫描全部
                } label: {
                    Label("扫描全部", systemImage: "arrow.triangle.2.circlepath")
                        .foregroundColor(DesignTokens.accent)
                }
            }
        }
        .navigationTitle("扫描目录")
        .navigationBarTitleDisplayMode(.inline)
    }
}
