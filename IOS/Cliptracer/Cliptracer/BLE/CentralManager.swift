//
//  CentralManager.swift
//  Cliptracer
//
//  Created by Pavlo Sharhan on 07.04.2024.
//

import Foundation
import CoreBluetooth

@propertyWrapper
struct Atomic<Value> {

    private var value: Value
    private let lock = NSLock()

    init(wrappedValue value: Value) {
        self.value = value
    }

    var wrappedValue: Value {
      get { return load() }
      set { store(newValue: newValue) }
    }

    func load() -> Value {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    mutating func store(newValue: Value) {
        lock.lock()
        defer { lock.unlock() }
        value = newValue
    }
}


/// A simple wrapper around CBCentralManager to handle CoreBluetooth Central related tasks
final class CentralManager: NSObject, ObservableObject {
    @Published var peripherals: [Peripheral] = []
    var setup = false
    private var manager: CBCentralManager!
    private var isReady: Bool { get { return manager.state == .poweredOn } }
    @Atomic private var onCentralStateChange: ((CBManagerState) -> Void)?

    override init() {
        super.init()
    }
    func init2(){
        let queue = DispatchQueue(label: "com.gopro.ble.central.queue", qos: .default)
        manager = CBCentralManager(delegate: self, queue: queue)
        setup = true
    }
    /// Starts a new BLE scan
    /// - Parameter withServices: the service UUIDs to scan for
    func start(withServices: [CBUUID]) {
        if isReady {
            peripherals.removeAll()
            manager.scanForPeripherals(withServices: withServices, options: nil)
        } else {
            onCentralStateChange = { [weak self] state in
                if state == .poweredOn {
                    DispatchQueue.main.async {
                        self?.start(withServices: withServices)
                    }
                }
            }
        }
    }

    /// Stops the current scan
    func stop() {
        manager.stopScan()
    }

    /// Connects the peripheral
    /// - Parameter peripheral: The peripheral to be connected
    func connectPeripheral(_ peripheral: CBPeripheral) {
        manager.connect(peripheral, options: nil)
    }

    /// Disconnects the peripheral
    /// - Parameter peripheral: The peripheral to disconnect
    func disconnectPeripheral(_ peripheral: CBPeripheral) {
        manager.cancelPeripheralConnection(peripheral)
    }
}

extension CentralManager : CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        onCentralStateChange?(central.state)
    }

    func centralManager(_ central: CBCentralManager, didDiscover cbPeripheral: CBPeripheral,
                        advertisementData: [String : Any], rssi RSSI: NSNumber) {
        guard let localName: String = advertisementData["kCBAdvDataLocalName"] as? String else { return }

        let peripheral = Peripheral(peripheral: cbPeripheral, localName: localName, manager: self)
        if peripherals.filter({$0.identifier == peripheral.identifier}).first == nil {
            DispatchQueue.main.async { [weak self] in
                self?.peripherals.append(peripheral)
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect cbPeripheral: CBPeripheral) {
        guard let peripheral = peripherals.filter({$0.identifier == cbPeripheral.identifier.uuidString}).first else { return }
        peripheral.onConnect?(nil)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect cbPeripheral: CBPeripheral, error: Error?) {
        print("FAILED TO CONNECT!!!")
        guard let peripheral = peripherals.filter({$0.identifier == cbPeripheral.identifier.uuidString}).first,
              let error = error else { return }

        peripheral.onConnect?(error)

        DispatchQueue.main.async { [weak self] in
            let index = self?.peripherals.firstIndex(of: peripheral)
            self?.peripherals.remove(at: index!)
        }

    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral cbPeripheral: CBPeripheral, error: Error?) {
        guard let peripheral = peripherals.filter({$0.identifier == cbPeripheral.identifier.uuidString}).first else { return }

        peripheral.onDisconnect?(error)

        DispatchQueue.main.async { [weak self] in
            let index = self?.peripherals.firstIndex(of: peripheral)
            self?.peripherals.remove(at: index!)
        }
    }
}
