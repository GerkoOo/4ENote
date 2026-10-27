import CryptoKit
import SwiftUI
import UIKit
import FourENote

@main
struct FourENoteApp: App {
    init() {
        IosCrypto.shared.aesGcm = CryptoKitAesGcm()
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
