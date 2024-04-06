//
//  CliptracerApp.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 06.04.2024.
//

import SwiftUI

@main
struct SingleWindowApp: App {
    var body: some Scene {
        WindowGroup {
            CameraSelectionView()
                .preferredColorScheme(.dark)
        }
    }
}
