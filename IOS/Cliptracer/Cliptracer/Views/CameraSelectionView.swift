//
//  CameraSelectionView.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//

import SwiftUI
import CoreBluetooth
import StoreKit

struct IntroView: View {
    @Binding var showIntro: Bool
    @Binding var hasFinishedIntro: Bool
    var body: some View {
        VStack(spacing: 20) {
            Text("Welcome to Cliptracer Remote!")
                .font(.largeTitle)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)


            Button("Connect camera") {
                hasFinishedIntro = true
                showIntro = false
            }
            .padding()
            .background(Color(red: 251/255, green: 73/255, blue: 12/255))
            .foregroundColor(.white)
            .cornerRadius(10)
        }
        .padding()
    }
}

struct CameraSelectionView: View {
    @State private var hasFinishedIntro: Bool = false
    @ObservedObject var scanner = CentralManager()
    @State private var selectedPeripheral: Peripheral?
    @State private var showControlView = false
    @State private var currentPeripheral: Peripheral? = nil
    @State private var initializedPlayer: AudioPlayerBridge? = nil
    @State private var showIntro: Bool = !UserDefaults.standard.bool(forKey: "introShown")

    var body: some View {
        NavigationView {
            if !hasFinishedIntro {
                IntroView(showIntro: $showIntro, hasFinishedIntro: $hasFinishedIntro)
            } else {
                mainContentView()
            }
        }
        .onAppear {
            if !showIntro {
                setupView()
            }
        }
    }

    func mainContentView() -> some View {
        VStack {
            NavigationLink(destination: ControlViewDestination, isActive: $showControlView) { EmptyView() }

            if scanner.peripherals.isEmpty {
                Text("The GoPro camera should appear in this list. Ensure it is paired in GoPro Quik and turned on.\nIf the camera doesn't appear in the list within 15 seconds, turn it off using the side button and wait a bit longer.")
                    .foregroundColor(.gray)
                    .padding([.leading, .trailing])
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 100)  // Adjust padding as needed to position the message
            } else {
                List {
                    ForEach(scanner.peripherals, id: \.self) { peripheral in
                        ZStack {
                            HStack() {
                                Text(peripheral.name)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .renderingMode(.template)
                                    .foregroundColor(.gray)
                            }
                            Button(action: {
                                if let current = self.currentPeripheral {
                                    NSLog("Disconnecting from \(current.name)..")
                                    current.disconnect()
                                }
                                self.currentPeripheral = nil
                                NSLog("Connecting to \(peripheral.name)..")
                                peripheral.connect { error in
                                    if let error = error {
                                        NSLog("Error connecting to \(peripheral.name): \(error)")
                                        return
                                    }
                                    NSLog("Connected to \(peripheral.name)!")
                                    peripheral.goproVersion11AndAbove = nil
                                    peripheral.goproVersion13AndAbove = false
                                    
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                                        peripheral.checkCameraTime()
                                    }
                                    
                                    initializedPlayer = AudioPlayerBridge(peripheral: peripheral)
                                    self.currentPeripheral = peripheral
                                    self.selectedPeripheral = peripheral
                                    showControlView = true
                                }
                            }, label: {
                                EmptyView()
                            })
                        }
                    }
                }
            }

        }
        .onAppear {
            showIntro = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                if !self.scanner.setup{
                    self.scanner.init2()
                }
                self.setupView()
                }

        }
        .onDisappear { scanner.stop() }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                            if showIntro {
                                Button("Back") {
                                    showIntro = true
                                    hasFinishedIntro = false
                                    IntroView(showIntro: $showIntro, hasFinishedIntro: $hasFinishedIntro)
                                }
                            }
                        }
            ToolbarItem(placement: .principal) {
                if scanner.peripherals.isEmpty {
                    Text("Turn on the GoPro")
                        .fontWeight(.bold)
                } else {
                    Text("Select Camera")
                        .fontWeight(.bold)
                }
            }
        }

        .navigationViewStyle(StackNavigationViewStyle())
    }

    func setupView() {
        if let peripheral = selectedPeripheral {
            NSLog("Disconnecting from \(peripheral.name)..")
            peripheral.disconnect()
        }
        NSLog("Scanning for GoPro cameras..")
        scanner.start(withServices: [CBUUID(string: "FEA6")])
    }

    private var ControlViewDestination: some View {
        if let player = initializedPlayer {
            return AnyView(ControlView(player: player))
        } else {
            return AnyView(EmptyView())
        }
    }
}

//
//struct CameraSelectionView: View {
//    @State private var hasFinishedIntro: Bool = false
//    @State private var showControlView = false
//    @State private var showIntro: Bool = !UserDefaults.standard.bool(forKey: "introShown")
//
//    var body: some View {
//        NavigationView {
//            if !hasFinishedIntro {
//                IntroView(showIntro: $showIntro, hasFinishedIntro: $hasFinishedIntro)
//            } else {
//                mainContentView()
//            }
//        }
//
//    }
//    
//    func mainContentView() -> some View {
//        VStack {
//            NavigationLink(destination: ControlViewDestination, isActive: $showControlView) { EmptyView() }
//
//            
//                Text("The GoPro camera should appear in this list. Ensure you've paired it in GoPro Quick and turned it on")
//                    .foregroundColor(.gray)
//                    .padding([.leading, .trailing])
//                    .multilineTextAlignment(.center)
//                    .fixedSize(horizontal: false, vertical: true)
//                    .padding(.top, 100)  // Adjust padding as needed to position the message
//            
//
//        }
//        .navigationBarTitleDisplayMode(.inline)
//        .toolbar {
//            ToolbarItem(placement: .navigationBarLeading) {
//                if showIntro {
//                    Button("Back") {
//                        showIntro = true
//                        hasFinishedIntro = false
//                        IntroView(showIntro: $showIntro, hasFinishedIntro: $hasFinishedIntro)
//                    }
//                }
//            }
//            ToolbarItem(placement: .principal) {
//                
//                    Text("Turn on the GoPro")
//                        .fontWeight(.bold)
//              
//            }
//        }
//        
//        .navigationViewStyle(StackNavigationViewStyle())
//    }
//    
//
//
//    private var ControlViewDestination: some View {
//       
//        return AnyView(ControlView())
//       
//    }
//}


struct CameraSelectionView_Previews: PreviewProvider {
    static var previews: some View {
        CameraSelectionView()
    }
}
