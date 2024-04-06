//
//  AudioPlayerBridge.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//

import Foundation
import SwiftUI
import CoreBluetooth
import AVFoundation // Import AVFoundation
import MediaPlayer  // Import MediaPlayer


class AudioPlayerBridge: UIViewController {
    var cameraStatus: CameraStatus?
    var cameraSettings: CameraSettings?

    var lastCommand: Data?
    var updateTimer: Timer? = nil
    var player: AVAudioPlayer?
    var isPlaying = false
    var peripheral: Peripheral?
    var shutterOn = false // Keeping track of the shutter state
    var shutterOnQueued = false
    var shutterOnQueuedFinished = true

    
    var curTitle = "Not connected" // Keeping track of the shutter state
    var curArtist = "Not connected" // Keeping track of the shutter state
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    init(peripheral: Peripheral?) {
        self.peripheral = peripheral
        print("Instantiated AudioPlayerBridge")
        super.init(nibName: nil, bundle: nil)
        print(Unmanaged.passUnretained(self).toOpaque())
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
    }

    // MARK: - Peripheral Methods
    
    func reconnectPeripheral() {
        self.peripheral?.connect { error in
            self.peripheral?.cbPeripheral.delegate = self.peripheral
            print(error == nil ? "Reconnected successfully." : "Failed to reconnect: \(error!.localizedDescription)")
        }
    }

    // MARK: - Audio Methods

    func setupAudioSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Setting up audio session failed.")
        }
    }
    
    func handleConnectionLoss() {
        if peripheral?.connectionLost == true{
            shutterOnQueued = true
            shutterOnQueuedFinished = false
            reconnectPeripheral()
        }
    }
    
    func handleEncoding() {
        guard let encodingValue = self.peripheral?.status?.encoding else { return }
        if encodingValue {
            self.setShutterOff()
            Thread.sleep(forTimeInterval: 2.0)
            self.powerOff()
        } else {
            self.setShutterOn()
        }
    }

    func updatePlaybackRate(isPlaying: Bool) {
        var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = player?.currentTime
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }

    func updateSongMetadata(title: String?, artist: String?) {
        var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        if let title = title {
            nowPlayingInfo[MPMediaItemPropertyTitle] = title
        }
        if let artist = artist {
            nowPlayingInfo[MPMediaItemPropertyArtist] = artist
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
    }
    

    func sendCameraCommand(command: Data){
        self.lastCommand = command
        self.peripheral?.setCommand(command: command) { result in
            switch result {
            case .success(let response):
                //Check command/response and do something
                let commandResponse: CommandResponse = response
                if ((self.lastCommand![0] == 0x01) && (commandResponse.response[1] == 0x01)){
                    //Shutter Command
                    if (self.lastCommand![2] == 0x01){
                        print("busy")
                    } else {
                        print("not busy")
                    }
                }
            case .failure(let error):
                NSLog("\(error)")
            }
        }
    }
    
    func setShutterOn() {
        self.shutterOnQueuedFinished = true
        NSLog("toggleShutterOn()")
        shutterOn.toggle()
        print("sent shitteron")
        sendCameraCommand(command: Data([0x01, 0x01, 0x01]))
    }
    
    func setEnableWifi() {
        NSLog("setEnableWifi()")
        shutterOn.toggle()
        print("sent setEnableWifi")
        sendCameraCommand(command: Data([0x17, 0x01, 0x01]))
    }
    
    func setHighlight() {
        NSLog("setHighlight()")
        self.peripheral?.setCommand(command: Data([0x18])) { result in
            switch result {
            case .success(let response):
                print("highlight sent")
            case .failure(let error):
                NSLog("\(error)")
            }
        }
    }
    
    func setShutterOff() {
        NSLog("toggleShutterOff()")
        shutterOn.toggle()
        print("sent shutteroff")
        sendCameraCommand(command: Data([0x01, 0x01, 0x00]))
    }
    
    func powerOff() {
        NSLog("powerOff()")
        self.peripheral?.setCommand(command: Data([0x05])) { result in
            switch result {
            case .success(let response):
                print("power off sent")
            case .failure(let error):
                NSLog("\(error)")
            }
        }
    }


    
    func ensurePlayingWhenEncoding(){
        guard !isPlaying else {
            return
        }
        print("isPlaying ", isPlaying)
        if curArtist != "sleep" && curArtist != "Ready" && curArtist != "Not connected" && curArtist != "waking up"{
            print("ensuring playing")
            // Reset playback to 0:00
            player?.currentTime = 0

            // Play the song
            player?.play()
            // After a second, stop the music
            self.isPlaying = true
        }
    }
    func ensureStopped(){
        guard isPlaying else {
            return
        }
        guard !shutterOnQueued else {
            return
        }
        print("Ensuring stopped")
        print(shutterOnQueuedFinished)
        guard shutterOnQueuedFinished else {
            return
        }
        print("Ensuring stopped")
        print(shutterOnQueuedFinished)
        if curArtist == "sleep" || curArtist == "Ready" || curArtist == "Not connected"{
            // Reset playback to 0:00
            print("ensuring stopping")
            player?.currentTime = 0
            self.player?.stop()
            self.isPlaying = false
        }
    }
    func playForASecond() {
        guard !isPlaying else {
            print("was playing, skipping")
            return
        }
        // Reset playback to 0:00
        player?.currentTime = 0

        // Play the song
        player?.play()
        if curArtist == "sleep" || curArtist == "Ready" || curArtist == "Not connected"{
            
            // After a second, stop the music
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                self.player?.stop()
                self.isPlaying = false
            }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            if let currentTime = self.player?.currentTime {
                var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo
                nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
                MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
            }
        }

    }
    
    func prevSongPressedHandler(){
        print("left pressed")
        self.setHighlight()
    }
    func playPausePressedHandler(){
        print("toggle handler")
        print(Unmanaged.passUnretained(self).toOpaque())
        print("connection Lost:")
        print(self.peripheral?.connectionLost)
        if self.peripheral?.connectionLost == true
        {
            print("connection lost")
            self.shutterOnQueuedFinished = false
            self.shutterOnQueued = true
            self.reconnectPeripheral()
            print("reconnecting ended")

        }
    
        print(self.peripheral?.status?.encoding)
        if self.peripheral?.status?.encoding == true {
            self.setShutterOff()
            Thread.sleep(forTimeInterval: 2.0)
            self.powerOff()
        } else {
            self.setShutterOn()
        }

        
        var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [String: Any]()
        
        if self.isPlaying {
            self.player?.pause()
            nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = 0.0 // Paused
        } else {
            self.player?.play()
            nowPlayingInfo[MPNowPlayingInfoPropertyPlaybackRate] = 1.0 // Playing
        }

        // Updating the current playback time
        if let currentTime = self.player?.currentTime {
            nowPlayingInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
        }

        // Updating the MPNowPlayingInfoCenter with the latest info
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
        
        // Toggling the isPlaying state
        self.isPlaying.toggle()
    }
    func nextSongPressedHandler(){
        print("right pressed")
        self.setHighlight()
    }
    func playSampleSong() {
        if let path = Bundle.main.path(forResource:"pulse_audio", ofType:"mp3") {
            let url = URL(fileURLWithPath: path)
            do {
                let commandCenter = MPRemoteCommandCenter.shared()
                do {
                    try AVAudioSession.sharedInstance().setCategory(.playback)
                    try AVAudioSession.sharedInstance().setActive(true)
                } catch {
                    print("Setting up audio session failed.")
                }
                
                player = try AVAudioPlayer(contentsOf: url)
                player?.prepareToPlay()
                player?.play()
                // Delay of 1 second and then stop the song
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    self.player?.pause()
                    self.isPlaying = false
                    var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo
                    nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = self.player?.currentTime ?? 0
                    nowPlayingInfo?[MPNowPlayingInfoPropertyPlaybackRate] = 0.0 // Indicating the song is paused
                    MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
                }
                // Set the metadata for the now playing center
                let songInfo: [String: Any] = [
                    MPMediaItemPropertyTitle: "Not Connected",
                    MPMediaItemPropertyArtist: "GoPro",
                    MPMediaItemPropertyPlaybackDuration: player?.duration ?? 0,
                    MPNowPlayingInfoPropertyElapsedPlaybackTime: player?.currentTime ?? 0,
                    MPNowPlayingInfoPropertyPlaybackRate: 1.0  // Indicating the song is playing
                ]
                MPNowPlayingInfoCenter.default().nowPlayingInfo = songInfo
                
                // Capture previous song command
                commandCenter.previousTrackCommand.addTarget { event in
                    print("prevSongPressedHandler")
                    self.prevSongPressedHandler()
                    return .success
                }
                
                // Capture next song command
                commandCenter.nextTrackCommand.addTarget { event in
                    print("nextSongPressedHandler")
                    self.nextSongPressedHandler()
                    return .success
                }
                
                commandCenter.togglePlayPauseCommand.addTarget { event in
                    print("togglePlayPauseCommand")
                    self.playPausePressedHandler()
                    return .success
                }

                
                commandCenter.pauseCommand.addTarget { event in
                    print("pause handler")
                    if self.peripheral?.connectionLost == true
                    {
                        self.shutterOnQueuedFinished = false
                        self.shutterOnQueued = true
                        self.reconnectPeripheral()
                        print("reconnecting ended")

                    }
                    print(self.peripheral?.status?.encoding)
                    if self.peripheral?.status?.encoding == true {
                        self.setShutterOff()
                        Thread.sleep(forTimeInterval: 2.0)
                        self.powerOff()
                    } else {
                        self.setShutterOn()
                    }

                    self.player?.pause()
                    self.isPlaying = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        if let currentTime = self.player?.currentTime {
                            var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo
                            nowPlayingInfo?[MPNowPlayingInfoPropertyElapsedPlaybackTime] = currentTime
                            MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
                        }
                    }
                    return .success
                }
                commandCenter.playCommand.addTarget { event in
                    print("play handler")
                    if self.peripheral?.connectionLost == true
                    {
                        self.shutterOnQueuedFinished = false
                        self.shutterOnQueued = true
                        self.reconnectPeripheral()
                        print("reconnecting ended")

                    }
                    print(self.peripheral?.status?.encoding)
                    if self.peripheral?.status?.encoding == true {
                        self.setShutterOff()
                        Thread.sleep(forTimeInterval: 2.0)
                        self.powerOff()
                    } else {
                        self.setShutterOn()
                    }
                    

                    self.player?.play()
                    self.isPlaying = true
                    return .success
                }
            } catch {
                print("Error loading audio: \(error)")
            }
        } else {
            print("Unable to find audio file.")
        }
    }
    
    func updateSongTitle(_ title: String) {
       // Get the current now playing information
       var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [String: Any]()
       
       // Update the title
       nowPlayingInfo[MPMediaItemPropertyTitle] = title
       curTitle = title
       // Set the updated information back
       MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
   }
   
   func updateSongArtist(_ artist: String) {
       // Get the current now playing information
       var nowPlayingInfo = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [String: Any]()
       
       // Update the title
       nowPlayingInfo[MPMediaItemPropertyArtist] = artist
       curArtist = artist
       
       // Set the updated information back
       MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlayingInfo
   }

    // MARK: - View Lifecycle

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        UIApplication.shared.beginReceivingRemoteControlEvents()
        self.becomeFirstResponder()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        UIApplication.shared.endReceivingRemoteControlEvents()
        self.resignFirstResponder()
    }

    override var canBecomeFirstResponder: Bool {
        return true
    }
}
