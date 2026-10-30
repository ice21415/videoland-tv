# 體育賽事 TV（非官方）

以遙控器操作的緯來體育 Android TV 社群客戶端，提供賽程、分類、最近回放與會員授權播放。

**[下載最新 APK](https://github.com/ice21415/videoland-tv/releases/latest/download/videoland-tv-universal.apk)** · [所有版本](https://github.com/ice21415/videoland-tv/releases) · [回報問題](https://github.com/ice21415/videoland-tv/issues)

本專案非緯來官方產品，未獲官方背書。觀看會員內容仍需自己的有效帳號及對應訂閱；本程式不提供影片、帳號或 DRM 金鑰，也不解除收視限制。官方服務與登入設定變更可能造成部分功能失效。

## 相容性

| 裝置 | 狀態 |
| --- | --- |
| Android TV／Google TV、Android 5.0（API 21）以上 | 主要支援目標；各品牌仍需實機驗證 |
| 小米盒子、Android 11、32 位元 ARM | 已驗證首頁、遙控器、Google 登入與授權播放 |
| 64 位元 ARM、x86／x86_64 Android 電視／模擬器 | 通用 APK 不限制 ABI；尚未逐機驗證 |
| Android 系統的 Fire TV／其他盒子 | 可嘗試側載，登入瀏覽器、DRM 與解碼能力需確認 |
| Samsung Tizen、LG webOS、Apple TV tvOS | 無法安裝 Android APK；可外接 Android TV／Google TV 裝置 |

APK 沒有隨附原生 `.so` 函式庫，不鎖定 CPU 架構。網格隨畫面寬度調整，支援方向鍵、確認鍵與返回鍵，不要求觸控螢幕、相機、手機助手或 root。這不代表每一款電視都已測試成功。

實際可播放畫質取決於官方串流、會員權限、裝置解碼器、Widevine／HDCP 及網路。選擇 1080p 不會把低碼率來源變成更清晰的影片；不保證所有裝置皆可播放所有內容。參考 [Android TV 宣告要求](https://developer.android.com/training/tv/get-started/create)及 [Media3 DRM 支援](https://developer.android.com/media/media3/exoplayer/drm)。

## 安裝與使用

1. 從上方連結下載 `videoland-tv-universal.apk`，傳到電視並允許檔案管理器安裝未知來源應用程式。
2. 開啟「體育賽事 TV（非官方）」，可先瀏覽公開賽程。
3. Google 登入需先安裝 [TV Bro](https://github.com/truefedex/tv-bro/releases)；登入在外部瀏覽器完成，授權後返回本 App。若仍顯示滑鼠游標，長按確認鍵約一秒，按下選擇「直接導覽模式」；返回鍵會退出該模式。此模式由瀏覽器控制，App 不會永久修改瀏覽器設定。
4. 以會員密碼登入的使用者可在 App 輸入緯來會員帳密。Google 密碼不等於會員密碼。
5. 選擇有權觀看的賽事。播放器按「上」顯示控制項，選擇畫質；有選單鍵的遙控器也可直接開啟畫質選單。

如果安裝時顯示簽章不符，可能已裝過其他人簽署的測試版；需先移除該版本，再安裝正式 Release（移除會清除該 App 的本機登入狀態）。後續官方倉庫的 Release 使用相同發布簽章。

Google 登入屬相容性實作，依賴服務提供者的公開 OAuth 用戶端設定，並非本專案自有的 Google OAuth 應用；無法保證日後持續可用。這些公開 client ID 是服務設定，不是個人 Google 帳號或手機識別資料。

## 本機建置

需要 JDK 17、Android SDK Platform 34、Build Tools 34.0.0。設定 `ANDROID_HOME`，或在不提交的 `local.properties` 設定 `sdk.dir`。

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
# Windows
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

除錯版位於 `app/build/outputs/apk/debug/app-debug.apk`。使用自己的簽章建置 Release：

```sh
export RELEASE_STORE_FILE=/path/to/your-release.jks
export RELEASE_STORE_PASSWORD='your-password'
export RELEASE_KEY_ALIAS=release
export RELEASE_KEY_PASSWORD='your-password'
./gradlew assembleRelease
```

請勿提交金鑰或密碼。未提供簽章環境變數時不會產生可安裝的已簽署 Release。

## GitHub 自動建置與發布

Pull request／主分支更新會執行單元測試、Lint 與通用 APK 建置。推送 `v*` 標籤會建置已簽署 Release，在 GitHub Releases 提供 APK 及 SHA-256 校驗檔。

維護者需設定 Actions secrets：`ANDROID_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`。金鑰僅在執行期間寫入 runner 暫存目錄，不包含在原始碼與 APK 內。

## 隱私與回報

專案不包含開發測試裝置截圖、私人 IP、手機資料、登入 token、帳號資料或憑證；沒有第三方分析或錯誤回報 SDK。登入資料保留在安裝裝置的 App 私有資料區，停用 Android 備份。API 請求送往服務提供者，Google 授權在外部瀏覽器完成。詳見 [隱私說明](PRIVACY.md)。

回報相容性問題請提供電視品牌／型號、Android 版本、App 版本、操作步驟及不含個資的錯誤碼。不要附上帳密、token、授權回呼網址或未遮蔽的登入截圖。

## 授權

本專案原始碼採 [MIT](LICENSE)。第三方依賴與服務內容的權利另見 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
