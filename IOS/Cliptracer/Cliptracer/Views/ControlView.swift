//
//  ControlView.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//

import SwiftUI

enum LayoutType: CaseIterable {
    case computer, watch
}

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
