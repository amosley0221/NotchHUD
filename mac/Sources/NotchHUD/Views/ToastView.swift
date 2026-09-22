import SwiftUI

/// Event feedback: key cap · label · optional meter · optional state tag.
struct ToastView: View {
    let toast: Toast
    let geometry: PanelGeometry

    var body: some View {
        HStack(spacing: 12) {
            ForEach(toast.keys, id: \.self) { KeyCap(text: $0) }

            Text(toast.label)
                .font(.ui(13, .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .truncationMode(.tail)

            if let meter = toast.meter {
                Meter(value: meter, color: toast.stateColor ?? Settings.shared.theme.accent)
            }

            if let stateText = toast.state {
                Text(stateText.uppercased())
                    .font(.ui(11, .semibold))
                    .kerning(0.5)
                    .foregroundStyle(toast.stateColor ?? Settings.shared.theme.accent)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
