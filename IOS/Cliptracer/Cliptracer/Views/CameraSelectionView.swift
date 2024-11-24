//
//  CameraSelectionView.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//

import SwiftUI

struct IntroView: View {
    @Binding var showIntro: Bool
    @Binding var hasFinishedIntro: Bool
    var body: some View {
        VStack(spacing: 20) {
            Text("Welcome to Cliptracer Controller!")
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
    @State private var showControlView = false
    @State private var showIntro: Bool = !UserDefaults.standard.bool(forKey: "introShown")

    var body: some View {
        NavigationView {
            if !hasFinishedIntro {
                IntroView(showIntro: $showIntro, hasFinishedIntro: $hasFinishedIntro)
            } else {
                mainContentView()
            }
        }

    }
    
    func mainContentView() -> some View {
        VStack {
            NavigationLink(destination: ControlViewDestination, isActive: $showControlView) { EmptyView() }

            
                Text("The GoPro camera should appear in this list. Ensure you've paired it in GoPro Quick and turned it on")
                    .foregroundColor(.gray)
                    .padding([.leading, .trailing])
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 100)  // Adjust padding as needed to position the message
            

        }
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
                
                    Text("Turn on the GoPro")
                        .fontWeight(.bold)
              
            }
        }
        
        .navigationViewStyle(StackNavigationViewStyle())
    }
    


    private var ControlViewDestination: some View {
       
        return AnyView(ControlView())
       
    }
}


struct CameraSelectionView_Previews: PreviewProvider {
    static var previews: some View {
        CameraSelectionView()
    }
}
