import Foundation
import UIKit
import WebKit
import AuthenticationServices
import CryptoKit
import Security

final class NativeBridge: NSObject, WKScriptMessageHandler, WKNavigationDelegate, ASWebAuthenticationPresentationContextProviding, URLSessionTaskDelegate {
    weak var web: WKWebView?
    var assetRoot: URL?
    private let io = DispatchQueue(label: "baby-together.storage")
    private var authSession: ASWebAuthenticationSession?
    private var accessToken: String?
    private var expiresAt = Date.distantPast
    private var authBusy = false
    private let scope = "https://www.googleapis.com/auth/drive.file"
    private let maxBytes = 16 * 1024 * 1024
    private let service = "com.natibiton.babytogether.google"
    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 25
        config.timeoutIntervalForResource = 35
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()
    private var clientID: String { Bundle.main.object(forInfoDictionaryKey: "GoogleClientID") as? String ?? "" }
    private var configured: Bool { clientID.hasSuffix(".apps.googleusercontent.com") && !clientID.contains("$") }
    private func failure(_ text: String) -> NSError { NSError(domain: "BabyTogether", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
    private func fileURL() throws -> URL {
        var dir = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("BabyTogether", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true; try dir.setResourceValues(values)
        return dir.appendingPathComponent("journal.json")
    }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.frameInfo.isMainFrame, message.webView === web, let url = web?.url, url.isFileURL, let root = assetRoot, url.path.hasPrefix(root.path + "/"),
              let body = message.body as? [String: Any], let id = body["id"] as? String, id.range(of: "^[0-9]{1,12}$", options: .regularExpression) != nil,
              let method = body["method"] as? String else { return }
        let args = body["args"] as? [String: Any] ?? [:]
        switch method {
        case "uuid": reply(id, true, UUID().uuidString.lowercased())
        case "config": reply(id, true, ["platform":"ios", "googleConfigured":configured])
        case "read", "write":
            io.async { do {
                let file = try self.fileURL()
                if method == "read" { let value: Any = FileManager.default.fileExists(atPath: file.path) ? try String(contentsOf: file, encoding: .utf8) : NSNull(); self.reply(id, true, value) }
                else { guard let raw = args["data"] as? String, let data = raw.data(using: .utf8), data.count <= self.maxBytes else { throw self.failure("היומן גדול מדי") }
                    _ = try JSONSerialization.jsonObject(with: data)
                    try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]); self.reply(id,true,true)
                }
            } catch { self.reply(id,false,"לא ניתן לקרוא או לשמור את היומן. הקובץ הקודם לא אופס.") } }
        case "authorize": authorize(id: id, interactive: args["interactive"] as? Bool ?? false, picker: args["picker"] as? Bool ?? false)
        case "drive": drive(id: id, args: args)
        case "forget": accessToken = nil; expiresAt = .distantPast; clearRefresh(); reply(id,true,true)
        case "export": export(id: id, args: args)
        default: reply(id,false,"פעולה לא נתמכת")
        }
    }
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        guard let url = navigationAction.request.url, url.isFileURL, let root = assetRoot, url.path.hasPrefix(root.path + "/"), navigationAction.navigationType == .other else { decisionHandler(.cancel); return }
        decisionHandler(.allow)
    }
    private func reply(_ id: String, _ ok: Bool, _ value: Any) {
        DispatchQueue.main.async {
            guard let data = try? JSONSerialization.data(withJSONObject:[id,ok,value], options:[.fragmentsAllowed]), let json = String(data:data,encoding:.utf8) else { return }
            self.web?.evaluateJavaScript("window.BTReply.apply(null,\(json));", completionHandler:nil)
        }
    }
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { web?.window ?? ASPresentationAnchor() }
    private func random() throws -> String { var b=[UInt8](repeating:0,count:32); guard SecRandomCopyBytes(kSecRandomDefault,b.count,&b)==errSecSuccess else {throw failure("לא ניתן להתחיל חיבור מאובטח")}; return base64(Data(b)) }
    private func base64(_ data: Data) -> String { data.base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"") }
    private func authorize(id: String, interactive: Bool, picker: Bool) {
        guard configured else {reply(id,false,"חיבור Google עדיין לא הוגדר בגרסה הזאת");return}
        guard !authBusy else {reply(id,false,"כבר מתבצע חיבור ל־Google");return}
        if !picker, accessToken != nil, Date() < expiresAt {reply(id,true,[:]);return}
        authBusy=true
        if !picker, let refresh = readRefresh() {
            tokenRequest(["client_id":clientID,"grant_type":"refresh_token","refresh_token":refresh]) { result in
                DispatchQueue.main.async { switch result {
                    case .success(let json): self.installToken(json);self.authBusy=false;self.reply(id,true,[:])
                    case .failure: self.clearRefresh();if interactive{self.beginAuth(id:id,picker:false)}else{self.authBusy=false;self.reply(id,false,"נדרש חיבור מחדש ל־Google דרך מסך ביחד")}
                } }
            };return
        }
        guard interactive else {authBusy=false;reply(id,false,"נדרש חיבור מחדש ל־Google דרך מסך ביחד");return}
        beginAuth(id:id,picker:picker)
    }
    private func beginAuth(id: String, picker: Bool) {
        do {
            let state=try random(), verifier=try random(), challenge=base64(Data(SHA256.hash(data:Data(verifier.utf8))))
            let scheme=clientID.components(separatedBy:".").reversed().joined(separator:"."), redirect=scheme+":/oauthredirect"
            var url=URLComponents(string:"https://accounts.google.com/o/oauth2/v2/auth")!
            var values=["client_id":clientID,"redirect_uri":redirect,"response_type":"code","scope":scope,"state":state,"code_challenge":challenge,"code_challenge_method":"S256","access_type":"offline","prompt":"consent"]
            if picker {values["trigger_onepick"]="true";values["mimetypes"]="application/json";values["allow_multiple"]="false"}
            url.queryItems=values.map{URLQueryItem(name:$0.key,value:$0.value)}
            let auth=ASWebAuthenticationSession(url:url.url!,callbackURLScheme:scheme){ callback,error in
                DispatchQueue.main.async {
                    guard error==nil,let callback=callback,callback.scheme==scheme,callback.path=="/oauthredirect",let components=URLComponents(url:callback,resolvingAgainstBaseURL:false) else {self.authBusy=false;self.reply(id,false,"החיבור ל־Google בוטל או לא הושלם");return}
                    let fields=Dictionary((components.queryItems ?? []).map{($0.name,$0.value ?? "")},uniquingKeysWith:{a,_ in a})
                    guard fields["state"]==state,let code=fields["code"],fields["error"]==nil else {self.authBusy=false;self.reply(id,false,"תשובת החיבור אינה תקינה");return}
                    self.tokenRequest(["client_id":self.clientID,"grant_type":"authorization_code","redirect_uri":redirect,"code_verifier":verifier,"code":code]){result in DispatchQueue.main.async {
                        self.authBusy=false
                        switch result {case .success(let json): self.installToken(json); self.reply(id,true,["pickedFileIds":(fields["picked_file_ids"] ?? "").split(separator:",").map(String.init)])
                        case .failure: self.reply(id,false,"לא ניתן להשלים את החיבור ל־Google")}
                    }}
                }
            }
            auth.presentationContextProvider=self;auth.prefersEphemeralWebBrowserSession=false;authSession=auth
            if !auth.start(){authBusy=false;reply(id,false,"לא ניתן לפתוח את חלון Google")}
        }catch{authBusy=false;reply(id,false,"לא ניתן להתחיל חיבור מאובטח")}
    }
    private func tokenRequest(_ values:[String:String],completion:@escaping(Result<[String:Any],Error>)->Void){
        var request=URLRequest(url:URL(string:"https://oauth2.googleapis.com/token")!);request.httpMethod="POST";request.setValue("application/x-www-form-urlencoded",forHTTPHeaderField:"Content-Type")
        var form=URLComponents();form.queryItems=values.map{URLQueryItem(name:$0.key,value:$0.value)};request.httpBody=form.percentEncodedQuery?.replacingOccurrences(of:"+",with:"%2B").data(using:.utf8)
        session.dataTask(with:request){data,response,error in
            guard error==nil,(response as? HTTPURLResponse)?.statusCode==200,let data=data,let json=(try? JSONSerialization.jsonObject(with:data)) as? [String:Any],json["access_token"] is String else {completion(.failure(self.failure("החיבור נכשל")));return}
            // OAuth must not silently return a token without the requested scope.
            if let scopes=json["scope"] as? String,!scopes.split(separator:" ").contains(Substring(self.scope)){completion(.failure(self.failure("לא ניתנה הרשאת Drive")));return}
            completion(.success(json))
        }.resume()
    }
    private func installToken(_ json:[String:Any]) {accessToken=json["access_token"] as? String;expiresAt=Date().addingTimeInterval(max(0,(json["expires_in"] as? Double ?? 3600)-120));if let refresh=json["refresh_token"] as? String{saveRefresh(refresh)}}
    private func keyQuery()->[String:Any]{[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"drive"]}
    private func saveRefresh(_ token:String){var query=keyQuery();SecItemDelete(query as CFDictionary);query[kSecValueData as String]=Data(token.utf8);query[kSecAttrAccessible as String]=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly;SecItemAdd(query as CFDictionary,nil)}
    private func readRefresh()->String?{var query=keyQuery();query[kSecReturnData as String]=true;query[kSecMatchLimit as String]=kSecMatchLimitOne;var item:CFTypeRef?;guard SecItemCopyMatching(query as CFDictionary,&item)==errSecSuccess,let data=item as? Data else{return nil};return String(data:data,encoding:.utf8)}
    private func clearRefresh(){SecItemDelete(keyQuery() as CFDictionary)}
    private func drive(id:String,args:[String:Any]){
        guard let path=args["path"] as? String,path.range(of:"^/(?:upload/)?drive/v3/files(?:/[A-Za-z0-9_-]+)?(?:/permissions(?:/[A-Za-z0-9_-]+)?)?$",options:.regularExpression) != nil,let method=args["method"] as? String,["GET","POST","PATCH","DELETE"].contains(method),let token=accessToken else{reply(id,false,"נדרש חיבור ל־Google");return}
        var url=URLComponents(string:"https://www.googleapis.com"+path)!;url.queryItems=(args["query"] as? [String:String] ?? [:]).map{URLQueryItem(name:$0.key,value:$0.value)}
        var request=URLRequest(url:url.url!);request.httpMethod=method;request.setValue("Bearer "+token,forHTTPHeaderField:"Authorization");request.setValue("application/json",forHTTPHeaderField:"Accept")
        if let body=args["body"] as? String {guard body.utf8.count<=maxBytes else{reply(id,false,"היומן גדול מדי");return};request.httpBody=Data(body.utf8);request.setValue("application/json; charset=UTF-8",forHTTPHeaderField:"Content-Type")}
        session.dataTask(with:request){data,response,error in
            guard error==nil,let response=response as? HTTPURLResponse,let data=data,data.count<=self.maxBytes else{self.reply(id,false,"הסנכרון לא הושלם. הרישומים נשמרו בטלפון.");return}
            if response.statusCode==401{DispatchQueue.main.async{self.accessToken=nil;self.expiresAt = .distantPast}}
            self.reply(id,true,["status":response.statusCode,"text":String(data:data,encoding:.utf8) ?? ""])
        }.resume()
    }
    func urlSession(_ session:URLSession,task:URLSessionTask,willPerformHTTPRedirection response:HTTPURLResponse,newRequest request:URLRequest,completionHandler:@escaping(URLRequest?)->Void){completionHandler(nil)}
    private func export(id:String,args:[String:Any]){
        guard let raw=args["data"] as? String,let data=raw.data(using:.utf8),data.count<=maxBytes,let controller=web?.window?.rootViewController else{reply(id,false,"לא ניתן לייצא גיבוי");return}
        let url=FileManager.default.temporaryDirectory.appendingPathComponent("baby-together-backup.json")
        do{try data.write(to:url,options:[.atomic,.completeFileProtection]);let share=UIActivityViewController(activityItems:[url],applicationActivities:nil);share.popoverPresentationController?.sourceView=web
            share.completionWithItemsHandler={_,completed,_,_ in try? FileManager.default.removeItem(at:url);if completed {self.reply(id,true,true)} else {self.reply(id,false,"ייצוא הגיבוי בוטל")}}
            var presenter=controller;while let presented=presenter.presentedViewController{presenter=presented};presenter.present(share,animated:true)
        }catch{reply(id,false,"לא ניתן ליצור את קובץ הגיבוי")}
    }
}
