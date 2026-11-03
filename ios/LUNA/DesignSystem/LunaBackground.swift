import SwiftUI

/// The vertical palette gradient with a soft glow at the top that every LUNA screen sits on.
struct LunaBackground: View {
    let palette: LunaPalette

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [palette.background.color, palette.backgroundBottom.color],
                startPoint: .top,
                endPoint: .bottom
            )
            RadialGradient(
                colors: [palette.glow.withAlpha(0.55).color, palette.glow.withAlpha(0).color],
                center: .top,
                startRadius: 0,
                endRadius: 420
            )
        }
        .ignoresSafeArea()
    }
}
