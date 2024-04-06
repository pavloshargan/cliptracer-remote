//
//  Peripheral+Camera.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//


import Foundation
import CoreBluetooth

/// A struct representing the camera's Wi-Fi settings
struct WiFiSettings {
    var SSID: String
    let password: String
}

func formattedTime(_ totalSeconds: Int) -> String {
    let hours = totalSeconds / 3600
    let minutes = (totalSeconds % 3600) / 60
    let seconds = totalSeconds % 60

    if hours > 0 {
        return "\(hours)H:\(String(format: "%02d", minutes))"
    } else {
        return String(format: "%02d:%02d", minutes, seconds)
    }
}



struct CameraStatus {
    var battery: Int
    var videoTime: Int // Note: Using UInt32 for videoTime to hold larger values up to 4 bytes
    var encoding: Bool
    var busy: Bool
    
    var description: String {
        return "\(battery)%|\(formattedTime(videoTime))"
    }
    
    var get_state: String {
        if encoding {
            return "Recording"
        } else if !busy {
            return "Ready"
        } else {
            return "Not Ready"
        }
    }

}

struct CameraSettings {
    var resolution_id: Int
    var fps_id: Int
    var lenses_id: Int

    var description: String {
        print("lenses id \(lenses_id)")
        let resolution = getResolutionDescription(id: resolution_id)
        let fps = getFpsDescription(id: fps_id)
        let lenses = getLensesDescription(id: lenses_id)
        return "\(resolution)|\(fps)|\(lenses)"
    }

    private func getResolutionDescription(id: Int) -> String {
        switch id {
        case 1: return "4k"
        case 4: return "2.7k"
        case 6: return "2.7k4:3"
        case 7: return "1440"
        case 9: return "1080"
        case 18: return "4k4:3"
        case 24: return "5k"
        case 25: return "5k4:3"
        case 26: return "5.3k8:7"
        case 27: return "5.3k4:3"
        case 28: return "4k8:7"
        case 100: return "5.3k"
        default: return "?"
        }
    }

    private func getFpsDescription(id: Int) -> String {
        switch id {
        case 0: return "240"
        case 1: return "120"
        case 2: return "100"
        case 5: return "60"
        case 6: return "50"
        case 8: return "30"
        case 9: return "25"
        case 10: return "24"
        case 13: return "200"
        default: return "?"
        }
    }

    private func getLensesDescription(id: Int) -> String {
        switch id {
        case 0: return "w"
        case 2: return "n"
        case 3: return "sv"
        case 4: return "l"
        case 7: return "msv"
        case 8: return "lev"
        case 9: return "hv"
        case 10: return "loc"
        default: return "?"
        }
    }
}


struct CommandResponse {
    var command: Data
    var response: Data
}

extension Peripheral {
    
    func setCommand(command: Data, completion: ((Result<CommandResponse, Error>) -> Void)?) {
        let serviceUUID = CBUUID(string: "FEA6")
        let commandUUID = CBUUID(string: "B5F90072-AA8D-11E3-9046-0002A5D5C51B")
        let commandResponseUUID = CBUUID(string: "B5F90073-AA8D-11E3-9046-0002A5D5C51B")
        let data = Data([UInt8(command.count)] + command)
        
        let finishWithResult: (Result<CommandResponse, Error>) -> Void = { result in
            // make sure to dispatch the result on the main thread
            DispatchQueue.main.async {
                completion?(result)
            }
        }
        
        registerObserver(serviceUUID: serviceUUID, characteristicUUID: commandResponseUUID) { data in
            // The response to a command is expected to be 3 bytes
            finishWithResult(.success(CommandResponse(command:command, response:data)))
        } completion: { [weak self] error in
            // Check that we successfully enable the notification for the response before writing to the characteristic
            if error != nil { finishWithResult(.failure(error!)); return }
            self?.write(data: data, serviceUUID: serviceUUID, characteristicUUID: commandUUID) { error in
                if error != nil { finishWithResult(.failure(error!)) }
            }
        }
    }
    
    
    
    typealias DataProcessingBlock = (Data) -> Result<Any, Error>
    
    func requestData(_ commandData: Data, serviceUUID: CBUUID, commandUUID: CBUUID, commandResponseUUID: CBUUID, processData: @escaping DataProcessingBlock, completion: @escaping () -> Void) {
        
        registerObserver(serviceUUID: serviceUUID, characteristicUUID: commandResponseUUID) { data in
            let result = processData(data)
            switch result {
            case .success(let dataResult):
                self.connectionLost = false
                if let cameraStatus = dataResult as? CameraStatus {
                    self.status = cameraStatus
                } else if let cameraSettings = dataResult as? CameraSettings {
                    self.settings = cameraSettings
                }
            case .failure(let error):
                self.connectionLost = true
                print("Data processing error: \(error)")
            }
        } completion: { [weak self] error in
            if let error = error {
                self?.connectionLost = true
                print("Error registering observer: \(error)")
                return
            }
            self?.write(data: commandData, serviceUUID: serviceUUID, characteristicUUID: commandUUID) { error in
                if let error = error {
                    self?.connectionLost = true
                    print("Error writing to characteristic: \(error)")
                }
                completion()
            }
        }
    }

    func requestCameraInfo() {
        let serviceUUID = CBUUID(string: "FEA6")
        let commandUUID = CBUUID(string: "B5F90076-AA8D-11E3-9046-0002A5D5C51B")
        let commandResponseUUID = CBUUID(string: "B5F90077-AA8D-11E3-9046-0002A5D5C51B")
        
        // your handleDataResponse function should go here.
        func handleDataResponse(_ data: Data) -> Result<Any, Error> {
            var messageHexString = ""
            for i in 0 ..< data.count {
                messageHexString += String(format: "%02X", data[i])
            }
            
            switch messageHexString.count {
            case 36:
                // Check if the messageHexString is long enough
                guard messageHexString.count >= 36 else {
                    print("response less than 36 symbols")
                    print(messageHexString.count)
                    print(messageHexString)
                    return .failure(CameraError.invalidResponse)
                }
                print(messageHexString)
                //NSLog("Status Raw: \(messageHexString)")
                let startIndex = messageHexString.index(messageHexString.startIndex, offsetBy: 22)
                let endIndex = messageHexString.index(startIndex, offsetBy: 7) // Since you want the range 4...5, this is 1 position from the startIndex
                var videoTime = Int(messageHexString[startIndex...endIndex], radix: 16)
                
                
                let startIndex2 = messageHexString.index(messageHexString.startIndex, offsetBy: 34)
                let endIndex2 = messageHexString.index(startIndex2, offsetBy: 1) // Since you want the range 4...5, this is 1 position from the startIndex
                var batteryPercents = Int(messageHexString[startIndex2...endIndex2], radix: 16)
                
                let startIndex3 = messageHexString.index(messageHexString.startIndex, offsetBy: 16)
                let endIndex3 = messageHexString.index(startIndex3, offsetBy: 1) // Since you want the range 4...5, this is 1 position from the startIndex
                var encoding = Bool(messageHexString[startIndex3...endIndex3] == "01")
                
                let startIndex4 = messageHexString.index(messageHexString.startIndex, offsetBy: 10)
                let endIndex4 = messageHexString.index(startIndex4, offsetBy: 1) // Since you want the range 4...5, this is 1 position from the startIndex
                var busy = Bool(messageHexString[startIndex4...endIndex4] == "01")
                let res = CameraStatus(battery: batteryPercents ?? 0, videoTime: videoTime ?? 0, encoding: encoding, busy: busy)
                print(res.description)
                return .success(res)
                
            case 24:
                // Check if the messageHexString is long enough
                guard messageHexString.count >= 24 else {
                    print("response less than 24 symbols")
                    print(messageHexString.count)
                    print(messageHexString)
                    return .failure(CameraError.invalidResponse)
                }
                print(messageHexString)
                
                //NSLog("Status Raw: \(messageHexString)")
                let startIndex3 = messageHexString.index(messageHexString.startIndex, offsetBy: 22)
                let endIndex3 = messageHexString.index(startIndex3, offsetBy: 1) // Since you want the range 4...5, this is 1 position from the startIndex
                var lenses_code = Int(messageHexString[startIndex3...endIndex3], radix: 16)
                
                let startIndex2 = messageHexString.index(messageHexString.startIndex, offsetBy: 16)
                let endIndex2 = messageHexString.index(startIndex2, offsetBy: 1) // Since you want the range 4...5, this is 1 position from the startIndex
                var fps_code = Int(messageHexString[startIndex2...endIndex2], radix: 16)
                
                let startIndex1 = messageHexString.index(messageHexString.startIndex, offsetBy: 10)
                let endIndex1 = messageHexString.index(startIndex1, offsetBy: 1) // Since you want the range 4...5, this is 1 position from the startIndex
                var resolution_code = Int(messageHexString[startIndex1...endIndex1], radix: 16)
                let res = CameraSettings(resolution_id: resolution_code ?? 0, fps_id: fps_code ?? 0, lenses_id: lenses_code ?? 0)
                print(res.description)
                return .success(res)
                
            default:
                print("Unexpected response length: \(messageHexString.count)")
                print(messageHexString)
                return .failure(CameraError.invalidResponse)
            }
        }
        
        requestData(Data([0x05, 0x13, 0x46, 0x23, 0x06, 0x0A]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleDataResponse) {
            self.requestData(Data([0x04, 0x12, 0x02, 0x03, 0x79]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleDataResponse) {}
        }
    }

}

