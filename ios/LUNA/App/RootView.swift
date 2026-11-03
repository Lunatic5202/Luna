import SwiftUI

/// Placeholder shell for the iOS foundation milestone. The library, player and
/// queue screens replace this as the milestones in docs/IOS.md land.
struct RootView: View {
    private let palette = LunaPalette.fallback

    var body: some View {
        ZStack {
            LunaBackground(palette: palette)

            VStack(spacing: 14) {
                Text("LUNA")
                    .font(.system(size: 44, weight: .heavy, design: .rounded))
                    .tracking(8)
                    .foregroundStyle(palette.primary.color)

                Text("Your music, on iPhone")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(palette.muted.color)

                Text("FOUNDATION BUILD")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(2)
                    .foregroundStyle(palette.secondary.color)
                    .padding(.top, 18)
            }
            .accessibilityElement(children: .combine)
        }
    }
}

#Preview {
    RootView()
}
