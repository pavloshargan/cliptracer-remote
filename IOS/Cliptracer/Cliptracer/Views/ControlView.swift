//
//  ControlView.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//

import SwiftUI
//import NetworkExtension
//import CoreBluetooth
//
//import AVFoundation // Import AVFoundation
//import MediaPlayer  // Import MediaPlayer
//



enum LayoutType: CaseIterable {
    case computer, watch
}



//struct ControlView: View {
//    @State private var layoutIndex = 0 // Current index for layout
//    @State private var layoutChangeTimer: Timer?
//    
//    var layoutType: LayoutType {
//        LayoutType.allCases[layoutIndex % LayoutType.allCases.count]
//    }
//    
//    private var backgroundImageName: String {
//        switch layoutType {
//        case .watch:
//            return "watchlayout"
//        case .computer:
//            return "cyclingcomp"
//        }
//    }
//
//    
//    private var yPosCoef: CGFloat {
//        switch layoutType {
//        case .watch:
//            return 1.27
//        case .computer:
//            return 1.2
//        }
//    }
//    
//    
//    @Environment(\.colorScheme) var colorScheme
//    
//    @State private var appStartedAt: Date = Date()
//    @State var cameraStatus: CameraStatus?
//    @State var updateTimer: Timer? = nil
//    @State var keepAppAliveTimer: Timer? = nil
//    @State var terminationTimer: Timer? = nil
//    @State var ensurePlayingTimer: Timer? = nil
//    @State var isPlaying = false
//    @State var recordingNow = false
//    @State var recordingStartTime: Date? = nil  // This will hold the start time when the recording begins
//    var recordingSeconds: Int {
//        guard let startTime = recordingStartTime else { return -1 }
//        return Int(Date().timeIntervalSince(startTime))
//    }
//    
//    @State var shutterOn = false // Keeping track of the shutter state
//    
//    @State var curTitle = "Not connected" // Keeping track of the shutter state
//    @State var curArtist = "Not connected" // Keeping track of the shutter state
//    
//    
//    @State var stateStr = "Not Connected"
//    var playerVC: AudioPlayerBridge
//    
//    @State private var showingCameraInfo = false
//    var cameraInfoSheet: some View {
//        NavigationView {
//            List {
//                Text("""
//                    General instructions:
//                    
//                    Use play/pause button on your
//                    smartwatch or headphones to
//                    start/stop recording
//                    """)
//                
//                Text("""
//                    Camera Status:
//                    
//                    Ready - powered on, ready to record
//                    Sleep - powered off, ready to record
//                    Not Connected - camera not ready
//                    """)
//                
//                Text("""
//                    Camera Settings:
//                    
//                    Field 1: resolution
//                    Field 2: fps
//                    Field 3: lenses
//                    Field 4: battery (percents)
//                    Field 5: memory left (minutes)
//                    """)
//                
//                Text("""
//                    Lenses Abbreviations:
//                    
//                    w - wide
//                    n - narrow
//                    l - linear
//                    sv - super view
//                    msv - max super view
//                    lev - linear + horizon leveling
//                    loc - linear + horizon loc
//                    hv - hyper view
//                    """)
//                
//                Text("""
//                    Stay tuned:
//                    
//                    cliptracer.com
//                    Youtube: @Cliptracer
//                    """)
//            }
//            .navigationBarTitle("Legend", displayMode: .inline)
//            .navigationBarItems(trailing: Button("Done") {
//                showingCameraInfo.toggle()
//            })
//        }
//    }
//    
//    
//    init(player: AudioPlayerBridge) {
//        print("calling view model")
//        print(player)
//        self.playerVC = player
//    }
//    
//    func startUpdatingCameraStatus() {
//        // Initialize the timer to call getCameraStatus every second
//        self.updateTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
//            self.updateStatus()
//        }
//    }
//    
//    func stopUpdatingCameraStatus() {
//        // Invalidate the timer when it's no longer needed
//        self.updateTimer?.invalidate()
//        self.updateTimer = nil
//    }
//    
//    func startEnsurePlaying() {
//        // Initialize the timer to call getCameraStatus every second
//        self.ensurePlayingTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { _ in
//            self.playerVC.ensurePlayingWhenEncoding()
//            self.playerVC.ensureStopped()
//        }
//    }
//    
//    func stopEnsurePlaying() {
//        // Invalidate the timer when it's no longer needed
//        self.ensurePlayingTimer?.invalidate()
//        self.ensurePlayingTimer = nil
//    }
//    
//    func startKeepingAlive() {
//        let timerInterval: TimeInterval = 55
//        
//        self.keepAppAliveTimer = Timer.scheduledTimer(withTimeInterval: timerInterval, repeats: true) { _ in
//            print(self.playerVC.isPlaying)
//            self.playerVC.playForASecond()
//        }
//        
//        let terminationTime: TimeInterval = 5 * 60 * 60
//        self.terminationTimer = Timer.scheduledTimer(withTimeInterval: terminationTime, repeats: false) { _ in
//            self.stopKeepingAlive()
//        }
//    }
//    
//    func stopKeepingAlive() {
//        // Invalidate the keepAppAliveTimer when it's no longer needed
//        self.keepAppAliveTimer?.invalidate()
//        self.keepAppAliveTimer = nil
//        
//        // Also invalidate the terminationTimer
//        self.terminationTimer?.invalidate()
//        self.terminationTimer = nil
//    }
//    
//    func updateStatus() {
//        print("update status attempt")
//        let elapsedTime = Date().timeIntervalSince(appStartedAt)
//        print("update settings debug1")
//        
//        playerVC.peripheral?.requestCameraInfo()
//        
//        let status = playerVC.peripheral?.status
//        let settings = playerVC.peripheral?.settings
//        
//        
//        // Process status
//        if playerVC.peripheral?.connectionLost == false, let status = status, let setting = settings {
//            if playerVC.shutterOnQueued {
//                playerVC.shutterOnQueuedFinished = true
//                playerVC.shutterOnQueued = false
//                playerVC.playPausePressedHandler()
//            }
//            print(status.description)
//            stateStr = "\(setting.description)" + "|" + "\(status.description)"   // <- Fixed here
//            playerVC.updateSongTitle(stateStr)
//            playerVC.updateSongTitle(stateStr)
//            curTitle = stateStr
//            
//            if status.encoding == true && recordingNow == true {
//                curArtist = formattedTime(recordingSeconds + 1)
//                playerVC.updateSongArtist(formattedTime(recordingSeconds + 1))
//            } else {
//                playerVC.updateSongArtist(status.get_state)
//                curArtist = status.get_state
//            }
//            
//            if !recordingNow && status.encoding {
//                recordingStartTime = Date()
//            } else if !status.encoding {
//                recordingStartTime = nil
//            }
//            recordingNow = status.encoding
//            
//        } else if curArtist != "Not connected" {
//            print("update status debug3")
//            curArtist = "sleep"
//            playerVC.updateSongArtist("sleep")
//        }
//        
//        if elapsedTime > 5 && curArtist == "Not connected" {
//            print("Error: CameraStatus was not updated within first 5 seconds, reconnecting")
//            playerVC.reconnectPeripheral()
//        }
//        
//    }
//    
//    func prevSongHandler(){
//        print("prevSong button Handler")
//        DispatchQueue.global(qos: .userInitiated).async {
//            playerVC.prevSongPressedHandler()
//        }
//    }
//    func playPauseHandler(){
//        print("playPause button Handler, player id:")
//        playerVC.playPausePressedHandler()
//    }
//    func nextSongHandler(){
//        print("nextSong button Handler")
//        DispatchQueue.global(qos: .userInitiated).async {
//            playerVC.nextSongPressedHandler()
//        }
//    }
//    var body: some View {
//        ZStack {
//            // Background layer
//            GeometryReader { geometry in
//                        Image(backgroundImageName) // Your background image name
//                            .resizable()
//                            .aspectRatio(contentMode: .fit) // Fit the image to the screen without exceeding its bounds
//                            .frame(width: geometry.size.width, height: geometry.size.height)
//                            .position(x: geometry.size.width / 2, y: ((geometry.size.height / 2))*yPosCoef)
//                            .edgesIgnoringSafeArea(.all) // Ensure it covers the entire screen, including safe areas
//                    }
//            VStack { // Encapsulate all your views in a VStack for vertical arrangement
//                Text(curArtist)
//                    .font(.system(size: 20))
//                    .padding()
//                    .foregroundColor(colorScheme == .dark ? .white : .black)
//                //.foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255))
//                
//                Text(curTitle)
//                    .font(.system(size: 20))
//                    .foregroundColor(colorScheme == .dark ? .white : .black)
//                    .padding()
//                //.foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255))
//                HStack(spacing: 7) {
//                    VStack {
//                        Button(action: prevSongHandler) {
//                            Image(systemName: "backward.fill")
//                                .resizable()
//                                .aspectRatio(contentMode: .fit)
//                                .frame(width: 30)
//                                .foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255)) // Icon color set to orange
//                        }
//                        Text("Highlight")
//                            .foregroundColor(colorScheme == .dark ? .white : .black)
//                            .font(.caption)
//                    }
//                    
//                    VStack {
//                        Button(action: playPauseHandler) {
//                            Image(systemName: "playpause.fill")
//                                .resizable()
//                                .aspectRatio(contentMode: .fit)
//                                .frame(width: 30)
//                                .foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255)) // Icon color set to orange
//                        }
//                        Text("Shutter")
//                            .foregroundColor(colorScheme == .dark ? .white : .black)
//                            .font(.caption)
//                    }
//                    
//                    VStack {
//                        Button(action: nextSongHandler) {
//                            Image(systemName: "forward.fill")
//                                .resizable()
//                                .aspectRatio(contentMode: .fit)
//                                .frame(width: 30)
//                                .foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255)) // Icon color set to orange
//                        }
//                        Text("Highlight")
//                            .foregroundColor(colorScheme == .dark ? .white : .black)
//                            .font(.caption)
//                    }
//                    
//                }
//                .padding(.top, 40) // Add some padding on top to separate from the text
//            }
//            .onAppear {
//                playerVC.playSampleSong()
//                self.startUpdatingCameraStatus()
//                self.startKeepingAlive()
//                self.startEnsurePlaying()
//                layoutChangeTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { _ in
//                                layoutIndex = (layoutIndex + 1) % LayoutType.allCases.count
//                            }
//            }
//            .onDisappear {
//                print("disappered")
//                self.playerVC.peripheral?.disconnect()
//                self.stopUpdatingCameraStatus()
//                self.stopKeepingAlive()
//                self.stopEnsurePlaying()
//                layoutChangeTimer?.invalidate()
//            }
//            .navigationBarItems(trailing: Button(action: {
//                showingCameraInfo.toggle()
//            }, label: {
//                Image(systemName: "info.circle")
//            }))
//            .sheet(isPresented: $showingCameraInfo, content: {
//                cameraInfoSheet
//            })
//        }
//    }
//}

//
//
struct ControlView: View {
    var curArtist = "Ready"
    var curTitle = "4k|120|n|45%|55GB"
    @State private var showingCameraInfo = false
    
    var layoutType: LayoutType = .computer
    
    private var backgroundImageName: String {
        switch layoutType {
        case .watch:
            return "watchlayout"
        case .computer:
            return "cyclingcomp"
        }
    }

    
    private var yPosCoef: CGFloat {
        switch layoutType {
        case .watch:
            return 1.27
        case .computer:
            return 1.2
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
                    loc - linear + horizon loc
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
    
    var body: some View {
        ZStack {
            // Background layer
            GeometryReader { geometry in
                        Image(backgroundImageName) // Your background image name
                            .resizable()
                            .aspectRatio(contentMode: .fit) // Fit the image to the screen without exceeding its bounds
                            .frame(width: geometry.size.width, height: geometry.size.height)
                            .position(x: geometry.size.width / 2, y: ((geometry.size.height / 2))*yPosCoef)
                            .edgesIgnoringSafeArea(.all) // Ensure it covers the entire screen, including safe areas
                    }
            VStack { // Encapsulate all your views in a VStack for vertical arrangement
                Text(curArtist)
                    .font(.system(size: 20))
                    .padding()
                    .foregroundColor(.white)
                //.foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255))
                
                Text(curTitle)
                    .font(.system(size: 20))
                    .foregroundColor( .white )
                    .padding()
                //.foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255))
                HStack(spacing: 7) {
                    VStack {
                        Button(action:{}) {
                            Image(systemName: "backward.fill")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 30)
                                .foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255)) // Icon color set to orange
                        }
                        Text("Highlight")
                            .foregroundColor(.white)
                            .font(.caption)
                    }
                    
                    VStack {
                        Button(action:{}) {
                            Image(systemName: "playpause.fill")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 30)
                                .foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255)) // Icon color set to orange
                        }
                        Text("Shutter")
                            .foregroundColor(.white)
                            .font(.caption)
                    }
                    
                    VStack {
                        Button(action:{}) {
                            Image(systemName: "forward.fill")
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(width: 30)
                                .foregroundColor(Color(red: 240/255, green: 90/255, blue: 26/255)) // Icon color set to orange
                        }
                        Text("Highlight")
                            .foregroundColor(.white)
                            .font(.caption)
                    }
                    
                }
                .padding(.top, 40) // Add some padding on top to separate from the text
            }
        
            .navigationBarItems(trailing: Button(action: {
                showingCameraInfo.toggle()
            }, label: {
                Image(systemName: "info.circle")
            }))
            .sheet(isPresented: $showingCameraInfo, content: {
                cameraInfoSheet
            })
        }
    }
    
}



struct Preview_Previews: PreviewProvider {
    static var previews: some View {
        ControlView()
    }
}
