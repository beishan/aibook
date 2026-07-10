import SwiftUI

// MARK: - StoreCategoryScreen（与安卓 StoreCategoryScreen.kt 对齐 — 完整实现）

struct StoreCategoryScreen: View {
    @State var filter: StoreCatalogFilter
    let onApply: (StoreCatalogFilter) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(ServiceLocator.self) private var locator
    @State private var searchText = ""
    @State private var sources: [(id: String, name: String)] = []

    var body: some View {
        List {
            // 搜索
            Section("搜索") {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(DesignTokens.softText)
                    TextField("书名 / 作者", text: $searchText)
                        .autocorrectionDisabled()
                    if !searchText.isEmpty {
                        Button {
                            searchText = ""
                            filter.query = nil
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(DesignTokens.softText)
                        }
                    }
                }
                .onChange(of: searchText) { _, newValue in
                    filter.query = newValue.isEmpty ? nil : newValue
                }
            }

            // 来源筛选
            if !sources.isEmpty {
                Section("来源") {
                    Button {
                        filter.sourceId = nil
                    } label: {
                        HStack {
                            Text("全部来源")
                            Spacer()
                            if filter.sourceId == nil {
                                Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                            }
                        }
                    }
                    .foregroundColor(.primary)

                    ForEach(sources, id: \.id) { source in
                        Button {
                            filter.sourceId = source.id
                        } label: {
                            HStack {
                                Text(source.name)
                                Spacer()
                                if filter.sourceId == source.id {
                                    Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                                }
                            }
                        }
                        .foregroundColor(.primary)
                    }
                }
            }

            // 格式筛选
            Section("格式") {
                Button {
                    filter.format = nil
                } label: {
                    HStack {
                        Text("全部格式")
                        Spacer()
                        if filter.format == nil {
                            Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                        }
                    }
                }
                .foregroundColor(.primary)

                ForEach(BookFormat.allCases, id: \.self) { format in
                    Button {
                        filter.format = filter.format == format ? nil : format
                    } label: {
                        HStack {
                            Text(format.displayName)
                            Spacer()
                            if filter.format == format {
                                Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                            }
                        }
                    }
                    .foregroundColor(.primary)
                }
            }

            // 排序
            Section("排序") {
                ForEach(StoreSortOption.allCases, id: \.self) { option in
                    Button {
                        filter.sort = option
                    } label: {
                        HStack {
                            Text(sortLabel(option))
                            Spacer()
                            if filter.sort == option {
                                Image(systemName: "checkmark").foregroundColor(DesignTokens.accent)
                            }
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
        }
        .navigationTitle("分类筛选")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("应用") {
                    onApply(filter)
                    dismiss()
                }
            }
        }
        .onAppear {
            sources = [("local", "本地书籍")]
            let connections = locator.opdsConnectionRepository.fetchAll()
            for conn in connections {
                sources.append((conn.id, conn.name))
            }
        }
    }

    private func sortLabel(_ option: StoreSortOption) -> String {
        switch option {
        case .recent: return "最近"
        case .title: return "书名"
        case .author: return "作者"
        case .source: return "来源"
        }
    }
}
