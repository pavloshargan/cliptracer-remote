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
    @State var updateStatusTimer: Timer? = nil
    @State var updateTimeTimer: Timer? = nil
    @State var keepAppAliveTimer: Timer? = nil
    @State var terminationTimer: Timer? = nil
    @State var ensurePlayingTimer: Timer? = nil
    @State var isPlaying = false
    @State var recordingNow = false
    @State var recordingStartTime: Date? = nil  // This will hold the start time when the recording begins
    @State var shutterOn = false // Keeping track of the shutter state
    @State var curTitle = "Not connected" // Keeping track of the shutter state
    @State var curArtist = "Not connected" // Keeping track of the shutter state
    @State var stateStr = "Not Connected"
    var playerVC: AudioPlayerBridge

    @State private var showingSettings = false
    @AppStorage("beepDuringRecording") private var beepDuringRecording: Bool = false
    @State private var showingCameraInfo = false
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
                            player: playerVC,
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
            if beepDuringRecording {
                playerVC.playSampleSong(songName: "beep150min")
            } else {
                playerVC.playSampleSong(songName: "silent_track")
            }
            
            self.startUpdatingCameraStatus()
            self.startUpdatingCameraTime()
            self.startKeepingAlive()
            self.startEnsurePlaying()
        }
        .onDisappear {
            print("disappered")
            self.playerVC.peripheral?.disconnect()
            self.stopUpdatingCameraStatus()
            self.stopUpdatingCameraStatus()
            self.stopKeepingAlive()
            self.stopEnsurePlaying()
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: {
                    showingCameraInfo.toggle()
                }) {
                    Image(systemName: "info.circle")
                        .foregroundColor(.blue) // Add color for visibility
                }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: {
                    showingSettings.toggle()
                }) {
                    Image(systemName: "gearshape")
                        .foregroundColor(.blue) // Add color for visibility
                }
            }
        }
        .sheet(isPresented: $showingCameraInfo) {
            cameraInfoSheet
        }
        .sheet(isPresented: $showingSettings) {
            settingsSheet
        }
    }

    var cameraInfoSheet: some View {
            NavigationView {
                List {
                    Text("""
                        General instructions:
                        Use play/pause button on your
                        smartwatch or headphones to
                        start/stop recording
                        """)
    
                    Text("""
                        You can enable or disable sound during recording by:
                        Going the settings -> toggle/untoggle beep_during_recording
                        """)
                    
                    Text("""
                        Camera Status:
                        Ready - powered on, ready to record
                        Sleep - powered off, ready to record
                        Not Connected - camera not ready
                        """)
    
                    Text("""
                        Camera Settings:
                        Field 1: resolution
                        Field 2: fps
                        Field 3: lenses
                        Field 4: battery (percents)
                        Field 5: memory left (minutes)
                        """)
    
                    Text("""
                        Lenses Abbreviations:
                        w - wide
                        n - narrow
                        l - linear
                        sv - super view
                        msv - max super view
                        lev - linear + horizon leveling
                        loc - linear + horizon lock
                        hv - hyper view
                        """)
    
                    Text("""
                        Stay tuned:
                        cliptracer.com
                        Youtube: @Cliptracer
                        """)
                }
                .navigationBarTitle("Legend", displayMode: .inline)
                .navigationBarItems(trailing: Button("Done") {
                    showingCameraInfo.toggle()
                })
            }
        }
    
        private func handleToggleChange(_ isOn: Bool) {
            print("handleToggleChange")
            // Execute the desired behavior
            if isOn {
                playerVC.playSampleSong(songName: "beep150min")
            } else {
                playerVC.playSampleSong(songName: "silent_track")
            }
        }

    
        var settingsSheet: some View {
            NavigationView {
                Form {
                    Section(header: Text("Recording Settings")) {
                        Toggle("Beep during recording", isOn: Binding(
                            get: { beepDuringRecording },
                            set: { newValue in
                                beepDuringRecording = newValue
                                handleToggleChange(newValue) // Ensure changes are saved and acted upon
                            }
                        ))
                    }

//                    Section(header: Text("Other Settings")) {
//                        Text("Placeholder for additional settings")
//                    }
                }
                .navigationBarTitle("Settings", displayMode: .inline)
                .navigationBarItems(trailing: Button("Done") {
                    showingSettings.toggle()
                })
            }
        }
    

        init(player: AudioPlayerBridge) {
            print("calling view model")
            print(player)
            self.playerVC = player
        }
    
        func startUpdatingCameraStatus() {
            // Initialize the timer to call getCameraStatus every second
            self.updateStatusTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
                self.updateStatus()
            }
        }
    
        func stopUpdatingCameraStatus() {
            // Invalidate the timer when it's no longer needed
            self.updateStatusTimer?.invalidate()
            self.updateStatusTimer = nil
        }
    
    func startUpdatingCameraTime() {
        // Initialize the timer to call getCameraStatus every second
        self.updateTimeTimer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { _ in
            self.updateTime()
        }
    }

    func stopUpdatingCameraTime() {
        // Invalidate the timer when it's no longer needed
        self.updateTimeTimer?.invalidate()
        self.updateTimeTimer = nil
    }

    
        func startEnsurePlaying() {
            // Initialize the timer to call getCameraStatus every second
            self.ensurePlayingTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
                self.playerVC.ensurePlayingWhenEncoding()
                self.playerVC.ensureStopped()
            }
        }
    
        func stopEnsurePlaying() {
            // Invalidate the timer when it's no longer needed
            self.ensurePlayingTimer?.invalidate()
            self.ensurePlayingTimer = nil
        }
    
        func startKeepingAlive() {
            let timerInterval: TimeInterval = 55
    
            self.keepAppAliveTimer = Timer.scheduledTimer(withTimeInterval: timerInterval, repeats: true) { _ in
                print(self.playerVC.isPlaying)
                self.playerVC.playForASecond()
            }
    
            let terminationTime: TimeInterval = 5 * 60 * 60
            self.terminationTimer = Timer.scheduledTimer(withTimeInterval: terminationTime, repeats: false) { _ in
                self.stopKeepingAlive()
            }
        }
    
        func stopKeepingAlive() {
            // Invalidate the keepAppAliveTimer when it's no longer needed
            self.keepAppAliveTimer?.invalidate()
            self.keepAppAliveTimer = nil
    
            // Also invalidate the terminationTimer
            self.terminationTimer?.invalidate()
            self.terminationTimer = nil
        }
    
        func updateTime(){
            print("update time attempt")
            playerVC.peripheral?.checkCameraTime()
        }
    
        func updateStatus() {
            print("update status attempt")
            let elapsedTime = Date().timeIntervalSince(appStartedAt)
            print("update settings debug1")
    
            playerVC.peripheral?.requestCameraInfo()
            //playerVC.peripheral?.checkCameraTime()

            let status = playerVC.peripheral?.status
            let settings = playerVC.peripheral?.settings
    
    
            // Process status
            if playerVC.peripheral?.connectionLost == false, let status = status, let setting = settings {
                if playerVC.shutterOnQueued {
                    playerVC.shutterOnQueuedFinished = true
                    playerVC.shutterOnQueued = false
                    playerVC.playPausePressedHandler()
                }
                print(status.description)
                stateStr = "\(setting.description)" + "|" + "\(status.description)"   // <- Fixed here
                playerVC.updateSongTitle(stateStr)
                playerVC.updateSongTitle(stateStr)
                curTitle = stateStr
    
                if status.encoding == true && recordingNow == true {
                    curArtist = formattedTime(recordingSeconds + 1)
                    playerVC.updateSongArtist(formattedTime(recordingSeconds + 1))
                } else {
                    playerVC.updateSongArtist(status.get_state)
                    curArtist = status.get_state
                }
    
                if !recordingNow && status.encoding {
                    recordingStartTime = Date()
                } else if !status.encoding {
                    recordingStartTime = nil
                }
                recordingNow = status.encoding
    
            } else if curArtist != "Not connected" {
                print("update status debug3")
                curArtist = "sleep"
                playerVC.updateSongArtist("sleep")
            }
    
            if elapsedTime > 5 && curArtist == "Not connected" {
                print("Error: CameraStatus was not updated within first 5 seconds, reconnecting")
                playerVC.reconnectPeripheral()
            }
    
        }
    
        func prevSongHandler(){
            print("prevSong button Handler")
            DispatchQueue.global(qos: .userInitiated).async {
                playerVC.prevSongPressedHandler()
            }
        }
        func playPauseHandler(){
            print("playPause button Handler, player id:")
            playerVC.playPausePressedHandler()
        }
        func nextSongHandler(){
            print("nextSong button Handler")
            DispatchQueue.global(qos: .userInitiated).async {
                playerVC.nextSongPressedHandler()
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

    
    private var onOffButtonLabel: String {
        if curArtist == "sleep" {
           return "On"
        } else {
            return "Off"
        }
    }
    
    private var shutterButtonLabel: String {
        if curArtist.contains("Recording") || curArtist.contains(":")  {
            return "Pause&Off"
        } else if curArtist == "sleep" {
            return "On&Start"
        } else if curArtist == "Ready" {
            return "Start"
        } else {
            return "Shutter"
        }
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
                    ButtonView(player: player, systemImageName: "backward.fill", action: player.prevSongPressedHandler, label: onOffButtonLabel)
                    ButtonView(player: player, systemImageName: "playpause.fill", action: player.playPausePressedHandler, label: shutterButtonLabel)
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
