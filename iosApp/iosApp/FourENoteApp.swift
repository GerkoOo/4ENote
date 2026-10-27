import CryptoKit
import Security
import SwiftUI
import UIKit
import FourENote

@main
struct FourENoteApp: App {
    init() {
        IosCrypto.shared.aesGcm = CryptoKitAesGcm()
        Secrets.shared.bridge = KeychainSecrets()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

/// Hosts the Compose Multiplatform UI built in Kotlin (see MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// AES-GCM for the shared Kotlin code (Crypto.ios.kt): Apple only offers it publicly in CryptoKit.
final class CryptoKitAesGcm: NSObject, AesGcmBridge {
    func seal(key: Data, nonce: Data, aad: Data, plain: Data) -> Data? {
        guard let nonce = try? AES.GCM.Nonce(data: nonce),
              let box = try? AES.GCM.seal(plain, using: SymmetricKey(data: key), nonce: nonce, authenticating: aad)
        else { return nil }
        return box.ciphertext + box.tag
    }

    func open(key: Data, nonce: Data, aad: Data, sealed: Data) -> Data? {
        guard sealed.count >= 16,
              let nonce = try? AES.GCM.Nonce(data: nonce),
              let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: sealed.dropLast(16), tag: sealed.suffix(16))
        else { return nil }
        return try? AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: aad)
    }
}

/// Small secrets (the WebDAV password) for the Kotlin side (AppStorage.ios.kt), in the Keychain.
/// This device only: they never go to iCloud Keychain or into backups restored elsewhere.
final class KeychainSecrets: NSObject, SecretBridge {
    private let service = "com.gerko.fourenote"

    func get(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func set(key: String, value: String?) {
        let match: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(match as CFDictionary)
        guard let value = value else { return }
        var add = match
        add[kSecValueData as String] = Data(value.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
}
