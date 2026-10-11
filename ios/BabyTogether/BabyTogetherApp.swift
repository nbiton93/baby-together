import SwiftUI
import WebKit

@main struct BabyTogetherApp: App {
    var body: some Scene { WindowGroup { JournalView().background(Color(red:0.969,green:0.965,blue:0.949)).preferredColorScheme(.light) } }
}
struct JournalView: UIViewRepresentable {
    func makeCoordinator() -> NativeBridge { NativeBridge() }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        config.userContentController.add(context.coordinator, name: "baby")
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.isOpaque = false
        web.backgroundColor = UIColor(red:0.969,green:0.965,blue:0.949,alpha:1)
        context.coordinator.web = web
        if let url = Bundle.main.url(forResource: "index", withExtension: "html", subdirectory: "shared") {
            context.coordinator.assetRoot = url.deletingLastPathComponent()
            web.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        }
        return web
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    static func dismantleUIView(_ web: WKWebView, coordinator: NativeBridge) {
        web.configuration.userContentController.removeScriptMessageHandler(forName: "baby")
    }
}
