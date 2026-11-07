# EzyPdf Android App Project

මෙය `https://ezypdf-app.pages.dev/` වෙබ් අඩවිය Android app එකක් ලෙස විවෘත කරන Android Studio project එකකි.

## දැනට සකස් කර ඇති දේ
- EzyPdf website එක fullscreen WebView එකක විවෘත කිරීම
- Gallery image picker සහ multiple image selection
- Camera capture සඳහා file chooser integration
- PDF blob download එක Android Downloads folder එකට save කිරීමට JavaScript bridge එකක්
- Android Back button support
- App icon සහ EzyPdf app name
- GitHub Actions මඟින් debug APK build workflow එක

## APK එක build කිරීම
1. මෙම ZIP එක extract කරන්න.
2. Android Studio හි `EzyPdf_Android_Project` folder එක open කරන්න.
3. Gradle sync අවසන් වන තුරු ඉන්න.
4. **Build > Build Bundle(s) / APK(s) > Build APK(s)** තෝරන්න.
5. Debug APK එක `app/build/outputs/apk/debug/app-debug.apk` තුළ ලැබේ.

## GitHub Actions මඟින් build කිරීම
1. Project එක GitHub repository එකකට upload කරන්න.
2. `Actions` tab එකට යන්න.
3. `Build EzyPdf APK` workflow එක තෝරා `Run workflow` කරන්න.
4. Build එක සාර්ථක වූ පසු `EzyPdf-debug-apk` artifact එකෙන් APK එක ලබාගන්න.

## වැදගත්
- මෙය දැනට **debug APK** build කරන source project එකකි; APK file එක මෙම ZIP එක තුළ නොමැත.
- පළමු build එකේදී Android SDK සහ Gradle dependencies download වීමට internet අවශ්‍ය වේ.
- Camera/Gallery සහ PDF download හැසිරීම device එකක පරීක්ෂා කළ යුතුය.
- Website එකේ පසුව සිදුවන වෙනස්කම් app එක තුළද පෙන්වෙන්නේ app එක live website එක load කරන නිසාය.
