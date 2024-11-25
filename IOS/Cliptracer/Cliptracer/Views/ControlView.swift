import SwiftUI
import NetworkExtension
import CoreBluetooth
import AVFoundation
import MediaPlayer

enum LayoutType: CaseIterable {
    case computer, watch
}

struct ControlView: View {
    @State private var layoutIndex = 0 // Current index for layout
    @Environment(\.colorScheme) var colorScheme

    @State private var appStartedAt: Date = Date()
    @State var cameraStatus: CameraStatus?
    @State var updateTimer: Timer? = nil
    @State var keepAppAliveTimer: Timer? = nil
    @State var terminationTimer: Timer? = nil
    @State var ensurePlayingTimer: Timer? = nil
    @State var isPlaying = false
    @State var recordingNow = false
    @State var recordingStartTime: Date? = nil
    @State var curTitle = "Not connected"
    @State var curArtist = "Not connected"
    @State var stateStr = "Not Connected"

    var player: AudioPlayerBridge

    var recordingSeconds: Int {
        guard let startTime = recordingStartTime else { return 0 }
        return Int(Date().timeIntervalSince(startTime))
    }

    var body: some View {
        GeometryReader { geometry in
            VStack {
                TabView(selection: $layoutIndex) {
                    ForEach(LayoutType.allCases.indices, id: \.self) { index in
                        LayoutView(
                            layoutType: LayoutType.allCases[index],
                            curArtist: $curArtist,
                            curTitle: $curTitle,
                            player: player,
                            colorScheme: colorScheme
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(PageTabViewStyle(indexDisplayMode: .automatic)) // Manual swiping

                Spacer()
                    .frame(height: geometry.size.height * 0.2) // 20% of screen height for spacing
            }
        }
        .onAppear {
            startTimers()
        }
        .onDisappear {
            stopTimers()
        }
        .sheet(isPresented: $showingCameraInfo) {
            cameraInfoSheet
        }
    }

    @State private var showingCameraInfo = false
    var cameraInfoSheet: some View {
        NavigationView {
            List {
                Text("General instructions: Use play/pause button on your smartwatch or headphones to start/stop recording.")
                Text("Camera Status: Ready - powered on, ready to record. Sleep - powered off, ready to record. Not Connected - camera not ready.")
                Text("Stay tuned: cliptracer.com Youtube: @Cliptracer")
            }
            .navigationBarTitle("Legend", displayMode: .inline)
            .navigationBarItems(trailing: Button("Done") {
                showingCameraInfo.toggle()
            })
        }
    }

    func startTimers() {
        // Start the periodic update timer
        updateTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
            updateStatus()
        }
    }

    func stopTimers() {
        updateTimer?.invalidate()
        updateTimer = nil
    }

    func updateStatus() {
        print("Updating status...")
        let elapsedTime = Date().timeIntervalSince(appStartedAt)

        player.peripheral?.requestCameraInfo()
        if let status = player.peripheral?.status, let settings = player.peripheral?.settings, player.peripheral?.connectionLost == false {
            curTitle = "\(settings.description) | \(status.description)"
            curArtist = status.encoding ? "Recording: \(recordingSeconds)s" : status.get_state

            if !recordingNow && status.encoding {
                recordingStartTime = Date()
            } else if !status.encoding {
                recordingStartTime = nil
            }
            recordingNow = status.encoding
        } else {
            curArtist = "Not connected"
        }

        if elapsedTime > 5 && curArtist == "Not connected" {
            print("Reconnecting...")
            player.reconnectPeripheral()
        }
    }
}

struct LayoutView: View {
    var layoutType: LayoutType
    @Binding var curArtist: String
    @Binding var curTitle: String
    var player: AudioPlayerBridge
    var colorScheme: ColorScheme

    private var backgroundImageName: String {
        switch layoutType {
        case .watch:
            return "watchlayout"
        case .computer:
            return "cyclingcomp"
        }
    }

    private var yPosCoef: CGFloat {
        return 1.05
    }

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                Image(backgroundImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .position(x: geometry.size.width / 2, y: geometry.size.height / 2 * yPosCoef)
                    .edgesIgnoringSafeArea(.all)
            }

            VStack {
                Text(curArtist)
                    .font(.system(size: 20))
                    .padding()
                    .foregroundColor(colorScheme == .dark ? .white : .black)

                Text(curTitle)
                    .font(.system(size: 20))
                    .padding()
                    .foregroundColor(colorScheme == .dark ? .white : .black)

                HStack(spacing: 20) {
                    ButtonView(player: player, systemImageName: "backward.fill", action: player.prevSongPressedHandler, label: "Highlight")
                    ButtonView(player: player, systemImageName: "playpause.fill", action: player.playPausePressedHandler, label: "Shutter")
                    ButtonView(player: player, systemImageName: "forward.fill", action: player.nextSongPressedHandler, label: "Highlight")
                }
                .padding(.top, 20)
            }
        }
    }
}

struct ButtonView: View {
    var player: AudioPlayerBridge
    var systemImageName: String
    var action: () -> Void
    var label: String

    var body: some View {
        VStack {
            Button(action: action) {
                Image(systemName: systemImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 30)
                    .foregroundColor(.orange)
            }
            Text(label)
                .font(.caption)
                .foregroundColor(.white)
        }
    }
}
