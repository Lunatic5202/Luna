import SwiftUI

/// Moon button on the player: pick a duration, or "end of this song". While a countdown runs the
/// remaining time is shown beside the moon.
struct SleepTimerButton: View {
    @ObservedObject var timer: SleepTimer
    let palette: LunaPalette

    var body: some View {
        Menu {
            ForEach(SleepTimer.Option.presets) { option in
                Button {
                    timer.start(option)
                } label: {
                    if timer.option == option {
                        Label(option.label, systemImage: "checkmark")
                    } else {
                        Text(option.label)
                    }
                }
            }
            if timer.option != nil {
                Divider()
                Button("Turn off timer", role: .destructive) { timer.cancel() }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: timer.option == nil ? "moon.zzz" : "moon.zzz.fill")
                if let end = timer.endDate {
                    let now = Date.now
                    Text(timerInterval: now...max(end, now), countsDown: true)
                        .monospacedDigit()
                } else if timer.option == .endOfTrack {
                    Text("End of song")
                }
            }
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(timer.option == nil ? palette.onBackground.color : palette.primary.color)
            .padding(.horizontal, 14)
            .frame(height: 36)
            .background(Color.white.opacity(0.08), in: Capsule())
        }
        .accessibilityLabel("Sleep timer")
        .accessibilityValue(timer.option?.label ?? "Off")
    }
}
