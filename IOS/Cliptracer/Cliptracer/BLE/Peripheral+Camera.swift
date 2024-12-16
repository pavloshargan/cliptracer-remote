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
    var mode_id: Int

    var description: String {
        print("lenses id \(lenses_id)")
        let resolution = getResolutionDescription(id: resolution_id)
        let fps = getFpsDescription(id: fps_id)
        let lenses = getLensesDescription(id: lenses_id)
        return "\(resolution)|\(fps)|\(lenses)"
    }

    func getResolutionDescription(id: Int) -> String {
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

    func getFpsDescription(id: Int) -> String {
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

    func getLensesDescription(id: Int) -> String {
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
    
    func getModeDescription(id: Int) -> String {
        switch id {
        case 12: return "Video"
        case 15: return "Looping"
        case 16: return "Photo"
        case 17: return "Burst Photo"
        case 18: return "Night Photo"
        case 19: return "Time Lapse Video"
        case 20: return "Time Lapse Photo"
        case 21: return "Night Lapse Photo"
        case 24: return "Night Lapse Photo"
        case 25: return "Time Warp Video"
        case 26: return "Night Lapse Video"
        case 27: return "Slo-Mo"
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
        print("goproVersion13AndAbove \(goproVersion13AndAbove) \n goproVersion11AndAbove \(goproVersion11AndAbove)")
        func handleDataResponse(_ data: Data) -> Result<Any, Error> {
            var messageHexString = ""
            for i in 0 ..< data.count {
                messageHexString += String(format: "%02X", data[i])
            }
            print("count \(messageHexString.count)")
            switch messageHexString.count {
            case 36:
                if messageHexString.starts(with: "1113"){
                    print(messageHexString)
                    
                    // Helper function to find a value after a specific prefix
                    func extractValue(after prefix: String, valueSize: Int, in hexString: String) -> String? {
                        guard let range = hexString.range(of: prefix) else {
                            return nil
                        }
                        let valueStartIndex = hexString.index(range.upperBound, offsetBy: 0) // Value starts right after the prefix
                        let valueEndIndex = hexString.index(valueStartIndex, offsetBy: valueSize * 2 - 1) // Each byte = 2 hex chars
                        return String(hexString[valueStartIndex...valueEndIndex])
                    }
                    
                    // Extract video time (4 bytes after "2304")
                    let videoTimeHex = extractValue(after: "2304", valueSize: 4, in: messageHexString)
                    let videoTime = Int(videoTimeHex ?? "0", radix: 16) ?? 0
                    
                    // Extract battery percentage (1 byte after "4601")
                    let batteryHex = extractValue(after: "4601", valueSize: 1, in: messageHexString)
                    let batteryPercents = Int(batteryHex ?? "0", radix: 16) ?? 0
                    
                    // Extract encoding (1 byte after "0A01")
                    let encodingHex = extractValue(after: "0A01", valueSize: 1, in: messageHexString)
                    let encoding = (encodingHex == "01")
                    
                    // Extract busy status (1 byte after "0601")
                    let busyHex = extractValue(after: "0601", valueSize: 1, in: messageHexString)
                    let busy = (busyHex == "01")
                    
                    // Create CameraStatus object
                    let res = CameraStatus(battery: batteryPercents, videoTime: videoTime, encoding: encoding, busy: busy)
                    print(res.description)
                    return .success(res)
                } else {
                    return .failure(CameraError.invalidResponse)
                }
            case 18:
                print("case 18")
                print(messageHexString)
                goproVersion13AndAbove = !goproVersion13AndAbove
                return .failure(CameraError.invalidResponse)
            case 24:
                print("case 24")
                goproVersion11AndAbove = false
                return .failure(CameraError.invalidResponse)
            case 32:
                goproVersion11AndAbove = true
                return .failure(CameraError.invalidResponse)
            case 30:
                print("case 30")
                if messageHexString.starts(with: "0E12"){
                    print(messageHexString)
                    
                    // Helper function to find a value after a setting code
                    func extractValue(after settingCode: String, in hexString: String) -> Int? {
                        guard let range = hexString.range(of: "\(settingCode)01") else {
                            return nil
                        }
                        let valueStartIndex = hexString.index(range.upperBound, offsetBy: 0) // Value is immediately after "01"
                        let valueEndIndex = hexString.index(valueStartIndex, offsetBy: 1)
                        return Int(hexString[valueStartIndex...valueEndIndex], radix: 16)
                    }
                    
                    // Extract values for resolution, fps, and lenses
                    let resolutionCode = extractValue(after: "02", in: messageHexString) ?? 0
                    let fpsCode = extractValue(after: "03", in: messageHexString) ?? extractValue(after: "EA", in: messageHexString) ?? 0
                    let lensesCode = extractValue(after: "79", in: messageHexString) ?? extractValue(after: "E5", in: messageHexString) ?? 0
                    
                    let modeCode = extractValue(after: "90", in: messageHexString) ?? 12
                    
                    // Create CameraSettings object
                    let res = CameraSettings(resolution_id: resolutionCode, fps_id: fpsCode, lenses_id: lensesCode, mode_id: modeCode)
                    
                    if (res.getModeDescription(id: modeCode) != "Video"){
                        setVideoMode()
                    }
                    
                    print(res.description)
                    return .success(res)
                    
                } else{
                    return .failure(CameraError.invalidResponse)
                    
                }
                
            case 40:
                goproVersion11AndAbove = true
                return .failure(CameraError.invalidResponse)

            default:
                print("Unexpected response length: \(messageHexString.count)")
                print(messageHexString)
                return .failure(CameraError.invalidResponse)
            }
        }
        
        if(goproVersion11AndAbove == nil){
            print("goproVersion11AndAbove is nil")
            requestData(Data([0x03,0x02, 0x13, 0x63]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleDataResponse) {}
        }
        
        //payload size,statuses query type, battery, videotime, busy, encoding
        requestData(Data([0x05, 0x13, 0x46, 0x23, 0x06, 0x0A]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleDataResponse) {
            
            //payload size,settings query type, resolution, fps, lenses
            if (self.goproVersion13AndAbove){
                self.requestData(Data([0x05, 0x12, 0x02, 0xEA, 0xE5, 0x90]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleDataResponse) {}
            }
            else{
                // resolution, fps, lenses
                self.requestData(Data([0x05, 0x12, 0x02, 0x03, 0x79, 0x90]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleDataResponse) {}
            }
            
        }
    }
    
    func checkCameraTime() {
        let serviceUUID = CBUUID(string: "FEA6")
        let commandUUID = CBUUID(string: "B5F90072-AA8D-11E3-9046-0002A5D5C51B")
        let commandResponseUUID = CBUUID(string: "B5F90073-AA8D-11E3-9046-0002A5D5C51B")
        
        func handleTimeResponse(_ data: Data) -> Result<Any, Error> {
            var messageHexString = ""
            for byte in data {
                messageHexString += String(format: "%02X", byte)
            }

            switch messageHexString.count {
            case 24:
                let currentDatetimeBytes = Array(data)
                let goproDatetimeSeconds = formatDatetime(currentDatetimeBytes: currentDatetimeBytes)
                
                let phoneTimeSeconds = Int64(Date().timeIntervalSince1970)
                let timeDifference = abs(phoneTimeSeconds - goproDatetimeSeconds)
                print("phoneTimeSeconds \(phoneTimeSeconds)")
                print("goproDatetimeSeconds \(goproDatetimeSeconds)")

                if timeDifference > 5 {
                    print("Time difference between phone and GoPro is > 5 seconds: \(timeDifference)")
                    setDateTimeOnGoPro()
                } else {
                    print("Time is synchronized. Difference: \(timeDifference) seconds")
                }
                
                return .success("Time processed")
            default:
                print("Unexpected response length: \(messageHexString.count)")
                goproVersion11AndAbove = true
                print(messageHexString)
                return .failure(CameraError.invalidResponse)
            }
        }
        // Requesting time
        requestData(Data([0x01, 0x0E]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleTimeResponse) {
            print("Time requested")
        }
    }
    
    func setVideoMode(){
        let serviceUUID = CBUUID(string: "FEA6")
        let commandUUID = CBUUID(string: "B5F90072-AA8D-11E3-9046-0002A5D5C51B")
        let commandResponseUUID = CBUUID(string: "B5F90073-AA8D-11E3-9046-0002A5D5C51B")
        
        func handleResponse(_ data: Data) -> Result<Any, Error> {return .success("request sent")}
        
        // [total number of bytes in the query, command id ( load preset), preset id value size (4 bytes), 1000 as uint32]
        requestData(Data([0x04, 0x3E, 0x02, 0x03, 0xE8]), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: commandResponseUUID, processData: handleResponse) {
                print("video mode requested")
        }
    }
    
    func setDateTimeOnGoPro() {
        let serviceUUID = CBUUID(string: "FEA6")
        let commandUUID = CBUUID(string: "B5F90072-AA8D-11E3-9046-0002A5D5C51B")
        
        // Determine the GoPro model
        print("Setting UTC date and time on GoPro")
        
        let utcDateTime: Date
        if (goproVersion11AndAbove == true) {
            utcDateTime = Date()
        } else {
            utcDateTime = Date(timeIntervalSince1970: (Date().timeIntervalSince1970 - Double(TimeZone.current.secondsFromGMT())))
        }
        
        // Extract date and time components
        let calendar = Calendar.current
        let year = calendar.component(.year, from: utcDateTime)
        let month = calendar.component(.month, from: utcDateTime)
        let day = calendar.component(.day, from: utcDateTime)
        let hour = calendar.component(.hour, from: utcDateTime)
        let minute = calendar.component(.minute, from: utcDateTime)
        let second = calendar.component(.second, from: utcDateTime)
        
        // Convert year to bytes
        let yearBytes = withUnsafeBytes(of: UInt16(year).bigEndian, Array.init)
        
        // Create date/time command
        let dateTimeCmd: [UInt8] = [
            0x09,  // Total number of bytes in the query
            0x0D,  // Command ID for set date/time
            0x07,
            yearBytes[0],               // First byte of year
            yearBytes[1],               // Second byte of year
            UInt8(month),               // Month
            UInt8(day),                 // Day
            UInt8(hour),                // Hour
            UInt8(minute),              // Minute
            UInt8(second)               // Second
        ]
        
        // Send the command using requestData
        requestData(Data(dateTimeCmd), serviceUUID: serviceUUID, commandUUID: commandUUID, commandResponseUUID: CBUUID(string: "B5F90073-AA8D-11E3-9046-0002A5D5C51B"), processData: { _ in return .success(()) }) {
            print("Date and time set on GoPro")
        }
    }
    
    func timeStringToSecond(dateTimeString: String) -> TimeInterval? {
        do {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy_MM_dd HH_mm_ss"
            if (goproVersion11AndAbove == true){
                let currentTimeZone = TimeZone.current
                let offsetInSeconds = currentTimeZone.secondsFromGMT()
                formatter.timeZone = TimeZone(secondsFromGMT: offsetInSeconds)
            }
            else{
                formatter.timeZone = TimeZone(secondsFromGMT: 0)
            }
           
            guard let datetime = formatter.date(from: dateTimeString) else { return nil }
            print("datetime parsed \(datetime)")
            return datetime.timeIntervalSince1970
           
        } catch {
            return nil
        }
    }
    
    
    func formatDatetime(currentDatetimeBytes: [UInt8]) -> Int64 {
        do {
            // Extract date and time components from the bytes
            let year = Int(UInt16(currentDatetimeBytes[4]) << 8 | UInt16(currentDatetimeBytes[5]))
            let month = Int(currentDatetimeBytes[6])
            let day = Int(currentDatetimeBytes[7])
            let hour = Int(currentDatetimeBytes[8])
            let minute = Int(currentDatetimeBytes[9])
            let second = Int(currentDatetimeBytes[10])
            
            // Construct a date string
            let stringTime = String(format: "%04d_%02d_%02d %02d_%02d_%02d", year, month, day, hour, minute, second)
            let epochSecond = timeStringToSecond(dateTimeString: stringTime)
            return Int64(epochSecond ?? 0)
        } catch {
            print("Failed to parse DateTime: \(error)")
            return 0 // Provide a default return value to satisfy return type
        }
    }
}
