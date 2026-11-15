package app.kanso.data

/** Categorie in cui Kanso divide le app. L'ordine è quello mostrato di default. */
enum class Category(val title: String) {
    COMUNICAZIONE("Comunicazione"),
    SOCIAL("Social"),
    FOTO("Foto e fotocamera"),
    VIDEO("Video e streaming"),
    MUSICA("Musica e audio"),
    GIOCHI("Giochi"),
    PRODUTTIVITA("Produttività"),
    FINANZA("Finanza"),
    SHOPPING("Shopping"),
    CIBO("Cibo e consegne"),
    VIAGGI("Mappe e viaggi"),
    SALUTE("Salute e sport"),
    ISTRUZIONE("Istruzione"),
    NOTIZIE("Notizie e lettura"),
    STRUMENTI("Strumenti"),
    ACCESSIBILITA("Accessibilità"),
    ALTRO("Altro"),
}

// Parole cercate nel package e nel nome dell'app. Le parole fino a 3 lettere devono combaciare intere.
private val keywords: List<Pair<Category, List<String>>> = listOf(
    Category.GIOCHI to listOf("game", "games", "gioco", "giochi", "supercell", "rovio", "mojang", "minecraft", "roblox", "chess", "scacchi", "puzzle", "solitaire", "solitario", "sudoku", "kingdom", "clash", "candy", "pokemon", "fortnite", "epicgames", "gameloft", "zynga", "playrix", "miniclip"),
    Category.COMUNICAZIONE to listOf("whatsapp", "telegram", "messenger", "messaging", "messaggi", "messages", "sms", "mms", "signal", "mail", "gmail", "outlook", "email", "chat", "discord", "skype", "teams", "zoom", "meet", "viber", "dialer", "phone", "telefono", "contacts", "rubrica", "thunderbird", "protonmail", "wechat"),
    Category.SOCIAL to listOf("facebook", "instagram", "twitter", "threads", "tiktok", "musically", "snapchat", "linkedin", "reddit", "pinterest", "tumblr", "mastodon", "bluesky", "bereal", "social", "tinder", "bumble", "hinge", "quora"),
    Category.FINANZA to listOf("bank", "banking", "banca", "pay", "paypal", "wallet", "revolut", "satispay", "n26", "intesa", "intesasanpaolo", "unicredit", "fineco", "bancoposta", "postepay", "crypto", "binance", "coinbase", "invest", "trading", "trade", "hype", "bper", "mediolanum", "isybank", "bnl", "credem", "widiba", "money", "finance", "finanza", "spese", "budget", "wise", "klarna", "scalapay", "nexi", "amex"),
    Category.SHOPPING to listOf("amazon", "ebay", "shein", "temu", "aliexpress", "zalando", "vinted", "subito", "ikea", "shop", "shopping", "coupon", "lidl", "conad", "coop", "esselunga", "carrefour", "decathlon", "wish", "etsy", "volantino", "volantinofacile", "doveconviene"),
    Category.CIBO to listOf("deliveroo", "glovo", "justeat", "ubereats", "food", "recipe", "recipes", "ricette", "ricetta", "thefork", "toogoodtogo", "mcdonalds", "burgerking", "starbucks", "cookpad", "giallozafferano"),
    Category.VIAGGI to listOf("maps", "map", "mappe", "waze", "uber", "taxi", "trenitalia", "italo", "booking", "airbnb", "ryanair", "easyjet", "skyscanner", "trip", "tripadvisor", "flight", "travel", "viaggi", "bus", "metro", "moovit", "citymapper", "parking", "freenow", "bolt", "lime", "enjoy", "sharenow", "telepass", "autostrade", "atac", "trainline", "expedia"),
    Category.MUSICA to listOf("spotify", "music", "musica", "audio", "podcast", "podcasts", "radio", "soundcloud", "deezer", "shazam", "audible", "tidal", "amazonmusic", "recorder", "registratore"),
    Category.VIDEO to listOf("youtube", "netflix", "primevideo", "avod", "disney", "disneyplus", "twitch", "video", "tv", "raiplay", "mediaset", "mediasetinfinity", "infinity", "dazn", "vlc", "plex", "crunchyroll", "paramount", "hbo", "max", "jellyfin", "kodi", "mxplayer", "skygo", "nowtv"),
    Category.FOTO to listOf("photo", "photos", "foto", "camera", "cam", "gallery", "galleria", "lightroom", "snapseed", "picsart", "vsco", "photoshop", "pixel", "instax", "gopro"),
    Category.SALUTE to listOf("fit", "fitness", "health", "salute", "run", "running", "strava", "workout", "sleep", "meditation", "calm", "headspace", "garmin", "fitbit", "nike", "adidas", "steps", "yoga", "diet", "dieta", "myfitnesspal", "yazio", "flo", "clue", "medico", "farmacia", "ospedale", "fse", "sport", "gym", "palestra"),
    Category.ISTRUZIONE to listOf("duolingo", "learn", "learning", "school", "scuola", "edu", "education", "babbel", "coursera", "udemy", "khan", "khanacademy", "dictionary", "dizionario", "translate", "traduttore", "classroom", "quizlet", "busuu", "universita", "studenti"),
    Category.NOTIZIE to listOf("news", "notizie", "repubblica", "corriere", "ansa", "giornale", "ilpost", "skytg24", "kindle", "reader", "books", "libri", "wattpad", "feedly", "rss", "medium", "substack", "magazine", "flipboard"),
    Category.PRODUTTIVITA to listOf("notion", "docs", "sheets", "slides", "office", "word", "excel", "powerpoint", "drive", "dropbox", "onedrive", "calendar", "calendario", "todo", "todoist", "tasks", "keep", "notes", "note", "evernote", "trello", "slack", "pdf", "scanner", "authenticator", "authy", "bitwarden", "1password", "lastpass", "obsidian", "asana", "jira", "miro", "canva", "adobe", "spid", "cie", "io"),
    Category.STRUMENTI to listOf("file", "files", "filemanager", "manager", "cleaner", "vpn", "browser", "chrome", "firefox", "brave", "opera", "duckduckgo", "keyboard", "tastiera", "gboard", "swiftkey", "launcher", "calculator", "calcolatrice", "clock", "orologio", "alarm", "sveglia", "weather", "meteo", "ilmeteo", "flashlight", "torcia", "torch", "qr", "barcode", "utility", "tools", "tool", "backup", "remote", "telecomando", "battery", "batteria", "wifi", "bluetooth", "speedtest", "termux", "widget", "wallpaper", "sfondi", "smartthings", "home", "alexa", "tuya", "googlehome"),
)

private fun tokens(text: String): List<String> =
    java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

/**
 * Sceglie la categoria di un'app. Prima conta la categoria "Gioco" dichiarata dallo sviluppatore,
 * poi le parole chiave nel package e nel nome, infine le altre categorie dichiarate ad Android.
 * [declared] è `ApplicationInfo.category` (-1 se non dichiarata).
 */
fun categorize(packageName: String, label: String, declared: Int): Category {
    if (declared == 0) return Category.GIOCHI // ApplicationInfo.CATEGORY_GAME
    // Il prefisso del package (com, it, org...) non dice nulla sull'app.
    val words = (tokens(packageName).drop(1) + tokens(label)).toSet()
    for ((category, list) in keywords) {
        if (list.any { k -> if (k.length <= 3) k in words else words.any { w -> w == k || (w.length > k.length && w.startsWith(k)) } }) return category
    }
    return when (declared) {
        1 -> Category.MUSICA
        2 -> Category.VIDEO
        3 -> Category.FOTO
        4 -> Category.SOCIAL
        5 -> Category.NOTIZIE
        6 -> Category.VIAGGI
        7 -> Category.PRODUTTIVITA
        8 -> Category.ACCESSIBILITA
        else -> Category.ALTRO
    }
}
