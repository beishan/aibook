import SwiftUI

// MARK: - PrivacyPermissionsScreen（与安卓 PrivacyPermissionsScreen.kt 对齐）

struct PrivacyPermissionsScreen: View {
    @Environment(ServiceLocator.self) private var locator
    @State private var personalizedRecommendations: Bool = true
    @State private var usageStatistics: Bool = false

    var body: some View {
        Form {
            Section("个性化") {
                Toggle("个性化推荐", isOn: $personalizedRecommendations)
                    .onChange(of: personalizedRecommendations) { _, newValue in
                        locator.serverConfigStore.personalizedRecommendations = newValue
                    }
            }

            Section("数据收集") {
                Toggle("使用统计", isOn: $usageStatistics)
                    .onChange(of: usageStatistics) { _, newValue in
                        locator.serverConfigStore.usageStatistics = newValue
                    }
            }

            Section(footer: Text("汗牛充栋尊重您的隐私。所有数据均存储在您的设备和私有服务器上，不会上传至第三方。")) {
                EmptyView()
            }
        }
        .navigationTitle("隐私权限")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            personalizedRecommendations = locator.serverConfigStore.personalizedRecommendations
            usageStatistics = locator.serverConfigStore.usageStatistics
        }
    }
}
