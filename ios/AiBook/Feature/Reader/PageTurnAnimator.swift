import SwiftUI

// MARK: - PageTurnAnimator（与安卓 PageTurnVisuals.kt 对齐 — 翻页动画效果）

struct PageTurnTransform {
    let offset: CGSize
    let rotation: Double
    let opacity: Double
    let scale: Double
}

enum PageTurnAnimator {
    /// 根据翻页模式和手势进度计算变换
    static func transform(
        mode: PageTurnMode,
        progress: CGFloat,  // -1.0 到 1.0，负数=上一页，正数=下一页
        pageSize: CGSize
    ) -> PageTurnTransform {
        let p = max(-1, min(1, progress))

        switch mode {
        case .simulation:
            return simulationTransform(progress: p, pageSize: pageSize)
        case .slide:
            return slideTransform(progress: p)
        case .cover:
            return coverTransform(progress: p, pageSize: pageSize)
        case .pan:
            return panTransform(progress: p, pageSize: pageSize)
        case .vertical:
            return PageTurnTransform(offset: .zero, rotation: 0, opacity: 1, scale: 1)
        }
    }

    // MARK: - 仿真翻页（书页卷曲效果）

    private static func simulationTransform(progress: CGFloat, pageSize: CGSize) -> PageTurnTransform {
        let absP = abs(progress)
        let sign: CGFloat = progress >= 0 ? 1 : -1

        // 水平偏移
        let offsetX = progress * pageSize.width

        // 轻微旋转（模拟书页卷曲）
        let rotation = Double(progress * -15)

        // 前半程淡出，后半程淡入
        let opacity: Double
        if absP < 0.5 {
            opacity = Double(1 - absP * 0.6)
        } else {
            opacity = Double(0.7 + (absP - 0.5) * 0.6)
        }

        // 轻微缩放
        let scale = 1.0 - Double(absP * 0.03)

        return PageTurnTransform(
            offset: CGSize(width: offsetX, height: 0),
            rotation: rotation,
            opacity: opacity,
            scale: scale
        )
    }

    // MARK: - 滑动翻页

    private static func slideTransform(progress: CGFloat) -> PageTurnTransform {
        let offsetX = progress * UIScreen.main.bounds.width

        return PageTurnTransform(
            offset: CGSize(width: offsetX, height: 0),
            rotation: 0,
            opacity: Double(1 - abs(progress) * 0.3),
            scale: 1
        )
    }

    // MARK: - 覆盖翻页（新页从右侧覆盖）

    private static func coverTransform(progress: CGFloat, pageSize: CGSize) -> PageTurnTransform {
        let absP = abs(progress)

        // 当前页不动，新页从右侧滑入覆盖
        let offsetX = progress >= 0
            ? pageSize.width * (1 - progress)  // 下一页从右滑入
            : -pageSize.width * progress         // 上一页从左滑入

        return PageTurnTransform(
            offset: CGSize(width: offsetX, height: 0),
            rotation: 0,
            opacity: 1,
            scale: 1 - Double(absP * 0.05)
        )
    }

    // MARK: - 平移翻页

    private static func panTransform(progress: CGFloat, pageSize: CGSize) -> PageTurnTransform {
        let offsetX = progress * pageSize.width

        return PageTurnTransform(
            offset: CGSize(width: offsetX, height: 0),
            rotation: 0,
            opacity: 1,
            scale: 1
        )
    }
}

// MARK: - AnimatedPageView（带翻页动画的分页阅读器）

struct AnimatedPageView<Content: View>: View {
    let mode: PageTurnMode
    let pageCount: Int
    @Binding var currentPage: Int
    let content: (Int) -> Content

    @State private var dragOffset: CGFloat = 0
    @State private var isDragging: Bool = false
    @GestureState private var gestureOffset: CGFloat = 0

    init(
        mode: PageTurnMode,
        pageCount: Int,
        currentPage: Binding<Int>,
        @ViewBuilder content: @escaping (Int) -> Content
    ) {
        self.mode = mode
        self.pageCount = pageCount
        self._currentPage = currentPage
        self.content = content
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // 当前页
                if currentPage < pageCount {
                    content(currentPage)
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .offset(x: currentOffset(width: geometry.size.width))
                        .animation(isDragging ? .none : .easeOut(duration: 0.3), value: dragOffset)
                }

                // 下一页（向左滑时显示）
                if effectiveOffset > 0, currentPage > 0 {
                    content(currentPage - 1)
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .offset(x: -geometry.size.width + effectiveOffset)
                }

                // 下一页（向右滑时显示）
                if effectiveOffset < 0, currentPage < pageCount - 1 {
                    content(currentPage + 1)
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .offset(x: geometry.size.width + effectiveOffset)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 20)
                    .onChanged { value in
                        isDragging = true
                        dragOffset = value.translation.width
                    }
                    .onEnded { value in
                        isDragging = false
                        let threshold = geometry.size.width * 0.25

                        if value.translation.width < -threshold, currentPage < pageCount - 1 {
                            // 向左滑 → 下一页
                            withAnimation(.easeOut(duration: 0.3)) {
                                currentPage += 1
                            }
                        } else if value.translation.width > threshold, currentPage > 0 {
                            // 向右滑 → 上一页
                            withAnimation(.easeOut(duration: 0.3)) {
                                currentPage -= 1
                            }
                        }

                        withAnimation(.easeOut(duration: 0.2)) {
                            dragOffset = 0
                        }
                    }
            )
        }
    }

    private var effectiveOffset: CGFloat {
        dragOffset + gestureOffset
    }

    private func currentOffset(width: CGFloat) -> CGFloat {
        switch mode {
        case .simulation:
            // 仿真效果：轻微旋转 + 偏移
            return dragOffset * 0.8
        case .slide:
            return dragOffset
        case .cover:
            // 覆盖效果：当前页不动，新页覆盖
            return dragOffset < 0 ? 0 : dragOffset
        case .pan:
            return dragOffset
        case .vertical:
            return 0
        }
    }
}
